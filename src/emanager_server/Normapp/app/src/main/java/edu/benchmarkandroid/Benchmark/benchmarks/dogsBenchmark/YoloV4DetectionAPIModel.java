package edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class YoloV4DetectionAPIModel implements Classifier {
    public static final String TAG = YoloV4DetectionAPIModel.class.getSimpleName();
    private static final float IMAGE_MEAN = 0.0f;
    private static final float IMAGE_STD = 255.0f;
    private static int NUM_THREADS = 4;
    private static final int[] OUTPUT_WIDTH = new int[]{2535, 2535};
    private boolean isModelQuantized;
    private int BATCH_SIZE;
    private int inputSize;
    private final List<String> labels = new ArrayList<>();
    private int[] intValues;
    private float minimumConfidence;

    private float[][][] outputLocations;
    private float[][][] outputClasses;

    private ByteBuffer imgData;

    private MappedByteBuffer tfLiteModel;
    private Interpreter.Options tfLiteOptions;
    private Interpreter tfLite;

    private YoloV4DetectionAPIModel() {}

    public static void setCPUThreads(int threads){
        YoloV4DetectionAPIModel.NUM_THREADS = threads;
    }

    /** Memory-map the model file in Assets. */
    static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
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
     *  @param modelFilename The model file path relative to the assets folder
     * @param labelFilename The label file path relative to the assets folder
     * @param inputSize The size of image input
     * @param isQuantized Boolean representing model is quantized or not
     * @param useGPU
     * @param useXNNPACK Enable an optimized set of floating point CPU kernels (provided by XNNPACK)
     * @param cpuThreads used to parallelize the inference     *
     */
    public static Classifier create(
            final Context context,
            final String modelFilename,
            final String labelFilename,
            final int inputSize,
            final boolean isQuantized,
            final float minimumConfidence,
            boolean useGPU,
            final boolean useXNNPACK,
            final int cpuThreads)
            throws IOException {
        YoloV4DetectionAPIModel.setCPUThreads(cpuThreads);
        final YoloV4DetectionAPIModel d = new YoloV4DetectionAPIModel();

        MappedByteBuffer modelFile = loadModelFile(context.getAssets(), modelFilename);
        try (BufferedReader br =
                     new BufferedReader(new InputStreamReader(context.getAssets().open(labelFilename)))) {
            String line;
            while ((line = br.readLine()) != null) {
                Log.w(TAG, line);
                d.labels.add(line);
            }
        }

        d.inputSize = inputSize;

        try {
            Interpreter.Options options = new Interpreter.Options();

            if (useGPU) {
                CompatibilityList compatibilityList = new CompatibilityList();
                GpuDelegate gpuDelegate = new GpuDelegate(compatibilityList.getBestOptionsForThisDevice());

                options.addDelegate(gpuDelegate);
            } else {
                options.setNumThreads(NUM_THREADS);
                options.setUseXNNPACK(useXNNPACK);
            }
            d.tfLite = new Interpreter(modelFile, options);
            d.tfLiteModel = modelFile;
            d.tfLiteOptions = options;
            d.BATCH_SIZE = 1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Pre-allocate buffers.
        int numBytesPerChannel;
        if (isQuantized) {
            numBytesPerChannel = 1; // Quantized
        } else {
            numBytesPerChannel = 4; // Floating point
        }

        d.imgData = ByteBuffer.allocateDirect(d.BATCH_SIZE * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imgData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];
        d.isModelQuantized = isQuantized;
        d.minimumConfidence = minimumConfidence;
        d.outputLocations = new float[1][OUTPUT_WIDTH[0]][4];
        d.outputClasses = new float[1][OUTPUT_WIDTH[1]][d.labels.size()];
        return d;
    }

    @Override
    public List<Recognition> recognizeImage(final Bitmap bitmap) {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");
        Trace.beginSection("preprocessBitmap");
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
        Trace.endSection(); // preprocessBitmap

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");

        outputLocations = new float[1][OUTPUT_WIDTH[0]][4];
        outputClasses = new float[1][OUTPUT_WIDTH[1]][labels.size()];
        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();

        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        Trace.endSection();

        Trace.beginSection("run");
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();

        int gridWith = OUTPUT_WIDTH[0];
        final ArrayList<Recognition> detections = new ArrayList<>();

        for (int i = 0; i < gridWith; i++) {
            float maxClass = 0;
            int detectedClass = -1;
            final float[] classes = new float[labels.size()];

            System.arraycopy(outputClasses[0][i], 0, classes, 0, labels.size());

            for (int c = 0; c < labels.size(); c++){
                if (classes[c] > maxClass){
                    detectedClass = c;
                    maxClass = classes[c];
                }
            }

            final float score = maxClass;
            if (score > minimumConfidence) {
                final float xPos = outputLocations[0][i][0];
                final float yPos = outputLocations[0][i][1];
                final float w = outputLocations[0][i][2];
                final float h = outputLocations[0][i][3];

                final RectF rectF = new RectF(
                        Math.max(0, xPos - w / 2),
                        Math.max(0, yPos - h / 2),
                        Math.min(bitmap.getWidth() - 1, xPos + w / 2),
                        Math.min(bitmap.getHeight() - 1, yPos + h / 2));
                detections.add(new Recognition("" + i, labels.get(detectedClass), score, detectedClass, rectF));
            }
        }

        Trace.endSection();
        return nms(detections);
    }

    @Override
    public void enableStatLogging(final boolean logStats) {}

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
        if (tfLite != null) {
            tfLite.close();
            tfLite = null;
        }
    }

    @Override
    public void setNumThreads(int numThreads) {
        if (tfLite != null) {
            tfLiteOptions.setNumThreads(numThreads);
            recreateInterpreter();
        }
    }

    @Override
    public void setUseNNAPI(boolean isChecked) {
        if (tfLite != null) {
            tfLiteOptions.setUseNNAPI(isChecked);
            recreateInterpreter();
        }
    }

    @Override
    public boolean isGPUAvailable() {
        CompatibilityList compatibilityList = new CompatibilityList();

        return compatibilityList.isDelegateSupportedOnThisDevice();
    }

    private void recreateInterpreter() {
        tfLite.close();
        tfLite = new Interpreter(tfLiteModel, tfLiteOptions);
    }

    //non maximum suppression
    protected ArrayList<Recognition> nms(ArrayList<Recognition> list) {
        ArrayList<Recognition> nmsList = new ArrayList<>();

        for (int k = 0; k < labels.size(); k++) {
            //1.find max confidence per class
            PriorityQueue<Recognition> pq =
                    new PriorityQueue<>(50,
                            (lhs, rhs) -> {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            });

            for (Recognition recognition : list) {
                if (recognition.getDetectedClass() == k) {
                    pq.add(recognition);
                }
            }

            //2.do non maximum suppression
            while (pq.size() > 0) {
                //insert detection with max confidence
                Recognition[] a = new Recognition[pq.size()];
                Recognition[] detections = pq.toArray(a);
                Recognition max = detections[0];
                nmsList.add(max);
                pq.clear();

                for (int j = 1; j < detections.length; j++) {
                    Recognition detection = detections[j];
                    RectF b = detection.getLocation();
                    if (box_iou(max.getLocation(), b) < mNmsThresh) {
                        pq.add(detection);
                    }
                }
            }
        }
        return nmsList;
    }

    protected float mNmsThresh = 0.6f;

    protected float box_iou(RectF a, RectF b) {
        return box_intersection(a, b) / box_union(a, b);
    }

    protected float box_intersection(RectF a, RectF b) {
        float w = overlap((a.left + a.right) / 2, a.right - a.left,
                (b.left + b.right) / 2, b.right - b.left);
        float h = overlap((a.top + a.bottom) / 2, a.bottom - a.top,
                (b.top + b.bottom) / 2, b.bottom - b.top);
        if (w < 0 || h < 0) return 0;
        return w * h;
    }

    protected float box_union(RectF a, RectF b) {
        float i = box_intersection(a, b);
        return (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - i;
    }

    protected float overlap(float x1, float w1, float x2, float w2) {
        float l1 = x1 - w1 / 2;
        float l2 = x2 - w2 / 2;
        float left = Math.max(l1, l2);
        float r1 = x1 + w1 / 2;
        float r2 = x2 + w2 / 2;
        float right = Math.min(r1, r2);
        return right - left;
    }
}
