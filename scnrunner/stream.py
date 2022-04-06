import importlib
import os
import sys
import threading
import time
import logging
from functools import partial
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler

import scnrunner.job.job_descriptor
from scnrunner.util.time_converter import to_milliseconds, from_nano_to_milliseconds


class StreamSource(object):

    RESULTS_HOME = ""
    STREAM_FILE = "/stream.csv"

    @classmethod
    def set_results_home(cls, results_root_directory):
        cls.RESULTS_HOME = results_root_directory

    @classmethod
    def get_results_home(cls):
        return cls.RESULTS_HOME

    def __init__(self, data_fields, buffer):
        self.buffer = buffer
        self.isClosed = False
        self.stream_init = 0
        self.parentLogger = logging.getLogger("scnrunner")
        import scnrunner.job.job_descriptor
        # create jobs home where job descriptors are going to be stored
        try:
            os.mkdir(scnrunner.job.job_descriptor.Job.get_job_desc_home())
            self.parentLogger.info("Job's descriptors directory created at: " +
                                   scnrunner.job.job_descriptor.Job.get_job_desc_home())
        except Exception as e:
            self.parentLogger.error("Error creating Jobs descriptor directory at: " +
                                    scnrunner.job.job_descriptor.Job.get_job_desc_home())

        data_fields = dict(data_fields)
        for key, val in data_fields.items():
            if key != "job_builder":
                setattr(self, key, self.compute_attr_value(val))
            else:
                setattr(self, "plain_job_builder", self.compute_attr_value(val))

    '''Writes a record in the RESULTS_HOME/elapsed_times.csv file. Records of such file are two columns named
           stage and elapsed_time which are the first and second arguments of the method'''

    def save_stream_detail(self, stream_record, end_character=''):
        with open(scnrunner.job.job_descriptor.Job.RESULTS_HOME + StreamSource.STREAM_FILE, 'a+') as f:
            f.write(stream_record + end_character)

    def create_job_builder(self, value):
        # The following is trick to dynamically import modules whose name we do not know before execution actually happens
        # https://stackoverflow.com/questions/547829/how-to-dynamically-load-a-python-class
        # combined with: https://stackoverflow.com/questions/714063/importing-modules-from-parent-folder
        builder_classname = value["builder_class"]
        module, klass = builder_classname.rsplit('.', 1)
        path = "../" + str(module).replace('.', os.path.sep) + ".py"

        '''add the path of the app to the system path'''
        sys.path.append(os.path.dirname(os.path.dirname(os.path.relpath(path))))
        job_module = importlib.import_module(module)
        job_builder_class = getattr(job_module, klass)
        return job_builder_class(value["builder_params"])

    def compute_attr_value(self, value):
        if isinstance(value, list):
            return [self.compute_attr_value(x) for x in value]
        elif isinstance(value, dict):
            return dict(value)
        else:
            return value

    def is_closed(self) -> bool:
        return self.isClosed

    def initialize(self):
        self.job_builder = self.create_job_builder(self.plain_job_builder)
        return self.buffer

    def producer(self, buffer):
        self.parentLogger.info("[STREAM]:Open")
        self.stream_init = time.monotonic_ns()
        while self.has_items():
            item = self.get_item()
            buffer.put(item)
            self.save_stream_detail(str(from_nano_to_milliseconds(time.monotonic_ns() - self.stream_init)) + ";" + item.__to_simulator_format__(), "\n")
        self.isClosed = True
        self.parentLogger.info("[STREAM]:Closed")

    def has_items(self):
        pass

    def get_item(self):
        pass

    def yield_items(self):
        items_producer = threading.Thread(target=self.producer, name="StreamProducerThread", args=(self.buffer,))
        items_producer.start()


'''Stateful object to create jobs using time and granularity parameters'''


class SimulatedFromImageFolder(StreamSource):

    def __init__(self, test_id: str, stream_data: dict, buffer):

        '''
            The following fields are expected to be dynamically initialized by the super().__init__ call
            self.per_job_frames = per_job_frames
            self.per_burst_jobs = per_burst_jobs
            self.millis_btw_jobs = millis_btw_jobs
            self.millis_btw_bursts = millis_btw_bursts
            self.job_builder
            self.img_folder
            self.img_extension
            self.launch_image_server
            self.image_server_port
        '''
        self.launch_images_server = False
        super().__init__(stream_data, buffer)

        '''attributes corresponding to the internal state'''
        self.test_id = test_id
        self.first_image_index = 1
        self.curr_jobs_in_burst = 0
        from scnrunner.job.image_producer import ImagesFolderReader
        self.img_producer = ImagesFolderReader(self.img_folder, self.img_extension)
        self.total_images = self.img_producer.images_count

    def initialize(self):
        # the following line is intentionally placed before super().initialize() call
        self.plain_job_builder["builder_params"]["img_producer"] = self.img_producer
        self.plain_job_builder["builder_params"]["test_id"] = self.test_id
        self.plain_job_builder["builder_params"]["jobs_dir"] = scnrunner.job.job_descriptor.Job.get_job_desc_home()
        super().initialize()
        if self.launch_images_server:
            self.init_images_server_in_background()

    def init_images_server_in_background(self):
        server_address = ('', self.image_server_port)
        frame_root_folder = str(self.img_folder).rsplit('/',1)[0]
        self.parentLogger.info("Initiating local web server at port: " + str(self.image_server_port) + " for serving"
                                                                                " images at: ../" + frame_root_folder)
        handler_class = partial(SimpleHTTPRequestHandler,
                                directory="../" + frame_root_folder)
        server = ThreadingHTTPServer(server_address, handler_class)

        images_server_thread = threading.Thread(target=server.serve_forever, name="ImagesServerThread", args=())
        images_server_thread.setDaemon(True)
        images_server_thread.start()

    def has_items(self):
        return self.first_image_index <= self.total_images

    def get_item(self):

        if self.curr_jobs_in_burst < self.per_burst_jobs:
            #if self.curr_jobs_in_burst != 0: #except for the burst first job
            time.sleep((self.millis_btw_jobs / 1000))  # simulate an elapsed time between one job and the next, even for the first job

            job_instance = self.job_builder.createJob(self.first_image_index,
                                                      self.get_last_img_index(self.first_image_index))
            self.first_image_index += self.per_job_frames
            self.curr_jobs_in_burst += 1
            return job_instance
        else:
            self.save_stream_detail(str(from_nano_to_milliseconds(time.monotonic_ns() - self.stream_init)) + ";END_JOB_BURST", "\n")
            self.curr_jobs_in_burst = 0
            time.sleep((self.millis_btw_bursts / 1000))  # simulate that a time elapsed until the next job burst arrives
            return self.get_item()

    def get_last_img_index(self, init_img_index: int):
        # correct last image file index if necessary which is the case when available images in input_root_dir
        # are not exact multiple of the granularity used.
        last_index = init_img_index
        if init_img_index is not None:
            while last_index < init_img_index + self.per_job_frames:
                if self.img_producer.next_image() is not None:
                    last_index += 1
                else:
                    break
        return last_index - 1


class StreamBuilder:

    def build_stream(self, test_id, stream_data, buffer):
        if stream_data["type"] == "SimulatedFromImageFolder":
            SimulatedFromImageFolder.RESULTS_HOME = StreamSource.get_results_home()
            return SimulatedFromImageFolder(test_id, stream_data["field"], buffer)
