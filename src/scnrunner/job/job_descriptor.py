import importlib
import json
import os
import sys
import time
# https://stackoverflow.com/questions/714063/importing-modules-from-parent-folder
from pathlib import Path
sys.path.append(str(Path('.').absolute().parent))


class Job:
    # class attribute to keep count of generated jobs
    seq_number = 0

    RESULTS_HOME = ""

    def __init__(self):
        self.job_sequence = Job.__next_job_seq_number__()
        self.descriptor_uri = ""
        self.json_template = ""
        self.job_id = ""
        self.input_uri = ""
        self.input_id = ""
        self.input_size = 0
        self.node_id = "@@model1@@"
        self.job_name = "@@name@@"

    def set_job_id(self, jid: str):
        self.job_id = jid

    def __to_simulator_format__(self):
        return str(self.job_sequence) + ";JOB_OPS;" + str(self.input_size) + ";OUTPUT_SIZE"

    def set_descriptor_uri(self, desc_uri: str):
        self.descriptor_uri = desc_uri

    def set_input_id(self, input_id: str):
        self.input_id = input_id

    def get_input_size(self, unit='B'):
        if str(unit).upper() == "KB":
            return float(int(self.input_size) / 1024)
        else:
            if str(unit).upper() == "MB":
                return float(int(self.input_size) / (1024 * 1024))
            else:
                return float(self.input_size)

    def set_node_id(self, node_id):
        self.node_id = node_id
        self.json_template["devices"][0]["deviceModel"] = str(node_id)

    def flush(self):
        with open(self.descriptor_uri, 'w') as f:
            json.dump(self.json_template, f)

    'returns a list of files associated to the job input, e.g., images files'

    def get_tasks_input(self):
        pass

    @classmethod
    def __next_job_seq_number__(cls):
        Job.seq_number += 1
        return Job.seq_number

    @classmethod
    def get_job_desc_home(cls):
        return Job.RESULTS_HOME +"/jobs/"

    @classmethod
    def set_results_home(cls, jobs_parent):
        Job.RESULTS_HOME = jobs_parent

    def __str__(self):
        return "job_id:" + str(self.job_id) + ", json_template:" + str(self.json_template)


class ImageInputJob(Job):

    def __init__(self):
        super().__init__()
        self.from_img_index = -1
        self.to_img_index = -1
        self.frame_container_folder = ""
        self.input_prefix = ""
        self.input_suffix = ""

    def set_image_container_folder(self, fcf):
        self.frame_container_folder = fcf

    def set_images_prefix(self, input_prefix):
        self.input_prefix = input_prefix

    def set_images_suffix(self, input_suffix):
        self.input_suffix = input_suffix

    def set_init_img_index(self, init_index: int):
        self.from_img_index = init_index

    def set_last_img_index(self, last_index: int):
        self.to_img_index = last_index

    def get_init_img_index(self):
        return self.from_img_index

    def get_last_img_index(self):
        return self.to_img_index

    '''returns a list of images paths that serve as input for the object recognition'''

    def get_tasks_input(self):
        img_paths = []
        for i in range(self.from_img_index, self.to_img_index + 1, 1):
            img_paths.append(self.frame_container_folder + "/" + self.input_prefix + "." + str(i) + self.input_suffix)
        return img_paths

    def __str__(self):
        return "init_img_index: " + self.from_img_index + ", last_img_index: " + self.to_img_index


class ImageInputJobBuilder:

    def __init__(self, iprod):
        self.image_producer = iprod
        from scnrunner.job.image_producer import ImagesFolderReader
        self.input_prefix = ImagesFolderReader.get_android_image_preffix(self.image_producer.destFolder)
        self.frame_container_folder = ImagesFolderReader.get_dest_folder()

    def get_job_input_path(self, frameindex):
        from scnrunner.job.image_producer import ImagesFolderReader
        baseFolder = ImagesFolderReader.get_dest_folder()
        return baseFolder + '/' + self.input_prefix + '.' + str(frameindex) + self.input_suffix

    def get_job_input_bytes(self, init_img_index, last_index):
        size = 0
        # print(last_index)
        for img_index in range(init_img_index, last_index+1, 1):
            job_path = self.get_job_input_path(img_index)
            while not os.path.exists(job_path):
                time.sleep(1)
                print(init_img_index, last_index)
                print(job_path)
            size += int(os.path.getsize(job_path))
        return size

    def createJob(self, init_img_index: int, last_img_index: int):
        pass
