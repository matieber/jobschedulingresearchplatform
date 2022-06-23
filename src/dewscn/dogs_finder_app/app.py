import logging
import re
import cv2
import time

import tensorflow as tf
from tensorflow.python.client.session import InteractiveSession
from PIL import Image
import dogs_finder_app.core.utils
from dogs_finder_app.core.yolov4 import filter_boxes
import numpy as np
from scnrunner.processor import Processor
from scnrunner.job.job_descriptor import Job
from scnrunner.util.time_converter import to_milliseconds

'''This is an adaptation of detect.py code 
from https://github.com/theAIGuysCode/yolov4-custom-functions/blob/master/detect.py 
'''
class DogsFinder(Processor):
    CAMERA_WIDTH = 640
    CAMERA_HEIGHT = 360

    FLAGS = dict()
    FLAGS['framework'] = 'tflite'
    FLAGS['weights'] = '../dogs_finder_app/tflite_models/yolov4-tiny-416.tflite'
    FLAGS['size'] = 416
    FLAGS['tiny'] = True
    FLAGS['model'] = 'yolov4'
    #FLAGS['image'] = '../dogs_finder_app/tflite_models/tensorflow-yolov4-tflite-master/data/kite.jpg'
    #FLAGS['output'] = 'result.png'
    FLAGS['iou'] = 0.6
    FLAGS['score'] = 0.5

    '''
            The following fields are expected to be dynamically initialized by the super().__init__ call
            self.tflite_model
    '''
    def __init__(self, fields):
        super().__init__(fields)
        print("TF version:" + str(tf.version))
        STRIDES, ANCHORS, NUM_CLASS, XYSCALE = dogs_finder_app.core.utils.load_config(DogsFinder.FLAGS)
        self.config = tf.compat.v1.ConfigProto()
        self.config.gpu_options.allow_growth = True
        self.session = InteractiveSession(config=self.config)

        self.input_size = DogsFinder.FLAGS['size']
        #self.image_example_path = DogsFinder.FLAGS['image']

        self.labels = self.__load_labels__(self.labels)
        self.num_classes = len(self.labels)
        self.save_result("frameFileName,detectTime(millis),labels", "\n")

    def process_job(self, jobinstance: Job):

        if str(self.useGPU).lower == "true":
            with tf.device('/gpu:0'):
                for frame_path in jobinstance.get_tasks_input():
                    self.process_frame(frame_path)
        else:
            for frame_path in jobinstance.get_tasks_input():
                    self.process_frame(frame_path)

    def process_frame(self, frame_path):
        #start_time = time.perf_counter_ns()
        start_time = time.perf_counter()
        original_image = cv2.imread(frame_path)
        fname = str(frame_path).rsplit("/", maxsplit=1)[1]
        original_image = cv2.cvtColor(original_image, cv2.COLOR_BGR2RGB)
        image_data = cv2.resize(original_image, (self.input_size, self.input_size))
        image_data = image_data / 255.

        images_data = []
        for i in range(1):
            images_data.append(image_data)
        images_data = np.asarray(images_data).astype(np.float32)
        output = self.__detect_objects__(original_image, images_data)

        results = self.format_results_labels(output)
        #python 3.7
        #elapsed_ms = (time.perf_counter_ns() - start_time) / 1000000
        elapsed_ms = (time.perf_counter() - start_time) / 1000
        self.save_result(fname + "," + str(elapsed_ms) + "," + str(results), "\n")

    # yolov4 implementation
    def __detect_objects__(self, original_image, images_data):
        """Returns a list of detection results, each a dictionary of object info."""

        if DogsFinder.FLAGS['framework'] == 'tflite':
            interpreter = tf.lite.Interpreter(model_path=DogsFinder.FLAGS['weights'])
            interpreter.allocate_tensors()
            input_details = interpreter.get_input_details()
            output_details = interpreter.get_output_details()
            interpreter.set_tensor(input_details[0]['index'], images_data)
            interpreter.invoke()

            pred = [interpreter.get_tensor(output_details[i]['index']) for i in range(len(output_details))]

            boxes, pred_conf = filter_boxes(pred[0], pred[1], score_threshold=0.25,
                                            input_shape=tf.constant([self.input_size, self.input_size]))

            boxes, scores, classes, valid_detections = tf.image.combined_non_max_suppression(
                boxes=tf.reshape(boxes, (tf.shape(boxes)[0], -1, 1, 4)),
                scores=tf.reshape(
                    pred_conf, (tf.shape(pred_conf)[0], -1, tf.shape(pred_conf)[-1])),
                max_output_size_per_class=50,
                max_total_size=50,
                iou_threshold=DogsFinder.FLAGS['iou'],
                score_threshold=DogsFinder.FLAGS['score']
            )
            pred_bbox = [boxes.numpy(), scores.numpy(), classes.numpy(), valid_detections.numpy()]
            #image = utils.draw_bbox(original_image, pred_bbox)
            #image = Image.fromarray(image.astype(np.uint8))
            #image = cv2.cvtColor(np.array(image), cv2.COLOR_BGR2RGB)
            #cv2.imwrite(DogsFinder.FLAGS['output'], image)
            return pred_bbox


    def format_results_labels(self, output):
        formatted_results = []
        out_boxes, out_scores, out_classes, num_boxes = output
        for i in range(num_boxes[0]):
            if int(out_classes[0][i]) < 0 or int(out_classes[0][i]) > self.num_classes: continue
            score = out_scores[0][i]
            class_ind = int(out_classes[0][i])
            bbox_mess = '%s:%.2f' % (self.labels[class_ind], score)
            formatted_results.append(bbox_mess)
        return formatted_results


    def __load_labels__(self, path):
        """Loads the labels file. Supports files with or without index numbers."""
        # print("relative path:" + os.getcwd())
        with open("../" + path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
            labels = {}
            for row_number, content in enumerate(lines):
                pair = re.split(r'[:\s]+', content.strip(), maxsplit=1)
                if len(pair) == 2 and pair[0].strip().isdigit():
                    labels[int(pair[0])] = pair[1].strip()
                else:
                    labels[row_number] = pair[0].strip()
        #print("LABELS:"+ str(labels))
        return labels
