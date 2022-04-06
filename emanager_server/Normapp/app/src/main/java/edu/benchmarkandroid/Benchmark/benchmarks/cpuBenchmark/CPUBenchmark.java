package edu.benchmarkandroid.Benchmark.benchmarks.cpuBenchmark;

import android.util.Log;
import edu.benchmarkandroid.Benchmark.Benchmark;
import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.jsonConfig.Variant;
import edu.benchmarkandroid.service.ProgressUpdater;
import edu.benchmarkandroid.service.ThresholdNotificator;
import edu.benchmarkandroid.utils.CPUUtils;

public class CPUBenchmark extends Benchmark {

    //private static final String EMPTY_PAYLOAD = "empty";
    private static final String TAG = "CPUBenchmark";
    private CPUUserThread[] cpuUsers;

    private static final int READING_TIMES = 30;
    private static final int WAITING = 300;

    private static long sleep;
    private int cpus = Runtime.getRuntime().availableProcessors();
    private final double target = getVariant().getParamsRunStage().getCpuLevel();
    private final double threshold = getVariant().getParamsSamplingStage().getDoubleValue("convergenceThreshold");

    private ProgressUpdater progressUpdater = null;

    public CPUBenchmark(Variant variant) {
        super(variant);
    }

    public void setCPUs(int cpus) {
        this.cpus = cpus;
    }

    @Override
    public void runBenchmark(StopCondition stopCondition, ProgressUpdater progressUpdater) {

        this.progressUpdater = progressUpdater;

        String msg;
        double cpuUsage;

        Log.i(TAG, "runBenchmark: sleep: " + sleep);

        if (target == 0.0) {
            runBenchmarkZero(stopCondition, progressUpdater);
        } else {

            cpuUsers = new CPUUserThread[this.cpus];
            for (int i = 0; i < this.cpus; i++) {
                cpuUsers[i] = new CPUUserThread();
                cpuUsers[i].setSleep(sleep);
                cpuUsers[i].start();
            }

            boolean stable = false;
            boolean nowStable;
            double diff;
            long sleepNew;

            Log.d(TAG, "runBenchmark: target: " + target + "threshold: " + threshold);
            while (stopCondition.canContinue()) {
                cpuUsage = cpuUsage();

                diff = cpuUsage / target;
                sleepNew = (long) (sleep * diff);

                Log.d(TAG, "runBenchmark: CPU Usage: " + cpuUsage +
                        " sleep: " + sleep + " diff: " + diff);

                msg = "CPUUsage: " + cpuUsage + " Sleep: " + sleep;

                nowStable = ((-threshold) < (cpuUsage - target)) && ((cpuUsage - target) < (threshold));

                if ((sleep == sleepNew) && !nowStable) {
                    if (diff > 1) {
                        sleep++;
                    } else {
                        sleep--;
                        if (sleep < 0) sleep = 0;
                    }

                } else {
                    sleep = sleepNew;
                }

                for (int i = 0; i < this.cpus; i++)
                    cpuUsers[i].setSleep(sleep);


                if (nowStable) {
                    Log.d(TAG, "runBenchmark: CPU Usage: " + cpuUsage + " nowStable: " + nowStable);
                } else {
                    Log.d(TAG, "runBenchmark: no esta estable");
                }
                progressUpdater.update(msg);
            }

            for (int i = 0; i < this.cpus; i++)
                cpuUsers[i].kill();
        }

        Log.d(TAG, "runBenchmark: END");
        progressUpdater.end();
        this.progressUpdater = null;
    }

    private void runBenchmarkZero(StopCondition stopCondition, ProgressUpdater progressUpdater) {

        this.progressUpdater = progressUpdater;

        while (stopCondition.canContinue()) {
            synchronized (this) {
                try {
                    this.wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                double cpuUsage = cpuUsage();
                Log.d(TAG, "CPU Usage: " + cpuUsage);

                String msg = "CPUUsage: " + cpuUsage + " Sleep: 0";

                progressUpdater.update(msg);
            }
        }
    }


    public void runSampling(StopCondition stopCondition, ProgressUpdater progressUpdater) { //  CONVERGENCE
        int iterations = 0;
        String msg;
        double cpuUsage;
        sleep = 1;

        if (target == 0.0) {
            return;
        }

        cpuUsers = new CPUUserThread[this.cpus];
        //creates as many cpu consumers as available cores
        for (int i = 0; i < this.cpus; i++) {
            cpuUsers[i] = new CPUUserThread();
            cpuUsers[i].setSleep(sleep);
            cpuUsers[i].start();
        }

        boolean nowStable;
        double diff;
        long sleepNew;

        Log.d(TAG, "runConvergence: target: " + target + " threshold: " + threshold);

        ThresholdNotificator thresholdNotificator = ThresholdNotificator.getInstance();

        while (stopCondition.canContinue() || iterations < 10) { //var "progress" to avoid early convergence
            cpuUsage = cpuUsage();

            diff = cpuUsage / target;
            sleepNew = (long) (sleep * diff);

            Log.d(TAG, "runConvergence: CPU Usage: " + cpuUsage +
                    " sleep: " + sleep + " diff: " + diff);

            msg = "CPUUsage: " + cpuUsage + " Sleep: " + sleep;

            thresholdNotificator.updateThresholdLevel(cpuUsage - target);

            if ((sleep == sleepNew) && stopCondition.canContinue()) { //canContinue checks if is not stable yet
                if (diff > 1) {
                    sleep++;
                } else {
                    sleep--;
                    if (sleep < 0) sleep = 0;
                }

            } else {
                sleep = sleepNew;
            }


            for (int i = 0; i < this.cpus; i++)
                cpuUsers[i].setSleep(sleep);

            iterations += 1;
            progressUpdater.update(msg);
        }

        Log.d(TAG, "runConvergence: END");

        for (int i = 0; i < this.cpus; i++)
            cpuUsers[i].kill();

    }

    protected synchronized double cpuUsage() {
        double result = 0;
        for (int i = 0; i < READING_TIMES; i++) {
            double aux = CPUUtils.readUsage();
            if (!Double.isNaN(aux)) {
                result += aux;
            }
            try {
                wait(WAITING);
            } catch (InterruptedException e) {
                Log.d(TAG, "cpuUsage: error waiting");
            }
        }
        return result / READING_TIMES;
    }


    @Override
    public void gentleTermination() {
        if (cpuUsers != null) {
            for (CPUUserThread cpuUserThread : cpuUsers) {
                cpuUserThread.kill();
            }

            if (progressUpdater != null)
                progressUpdater.end();
        }
    }

}

