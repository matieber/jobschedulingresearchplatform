package edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.Log;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.jsonConfig.ParamsRunStage;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.BatteryNotificator;
import edu.benchmarkandroid.service.ProgressUpdater;
import edu.benchmarkandroid.service.WifiNotificator;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("unused")
public class DogsBenchmark extends Benchmark {

    private static final String TAG = "DogsBenchmark";

    ParamsRunStage prs = getVariant().getParamsRunStage();
    private final String imagePrefix = prs.getValue("imagePreffix");
    private final int beginFrameIndex = prs.getIntValue("beginFrameIndex");
    private final int endFrameIndex = prs.getIntValue("endFrameIndex");
    private final boolean useGpu = prs.getBooleanValue("useGpu");
    private final boolean useXNNPack = prs.getBooleanValue("useXNNPack");
    private final String model = prs.getValue("model");

    private ProgressUpdater progressUpdater = null;

    private static final int MOBILE_NET_MODEL_INPUT_SIZE = 300;
    private static final int YOLO_V4_MODEL_INPUT_SIZE = 416;
    private static final boolean IS_MODEL_QUANTIZED = true;
    private static final String MOBILENET_MODEL_FILE = "detect.tflite";
    private static final String MOBILENET_LABELS_FILE = "labelmap.txt";
    private static final String YOLO_V4_MODEL_FILE = "yolov4-tiny-416.tflite";
    private static final String YOLO_V4_LABELS_FILE = "cocomap.txt";


    private final BatteryNotificator batteryNotificator;
    private final WifiNotificator wifiInfoNotificator;

    private Classifier detector;

    public DogsBenchmark(Variant variant) {
        super(variant);
        batteryNotificator = BatteryNotificator.getInstance();
        wifiInfoNotificator = WifiNotificator.getInstance();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void runBenchmark(StopCondition stopCondition, final ProgressUpdater progressUpdater) {
        Log.d(TAG, String.format("Model: %s, use GPU: %s, use XNNPack: %s", model, useGpu, useXNNPack));
        long jobInitTime = System.currentTimeMillis();
        try {
            if (model.equals("yolov4-lite") || model.equals("yolov4")) {
                int CPUThreads = prs.getIntValue("cpuThreads");
                this.detector = YoloV4DetectionAPIModel.create(
                        getContext(),
                        YOLO_V4_MODEL_FILE,
                        YOLO_V4_LABELS_FILE,
                        YOLO_V4_MODEL_INPUT_SIZE,
                        false,
                        0.5f,
                        useGpu,
                        useXNNPack,
                        CPUThreads);
            } else {
                this.detector =
                        MobileNetDetectionAPIModel.create(
                                getAssets(),
                                MOBILENET_MODEL_FILE,
                                MOBILENET_LABELS_FILE,
                                MOBILE_NET_MODEL_INPUT_SIZE,
                                IS_MODEL_QUANTIZED,
                                useGpu);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        this.progressUpdater = progressUpdater;

        this.progressUpdater.update("frameno,success,jobInitTime(millis),detectTime(millis),inputNetTime(millis),inputSize(kb),rssi,battLevel[init;end],recognition");

        for (int i = beginFrameIndex; i <= endFrameIndex; i++) {
            String imageName = imagePrefix + "." + i + ".jpg";
            String url = "http://" + getServerIp() + ":8001/" + imagePrefix + "/" + imageName;
            StringBuilder batteryLevelInfo = new StringBuilder();
            batteryLevelInfo.append(batteryNotificator.getCurrentLevel());
            boolean success = false;
            long startNetworkTime = System.currentTimeMillis();
            Bitmap bm;
            long kb_input = 0;
            int rssi_value = wifiInfoNotificator.getCurrentSignalStrength();
            long endNetworkTime = -1;
            long endDetectionTime = -1;
            StringBuilder recog = new StringBuilder();
            try {
                Log.d(TAG, "Downloading " + url);
                bm = Picasso.with(getContext()).load(url).memoryPolicy(MemoryPolicy.NO_STORE).get();
                /* Uncomment the next line (and comment the above) for loading images from local filesystem
                 which you may find useful for debugging purposes*//*
                bm = Picasso.with(getContext()).load(new File("/sdcard/download/cycling.1.jpg")).memoryPolicy(MemoryPolicy.NO_STORE).get();
                */
                int color = 4; //32 bits = 4 bytes
                kb_input = bm.getHeight() * bm.getWidth() * color / 1024;
                success = true;
                endNetworkTime = System.currentTimeMillis() - startNetworkTime;
                long startDetectionTime = System.currentTimeMillis();
                List<Classifier.Recognition> recognitionList = detector.recognizeImage(bm);
                endDetectionTime = System.currentTimeMillis() - startDetectionTime;
                recog.append("[");

                recognitionList.sort(new Comparator<Classifier.Recognition>() {
                    @Override
                    public int compare(Classifier.Recognition o1, Classifier.Recognition o2) {
                        if (o1.getConfidence() > o2.getConfidence())
                            return -1;
                        else
                            if (o1.getConfidence() < o2.getConfidence())
                                return 1;
                        return 0;
                    }
                });
                for (Classifier.Recognition r : recognitionList) {
                    recog.append("'").append(r.getTitle()).append(":").append(String.format("%.2f",r.getConfidence())).append("'");
                    recog.append(";");
                }
                recog.deleteCharAt(recog.length()-1);
                recog.append("]");
                recog.trimToSize();
            } catch (IOException ex) {
                Log.d(TAG, "Fail to download " + url + ". None retry implemented", ex);
            }
            String msg = imageName + "," + success + "," + jobInitTime + "," + endDetectionTime + "," + endNetworkTime;
            batteryLevelInfo.append(";");
            batteryLevelInfo.append(batteryNotificator.getCurrentLevel());
            msg += "," + kb_input + "," + rssi_value + "," + batteryLevelInfo + "," + recog;
            Log.d(TAG,msg);
                    this.progressUpdater.update(msg);
        }

        Log.i(TAG, "runBenchmark: imagePreffix: " + imagePrefix + " beginIndex = " + beginFrameIndex + " endIndex = " + endFrameIndex);

        Log.d(TAG, "runBenchmark: END");
        progressUpdater.end();
        detector.close();
        this.progressUpdater = null;
        this.detector = null;
    }

    @Override
    public void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater) {
    }

    @Override
    public void gentleTermination() {
        if (progressUpdater != null)
            progressUpdater.end();
        if (detector != null) {
            detector.close();
        }
    }

}

