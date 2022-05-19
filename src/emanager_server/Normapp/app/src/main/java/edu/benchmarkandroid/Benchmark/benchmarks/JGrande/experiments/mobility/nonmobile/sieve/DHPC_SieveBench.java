package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.sieve;

import edu.benchmarkandroid.service.ProgressUpdater;

public class DHPC_SieveBench implements Runnable {

    private int m = 100000;
    private int n = 8192;
    private ProgressUpdater progressUpdater;


    public DHPC_SieveBench(ProgressUpdater progressUpdater) {
        this.progressUpdater = progressUpdater;
    }

    public void JGFinitialise() {
    }

    public void JGFkernel() {
        sieve(m, n);
    }

    public void JGFvalidate() {

    }

    public void run() {
        JGFrun();
    }

    public void JGFtidyup() {

    }

    public void JGFrun() {

        JGFinitialise();
        JGFkernel();
        JGFvalidate();
        JGFtidyup();
    }

    void sieve(int m, int n) {
        int i, k, ci;
        int count, size;
        int prime = 0;
        long N_Prime, L_Prime;
        boolean flags[] = new boolean[m];
        long iter, j;
        int ptr = 0;

        size = m - 1;

        N_Prime = 0L;
        L_Prime = 0L;

        j = 0;
        for (iter = 1; iter <= n; iter++) {
            count = 0;

            for (i = 0; i <= size; i++)
                flags[ptr + i] = true;

            ci = 0;
            for (i = 0; i <= size; i++) {
                if (flags[ptr + i]) {
                    count++;
                    prime = i + i + 3;
                    for (k = i + prime; k <= size; k += prime) {
                        ci++;
                        flags[ptr + k] = false;
                    }

                }
            }

            j = j + count;
        }

        N_Prime = j / n;
        L_Prime = prime;

    }

}
