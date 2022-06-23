import concurrent
import math
import multiprocessing
from copy import deepcopy

from dogs_finder_app.app import DogsFinder
from scnrunner.job.job_descriptor import Job


class DogsFinderMulticore(DogsFinder):

    def __init__(self, fields):
        self.min_thread_load = 2  # To processing at least two images per thread
        self.threads_count = multiprocessing.cpu_count()
        super().__init__(fields)

    def process_job(self, jobinstance: Job):

        max_threads = math.floor(len(jobinstance.get_tasks_input()) / self.min_thread_load)
        if max_threads < self.threads_count:
            jobs_list = self.split_jobinstance(jobinstance, max_threads)
        else:
            jobs_list = self.split_jobinstance(jobinstance, self.threads_count)

        with concurrent.futures.ThreadPoolExecutor() as executor:
            futures = []
            for sub_job in jobs_list:
                futures.append(executor.submit(super().process_job, sub_job))
            for future in concurrent.futures.as_completed(futures):
                try:
                    future.result()
                except Exception as exc:
                    print('%r generated an exception: %s' % (sub_job.job_id, exc))

    def split_jobinstance(self, jobinstance, max_threads):
        print("Job splitted into " + str(max_threads) + " threads")
        remaining_images = len(jobinstance.get_tasks_input()) % int(max_threads)
        min_bulk_size = math.floor(len(jobinstance.get_tasks_input()) / max_threads)
        sub_jobs = []
        new_init_index = jobinstance.get_init_img_index()
        for i in range(0, max_threads):
            resized_job = deepcopy(jobinstance)
            resized_job.set_init_img_index(new_init_index)
            new_last_index = new_init_index + min_bulk_size
            # assign one remaining image to first resized jobs
            new_last_index = new_last_index - 1 if remaining_images <= 0 else new_last_index
            resized_job.set_last_img_index(new_last_index)
            sub_jobs.append(resized_job)

            # set limits for the next sub_job
            new_init_index = new_last_index + 1
            remaining_images -= 1
        return sub_jobs
