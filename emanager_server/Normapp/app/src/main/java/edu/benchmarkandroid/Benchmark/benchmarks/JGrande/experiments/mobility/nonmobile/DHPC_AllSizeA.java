package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile;

import edu.benchmarkandroid.Benchmark.StopCondition;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.Statistics;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.ep.DHPC_EPBench;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.fft.DHPC_FFTBench;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.hanoi.DHPC_HanoiBench;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.prime.DHPC_PrimeBench;
import edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.sieve.DHPC_SieveBench;
import edu.benchmarkandroid.Benchmark.jsonConfig.ParamsRunStage;
import edu.benchmarkandroid.service.ProgressUpdater;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * ep NAS Embarrasingly Parallel benchmark (Class S problem size) fib Calculate
 * the 40th Fibonacci benchmark fft NAS Fast Fourier Transform benchmark (Class
 * S problem size) hanoi Solve the Tower of Hanoi with 25 disks sieve Sieve of
 * Erasthosthenes with an array size of 10000
 */
public class DHPC_AllSizeA {

    private int executed = 0;

    public DHPC_AllSizeA() {
    }

    public int getExecutedCount() {
        return executed;
    }

    public void run(ProgressUpdater progressUpdater, ParamsRunStage paramsRunStage, StopCondition stopCondition) {

        int runs = paramsRunStage.getIntValue("runs");

        double[] CPUSnapshots = new double[runs];

        try {
            for (int i = 0; i < runs; i++) {
                long l = System.currentTimeMillis();
                runClass(DHPC_FFTBench.class, progressUpdater);
                CPUSnapshots[i] = System.currentTimeMillis() - l;
                executed++;
                if (!stopCondition.canContinue()) {
                    progressUpdater.updateLog("Condition not met while executing DHPC_FFTBench");
                    return;
                }
            }
            progressUpdater.updateLog("DHPC_FFTBench\t" + Statistics.average(CPUSnapshots)
                    + "\t" + Statistics.standardDeviation(CPUSnapshots));
        } catch (Throwable e) {
            progressUpdater.updateLog("Error executing DHPC_FFTBench");
        }

        try {
            for (int i = 0; i < runs; i++) {
                long l = System.currentTimeMillis();
                runClass(DHPC_SieveBench.class, progressUpdater);
                CPUSnapshots[i] = System.currentTimeMillis() - l;
                executed++;
                if (!stopCondition.canContinue()) {
                    progressUpdater.updateLog("Condition not met while executing DHPC_SieveBench");
                    return;
                }
            }
            progressUpdater.updateLog("DHPC_SieveBench\t"
                    + Statistics.average(CPUSnapshots) + "\t"
                    + Statistics.standardDeviation(CPUSnapshots));
        } catch (Throwable e) {
            progressUpdater.updateLog("Error executing DHPC_SieveBench");
            //e.printStackTrace(System.out);
        }

        try {
            for (int i = 0; i < runs; i++) {
                long l = System.currentTimeMillis();
                runClass(DHPC_HanoiBench.class, progressUpdater);
                CPUSnapshots[i] = System.currentTimeMillis() - l;
                executed++;
                if (!stopCondition.canContinue()) {
                    progressUpdater.updateLog("Condition not met while executing DHPC_HanoiBench");
                    return;
                }
            }
            progressUpdater.updateLog("DHPC_HanoiBench\t"
                    + Statistics.average(CPUSnapshots) + "\t"
                    + Statistics.standardDeviation(CPUSnapshots));
        } catch (Throwable e) {
            progressUpdater.updateLog("Error executing DHPC_HanoiBench");
            //e.printStackTrace(System.out);
        }

        try {
            for (int i = 0; i < runs; i++) {
                long l = System.currentTimeMillis();
                runClass(DHPC_EPBench.class, progressUpdater);
                CPUSnapshots[i] = System.currentTimeMillis() - l;
                executed++;
                if (!stopCondition.canContinue()) {
                    progressUpdater.updateLog("Condition not met while executing DHPC_EPBench");
                    return;
                }
            }
            progressUpdater.updateLog("DHPC_EPBench\t" + Statistics.average(CPUSnapshots)
                    + "\t" + Statistics.standardDeviation(CPUSnapshots));
        } catch (Throwable e) {
            progressUpdater.updateLog("Error executing DHPC_EPBench");
            //e.printStackTrace(System.out);
        }

        try {
            for (int i = 0; i < runs; i++) {
                long l = System.currentTimeMillis();
                runClass(DHPC_PrimeBench.class, progressUpdater);
                CPUSnapshots[i] = System.currentTimeMillis() - l;
                executed++;
                if (!stopCondition.canContinue()) {
                    progressUpdater.updateLog("Condition not met while executing DHPC_PrimeBench");
                    return;
                }
            }
            progressUpdater.updateLog("DHPC_PrimeBench\t"
                    + Statistics.average(CPUSnapshots) + "\t"
                    + Statistics.standardDeviation(CPUSnapshots));
        } catch (Throwable e) {
            progressUpdater.updateLog("Error executing DHPC_PrimeBench");
            //e.printStackTrace(System.out);
        }

    }

    private void runClass(Class target, ProgressUpdater progressUpdater) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Class[] type = {ProgressUpdater.class};
        Object[] params = {progressUpdater};
        List<Future> list = new ArrayList<Future>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            Runnable task = (Runnable) target.getConstructor(type).newInstance(params);
            list.add(pool.submit(task));
        }
        pool.shutdown();
        for (Future item : list) {
            item.get();
        }
    }
}