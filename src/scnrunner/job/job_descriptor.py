import importlib
import json
import os
import sys
import time
# https://stackoverflow.com/questions/714063/importing-modules-from-parent-folder
from pathlib import Path
sys.path.append(str(Path('.').absolute().parent))
from scnrunner.job.image_producer import ImagesFolderReader


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
        return Job.RESULTS_HOME + "/jobs/"

    @classmethod
    def set_results_home(cls, jobs_parent):
        Job.RESULTS_HOME = jobs_parent

    def __str__(self):
        return "job_id:" + str(self.job_id) + ", json_template:" + str(self.json_template)


'''A specialization of Job which uses images as input. Images are located in a folder of the filesystem. Their name share
a suffix and a prefix. Since images are numerated consecutively, an ImageInputJob instance has "from" and "to" indexes'''


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

    def __init__(self, params):
        self.image_producer = params["img_producer"]
        self.input_prefix = self.image_producer.get_android_image_preffix(self.image_producer.destFolder)
        self.frame_container_folder = self.image_producer.get_dest_folder()
        self.input_suffix = self.image_producer.get_android_image_suffix()

    def get_job_input_path(self, frameindex):
        #from image_producer import ImagesFolderReader
        #baseFolder = ImagesFolderReader.get_dest_folder()
        return self.frame_container_folder + '/' + self.input_prefix + '.' + str(frameindex) + self.input_suffix

    def get_job_input_bytes(self, init_img_index, last_index):
        size = 0
        # print(last_index)
        for img_index in range(init_img_index, last_index + 1, 1):
            job_path = self.get_job_input_path(img_index)
            while not os.path.exists(job_path):
                time.sleep(1)
                print(init_img_index, last_index)
                print(job_path)
            size += int(os.path.getsize(job_path))
        return size

    def createJob(self, init_img_index: int, last_img_index: int):
        pass


class ImageBasedTensorFlowJobBuilder(ImageInputJobBuilder):

    def __init__(self, params):
        super().__init__(params)
        self.job_template_filepath = "../" + params["json_template"]
        self.jobs_name_prefix = params["test_id"]
        self.job_template = ""
        self.jobs_dir = params["jobs_dir"]
        self.tf_jobs_params = dict(params["tf_params"])

    def get_job_template_size(self, unit="B"):
        ret = int(os.path.getsize(self.job_template_filepath))
        if str(unit).upper() == "KB":
            return float(ret / 1024)
        else:
            if str(unit).upper() == "MB":
                return float(ret / (1024 * 1024))
            else:
                return float(ret)

    def createJob(self, init_img_index: int, last_img_index: int):
        with open(self.job_template_filepath, "r") as content:
            self.job_template = json.load(content)

        job_name = self.input_prefix + "_" + str(init_img_index) + "_to_" + str(last_img_index)
        variant_id = job_name
        job_instance = ImageInputJob()
        job_instance.set_init_img_index(init_img_index)
        job_instance.set_last_img_index(last_img_index)
        job_instance.set_image_container_folder(self.frame_container_folder)
        job_instance.set_images_suffix(self.input_suffix)
        job_instance.set_images_prefix(self.input_prefix)
        job_instance.job_id = job_name

        # input size includes images and job descriptor file sizes.
        job_instance.input_size = self.get_job_input_bytes(init_img_index, last_img_index) + int(
            self.get_job_template_size("B"))
        job_filename = self.jobs_name_prefix + "_" + job_name + ".json"

        self.job_template["benchmarkDefinitions"][0]["benchmarkId"] = job_filename
        job_instance.descriptor_uri = str(os.path.join(self.jobs_dir, job_filename))
        self.job_template["benchmarkDefinitions"][0]["benchmarkClass"] = self.get_mobile_job_classname()
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["variantId"] = variant_id
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"] = \
            [self.input_prefix, str(init_img_index), str(last_img_index)]

        param_names, param_values = self.overrideTFJobParams(
            self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["names"],
            self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"])

        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["names"] = param_names
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"] = param_values
        self.job_template["devices"][0]["variants"] = [variant_id]

        job_instance.json_template = self.job_template

        return job_instance

    def overrideTFJobParams(self, param_names, param_values):
        for param in list(self.tf_jobs_params.keys()):
            if param not in param_names:
                param_names.append(str(param))
                param_values.append(str(self.tf_jobs_params[param]))
        return param_names, param_values

    def get_mobile_job_classname(self):
        pass
