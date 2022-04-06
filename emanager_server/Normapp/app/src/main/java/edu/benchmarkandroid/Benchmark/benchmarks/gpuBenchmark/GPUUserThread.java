package edu.benchmarkandroid.Benchmark.benchmarks.gpuBenchmark;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.IOException;

public class GPUUserThread extends Thread {

    private long sleep;
    private boolean alive;
    private final AssetManager am;
    private final int gpuThreads;
    private final Bitmap sampleBitmap;
    private ImageClassifier classifier;

    private static final String TAG = "GPUUserThread";

    public GPUUserThread(AssetManager am, int gpuThreads, Bitmap sampleBitmap) throws IOException {
        super();
        this.alive = true;
        this.am = am;
        this.gpuThreads = gpuThreads;
        this.sampleBitmap = sampleBitmap;
        this.setDaemon(true);
    }

    /**
     * Consumes GPU by means of performing object detection
     */
    @Override
    public void run() {
        try {
            this.classifier = new ImageClassifierFloatMobileNet(this.am, this.gpuThreads);
            this.classifier.recreateBitmap(this.sampleBitmap);
            this.classifier.recreateModel();
        } catch (IOException ex) {
            Log.e(TAG, "Fail creating classifier", ex);
            this.kill();
        }
        while (this.alive) {
            synchronized (this) {
                if (this.sleep > 0)
                    try {
                        this.wait(this.sleep);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Fail waiting", e);
                    }
            }
            try {
                classifier.classifyFrame();
            } catch (Throwable t) {
                Log.d(TAG, "Fail classifying", t);
            }
        }
    }

    public synchronized long getSleep() {
        return sleep;
    }

    public synchronized void setSleep(long sleep) {
        this.sleep = sleep;
        this.notify();
    }

    public synchronized void kill() {
        this.alive = false;
        if (this.classifier != null)
            this.classifier.close();
        this.notify();
    }
}
