import queue

import requests
import web
import json
import os
import threading
import sys
import enum
import time
import subprocess
import serial
import re
from datetime import datetime
import logging
import signal
from tkinter import messagebox
import tkinter
from queue import Queue

urls = (
    '/energy/(.+)', 'EnergySwitchService',
    '/device/(.+)', 'DeviceService',
    '/job/(.+)', 'JobService',
    '/info/(.+)', 'InfoService',
    '/devices/(.+)', 'ConnectedDevicesService',
    '/connection/(.+)', 'MobilityService'
)


class Error(Exception):
    isSevereError = False
    specificMessage = None

    def __init__(self, isSevereError, specificMessage):
        self.isSevere = isSevereError
        self.specificMessage = specificMessage


class SevereError(Error):

    def __init__(self, specificMessage):
        super().__init__(True, specificMessage)


class RegularError(Error):

    def __init__(self, specificMessage):
        super().__init__(False, specificMessage)


class DeviceStateType(enum.Enum):
    STARTING = "starting"
    IDLE = "idle"
    BUSY = "busy"
    TERMINATED = "terminated"

    @staticmethod
    def from_str(label):
        if label in ('idle'):
            return DeviceStateType.IDLE
        elif label in ('busy'):
            return DeviceStateType.BUSY
        elif label in ('starting'):
            return DeviceStateType.STARTING
        elif label in ('terminated'):
            return DeviceStateType.TERMINATED
        else:
            raise NotImplementedError


class SwitchStateType(enum.Enum):
    ON_AC = "on_ac"
    ON_USB = "on_usb"
    OFF = "off"
    ANY = "any"

    @staticmethod
    def from_str(label):
        if label in ('on_ac', 'charging_ac'):
            return SwitchStateType.ON_AC
        if label in ('on_usb', 'charging_usb'):
            return SwitchStateType.ON_USB
        elif label in ('off', 'discharging'):
            return SwitchStateType.OFF
        elif label in ('any'):
            return SwitchStateType.ANY
        else:
            raise NotImplementedError


class ArduinoSwitchManager:

    def __init__(self, logger, maxDevices, params):
        self.arduino = None
        self.noOfInputs = maxDevices
        self.onCodes = ['G', 'H', 'I', 'J']
        self.offCodes = ['g', 'h', 'i', 'j']
        self.logger = logger
        self.initializeArduinoDevice()
        self.ensureOn()

    # ----- Start Arduino-specific methods -----#

    def initializeArduinoDevice(self, devFile=None):
        if devFile is None:
            files = [f for f in os.listdir('/dev') if re.match(r'ttyACM[0-9]$', f)]
            if len(files) == 0:
                raise Exception("Device not found!")
            devFile = files[0]
        self.arduino = serial.Serial("/dev/" + devFile, 9600)
        time.sleep(5)
        self.logger.info("Arduino device initialized.")

    # Switch the state of a specific input whose Id is [1:noOfInputs]
    def switchArduinoState(self, state, USB_id):
        if USB_id < 1 or USB_id > self.noOfInputs:
            raise Exception("Invalid arduino input!")
        if state == SwitchStateType.ON_AC:
            self.writeArduinoChar(self.onCodes[USB_id - 1])
        elif state == SwitchStateType.OFF:
            self.writeArduinoChar(self.offCodes[USB_id - 1])
        else:
            raise Exception("Invalid switch state!")

    def writeArduinoChar(self, char):
        if self.arduino is None:
            raise Exception("Device not initialized!")
        try:
            self.arduino.write(char.encode('UTF-8'))
            # time.sleep(.1)
            out = self.arduino.readline()
            self.logger.info('[output]#Receiving...' + str(out))
            self.arduino.reset_input_buffer()
        except Exception as e:
            raise e

    # ----- End Arduino-specific methods -----#

    def ensureOff(self):
        self.writeArduinoChar('0')

    def ensureOn(self):
        self.writeArduinoChar('F')

    def switchTo(self, deviceModel, state, slotId):
        if deviceModel == "all" and state == SwitchStateType.OFF:
            self.ensureOff()
        elif deviceModel == "all" and state == SwitchStateType.ON_AC:
            self.ensureOn()
        else:
            self.switchArduinoState(state, slotId)

    # Call when the server shuts down and all devices have been disconnected
    def dispose(self):
        self.ensureOff()
        self.arduino.close()

    def getEnergyState(self):
        return ['unknown' for i in range(self.noOfInputs)]


