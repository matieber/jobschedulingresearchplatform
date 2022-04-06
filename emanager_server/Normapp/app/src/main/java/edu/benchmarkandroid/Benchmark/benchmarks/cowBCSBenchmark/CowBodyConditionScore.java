package edu.benchmarkandroid.Benchmark.benchmarks.cowBCSBenchmark;

import android.graphics.Bitmap;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import org.tensorflow.lite.DataType;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.Classifier;
import edu.benchmarkandroid.Benchmark.jsonConfig.ParamsRunStage;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.BatteryNotificator;
import edu.benchmarkandroid.service.BenchmarkExecutor;
import edu.benchmarkandroid.service.ProgressUpdater;
import edu.benchmarkandroid.service.WifiNotificator;
import edu.jnpy.*;
import org.tensorflow.lite.DataType;
import com.chaquo.python.PyException;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

@SuppressWarnings("unused")
public class CowBodyConditionScore extends Benchmark {

    private static final String TAG = "CowBodyConditionScore";
    private final int MAX_RETRIES = 20;

    ParamsRunStage prs = getVariant().getParamsRunStage();
    private final String imagePrefix = prs.getValue("imagePreffix");
    private final int beginFrameIndex = prs.getIntValue("beginFrameIndex");
    private final int endFrameIndex = prs.getIntValue("endFrameIndex");
    private final boolean preLoadedImg = prs.getBooleanValue("preLoadedImg");

    private ProgressUpdater progressUpdater = null;

    private static final boolean IS_MODEL_QUANTIZED = true;

    private final BatteryNotificator batteryNotificator;
    private final WifiNotificator wifiInfoNotificator;
    private final String img_path = "/sdcard/Download/my.png";
    private final String preprocessed_img_path = "/sdcard/Download/my.npy";

    private BcsDetectionAPIModel detector;
    private PyObject module = null;

    public CowBodyConditionScore(Variant variant) {
        super(variant);
        batteryNotificator = BatteryNotificator.getInstance();
        wifiInfoNotificator = WifiNotificator.getInstance();
        Log.d(TAG, "Starting Python environment");
        /** /**The following code was possible thanks to chaquopy gradle plugin:
         * https://chaquo.com/chaquopy/doc/current/android.html#android-requirements
         *
         * Code to initialize Python environment*/
        if (! Python.isStarted()) {
            Python.start(new AndroidPlatform(BenchmarkExecutor.getBenchmarkExecutorAppContext()));
        }
        Python py = Python.getInstance();
        Log.d(TAG, "Getting preprocess_image python module");
        this.module = py.getModule("preprocess_image");
    }

