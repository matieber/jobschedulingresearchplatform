import importlib
import logging
import os
import sys
import time

from job.job_descriptor import Job


class Processor(object):

    RESULTS_HOME = ""
    ELAPSED_TIME_FILE = "/elapsed_times.csv"
    RESULTS_FILE = "/results.csv"

    @staticmethod
    def set_results_home(results_root_directory):
        Processor.RESULTS_HOME = results_root_directory

    @staticmethod
    def get_results_home():
        return Processor.RESULTS_HOME

    def __init__(self, data_fields):

        with open(type(self).RESULTS_HOME + Processor.ELAPSED_TIME_FILE, 'w') as f:
            f.write("stage,elapsed_millis\n")
        self.__init_test_time__ = None

        self.infoLogger = logging.getLogger("scnrunner")
        data_fields = dict(data_fields)
        for key, val in data_fields.items():
            if key != "logic":
                setattr(self, key, self.compute_attr_value(val))
            else:
                setattr(self, "plain_processor", self.compute_attr_value(val))

    def compute_attr_value(self, value):
        if isinstance(value, list):
            return [self.compute_attr_value(x) for x in value]
        elif isinstance(value, dict):
            return dict(value)
        else:
            return value

    def create_processor(self, value):
        # https://stackoverflow.com/questions/547829/how-to-dynamically-load-a-python-class
        # combined with: https://stackoverflow.com/questions/714063/importing-modules-from-parent-folder
        builder_classname = value["processor_class"]
        module, klass = builder_classname.rsplit('.', 1)

        '''By convention, application's directory should be located at the same directory tree level of scnrunner
         directory, then a "../" is added to the application package indicated in the scenarioDescriptor file'''
        path = "../"+str(module).replace('.', os.path.sep)+".py"
        sys.path.append(os.path.dirname(os.path.dirname(os.path.relpath(path))))
        processor_module = importlib.import_module(module)
        processor_class = getattr(processor_module, klass)
        processor_class.RESULTS_HOME = type(self).RESULTS_HOME
        return processor_class(value["processor_params"])

    '''Writes a record in the RESULTS_HOME/elapsed_times.csv file. Records of such file are two columns named
        stage and elapsed_time which are the first and second arguments of the method'''

    def save_elapsed_time(self, stage, elapsed_time):
        with open(type(self).RESULTS_HOME + Processor.ELAPSED_TIME_FILE, 'a+') as f:
            f.write(str(stage) + "," + str(elapsed_time) + "\n")

    '''Writes a record in the RESULTS_HOME/results.csv file. Records format of such file is free'''
    def save_result(self, result_record, end_character=''):
        with open(type(self).RESULTS_HOME + Processor.RESULTS_FILE, 'a+') as f:
            f.write(result_record + end_character)

    '''Records the time when the current test initiates. The value of such record can be consulted
    via get_test_init_test_time() method'''
    def initialize(self):
        self.__init_test_time__ = time.monotonic_ns()

    def get_init_test_time(self):
        return self.__init_test_time__

    def process_job(self, jobinstance: Job):
        pass

    '''Returns True when all jobs submitted via process_job method were completed. False otherwise.
    When True, self.__end_test_time should be set with the current time'''
    def all_jobs_completed(self):
        pass


class ProcessorBuilder:

    def build_processor(self, fields):
        processor_class = ProcessorBuilder.load_klass(fields["hardware_support"])
        processor_class.RESULTS_HOME = Processor.get_results_home()
        return processor_class(fields["processor_impl"])


    '''given a dot representation of a class, e.g. full.package.name.Class, returns the imported class object to be instantiated'''
    @classmethod
    def load_klass(cls, full_package):
        module, klass = full_package.rsplit('.', 1)
        processor_module = importlib.import_module(module)
        return getattr(processor_module, klass)