class ESP8266SwitchManager:

    def __init__(self, logger, maxDevices, params):
        self.espchip = None
        self.noOfInputs = maxDevices
        self.onCodes = ['1', '2', '3', '4']
        self.offCodes = ['5', '6', '7', '8']
        self.logger = logger
        self.ip = self.initializeESP8266Device(params["wifi_SSID"], params["wifi_PASS"])
        response = self.__ping_ESP()
        if str(response).lower() == 'true':
            print("ESP8266 at " + self.ip, 'is up!')
            self.on_off_service = "http://"+ str(self.ip) + "/OnOff?handle="
            self.ensureOn()
        else:
            print("ESP8266 at " + self.ip, 'is down!')
            raise Exception

    # ----- Start ESP8266-specific methods -----#

    def __ping_ESP(self):
        timeout = 300
        command = ['ping', '-c', '1', '-w', str(timeout), self.ip]
        # run parameters: discard output and error messages
        result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return result.returncode == 0

    def initializeESP8266Device(self, ssid, password):
        '''if devFile is None:
			files = [f for f in os.listdir('/dev') if re.match(r'ttyUSB[0-9]$', f)]
			if len(files) == 0:
				raise Exception("Device not found!")
			devFile = files[0]'''
        # self.arduino = serial.Serial("/dev/" + devFile, 115200)
        self.espchip = serial.Serial("/dev/ttyUSB0", 115200)
        time.sleep(5)
        try:
            #ssid = "isistan"
            #password = "Mn5w4ttsEWXEiHU7"
            # arduino.write(('campus ' + ' ' + password).encode('UTF-8'))
            self.espchip.write((ssid + ' ' + password).encode('UTF-8'))
            out = self.espchip.readline()
            out = self.espchip.readline()
            out = self.espchip.readline()
            print("ESP8266 address: "+ str(out))
            address = str(out).split(':')
            a, b, c, d = str(address[1]).split('.')
            ret = a + '.' + b + '.' + c + '.' + d[0] + d[1] + d[2]
            # the nested ifs are to avoid a error and consider ip addresses that end with one and two instead of three
            # decimal digits
            if not str(d[2]).isdigit():
                ret = ret[:-1]
                if not str(d[1]).isdigit():
                    ret = ret[:-1]
            print("ESP8266 device initialized at " + str(ret).strip() + ". Now you can unplug the device.")
            return str(ret).strip()
        except Exception as e:
            raise e

    # Switch the state of a specific input whose Id is [1:noOfInputs]
    def switchESP8266State(self, state, USB_id):
        if USB_id < 1 or USB_id > self.noOfInputs:
            raise Exception("Invalid ESP8266 input!")
        if state == SwitchStateType.ON_AC:
            self.writeESP8266Char(self.onCodes[USB_id - 1])
        elif state == SwitchStateType.OFF:
            self.writeESP8266Char(self.offCodes[USB_id - 1])
        else:
            raise Exception("Invalid switch state!")

    def writeESP8266Char(self, char):

        try:
            print(self.on_off_service + char)
            resp = requests.get(self.on_off_service + char)
            # self.espchip.write(char.encode('UTF-8'))
            time.sleep(.1)
            self.logger.info('[output]#Receiving...' + str(resp.text))
        except Exception as e:
            raise e

    # ----- End ESP8266-specific methods -----#

    def ensureOff(self):
        self.writeESP8266Char('0')

    def ensureOn(self):
        self.writeESP8266Char('9')

    def switchTo(self, deviceModel, state, slotId):
        if deviceModel == "all" and state == SwitchStateType.OFF:
            self.ensureOff()
        elif deviceModel == "all" and state == SwitchStateType.ON_AC:
            self.ensureOn()
        else:
            self.switchESP8266State(state, slotId)

    # Call when the server shuts down and all devices have been disconnected
    def dispose(self):
        self.ensureOff()
        self.espchip.close()

    def getEnergyState(self):
        return ['unknown' for i in range(self.noOfInputs)]


