import sys
from job.job_descriptor import ImageBasedTensorFlowJobBuilder


class DogsFinderJobBuilder(ImageBasedTensorFlowJobBuilder):

    def __init__(self, params):
        super().__init__(params)

    def get_mobile_job_classname(self):
        return "dogsBenchmark.DogsBenchmark"
