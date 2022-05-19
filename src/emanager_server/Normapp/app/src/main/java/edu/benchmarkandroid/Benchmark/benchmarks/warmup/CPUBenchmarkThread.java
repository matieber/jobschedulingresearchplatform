package edu.benchmarkandroid.Benchmark.benchmarks.warmup;

import android.util.Log;

public class CPUBenchmarkThread extends Thread {
    private long sleep;
    private int cycles = 1000000;
    private int times;
    private boolean alive;
    private long time;

    private static final String TAG = "CPUUserThread";

    public CPUBenchmarkThread(int times) {
        super();
        this.alive = true;
        this.times = times;
        this.setDaemon(true);
    }

    /**
     * Consumes CPU by means of floating point operation and then sleeps
     */
    @Override
    public void run() {
        double a = 1;
        double b = 2;
        long startTime, endTime;

        startTime = System.currentTimeMillis();
        for (int i = 0; i < times && alive; i++) {
            for (int j = 0; j < cycles; j++) {
                if (a == 0) a = 1;
                a *= b;
            }
        }
        endTime = System.currentTimeMillis();
        time = endTime - startTime;
    }

    public synchronized void kill() {
        this.alive = false;
        this.notify();
    }

    public long getTime() {
        return time;
    }
}
