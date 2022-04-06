import json
import os
import re
import subprocess
import sys
import traceback
import time
from scnrunner.processor import Processor, ProcessorBuilder
from scnrunner.job.job_descriptor import Job
import requests
# https://stackoverflow.com/questions/714063/importing-modules-from-parent-folder
from pathlib import Path

from scnrunner.util.time_converter import to_milliseconds, from_nano_to_milliseconds

sys.path.append(str(Path('.').absolute().parent))


class LabMobileCluster(Processor):
    DEVS_BATT_INFO_FILE = "/devices_battery_info.csv"

    def __init__(self, fields):
        '''Expected fields to be initialialized dynamically after returning from super().__init__(fields):
        self.emanager_server_url: the url and port where emanager_server is running. e.g: http://localhost:1080/
        self.emanager_output_path: a directory path (relative to dew_runner.py script) where emanager server saves
                                    mobile devices uploaded results.
        self.devs_batt_init: information about the number of devices that compose the cluster and the battery level
        required to have each to start the test. e.g.: {
                                                         "motorola_moto_g6":-1.0
                                                        }
        '''
        super().__init__(fields)
        self.create_output_files()
        self.__prep_init_time__ = None
        self.broker = HTTPBroker(self.emanager_server_url)
        self.plain_processor["processor_params"]["broker"] = self.broker
        scheduler_class = ProcessorBuilder.load_klass(self.plain_processor["processor_class"])
        self.scheduler = scheduler_class(self.plain_processor["processor_params"])

    def initialize(self):
        self.broker.wait_for_emanager_server()
        # Waits until all devices join and report the battery level configured in the scenario
        try:
            if self.enable_cluster_devices():
                self.waiting_for_devices_battery_level()
                self.save_all_devices_battery_info("PROCESSING_START")
                super().initialize()
            else:
                self.infoLogger.error("Problem configuring/waiting for devices!")
                sys.exit(-1)
        except Exception as e:
            self.infoLogger.error("Problem configuring/waiting for devices!")
            self.infoLogger.error(traceback.format_exception(None, e, e.__traceback__))
            sys.exit(-1)

    def save_all_devices_battery_info(self, stage):
        # get_alldevices_info Response Example: '[{"model": "Xiaomi_Redmi_Note_7", "currentLevel": -1.0, "mflops": 914064600, "rssi": 0, "pendingJobs": 0,
        #                                           "ip":192.168.0.2, "slotId":1}]'
        devices = json.loads(self.broker.get_alldevices_info())["info"]
        self.infoLogger.info(str(devices))
        with open(LabMobileCluster.RESULTS_HOME + LabMobileCluster.DEVS_BATT_INFO_FILE, "a+") as f:
            for device in devices:
                f.write(str(device["model"]) + "," + str(stage) + "," + str(device["currentLevel"]) + "\n")

    def enable_cluster_devices(self) -> bool:
        print("Disabling all devices but " + str(list(self.devs_batt_init.keys())), end="\n", flush=True)
        self.broker.disconnect_device("all")
        self.broker.unplug_device("all")
        for dev in list(self.devs_batt_init.keys()):
            if not self.broker.connect_device(dev):
                self.infoLogger.error("Aborted test: not all devices are ready to start the test. "
                                      "Check that emanager server is up and all devices of the scenario are registered")
                return False
        print("Done", flush=True)
        self.infoLogger.info("Devices for the test are registered and virtually connected.")
        return True

    '''
    def waiting_for_devices_to_join_cluster(self):
        expected_devices = len(list(self.devs_batt_init.keys()))
        joined_devices = []
        print("Waiting for device/s to join..", end='', flush=True)
        while len(joined_devices) < expected_devices:
            time.sleep(5)
            print(".", end='', flush=True)
            joined_devices = list(json.loads(self.broker.get_alldevices_info())["info"])
        print("Done", flush=True)
        self.infoLogger.info(str(expected_devices) + " devices have joined.")
    '''

    def waiting_for_devices_battery_level(self):
        '''parse target battery levels for participant devices
                   battlevels example:

                    {
                        'motorola_moto_g6':0.22,
                        'samsung_SM_A022M':0.82,
                        'Xiaomi_Redmi_Note_7':0.68,
                        'samsung_SM_A305G':0.3,
                        'Xiaomi_M2004J19C':1.0
                    }
                '''
        joined_devices = json.loads(self.broker.get_alldevices_info())["info"]
        self.save_all_devices_battery_info("PREP_START")
        self.submit_device_prep_jobs([item["model"] for item in joined_devices], self.devs_batt_init)
        time.sleep(3)
        self.infoLogger.info("Waiting for devices to reach battery level: " + str(self.devs_batt_init))
        init_prep_time = time.monotonic_ns()

        #TODO: eliminate polling and indirection by making devices to inform job_results to this component instead of
        # to the emanager_server
        self.wait_for_prepjob_completion()
        self.save_elapsed_time("PREP_STAGE", from_nano_to_milliseconds(time.monotonic_ns() - init_prep_time))
        self.save_all_devices_battery_info("PREP_END")

    def submit_device_prep_jobs(self, joinedDevices, battLevelDict):
        for deviceModel in joinedDevices:
            initial_battery_level = -1.0 if deviceModel not in list(battLevelDict.keys()) else battLevelDict[
                deviceModel]
            if float(initial_battery_level) != -1.0:
                charging_job = self.create_job("./templates/fast_charge-jobtemplate.json", -1.0,
                                               initial_battery_level,
                                               deviceModel + "_charge_job",
                                               deviceModel + "_charge_job", deviceModel,
                                               Job.get_job_desc_home() + deviceModel + "_charge_job.json")
                self.broker.submit_job(charging_job.descriptor_uri)
                self.infoLogger.info("Battery Charge task for device " + deviceModel + " [submitted]")

                discharging_job = self.create_job("./templates/slow_discharge-jobtemplate.json", -1.0,
                                                  initial_battery_level,
                                                  deviceModel + "_discharge_job",
                                                  deviceModel + "_discharge_job", deviceModel,
                                                  Job.get_job_desc_home() + deviceModel + "_discharge_job.json")
                self.broker.submit_job(discharging_job.descriptor_uri)
                self.infoLogger.info("Battery Discharge task for device " + deviceModel + " [submitted]")
            else:
                self.infoLogger.info("Battery preparation task is not necessary for model: " + deviceModel)

    def create_job(self, template_path, start_battery, end_battery, job_id, job_name, dev_id, descriptor):
        with open(template_path, "r") as content:
            job_template = json.load(content)
        job_inst = Job()
        job_inst.set_job_id(job_id)
        job_inst.job_name = job_name
        job_inst.json_template = job_template.copy()

        job_inst.json_template["benchmarkDefinitions"][0]["variants"][0]["energyPreconditionRunStage"][
            "startBatteryLevel"] = float(start_battery)
        job_inst.json_template["benchmarkDefinitions"][0]["variants"][0]["energyPreconditionRunStage"][
            "endBatteryLevel"] = float(end_battery)

        job_inst.set_node_id(dev_id)
        job_inst.set_descriptor_uri(descriptor)
        job_inst.flush()
        return job_inst

    def wait_for_prepjob_completion(self):
        joined_devices = json.loads(self.broker.get_alldevices_info())["info"]
        print("Waiting for devices to complete battery preparation tasks...", end='', flush=True)
        while not self.assert_battery_level(joined_devices, self.devs_batt_init) and \
                not self.check_jobs_completed(joined_devices):
            time.sleep(5)
            print(".", end='', flush=True)
            joined_devices = json.loads(self.broker.get_alldevices_info())["info"]
        print("Done", flush=True)
        self.infoLogger.info("Devices reached battery level configured. Current level: " + str(joined_devices))

    def assert_battery_level(self, joined_devices, batt_level_dict):
        for device in joined_devices:
            if float(batt_level_dict[device["model"]]) != -1:
                if float(device["currentLevel"]) != float(batt_level_dict[device["model"]]):
                    return False
        return True

    # Returns False if at least one of the devices passed as argument has pending or running jobs
    def check_jobs_completed(self, joined_devices):
        for device in joined_devices:
            if int(device["pendingJobs"]) > 0 or int(device["runningJobs"]) > 0:
                return False
        return True

    def wait_for_job_completion(self):
        print("Waiting for devices to complete assigned jobs..", end='', flush=True)
        while not self.check_jobs_completed(json.loads(self.broker.get_alldevices_info())["info"]):
            time.sleep(5)
            print(".", end='', flush=True)
        print("Done", flush=True)
        self.save_all_devices_battery_info("PROCESSING_END")

    def process_job(self, job_instance: Job):
        assigned_job = self.scheduler.assign_job(job_instance)
        assigned_job.flush()
        self.broker.submit_job(assigned_job.descriptor_uri)

    def all_jobs_completed(self):
        self.wait_for_job_completion()
        self.infoLogger.info("[PROCESSOR]all jobs done")
        self.save_elapsed_time("SCN_EXEC_TIME", from_nano_to_milliseconds(time.monotonic_ns() - self.get_init_test_time()))
        self.collect_results()
        #self.flush_logcat_files()
        return True

    def create_output_files(self):
        with open(LabMobileCluster.RESULTS_HOME + LabMobileCluster.DEVS_BATT_INFO_FILE, 'w') as f:
            f.write("device,stage,battery_level\n")

    def flush_logcat_files(self):
        joined_devices = json.loads(self.broker.get_alldevices_info())["info"]
        for device in joined_devices:
            self.infoLogger.info("[PROCESSOR] flushing "+ device["model"] + " logcat")
            logcat_file = LabMobileCluster.RESULTS_HOME + device["model"] + "_LOGCAT.txt"
            params = ["./scripts/flush_logcat.sh", logcat_file, device["ip"]]
            proc = subprocess.Popen(params, stderr=subprocess.PIPE)
            error = False
            while True:
                line = proc.stderr.readline()
                self.infoLogger.info(line)
                if 'OSError' in str(line):
                    error = True
                    break
                if not line:
                    break
            if error:
                self.infoLogger.info("[PROCESSOR] couldn't flush " + device["model"] + " logcat file")

    def collect_results(self):
        '''
            Expected directory tree:
                    self.switch_manager_output_path __
                                                      |__mobile_model1__
                                                      |                 |__results_<scn_id_1>.zip
                                                      |                 |__results_<scn_id_2>.zip
                                                      |                 |__...
                                                      |
                                                      |__mobile_model2__
                                                      |                 |__results_<scn_id_1>.zip
                                                      |                 |__results_<scn_id_2>.zip
                                                      |                 |__...
                                                      |
                                                      |__mobile_model3__
                                                      |                 |__results_<scn_id_1>.zip
                                                      |                 |__results_<scn_id_2>.zip
                                                      |                 |__...
                                                      ...
        '''
        self.save_result("device,currentmillis,frameno,success,jobInitTime(millis),detectTime(millis),inputNetTime(millis),"
                         "preprocessingTime(millis),inputSize(kb),rssi,battLevel[init;end],recognition", "\n")
        import shutil
        devs = os.listdir(self.emanager_output_path)
        for dev in devs:
            root_dir = os.path.join(self.emanager_output_path, dev)
            files = os.listdir(root_dir)
            for zipfile in files:
                zippath = os.path.join(root_dir, zipfile)
                if not (zipfile.endswith("idle_discharging_job.zip") or zipfile.endswith("fast_charging_job.zip")) and \
                        zipfile.endswith(".zip"):
                    print("Unzipping " + zippath)
                    shutil.unpack_archive(filename=zippath, extract_dir=root_dir)
                    self.consolidate_results(root_dir, dev)
                os.remove(zippath)

    def consolidate_results(self, results_dirpath, dev):
        allfiles = os.listdir(results_dirpath)
        for txtfile in [file for file in allfiles if file.endswith(".txt")]:
            txtpath = os.path.join(results_dirpath, txtfile)
            print("Reading records from " + txtpath)
            with open(txtpath, 'r') as f:
                for line in f.readlines()[1:]:
                    self.save_result(dev + "," + line)
            os.remove(txtpath)


