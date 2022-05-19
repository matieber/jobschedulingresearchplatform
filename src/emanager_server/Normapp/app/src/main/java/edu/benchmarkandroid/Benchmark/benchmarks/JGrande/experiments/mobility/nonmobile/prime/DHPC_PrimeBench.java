package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.prime;

import edu.benchmarkandroid.service.ProgressUpdater;

public class DHPC_PrimeBench implements Runnable {

    private ProgressUpdater progressUpdater;

    public DHPC_PrimeBench(ProgressUpdater progressUpdater) {
        this.progressUpdater = progressUpdater;
    }

    public void JGFinitialise() {

    }

    public void JGFkernel() {
        for (int i = 2; i <= 10000000; i++) {
            isPrime(i);
        }

    }

    public void run() {
        JGFrun();
    }

    public void JGFvalidate() {
    }

    public void JGFtidyup() {
    }

    public void JGFrun() {
        JGFinitialise();
        JGFkernel();
        JGFvalidate();
        JGFtidyup();

    }

    public boolean isPrime(int num) {
        boolean prime = true;
        int limit = (int) Math.sqrt(num);

        for (int i = 2; i <= limit; i++) {
            if (num % i == 0) {
                prime = false;
                break;
            }
        }

        return prime;
    }

}