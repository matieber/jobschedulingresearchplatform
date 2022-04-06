import os
import subprocess
import time

from scnrunner.job.job_descriptor import Job
from scnrunner.processor import Processor
from scnrunner.util.time_converter import from_nano_to_milliseconds


class SBC(Processor):
    '''
        The following fields are expected to be dynamically initialized by the super().__init__ call
        self.tflite_model
    '''

    def __init__(self, fields):
        super().__init__(fields)
        self.processor = self.create_processor(self.plain_processor)

    def initialize(self):
        if str(self.launch_cpu_usage_monitor).lower() == "true":
            print("log CPU usage enabled: " + type(self).RESULTS_HOME + "/cpu_usage.log")
            params = ['./scripts/measures_cpu.sh', type(self).RESULTS_HOME + "/cpu_usage.log"]
            subprocess.Popen(params)
        super().initialize()

    def process_job(self, jobinstance: Job):
        self.processor.process_job(jobinstance)

    def all_jobs_completed(self):
        self.infoLogger.info("[PROCESSOR]all jobs done")
        self.save_elapsed_time("SCN_EXEC_TIME",
                               from_nano_to_milliseconds(time.monotonic_ns() - self.get_init_test_time()))
        os.system("killall -9 measures_cpu.sh")
        return True
