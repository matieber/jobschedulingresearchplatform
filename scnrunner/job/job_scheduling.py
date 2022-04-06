import logging
import os
import sys
import traceback
import json
import time

import requests

from scnrunner.hardsupp.mobile_cluster import Broker
from scnrunner.job.job_descriptor import Job, ImageInputJobBuilder


class JobScheduler(object):

    def __init__(self, broker_inst):
        self.broker = broker_inst
        self.job_nmb = 0
        self.parentLogger = logging.getLogger("scnrunner")

    def assign_job(self, job: Job) -> Job:
        self.job_nmb += 1
        return job

    def start_session(self):
        return

    def stop_session(self):
        return

    def reset_session(self):
        return


class RoundRobin(JobScheduler):

    def __init__(self, params: dict):
        super().__init__(params["broker"])
        self.devices = []
        self.lastnode = 0
        self.name = 'RoundRobin'

    def assign_job(self, job: Job) -> Job:
        super().assign_job(job)
        self.devices = self.update_devs_info()
        if len(self.devices) == 0: return job

        self.lastnode = self.lastnode + 1 if self.lastnode < len(self.devices) - 1 else 0
        job.set_node_id(self.devices[self.lastnode])
        self.parentLogger.info("[PROCESSOR]job assignment: " + job.job_id + " to " + job.node_id)
        return job

    "returns an up-to-date sorted list of device models"

    def update_devs_info(self):
        devices = []
        for dev in json.loads(self.broker.get_alldevices_info())["info"]:
            devices.append(dev["model"])
        return sorted(devices)


class RankingBasedScheduler(JobScheduler):

    def __init__(self, params: dict):
        super().__init__(params["broker"])
        '''devs_info is a dict of dicts, with device model (device_id in the future). The following is an example of its
           structure:
            {
               motorola_moto_g6: {"model": "motorola_moto_g6",
                                  "currentLevel": 0.53,
                                  "benchmark":{
                                                "mflops": 323962333,
                                                "tflite4th":161981166 
                                                },
                                  "rssi": -50,
                                  "pendingJobs": 1,
                                  "runningJobs":1
                                  }               
            }'''
        self.devs_info = dict()

    ''' Job : a job instance with undefined value for its node field
        Returns a job instance with a defined node id'''

    def assign_job(self, job: Job) -> Job:
        super().assign_job(job)
        self.update_devs_info(self.job_nmb)
        best_dev = list(self.devs_info.keys())[0]
        if len(self.devs_info) > 0:
            for dev in list(self.devs_info.keys())[0:]:
                if self.compare(best_dev, dev,
                                job) == -1:  # means that there is a device with better characteristics than the
                    best_dev = dev  # actual best_dev to execute the job
        job.set_node_id(best_dev)
        print("job assignment: " + job.job_id + " to " + job.node_id)
        return job

    def compare(self, dev1, dev2, job):
        rank_dev1 = self.evaluate(self.devs_info[dev1], job)
        rank_dev2 = self.evaluate(self.devs_info[dev2], job)
        if float(rank_dev1) > float(rank_dev2):
            return 1
        else:
            if float(rank_dev1) < float(rank_dev2):
                return -1
            else:
                return 0

    def update_devs_info(self, job_nmb):
        self.devs_info = dict()
        alldevsinfo = json.loads(self.broker.get_alldevices_info())["info"]
        for dev in alldevsinfo:
            self.devs_info[dev["model"]] = dev

    def evaluate(self, dev_info, job):
        pass


class AhESEAS(RankingBasedScheduler):

    def __init__(self, params: dict):
        super().__init__(params["broker"])
        self.name = 'AhESEAS'

    def evaluate(self, dev_info, job):
        return (int(dev_info["benchmark"]["mflops"]) * float(dev_info["currentLevel"])) / (
            int(dev_info["pendingJobs"] + 1))