class MockSwitchManager:

    def __init__(self, logger, maxSupportedDevices, params):
        self.maxSupportedDevices = maxSupportedDevices

    def ensureOn(self):
        self.showSwitchDialog("Plug all devices")

    def ensureOff(self):
        self.showSwitchDialog("Unplug all devices")

    def showSwitchDialog(self, message):
        parent = tkinter.Tk()  # Create the object
        parent.withdraw()
        info = messagebox.showinfo('Switch energy', message, parent=parent)

    def switchTo(self, deviceModel, state, slotId):
        newState = "ON" if state == SwitchStateType.ON_USB or state == SwitchStateType.ON_AC else "OFF"
        self.showSwitchDialog("Put " + str(slotId) + " for " + deviceModel + " to " + newState)

    def getEnergyState(self):
        return ['unknown' for i in range(self.maxSupportedDevices)]


class NullSwitchManager(MockSwitchManager):

    def showSwitchDialog(self, message):
        pass


class DeviceJobQueue:

    def __init__(self):
        self.queue = Queue()
        self.currJobId = None


# First-Come First-Served
class FCFS:
    def __init__(self, logger):
        self.logger = logger
        self.pendingJobsByDeviceLock = threading.Lock()
        self.pendingJobsByDevice = {}
        self.singleJobsQueue = Queue()

    def onDeviceEnter(self, deviceModel):
        self.pendingJobsByDeviceLock.acquire()
        # (job queue, current job)
        self.pendingJobsByDevice[deviceModel] = DeviceJobQueue()
        self.pendingJobsByDeviceLock.release()

    def onArriveJob(self, nextJobId, deviceModel, jobData):
        self.logger.info('[output]#Job Submission id:' + nextJobId + ' for device:' + deviceModel)
        if str(deviceModel).lower() == "any":
            self.singleJobsQueue.put((nextJobId, jobData))
        else:
            self.pendingJobsByDevice[deviceModel].queue.put((nextJobId, jobData))

    def nextJob(self, deviceModel):
        deviceEntry = self.pendingJobsByDevice[deviceModel]

        # first consume device jobs queue. if empty then  consume from shared jobs queue
        p = None
        try:
            p = deviceEntry.queue.get(block=False)
        except queue.Empty:
            try:
                p = self.singleJobsQueue.get(block=False)
            except queue.Empty:
                return None, None

        '''if deviceEntry.queue.empty():
            if self.singleJobsQueue.empty():
                return None, None
            p = self.singleJobsQueue.get()
        else:
            p = deviceEntry.queue.get()
        '''
        if p is not None:
            jobId = p[0]
            taskData = p[1]
            taskToExec = taskData["devices"][0]["variants"]
            taskData["runOrder"] = taskToExec
            deviceEntry.currJobId = jobId
            return taskData, jobId
        else:
            return None, None

    '''backup original nextJob
    
        deviceEntry = self.pendingJobsByDevice[deviceModel]
        if deviceEntry.queue.empty():            
            return None, None
        p = deviceEntry.queue.get()
        jobId = p[0]
        taskData = p[1]
        taskToExec = taskData["devices"][0]["variants"]
        taskData["runOrder"] = taskToExec
        deviceEntry.currJobId = jobId
        return taskData, jobId
    '''

    ''' 
		The method empties the jobs queue for the device specified as parameter. currJobID, i.e, the job  
		pulled by the device before this method call, remains without changes.
	'''

    def resetDeviceJobsState(self, deviceModel):
        deviceEntry = self.pendingJobsByDevice[deviceModel]
        deviceEntry.queue = Queue()
        deviceEntry.currJobId = None

    def countPendingJobs(self, deviceModel):
        return self.pendingJobsByDevice[deviceModel].queue.qsize()

    def currentJobByDevice(self, deviceModel):
        return self.pendingJobsByDevice[deviceModel].currJobId


class DeviceState:

    def __init__(self):
        self.rssi = 0
        self.currentBatteryLevel = -1.0
        self.firstBatteryLevel = -1.0
        self.ip = None
        self.slotId = None
        self.runningJobs = 0
        self.virtuallyConnected = True

    def toString(self):
        ret = "ip: " + str(self.ip) + "  rssi: " + str(self.rssi) + " curBattLevel: " + str(self.currentBatteryLevel) \
              + " running_jobs: " + str(self.runningJobs) + " virtuallyConnected: " + str(self.virtuallyConnected) + " slotId: " + str(self.slotId)
        return ret