class Broker:

    def submit_job(self):
        pass

    def get_devices_info(self):
        pass

    def get_jobs_info(self):
        pass

    def connect_device(self, dev):
        pass

    def disconnect_device(self, dev):
        pass


class HTTPBroker(Broker):
    put_into_charging_ac_data = '?requiredEnergyState=charging_ac&slotId='
    put_into_discharging_data = '?requiredEnergyState=discharging&slotId='
    connected_data = {'requiredVirtualConnectionState': 'connected'}
    disconnected_data = {'requiredVirtualConnectionState': 'disconnected'}

    def __init__(self, base, job_submission_service="job/Submitter", jobs_service="jobs", singledevice_service="info",
                 alldevices_service="info/all", connection_service="connection", energy_service="energy"):
        self.base_url = base
        self.job_submission = base + job_submission_service
        self.device_info = base + singledevice_service
        self.alldevices_info = base + alldevices_service
        self.jobs_info = base + jobs_service
        self.virtual_connection = base + connection_service
        self.energy = base + energy_service

    def submit_job(self, job_desc_file) -> requests.Response:
        response = requests.post(self.job_submission, files={'data': open(job_desc_file, 'rb')})
        return response

    def get_device_info(self, dev_id) -> requests.Response:
        return requests.get(self.device_info + "/" + dev_id).text

    def get_alldevices_info(self, connected_status="true") -> requests.Response:
        while True:
            try:
                return requests.get(self.alldevices_info, params={'connected': connected_status}).text
            except ConnectionError:
                time.sleep(5)

    def get_jobs_info(self):
        return requests.get(self.jobs_info).text

    def wait_for_emanager_server(self):
        print("Connecting to switch manager server..", end='', flush=True)
        while True:
            try:
                self.get_alldevices_info()
                print("Done", flush=True)
                return
            except ConnectionError:
                print(".", end='', flush=True)
                try:
                    time.sleep(5)
                except KeyboardInterrupt:
                    sys.exit(-1)

    '''
        Put device into virtually connected which means it is a device that is within the range of the hotspot.
    '''
    def connect_device(self, dev) -> bool:
        return bool(json.loads(
            requests.put(self.virtual_connection + "/" + dev, data=json.dumps(HTTPBroker.connected_data)).text)[
                        "success"])

    '''
    Put device into virtually disconnected which means it simulates a device that is out of the range of the hotspot
    '''
    def disconnect_device(self, dev) -> bool:
        return bool(json.loads(
            requests.put(self.virtual_connection + "/" + dev, data=json.dumps(HTTPBroker.disconnected_data)).text)[
                        "success"])


    '''Interrupts the current power of the device.'''
    def unplug_device(self, dev, slotId=0) -> bool:
        url=str(self.energy) + "/" + str(dev) + HTTPBroker.put_into_discharging_data + str(slotId)
        print(str(url))
        return bool(json.loads(requests.put(url).text)["success"])

    '''Put the device into charging state'''
    def plug_device(self, dev, slotId=0) -> bool:
        url = str(self.energy) + "/" + str(dev) + HTTPBroker.put_into_charging_ac_data + str(slotId)
        return bool(json.loads(requests.put(url).text)["success"])