    @Override
    public void runBenchmark(StopCondition stopCondition, final ProgressUpdater progressUpdater) {
        long jobInitTime = System.currentTimeMillis();
        Log.d(TAG, String.format("Initiating Job at SystemCurrentMillis: %s", jobInitTime));
        try {

            this.detector = BcsDetectionAPIModel.create(
                    getAssets(),
                    "bcs_classifier_preProcDE-channels.tflite",
                    "bcslabel.txt",
                    false
            );


        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        this.progressUpdater = progressUpdater;

        this.progressUpdater.update("frameno,success,jobInitTime(millis),detectTime(millis),inputNetTime(millis),preprocessingTime(millis),inputSize(kb),rssi,battLevel[init;end],recognition");
        Log.d(TAG, "Begin iteration over job images");
        for (int i = beginFrameIndex; i <= endFrameIndex; i++) {
            StringBuilder batteryLevelInfo = new StringBuilder();
            batteryLevelInfo.append(batteryNotificator.getCurrentLevel());
            boolean success = false;
            Bitmap bm;
            long kb_input = 0;
            int rssi_value = wifiInfoNotificator.getCurrentSignalStrength();
            long endNetworkTime = -1;
            long endDetectionTime = -1;
            long endPreprocessingTime = -1;
            success = false;
            TensorBuffer tensorBuffer = null;
            List<Classifier.Recognition> recognitionList = new ArrayList<>();
            StringBuilder recog = new StringBuilder();


            try {
                String imgURI = !preLoadedImg ? "http://" + getServerIp() + ":8001/" + imagePrefix + "/"
                        : "/sdcard/Download/" + imagePrefix + "/";
                imgURI = imgURI + imagePrefix + "." + i + ".png";
                endNetworkTime = getImg(0, System.currentTimeMillis() ,imgURI);

                long startPreprocessingTime = System.currentTimeMillis();
                preprocessImg(img_path);
                Npy npy = new Npy(preprocessed_img_path);
                float[] npyData = npy.floatElements();
                int[] inputShape = new int[]{1, 424, 512, 2};   //the data shape before I flattened it
                tensorBuffer = TensorBuffer.createFixedSize(inputShape, DataType.FLOAT32);
                tensorBuffer.loadArray(npyData);
                endPreprocessingTime = System.currentTimeMillis() - startPreprocessingTime;
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            long startDetectionTime = System.currentTimeMillis();

            /**
             *
             * Here insert code specific to the image recognition model using bm variable as
             * input bitmap for the model.
             * **/
            if (success)
                recognitionList = detector.recognize(tensorBuffer);


            endDetectionTime = System.currentTimeMillis() - startDetectionTime;
            recog.append("'");

            /**
             *
             * Here insert code specific to output of the recognition. Please use recog
             * variable to append the result. The format is like:
             *
             * 'output result, e.g., classes, labels,score'
             *
             * */
            for (Classifier.Recognition r : recognitionList) {
                recog.append(r.toString());
                recog.append(";");
            }

            recog.append("'");
            recog.trimToSize();

            String msg = i + "," + success + "," + jobInitTime + "," + endDetectionTime + "," + endNetworkTime + "," + endPreprocessingTime;
            batteryLevelInfo.append(";");
            batteryLevelInfo.append(batteryNotificator.getCurrentLevel());
            msg += "," + kb_input + "," + rssi_value + "," + batteryLevelInfo + "," + recog;
            this.progressUpdater.update(msg);
        }
        Log.i(TAG, "runBenchmark: imagePreffix: " + imagePrefix + " beginIndex = " + beginFrameIndex + " endIndex = " + endFrameIndex);

        Log.d(TAG, "runBenchmark: END");
        progressUpdater.end();
        this.progressUpdater = null;

    }

    private long getImg(int retry_nmb, final long request_start_millis, final String imgURI) throws IOException{

        if (retry_nmb != 0 && retry_nmb < MAX_RETRIES){
            Log.d(TAG, "Fail to get " + imgURI + ". Retry nmb: "+ retry_nmb);
        }
        else {
            if (retry_nmb == MAX_RETRIES)
                throw new IOException("MAX RETRIES reach while trying to get " + imgURI);
        }
        Log.d(TAG, "Getting " + imgURI);
        try (
                InputStream is = !preLoadedImg ? new URL(imgURI).openStream() : new FileInputStream(imgURI);
                BufferedInputStream in = new BufferedInputStream(is);
                FileOutputStream fileOutputStream = new FileOutputStream(img_path)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (IOException e) {
            //throw new RuntimeException(e);
            getImg(retry_nmb+1, request_start_millis,imgURI);
        }

        return System.currentTimeMillis() - request_start_millis;
    }

    private void preprocessImg(String pngImgPath) {

        try {
            /**The following code was possible thanks to chaquopy gradle plugin:
             * https://chaquo.com/chaquopy/doc/current/android.html#android-requirements
             *
             * it is assumed that
             * preprocessing_image script saves a .npy image at: "/sdcard/Download/my.npy"*/
            Log.d(TAG, "calling preprocess_image python module");
            module.callAttr("preprocess_image",pngImgPath);
        } catch (PyException e) {
            e.printStackTrace();
        }
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

