/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

import edu.benchmarkandroid.Benchmark.jsonConfig.ParamsRunStage;
import edu.benchmarkandroid.service.TFLiteModelFactory;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 * <p>
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class MobileNetDetectionAPIModel implements Classifier {

    private static final int MOBILE_NET_MODEL_INPUT_SIZE = 300;
    private static final String MOBILENET_MODEL_FILE = "detect.tflite";
    private static final String MOBILENET_LABELS_FILE = "labelmap.txt";
    // Only return this many results.
    private static final int NUM_DETECTIONS = 10;
    // Float model
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;
    // Number of threads in the java app
    private static int NUM_THREADS = 1;
    private boolean isModelQuantized;
    // Config values.
    private int inputSize;
    // Pre-allocated buffers.
    private final Vector<String> labels = new Vector<>();
    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    private ByteBuffer imgData;

    private Interpreter tfLite;

    private GpuDelegate gpuDelegate;

    private MobileNetDetectionAPIModel() {
    }

    /**
     * Memory-map the model file in Assets.
     */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static Classifier create(ParamsRunStage prs, AssetManager assets) throws IOException {
        boolean useGpu = prs.getBooleanValue("usesGPU");
        boolean useXNNPack = prs.getBooleanValue("usesXNNPack");
        MobileNetDetectionAPIModel.NUM_THREADS = prs.getIntValue("cpuThreads");
        String quantizedMethod = prs.getValue("quantizedMethod");
        //TODO: download the model
        return MobileNetDetectionAPIModel.create(assets, MOBILENET_MODEL_FILE,
                MOBILENET_LABELS_FILE, MOBILE_NET_MODEL_INPUT_SIZE, quantizedMethod,
                useGpu, MobileNetDetectionAPIModel.NUM_THREADS, useXNNPack);
    }

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     * @param inputSize     The size of image input
     * @param quantizedMethod   String representing the model quantized method
     */
    public static Classifier create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final String quantizedMethod,
            final boolean useGpu,
            final int numThreads,
            final boolean useXNNPACK)
            throws IOException {
        final MobileNetDetectionAPIModel d = new MobileNetDetectionAPIModel();

        InputStream labelsInput = assetManager.open(labelFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            d.labels.add(line);
        }
        br.close();

        d.inputSize = inputSize;

        try {
            Interpreter.Options options = new Interpreter.Options();
            if (useGpu) {
                CompatibilityList compatibilityList = new CompatibilityList();

                d.gpuDelegate = new GpuDelegate(compatibilityList.getBestOptionsForThisDevice());
                options.addDelegate(d.gpuDelegate);
            } else {
                options.setNumThreads(numThreads);
                options.setUseXNNPACK(useXNNPACK);
            }
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename), options);
        } catch (Exception e) {
            throw new IOException(e);
        }

        // Pre-allocate buffers.
        int numBytesPerChannel;
        if ( quantizedMethod.compareTo(TFLiteModelFactory.QUANTIZED_INPUT_8) == 0 ) {
            numBytesPerChannel = 1; // Quantized
            d.isModelQuantized = true;
        } else {
            numBytesPerChannel = 4; // Floating point
            d.isModelQuantized = false;
        }
        d.imgData = ByteBuffer.allocateDirect(d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

        d.outputLocations = new float[1][NUM_DETECTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTIONS];
        d.outputScores = new float[1][NUM_DETECTIONS];
        d.numDetections = new float[1];
        return d;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap) {
        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        // Show the best detections.
        // after scaling them back to the input size.

        // You need to use the number of detections from the output and not the NUM_DETECTONS variable declared on top
        // because on some models, they don't always output the same total number of detections
        // For example, your model's NUM_DETECTIONS = 20, but sometimes it only outputs 16 predictions
        // If you don't use the output's numDetections, you'll get nonsensical data
        int numDetectionsOutput = Math.min(NUM_DETECTIONS, (int) numDetections[0]); // cast from float to integer, use min for safety

        final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        for (int i = 0; i < numDetectionsOutput; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[0][i][1] * inputSize,
                            outputLocations[0][i][0] * inputSize,
                            outputLocations[0][i][3] * inputSize,
                            outputLocations[0][i][2] * inputSize);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i,
                            labels.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            0, detection));
        }
        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {
    }

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
        if (tfLite != null) {
            //The app may crash if this is done!
            tfLite.close();
            tfLite = null;
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
            gpuDelegate = null;
        }
    }

    public void setNumThreads(int num_threads) {
        MobileNetDetectionAPIModel.NUM_THREADS = num_threads;
        //if (tfLite != null) tfLite.setNumThreads(num_threads);
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {

    }

    @Override
    public boolean isGPUAvailable() {
        CompatibilityList compatibilityList = new CompatibilityList();

        return compatibilityList.isDelegateSupportedOnThisDevice();
    }


}