class Synchronizer:
    def __init__(self, logger, scheduler):
        self.logger = logger
        self.registeredDevicesLock = threading.RLock()
        self.deviceState = {}
        self.scheduler = scheduler

    def updateDevice(self, deviceModel, webData, deviceIp):
        self.registeredDevicesLock.acquire()
        if not deviceModel in self.deviceState:
            # First time, so the device is joining...
            path = PROFILES_FOLDER + "/" + deviceModel
            os.makedirs(path, exist_ok=True)
            deviceData = DeviceState()
            deviceData.ip = deviceIp
            deviceData.firstBatteryLevel = float(webData["currentBatteryLevel"])
            deviceData.currentBatteryLevel = float(webData["currentBatteryLevel"])
            deviceData.slotId = int(webData["slotId"])
            self.deviceState[deviceModel] = deviceData
            self.scheduler.onDeviceEnter(deviceModel)
            self.logger.info("[statistics]#" + deviceModel + "at " + deviceIp + ", slotID: " + str(deviceData.slotId) +" registered")
            self.registeredDevicesLock.release()
        # TODO Maintain profiles in memory
        else:
            # The device is updating dynamic info (e.g. battery level
            self.deviceState[deviceModel].rssi = int(webData["rssi"])
            self.deviceState[deviceModel].currentBatteryLevel = float(webData["currentBatteryLevel"])
            self.registeredDevicesLock.release()
            self.logger.info("[output]#" + str(self.deviceState[deviceModel].toString()))

    def setDeviceConnectionState(self, devModel, virtuallyConnected=True):
        self.registeredDevicesLock.acquire()
        try:
            self.deviceState[devModel].virtuallyConnected = virtuallyConnected
            self.logger.info("[output]#" + str(self.deviceState[devModel].toString()))
        except Exception as e:
            self.logger.info("[output]# Exception at setDeviceConnectionState. Is " + str(devModel) + " registered?")
            raise Exception(devModel + " device is not registered") from e
        finally:
            self.registeredDevicesLock.release()

    def isDeviceVirtuallyConnected(self, devModel):
        self.registeredDevicesLock.acquire()
        ret = False
        if devModel in self.deviceState:
            ret = self.deviceState[devModel].virtuallyConnected
        self.registeredDevicesLock.release()
        return ret

    def getRegisteredDevices(self):
        registered = None
        self.registeredDevicesLock.acquire()
        registered = self.deviceState.keys()
        self.registeredDevicesLock.release()
        return registered

    # Returns a (battery_upon_joining, current_battery, battery_state)
    def getBatteryInfo(self, deviceModel):
        self.registeredDevicesLock.acquire()
        deviceData = self.deviceState[deviceModel]
        battInfoTriplet = (deviceData.firstBatteryLevel, deviceData.currentBatteryLevel, deviceData.slotId)
        self.registeredDevicesLock.release()
        return battInfoTriplet

    def getRSSIInfo(self, deviceModel):
        self.registeredDevicesLock.acquire()
        rssi = self.deviceState[deviceModel].rssi
        self.registeredDevicesLock.release()
        return rssi

    def getRunningJobsInfo(self, deviceModel):
        self.registeredDevicesLock.acquire()
        runningJobs = self.deviceState[deviceModel].runningJobs
        self.registeredDevicesLock.release()
        return runningJobs

    def getAllDeviceInfo(self, deviceModel):
        info_dict = {}
        self.registeredDevicesLock.acquire()
        deviceData = self.deviceState[deviceModel]

        info_dict["running_jobs"] = deviceData.runningJobs
        info_dict["rssi"] = deviceData.rssi
        info_dict["currentBatteryLevel"] = deviceData.currentBatteryLevel
        info_dict["ip"] = deviceData.ip
        info_dict["slotId"] = deviceData.slotId
        info_dict["virtuallyConnected"] = deviceData.virtuallyConnected
        info_dict["pending_jobs"] = self.countPendingJobsByDevice(deviceModel)
        self.registeredDevicesLock.release()

        return info_dict

    def getNextTaskListFor(self, deviceModel):
        taskData = None
        jobId = None
        if self.isDeviceVirtuallyConnected(deviceModel):
            self.registeredDevicesLock.acquire()
            taskData, jobId = self.scheduler.nextJob(deviceModel)
            if jobId is not None:
                self.logger.info("[statistics]#" + deviceModel + ",startBenchmark-" + str(jobId))
                self.deviceState[deviceModel].runningJobs += 1
            self.logger.info("[output]# After " + deviceModel + " requests a job. QueuedJobs: " + str(synchronizer.countPendingJobsByDevice(deviceModel)) +
                             ". Currently running  jobs: " + str(self.deviceState[deviceModel].runningJobs) +
                             ". Last JobId sent: " + str(jobId))
            self.registeredDevicesLock.release()
        else:
            self.logger.info("[output]# Ignoring " + deviceModel + " jobs requests due to it's virtually disconnected")

        return taskData, jobId


    def processArrivedJob(self, jobData):
        # nextJobId = str(time.time())
        nextJobId = jobData["benchmarkDefinitions"][0]["benchmarkId"]
        deviceModel = jobData["devices"][0]["deviceModel"]
        scheduler.onArriveJob(nextJobId, deviceModel, jobData)

    def currentJobByDevice(self, deviceModel):
        self.registeredDevicesLock.acquire()
        jobsCount = scheduler.currentJobByDevice(deviceModel)
        self.registeredDevicesLock.release()
        return jobsCount

    def processArrivedJobResult(self, deviceModel, content, validResultPost: bool):
        self.registeredDevicesLock.acquire()
        self.deviceState[deviceModel].runningJobs -= 1
        self.registeredDevicesLock.release()
        if validResultPost:
            self.logger.info("[statistics]#" + deviceModel + ", postResult: " + str(content))

    def countPendingJobsByDevice(self, device):
        self.registeredDevicesLock.acquire()
        pendingJobs = scheduler.countPendingJobs(device)
        self.registeredDevicesLock.release()
        return pendingJobs

    def stopAttachedDevices(self):
        self.logger.info("[output]#Attached devices are being stopped")
        params = ['./scripts/stop_normapp.sh']
        for deviceModel in self.getRegisteredDevices():
            ip = self.deviceState[deviceModel].ip
            params.append(ip)
        proc = subprocess.Popen(params, stderr=subprocess.PIPE)
        error = False
        while True:
            line = proc.stderr.readline()
            self.logger.info(line)
            if 'OSError' in str(line):
                error = True
                break
            if not line:
                break
        if error:
            self.logger.info("[output]#Couldn't stop some devices!")

    def resetDeviceJobsState(self, deviceModel):
        self.registeredDevicesLock.acquire()
        scheduler.resetDeviceJobsState(deviceModel)
        self.deviceState[deviceModel].runningJobs = 0
        self.registeredDevicesLock.release()

