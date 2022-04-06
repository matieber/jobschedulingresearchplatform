package edu.benchmarkandroid.Benchmark.benchmarks.warmup;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.Classifier;
import edu.benchmarkandroid.Benchmark.benchmarks.dogsBenchmark.MobileNetDetectionAPIModel;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.ProgressUpdater;

import java.io.IOException;

public class WarmUpBenchmark extends Benchmark {
    private static final String TAG = WarmUpBenchmark.class.getSimpleName();
    private static final int MODEL_INPUT_SIZE = 300;
    private static final boolean IS_MODEL_QUANTIZED = true;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "labelmap.txt";
    private static final String SAMPLE_IMAGE = "kite.jpg";
    private CPUBenchmarkThread[] cpuUsers;

    private ProgressUpdater progressUpdater = null;
    private Classifier detector;

    public WarmUpBenchmark(Variant variant) {
        super(variant);
    }

    @Override
    public void runBenchmark(StopCondition stopCondition, ProgressUpdater progressUpdater) {
        Log.d(TAG, "Beginning warm up benchmark");
        WarmUpData warmUpData = new WarmUpData();

        this.progressUpdater = progressUpdater;

        runCPUBenchmark(warmUpData);
        runDLBenchmark(warmUpData,false, 50);
        runDLBenchmark(warmUpData,true, 50);
        warmUpData.setGPUAvailable(true);
        progressUpdater.update("WarmingUp: " + warmUpData);
    }

    private void runDLBenchmark(WarmUpData data, boolean useGpu, int times) {
        long totalTime = 0;

        try {
            this.detector = MobileNetDetectionAPIModel.create(
                    getAssets(),
                    MODEL_FILE,
                    LABELS_FILE,
                    MODEL_INPUT_SIZE,
                    IS_MODEL_QUANTIZED,
                    useGpu
            );

            if (useGpu)
                data.setGPUAvailable(detector.isGPUAvailable());

            Bitmap bm = BitmapFactory.decodeStream(getAssets().open(SAMPLE_IMAGE));

            for (int i = 0; i < times; i++) {
                long startDetectionTime = System.currentTimeMillis();
                detector.recognizeImage(bm);
                long endDetectionTime = System.currentTimeMillis() - startDetectionTime;
                totalTime += endDetectionTime;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (useGpu) {
            data.setAverageGPUInferring((float) totalTime / times);
        } else {
            data.setAverageCPUInferring((float) totalTime / times);
        }
    }

    @Override
    public void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater) {
    }

    @Override
    public void gentleTermination() {
        if (cpuUsers != null)
            for(CPUBenchmarkThread thread : cpuUsers)
                thread.kill();

        if (progressUpdater != null)
            progressUpdater.end();

        if (detector != null)
            detector.close();

    }

    private void runCPUBenchmark(WarmUpData warmUpData)
    {
        try {
            Log.d(TAG, "running CPU Benchmark");

            int cpus = Runtime.getRuntime().availableProcessors();
            cpuUsers = new CPUBenchmarkThread[cpus];
            for (int i = 0; i < cpus; i++) {
                cpuUsers[i] = new CPUBenchmarkThread(1000);
                cpuUsers[i].start();
            }

            long totalTime = 0;
            for (CPUBenchmarkThread thread : cpuUsers) {
                thread.join();
                totalTime += thread.getTime();
            }

            warmUpData.setCpus(cpus);
            warmUpData.setAverageCPUTime((float)totalTime / cpus);
            Log.d(TAG, "average CPU time: " + warmUpData.getAverageCPUTime());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
