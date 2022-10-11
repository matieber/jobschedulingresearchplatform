import logging
import queue
import argparse
import json

from processor import ProcessorBuilder, Processor
from stream import StreamBuilder

scn_results_dir = ""

if __name__ == '__main__':

    parser = argparse.ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)

    parser.add_argument('--scenarioDescriptor', help='filepath relative to this script folder containing all necessary'
                                                     'configuration to setup dew scenario entities', type=str,
                        required=True)

    args = parser.parse_args()
    pb = ProcessorBuilder()
    sb = StreamBuilder()

    scn_data = ""
    with open(args.scenarioDescriptor, "r") as content:
        scn_data = json.load(content)

    logging.basicConfig(filename=scn_data["log_file"],format='%(asctime)s %(message)s', level=logging.INFO)
    logger = logging.getLogger("scnrunner")
    print("Output log at:" + scn_data["log_file"])
    logger.info(str(scn_data))
    scn_results_dir = scn_data["results_dir"]
    print("Results dir: "+ scn_results_dir)
    import job.job_descriptor as jd
    jd.Job.set_results_home(scn_results_dir)
    Processor.set_results_home(scn_results_dir)
    processor = pb.build_processor(scn_data["processor"])
    jobs_queue = queue.Queue(0)
    stream = sb.build_stream(scn_data["scn_id"], scn_data["stream_source"], jobs_queue)

    processor.initialize()
    stream.initialize()

    stream.yield_items()
    test_end = False
    while not test_end:
        try:
            job_desc = jobs_queue.get(block=True, timeout=1)
            processor.process_job(job_desc)
            jobs_queue.task_done()
        except queue.Empty:
            if stream.is_closed() and processor.all_jobs_completed():
                test_end = True

    jobs_queue.join()
    if jobs_queue.all_tasks_done:
        quit(0)
