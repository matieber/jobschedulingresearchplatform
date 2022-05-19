import logging
import os
import shutil
import cv2
from PIL import Image
import glob


# from imageio import imread
# from imageio import imwrite
# import numpy as np
# from cow_bcs.preprocess import pre_process
# from cow_bcs.preprocess import process_depth_image


class ImageProducer:

    @staticmethod
    def get_frame_root_folder():
        pass

    @staticmethod
    def get_frame_container_folder(videofilepath):
        pass

    @staticmethod
    def get_android_image_suffix():
        pass

    @staticmethod
    def get_android_image_preffix(filepath):
        pass

    def next_image(self):
        pass


class ImagesFolderReader(ImageProducer):

    def __init__(self, images_folder_path, image_extension):
        self.destFolder = "../" + images_folder_path
        ImagesFolderReader.imgFolder = self.destFolder.split('/')[-1]
        ImagesFolderReader.fullImgFolder = self.destFolder
        # get all but last part of images folder fullpath. The result is like "../app_name/frameRoot"
        ImagesFolderReader.imgRootFolder = self.destFolder.rsplit('/', 1)[0]
        logging.getLogger("scnrunner").info("IMAGES PATH: " + str(self.destFolder))
        self.frameFilePreffix = images_folder_path.rsplit('/',1)[1]
        self.currframe = 0
        ImagesFolderReader.imgPrefix = self.frameFilePreffix
        ImagesFolderReader.imgExtension = image_extension
        self.images_count = self.get_images_count(self.destFolder, image_extension)
        logging.getLogger("scnrunner").info("IMAGES PREFIX: " + str(self.frameFilePreffix))
        logging.getLogger("scnrunner").info("IMAGES COUNT: " + str(self.images_count))
        logging.getLogger("scnrunner").info("IMAGES EXTENSION: " + str(image_extension))

    @staticmethod
    def get_dest_folder():
        return ImagesFolderReader.fullImgFolder

    def get_images_sorted_images(self, img_folder, extension):
        for subdir, dirs, files in os.walk(img_folder):
            sortedImgFiles = sorted([imgFile for imgFile in files if imgFile.endswith("." + extension)])
            return sortedImgFiles

    def next_image(self):
        self.currframe += 1
        if (self.currframe > self.images_count):
            return None

        return self.currframe

    @staticmethod
    def get_android_image_suffix():
        return '.' + ImagesFolderReader.imgExtension

    @staticmethod
    def get_android_image_preffix(filepath):
        return ImagesFolderReader.imgPrefix

    @staticmethod
    def get_frame_container_folder(filepath):
        return ImagesFolderReader.imgFolder

    @staticmethod
    def get_frame_root_folder():
        return ImagesFolderReader.imgRootFolder

    def get_images_count2(self, images_folder_path, image_extension):
        return len([imgname.endswith(image_extension) for imgname in os.listdir(images_folder_path)])

    def get_images_count(self, images_folder_path, image_extension):
        print("IMG PATTERN MATCHING: "+ImagesFolderReader.get_android_image_preffix("")+".*." + image_extension)
        return len(glob.glob1(images_folder_path, ImagesFolderReader.get_android_image_preffix("")+".*." + image_extension))


class VideoFrameExtractor(ImageProducer):

    def __init__(self, videoFilePath, android_image_width, android_image_height):
        destFolder = videoFilePath.split('.')[-2]
        print(str(destFolder))
        self.frameFilePreffix = destFolder.split('/')[-1]
        self.currframe = 1
        self.android_image_width = android_image_width
        self.android_image_height = android_image_height
        self.imgPreffix = destFolder + '/' + self.frameFilePreffix + '.'
        self.androidImgPreffix = destFolder + '/' + "android_" + self.frameFilePreffix + '.'
        self.video_file_path = videoFilePath

        VideoFrameExtractor.create_framestorage(destFolder)
        self.cap = cv2.VideoCapture(videoFilePath)
        self.video_length = int(self.cap.get(cv2.CAP_PROP_FRAME_COUNT)) - 1
        print("VIDEO FRAMES: " + str(self.video_length))

    @staticmethod
    def create_framestorage(destFolder):
        if not os.path.exists(destFolder):
            os.makedirs(destFolder)
        else:
            shutil.rmtree(destFolder)
            os.makedirs(destFolder)

    # https://stackoverflow.com/questions/33311153/python-extracting-and-saving-video-frames
    @staticmethod
    def extract_allframes(frameFileFolder, frameFilePreffix, videoFileName, android_image_width, android_image_height):
        destFolder = frameFileFolder + '/' + frameFilePreffix
        VideoFrameExtractor.create_framestorage(destFolder)
        cap = cv2.VideoCapture(frameFileFolder + "/" + videoFileName)
        video_length = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) - 1
        i = 1
        while (cap.isOpened()):
            ret, frame = cap.read()
            if ret == False:
                break
            imageName = destFolder + '/' + frameFilePreffix + '.' + str(i) + '.jpg'
            cv2.imwrite(imageName, frame)
            imageAndroid = Image.open(imageName).convert('RGB').resize((android_image_width, android_image_height),
                                                                       Image.ANTIALIAS)
            imageAndroid.save(destFolder + '/' + "android_" + frameFilePreffix + '.' + str(i) + '.jpg')
            i += 1
            if (i > (video_length - 1)):
                cap.release()
        return i - 1

    '''return frame number or None if there is no more frames'''

    def next_image(self):
        if self.cap.isOpened():
            ret, frame = self.cap.read()
            if ret:
                imageName = self.imgPreffix + str(self.currframe) + '.jpg'
                cv2.imwrite(imageName, frame)
                imageAndroid = Image.open(imageName).convert('RGB').resize(
                    (self.android_image_width, self.android_image_height),
                    Image.ANTIALIAS)
                imageAndroid.save(self.androidImgPreffix + str(self.currframe) + '.jpg')
                self.currframe += 1
                if self.currframe > (self.video_length - 1):
                    self.cap.release()
                return self.currframe - 1
            else:
                None
        else:
            None

    '''input_prefix: the common first string of frames name extracted from videofilepath'''

    @staticmethod
    def get_android_image_preffix(filepath):
        return "android_" + VideoFrameExtractor.get_frame_container_folder(filepath)

    @staticmethod
    def get_frame_container_folder(videofilepath):
        return videofilepath.split('/')[-1].split('.mp4')[0]

    '''input_suffix: the common ending string of frames name extracted from videofilepath'''

    @staticmethod
    def get_android_image_suffix():
        return ".jpg"