class DewSimWebPyService:

    def __init__(self):
        web.header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS', unique=True)
        web.header('Access-Control-Allow-Headers', 'Origin, Content-Type', unique=True)
        web.header('Access-Control-Allow-Origin', '*', unique=True)
        web.header('Content-Type', 'application/json', unique=True)

    # Differentiates between severe errors (i.e. the client should cease operation)
    # versus less severe errors (i.e. the client should re-attempt to do the request, such as re-uploading a result file)
    # Just for now, if any error is returned, the client will cease operation
    def buildJSONError(self, ex, doPrint=True):
        if doPrint:
            logger.error(str(ex))
        error = {}
        error['success'] = False
        error['message'] = ex.specificMessage
        error['errorIsSevere'] = ex.isSevereError
        return json.dumps(error, ensure_ascii=False)

    def log(self, httpMethod, requester_entity):
        logger.info("[http_output]#Processing " + httpMethod + "," + requester_entity)

    def logError(self, httpMethod, requester_entity, error):
        logger.error("[http_output]#Error in " + httpMethod + "," + requester_entity)
        logger.error("[http_output]#" + error)

    def defaultSuccessResponse(self, message=""):
        response = {}
        response['success'] = True
        response['message'] = message
        return json.dumps(response, ensure_ascii=False)

    # Updates device data
    # /module/[requester_entity]
    def PUT(self, requester_entity):
        try:
            self.log("PUT", requester_entity)
            logger.info("web.input details:" + web.input().__str__())
            return self.doPut(requester_entity, web)
        except Error as e:
            data = self.buildJSONError(e)
            self.logError("PUT", requester_entity, data)
            raise web.HTTPError("404", data=data)

    # Gets data for device
    # /module/[requester_entity]
    def GET(self, requester_entity):
        try:
            self.log("GET", requester_entity)
            return self.doGet(requester_entity, web)
        except Error as e:
            data = self.buildJSONError(e)
            self.logError("GET", requester_entity, data)
            raise web.HTTPError("404", data=data)

    # Uploads data to server
    # /module/[requester_entity]
    # File contents in the body
    def POST(self, requester_entity):
        try:
            self.log("POST", requester_entity)
            return self.doPost(requester_entity, web)
        except Error as e:
            data = self.buildJSONError(e)
            self.logError("POST", requester_entity, data)
            raise web.HTTPError("404", data=data)