class ComTECAC(RankingBasedScheduler):

    def __init__(self, params: dict):
        super().__init__(params)
        self.name = 'ComTECAC'
        # The link efficiency values we use were reported in a study of 2013 named: "Characterizing and modeling the
        # impact of wireless signal strength on smartphone battery drain" Authors: Ding, Ning and Wagner, Daniel and
        # Chen, Xiaomeng and Pathak, Abhinav and Hu, Y Charlie and Rice, Andrew
        # There is a need to reproduce a similar study to find out variations of this values for new wifi norms.
        self.linkefficiency_morethan_10kb = {-50: float(1 / 0.0018648),
                                             -80: float(1 / 0.0022644),
                                             -85: float(1 / 0.0033),
                                             -90: float(1 / 0.012654)
                                             }

        self.linkefficiency_10kb_orless = {-50: float(1 / 0.0099),
                                           -80: float(1 / 0.0106),
                                           -85: float(1 / 0.0133),
                                           -90: float(1 / 0.0346)

                                           }
        '''indexed by device model, the dictionary saves the battery level each device joined the cluster with
        #ej.: { motorola_moto_g6: 0.8
                Xiaomi_A2_lite: 1
            }
        '''
        self.devJoinBattLevel = dict()

    # netperf values are, in fact, several for each device model, i.e., one for each RSSI value
    # It is pending to develop a measurement procedure to find out these values for each mobile device model.
    def __netperf__(self, rssi_value, kb_datasize):
        if rssi_value is None or int(rssi_value) == -1: return 0
        rssi_value = int(rssi_value)
        if rssi_value > -80:
            if kb_datasize >= 10:
                return self.linkefficiency_morethan_10kb[-50]
            else:
                return self.linkefficiency_10kb_orless[-50]

        if -80 >= rssi_value > -85:
            if kb_datasize >= 10:
                return self.linkefficiency_morethan_10kb[-80]
            else:
                return self.linkefficiency_10kb_orless[-80]

        if -85 >= rssi_value > -90:
            if kb_datasize >= 10:
                return self.linkefficiency_morethan_10kb[-85]
            else:
                return self.linkefficiency_10kb_orless[-85]

        if -90 >= rssi_value:
            if kb_datasize >= 10:
                return self.linkefficiency_morethan_10kb[-90]
            else:
                return self.linkefficiency_10kb_orless[-90]

    def update_devs_info(self, job_nmb):
        super().update_devs_info(job_nmb)
        if job_nmb == 1:
            print(list(self.devs_info.keys()))
            self.reset_session()
            print(self.devJoinBattLevel)

    def reset_session(self):
        for dev in list(self.devs_info.keys())[0:]:
            self.devJoinBattLevel[dev] = self.devs_info[dev]["currentLevel"]

    def evaluate(self, dev_info, job):
        # ComTECAC = mflops * netperf * (SOC - eContrib) / (queuedJobs + 1)
        #
        # where eContrib = joinBattLevel - SOC
        #
        return float(int(dev_info["benchmark"]["mflops"]) * self.__netperf__(int(dev_info["rssi"]),
                                                                              int(job.get_input_size("KB"))) * (
                             float(dev_info["currentLevel"]) - (
                             float(self.devJoinBattLevel[dev_info["model"]]) - float(dev_info["currentLevel"]))) / (
                             int(dev_info["pendingJobs"]) + 1))


class MemoComTECAC(ComTECAC):

    def __init__(self, params: dict):
        super().__init__(params)
        self.name = 'MemoComTECAC'
        self.assignedJobs = dict()

    def evaluate(self, dev_info, job):
        # MemoComTECAC = mflops * netperf * (SOC - eContrib) / (inSessionAssignedJobs + 1)
        #
        # where eContrib = joinBattLevel - SOC
        #
        return float(int(dev_info["benchmark"]["mflops"]) * self.__netperf__(int(dev_info["rssi"]),
                                                                              int(job.get_input_size("KB"))) * (
                             float(dev_info["currentLevel"]) - (
                             float(self.devJoinBattLevel[dev_info["model"]]) - float(dev_info["currentLevel"]))) / (
                             int(self.assignedJobs[dev_info["model"]]) + 1))

    def reset_session(self):
        super().reset_session()
        for dev in list(self.devs_info.keys())[0:]:
            self.assignedJobs[dev] = 0

    def assign_job(self, jobinst: Job) -> Job:
        assignment = super().assign_job(jobinst)
        self.assignedJobs[assignment.node_id] += 1
        return assignment


'''NOTE: this scheduler is used in combination with PullBased scheduler of emanager component.
This scheduler simply forwards a job to the emanager that has a common job queue for
all devices and devices pull jobs from there on demand.'''
class PullBased(JobScheduler):

    def __init__(self, params: dict):
        super().__init__(params["broker"])
        self.name = 'PullBased'

    def assign_job(self, job: Job) -> Job:
        super().assign_job(job)
        job.set_node_id("any")
        return job