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

package edu.benchmarkandroid.Benchmark.benchmarks.cowBCSBenchmark;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.Classifier;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * - https://github.com/tensorflow/models/tree/master/research/object_detection
 * where you can find the training code.
 * <p>
 * To use pretrained models in the API or convert to TF Lite models, please see docs for details:
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md
 * - https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tensorflowlite.md#running-our-model-on-android
 */
public class BcsDetectionAPIModel implements Classifier {


    private static int NUM_THREADS = 4;
    private boolean isModelQuantized;
    // Pre-allocated buffers.
    private final Vector<String> labels = new Vector<>();

    private float[][][] outputLocations;


    private  float[][] mResult ;
    private Interpreter tfLite;

    private GpuDelegate gpuDelegate;

    private BcsDetectionAPIModel() {
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

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param assetManager  The asset manager to be used to load assets.
     * @param modelFilename The filepath of the model GraphDef protocol buffer.
     * @param labelFilename The filepath of label file for classes.
     *
     */
    public static BcsDetectionAPIModel create(
            final AssetManager assetManager,
            final String modelFilename,
            final String labelFilename,
            final boolean useGpu)
            throws IOException {
        final BcsDetectionAPIModel d = new BcsDetectionAPIModel();

        InputStream labelsInput = assetManager.open(labelFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            d.labels.add(line);
        }
        br.close();


        try {
            Interpreter.Options options = new Interpreter.Options();
            if (useGpu) {
                CompatibilityList compatibilityList = new CompatibilityList();

                d.gpuDelegate = new GpuDelegate(compatibilityList.getBestOptionsForThisDevice());
                options.addDelegate(d.gpuDelegate);
            } else {
                options.setNumThreads(NUM_THREADS);
                options.setUseXNNPACK(true);
            }
            d.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename), options);
        } catch (Exception e) {
            throw new IOException(e);
        }

        d.mResult = new float[1][d.labels.size()];

        return d;
    }

    public List<Recognition> recognize(final TensorBuffer tensorBuffer ) {

        tfLite.run(tensorBuffer.getBuffer(), mResult);
        final ArrayList<Recognition> recognitions = new ArrayList<>(1);
        int labelOffset = 1;
        int max= argmax (mResult[0]);
        recognitions.add(
                new Recognition(
                        "0" , labels.elementAt(max)                         ,
                        mResult[0][max],
                        max,null));
        //}
        return recognitions;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap) {
        return null;
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
        BcsDetectionAPIModel.NUM_THREADS = num_threads;
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

    private static int argmax(float[] probs) {
        int maxIdx = -1;
        float maxProb = 0.0f;
        for (int i = 0; i < probs.length; i++) {
            if (probs[i] > maxProb) {
                maxProb = probs[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

}
