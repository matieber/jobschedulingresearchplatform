package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments;

public class Statistics {

    public static double average(double[] cPUSnapshots) {
        double d = 0;
        for (double n : cPUSnapshots)
            d += n;
        return d / cPUSnapshots.length;
    }

    public static double standardDeviation(double[] cPUSnapshots) {
        double avg = average(cPUSnapshots);
        double d = 0;
        for (double n : cPUSnapshots)
            d += Math.pow(n - avg, 2);
        return Math.pow(d / cPUSnapshots.length, .5);
    }

}