import json
import os
from scnrunner.job.job_descriptor import ImageInputJobBuilder, ImageInputJob


class DogsFinderJobBuilder(ImageInputJobBuilder):

    def __init__(self, params):
        super().__init__(iprod=params["img_producer"])
        self.job_template_filepath = "../"+params["json_template"]
        self.jobs_name_prefix = params["test_id"]
        self.job_template = ""
        self.input_suffix = ".jpg"
        self.cpuThreads = 4
        self.usesGPU = False

    def get_job_template_size(self, unit="B"):
        ret = int(os.path.getsize(self.job_template_filepath))
        if str(unit).upper() == "KB":
            return float(ret / 1024)
        else:
            if str(unit).upper() == "MB":
                return float(ret / (1024*1024))
            else:
                return float(ret)

    def createJob(self, init_img_index, last_img_index):
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
        # print(job_name + " size in KBs:" + str(job_instance.input_size / 1024))
        job_filename = self.jobs_name_prefix + "_" + job_name + ".json"

        self.job_template["benchmarkDefinitions"][0]["benchmarkId"] = job_filename
        self.job_template["benchmarkDefinitions"][0]["benchmarkClass"] = "dogsBenchmark.DogsBenchmark"
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["variantId"] = variant_id
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"] = \
            [self.input_prefix, str(init_img_index), str(last_img_index), str(self.usesGPU), "yolov4",
             str(self.cpuThreads)]
        self.job_template["devices"][0]["variants"] = [variant_id]

        job_instance.json_template = self.job_template
        return job_instance


class MobileDogsFinderJobBuilder(DogsFinderJobBuilder):

    def __init__(self, params: dict):
        super().__init__(params)
        self.jobs_dir = params["jobs_dir"]
        self.usesXNNPack = False if 'usesXNNPack' not in list(params.keys()) else params["usesXNNPack"]

    def createJob(self, init_img_index, last_img_index):
        job_instance = super().createJob(init_img_index, last_img_index)
        job_instance.descriptor_uri = str(os.path.join(self.jobs_dir,
                                                       self.job_template["benchmarkDefinitions"][0]["benchmarkId"]))
        paramNames = self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["names"]
        paramNames.append("useXNNPack")
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["names"] = paramNames
        paramValues = self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"]
        paramValues.append(str(self.usesXNNPack))
        self.job_template["benchmarkDefinitions"][0]["variants"][0]["paramsRunStage"]["values"] = paramValues
        job_instance.json_template = self.job_template
        return job_instance

