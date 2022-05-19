package edu.benchmarkandroid.Benchmark.benchmarks.gpuBenchmark;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.ProgressUpdater;
import edu.benchmarkandroid.utils.GPUUtils;

import java.io.IOException;
import java.io.InputStream;

public class GPUBenchmark extends Benchmark {

    private static final String TAG = "GPUBenchmark";
    private static final int WAITING = 300;
    private static final String SAMPLE_PNG_FILE = "sample.png";

    private int threads = getVariant().getParamsRunStage().getIntValue("threads");
    private int workerThreads = 1;
    private ProgressUpdater progressUpdater = null;
    private GPUUserThread[] gpuUsers;

    public GPUBenchmark(Variant variant) {
        super(variant);
    }

    @Override
    public void runBenchmark(StopCondition stopCondition, ProgressUpdater progressUpdater) {

        this.progressUpdater = progressUpdater;
        Bitmap sampleBitmap = null;

        try {
            InputStream pngInputStream = getAssets().open(SAMPLE_PNG_FILE);
            sampleBitmap = BitmapFactory.decodeStream(pngInputStream);
        } catch (IOException ex) {
            Log.d(TAG, "Could not load sample.png");
            Log.d(TAG, ex.toString());
            return;
        }

        try {
            gpuUsers = new GPUUserThread[workerThreads];
            for (int i = 0; i < workerThreads; i++) {
                gpuUsers[i] = new GPUUserThread(getAssets(), this.threads, sampleBitmap);
                gpuUsers[i].setSleep(0);
                gpuUsers[i].start();
            }
        } catch (final IOException e) {
            Log.d(TAG, "Classifiers could not be initialized");
            Log.d(TAG, e.toString());
            return;
        }

        Log.i(TAG, "runBenchmark: threads: " + threads);

        while (stopCondition.canContinue()) {
            double gpuUsage = gpuUsage();
            String msg = "GPUUsage: " + gpuUsage;
            Log.d(TAG, "runBenchmark: " + msg);
            progressUpdater.update(msg);
        }

        for (int i = 0; i < this.workerThreads; i++)
            gpuUsers[i].kill();

        gpuUsers = null;
        Log.d(TAG, "runBenchmark: END");
        progressUpdater.end();
        this.progressUpdater = null;
    }

    @Override
    public void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater) {
    }

    protected synchronized double gpuUsage() {
        try {
            wait(WAITING);
        } catch (InterruptedException e) {
            Log.d(TAG, "gpuUsage: error waiting");
        }
        return GPUUtils.readUsage();
    }

    @Override
    public void gentleTermination() {
        if (gpuUsers != null) {
            for (GPUUserThread gpuUserThread : gpuUsers) {
                gpuUserThread.kill();
            }

            if (progressUpdater != null)
                progressUpdater.end();
        }
    }

}