class EnergySwitchService(DewSimWebPyService):

    def __init__(self):
        super().__init__()

    def doPut(self, deviceModel, web):
        try:
            # "requiredEnergyState" = "charging_ac", "charging_usb", or "discharging" (default = "discharging")
            # "slotId" = int[0..3]
            data = web.input(requiredEnergyState="discharging")
            logger.info(
                "[output]# " + deviceModel + " asks motrol to put slotId:" + data.slotId + " in " + data.requiredEnergyState)
            requiredEnergyState = SwitchStateType.from_str(data.requiredEnergyState)

            switchManager.switchTo(deviceModel, requiredEnergyState, int(data.slotId))
            return self.defaultSuccessResponse()
        except SevereError as err:
            raise err
        except RegularError as err:
            raise err
        except Exception as e:
            raise RegularError("Problem processing get request!")

    # Returns energy switch state for a device
    def doGet(self, deviceModel, web):
        try:
            state = json.dumps(switchManager.getEnergyState(), ensure_ascii=False)
            return self.defaultSuccessResponse(state)
        except Exception as e:
            logger.error(str(e))
            raise RegularError("Problem updating device info!")


class InfoService(DewSimWebPyService):

    def __init__(self):
        super().__init__()
        # Hardcoded values until have a mechanism to populate such information on the fly, i.e, at the time a
        # device joins the cluster.
        self.benchmark = {}

        # Linpack for android benchmark multithread
        self.benchmark["samsung_SM_A022M"] = {"mflops": 242765772, "tflite4th": 230627483, "cow_bcs": 155855625}
        self.benchmark["samsung_SM_A305G"] = {"mflops": 674633950, "tflite4th": 505975462, "cow_bcs": 0}
        self.benchmark["Xiaomi_Redmi_Note_7"] = {"mflops": 914064600, "tflite4th": 685548450, "cow_bcs": 484454238}
        self.benchmark["Xiaomi_Mi_A2_Lite"] = {"mflops": 642355050, "tflite4th": 321177525, "cow_bcs": 0}
        self.benchmark["motorola_moto_g6"] = {"mflops": 323962333, "tflite4th": 161981166, "cow_bcs": 139303803}
        self.benchmark["motorola_moto_g9_play"] = {"mflops": 841400523, "tflite4th": 546910339, "cow_bcs": 434162669}

        self.benchmark["Xiaomi_M2004J19C"] = {"mflops": 1214064600, "tflite4th": 0}
        self.benchmark["motorola_moto_g7_power"] = {"mflops": 0, "tflite4th": 0}
        self.benchmark["samsung_SM_P610"] = {"mflops": 0, "tflite4th": 0}

    def doGet(self, deviceModel, web):
        try:
            synchronizer.registeredDevicesLock.acquire()
            data = web.input(connected="any")
            connected = str(data.connected).lower()
            devicesToInform = [deviceModel] if deviceModel != "all" else synchronizer.getRegisteredDevices()
            response = {}
            response["success"] = True
            response["info"] = []
            for device in devicesToInform:
                infotuple = synchronizer.getAllDeviceInfo(device)
                virtuallyConnected = infotuple["virtuallyConnected"]
                print("model: " + device + " pendingJobs: "+ str(infotuple["pending_jobs"]) + " runningJobs: " + str(infotuple["running_jobs"]))
                if connected == "any" or (connected == str(virtuallyConnected).lower()):
                    nextDevice = {"model": device, "currentLevel": infotuple["currentBatteryLevel"],
                                  "benchmark": self.benchmark[device], "rssi": infotuple["rssi"],
                                  "pendingJobs": infotuple["pending_jobs"], "runningJobs": infotuple["running_jobs"],
                                  "connected:": virtuallyConnected, "slotId": infotuple["slotId"], "ip":infotuple["ip"]}
                    response["info"].append(nextDevice)
            return json.dumps(response, ensure_ascii=False)
        except Exception as e:
            logger.error(str(e))
            raise SevereError("Problem processing get request!")
        finally:
            synchronizer.registeredDevicesLock.release()


class MobilityService(DewSimWebPyService):

    def __init__(self):
        super().__init__()

    def doPut(self, deviceModel, web):
        synchronizer.registeredDevicesLock.acquire()
        try:
            response = {}
            response["success"] = True
            response["info"] = []
            connected_devices = {}
            affected_devices = [deviceModel] if deviceModel != "all" else synchronizer.getRegisteredDevices()
            for device in affected_devices:
                try:
                    # "requiredVirtualConnectionState" = "connected" or "disconnected"
                    connectionServiceData = json.loads(web.data())["requiredVirtualConnectionState"]
                    if str(connectionServiceData) == "connected":
                        synchronizer.setDeviceConnectionState(device, True)
                    else:
                        synchronizer.setDeviceConnectionState(device, False)
                    connected_devices[device] = connectionServiceData
                    logger.info("[output]# " + deviceModel + " connection state modified, now is virtually: "
                                + str(connectionServiceData))
                except SevereError as err:
                    raise err
                except RegularError as err:
                    raise err
                except Exception as e:
                    raise RegularError("Problem processing DeviceVirtualConnectionState request! " + str(e.args))
            response["info"] = connected_devices
            return json.dumps(response, ensure_ascii=False)
        finally:
            synchronizer.registeredDevicesLock.release()


class DeviceService(DewSimWebPyService):

    def __init__(self):
        super().__init__()

    def doGet(self, deviceModel, web):
        try:
            task, jobId = synchronizer.getNextTaskListFor(deviceModel)
            response = {}
            response["success"] = True if not task == None else False
            response["benchmarkData"] = task if not task == None else {}
            response["jobId"] = jobId if not jobId == None else ""
            return json.dumps(response, ensure_ascii=False)
        except Exception as e:
            logger.error(str(e))
            raise SevereError("Problem processing get jobs request!")

    def doPut(self, deviceModel, web):
        try:
            # Info (JSON dictionary) might be:
            # "currentBatteryLevel" (float [0.0, 1.0])
            data = json.loads(web.data())
            synchronizer.updateDevice(deviceModel, data, web.ctx['ip'])
            return self.defaultSuccessResponse()
        except Exception as e:
            logger.error(str(e))
            raise RegularError("Problem updating device info!")

    def doPost(self, deviceModel, web):
        try:
            jobId = synchronizer.currentJobByDevice(deviceModel)
            validResultPost = synchronizer.isDeviceVirtuallyConnected(deviceModel)
            if validResultPost:
                data = web.input()
                contents = data["fileName"]
                path = PROFILES_FOLDER + "/" + deviceModel + "/"
                os.makedirs(path, exist_ok=True)
                path = path + "results_" + str(jobId) + ".zip"

                with open(path, "wb") as f:
                    f.write(contents)
            else:
                logger.info("[output]# Ignoring " + deviceModel + " " + str(jobId) +
                            "job result submission due to it's virtually disconnected")
            synchronizer.processArrivedJobResult(deviceModel, path, validResultPost)
            return self.defaultSuccessResponse(message=str(path))
        except Exception as e:
            logger.error(str(e))
            raise SevereError("Problem saving benchmark result!")

# this class processes post requests of incoming jobs
class JobService(DewSimWebPyService):

    def __init__(self):
        super().__init__()

    def doPost(self, submitting_pc, web):
        try:
            fileContents = web.input().data
            synchronizer.processArrivedJob(json.loads(fileContents))
            return self.defaultSuccessResponse()
        except Exception as e:
            logger.error(str(e))
            raise SevereError("Problem submitting job data!")

    def doPut(self, deviceModel, web):
        try:
            synchronizer.resetDeviceJobsState(deviceModel)
            devstate=str(synchronizer.getAllDeviceInfo(deviceModel))
            return self.defaultSuccessResponse(message=devstate)
        except Exception as e:
            logger.error(str(e))
            raise RegularError("Problem reseting device jobs queue!")


def looperThreadFunction(webapp, semaphore, switchManager, synchronizer):
    logger.info("Looper thread started.")
    semaphore.acquire()
    switchManager.ensureOff()
    synchronizer.stopAttachedDevices()
    logger.info("Shutting down server...")
    time.sleep(5)
    webapp.stop()
    logger.info("Server stopped.")


def signal_handler(sig, frame):
    logger.info('Server needs to be stopped!')
    try:
        mutexFinish.release()
    except Error as e:
        logger.error(str(e))
    logger.info("Ctrl+C processed.")


def buildScheduler(logger, schedulerClass):
    schedulerDict = {"FCFS": FCFS}
    return schedulerDict[schedulerClass](logger)


def buildSwitchManager(logger, driverClass, energyConfig):
    driverDict = {"ArduinoSwitchManager": ArduinoSwitchManager,
                  "MockSwitchManager": MockSwitchManager,
                  "NullSwitchManager": NullSwitchManager,
                  "ESP8266SwitchManager": ESP8266SwitchManager}
    params = None
    if not "params" in energyConfig.keys():
        params = {}
    else:
        params = energyConfig["params"]
    manager = driverDict[driverClass](logger, int(energyConfig["maxSupportedDevices"]), params)
    return manager


def attachDevices(logger, ENERGY_HARDWARE):
    logger.info("[output]#Attaching devices...")
    params = ["./scripts/install_apk_" + ENERGY_HARDWARE + ".sh"]
    if len(sys.argv) > 1:
        params.append(sys.argv[1])
    proc = subprocess.Popen(params, stderr=subprocess.PIPE)
    error = False
    while True:
        line = proc.stderr.readline()
        logger.info(line)
        if 'OSError' in str(line):
            error = True
            break
        if not line:
            break
    if error:
        logger.info("[output]#Error when attaching devices!")
        sys.exit(-1)


logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
fh = logging.FileHandler('log.txt')
fh.setLevel(logging.INFO)
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
fh.setFormatter(formatter)
ch.setFormatter(formatter)
logger.addHandler(fh)
logger.addHandler(ch)

logger.info("Starting server, " + str(datetime.now()))

BENCHMARK_CONFIG_FILE = None
PROFILES_FOLDER = None
MOCK_ENERGY = False
ENERGY_CONFIGURATION = None

try:
    with open('serverConfig.json') as json_file:
        data = json.load(json_file)
        PROFILES_FOLDER = data['server']['profilesFolder']
        BENCHMARK_CONFIG_FILE = data['benchmark']['benchmarkConfigFile']
        ENERGY_HARDWARE = data['benchmark']['energyHardware']
        ENERGY_CONFIGURATION = data['benchmark']['energyHardwareDefinitions'][ENERGY_HARDWARE]
        SCHEDULER_ALGORITHM = data['benchmark']['scheduler']
except Exception as e:
    logger.error("Error accessing serverConfig.json")
    sys.exit(-1)

logger.info("*** Press ctrl+C if you want to stop the test at any point ***")
logger.info("*** You can also kill -KILL or kill -TERM this process ***")

logger.info("BENCHMARK_CONFIG_FILE: " + BENCHMARK_CONFIG_FILE)
logger.info("PROFILES_FOLDER: " + str(PROFILES_FOLDER))
logger.info("ENERGY_HARDWARE: " + str(ENERGY_HARDWARE))
logger.info("SCHEDULER_ALGORITHM: " + str(SCHEDULER_ALGORITHM))

logger.info("Loading energy hardware configuration: " + ENERGY_HARDWARE)
switchManager = None
try:
    switchManager = buildSwitchManager(logger, ENERGY_HARDWARE, ENERGY_CONFIGURATION)
except Exception as e:
    logger.info(e)
    logger.info("Can't initialize switch manager!")
    sys.exit(-1)

scheduler = None
try:
    scheduler = buildScheduler(logger, SCHEDULER_ALGORITHM)
except Exception as e:
    logger.info(e)
    logger.info("Can't initialize scheduler!")
    sys.exit(-1)

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

try:
    with open('serverConfig.json') as json_file:
        data = json.load(json_file)
        os.environ["PORT"] = str(data['server']['httpPort'])
except Exception as e:
    logger.error(str(e))
    sys.exit(-1)

mutexFinish = threading.Semaphore(0)

synchronizer = Synchronizer(logger, scheduler)

gl = globals()
fvars = {}
fvars["DewSimWebPyService"] = gl["DewSimWebPyService"]
fvars["EnergySwitchService"] = gl["EnergySwitchService"]
fvars["DeviceService"] = gl["DeviceService"]
fvars["InfoService"] = gl["InfoService"]
fvars["MobilityService"] = gl["MobilityService"]
fvars["JobService"] = gl["JobService"]
fvars["switchManager"] = gl["switchManager"]
fvars["synchronizer"] = gl["synchronizer"]
fvars["mutexFinish"] = gl["mutexFinish"]
fvars["logger"] = gl["logger"]

attachDevices(logger, ENERGY_HARDWARE)

app = web.application(urls, fvars)
looper = threading.Thread(target=looperThreadFunction, args=(app, mutexFinish, switchManager, synchronizer,))
looper.start()
app.run()
