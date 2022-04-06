package edu.benchmarkandroid.Benchmark.benchmarks.JGrande.experiments.mobility.nonmobile.fft;

import edu.benchmarkandroid.service.ProgressUpdater;

public class DHPC_FFTBench implements Runnable {

    private ProgressUpdater progressUpdater;

    public DHPC_FFTBench(ProgressUpdater progressUpdater) {
        this.progressUpdater = progressUpdater;
    }

    public void JGFinitialise() {
        init();
    }

    public void JGFkernel() {
        doFFT();
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

    public void run() {
        JGFrun();
    }

    int n1 = 64;

    int n2 = 64;

    int n3 = 64;

    int iterations = 60;

    double data[];

    double data2[];

    public void doFFT() {
        int nn3 = n1 * n2 * n3 * 2;
        int nn[] = new int[3];

        data = new double[nn3];
        data2 = new double[nn3];

        nn[0] = n1;
        nn[1] = n2;
        nn[2] = n3;

        int i;
        for (i = 0; i < nn3; i++) {
            data[i] = next();
        }

        complex_fouriernd(data, nn);

        for (i = 1; i <= iterations; i++) {
            evolve(data, nn, i);
            inverse_complex_fouriernd(data2, nn);
            double X1 = 0.0, X2 = 0.0;
            int q, r, s, pos;
            for (int j = 0; j < 1024; j++) {
                q = j % n1;
                r = (3 * j) % n2;
                s = (5 * j) % n3;
                pos = 2 * (n1 * n2 * s + n1 * r + q);
                X1 += data[pos];
                X2 += data[pos + 1];
            }

        }

    }

    public void inter(double data[]) {
        double c1, c2;
        int pos;

        // int j=13;int k=7;
        for (int k = 0; k < n3; k++) {
            c1 = 0.0;
            c2 = 0.0;
            for (int j = 0; j < n2; j++)
                for (int i = 0; i < n1; i++) {
                    pos = 2 * (k * n1 * n2 + j * n1 + i);
                    c1 += data[pos];
                    c2 += data[pos + 1];
                    progressUpdater.updateLog(data[pos] + "," + data[pos + 1]);
                }
        }
    }

    public void evolve(double data[], int nn[], int t) {
        int pos = 0;

        double pipi = Math.PI * Math.PI;
        double a = -4.0 * t * pipi / 1000000.0;
        for (int i = 0; i < n1; i++)
            for (int j = 0; j < n2; j++)
                for (int k = 0; k < n3; k++) {
                    pos = 2 * (k * n1 * n2 + j * n1 + i);
                    data2[pos] = Math.exp(a * map(i, j, k)) * data[pos];
                    data2[pos + 1] = Math.exp(a * map(i, j, k)) * data[pos + 1];
                }
    }

    public double map(int i, int j, int k) {
        int ii, jj, kk;
        ii = i < n1 / 2 ? i : i - n1;
        jj = j < n2 / 2 ? j : j - n2;
        kk = k < n3 / 2 ? k : k - n3;
        return ((double) (ii * ii + jj * jj + kk * kk));
    }

    public void complex_fouriernd(double data[], int nn[]) {
        auxiliary_complex_fouriernd(data, nn, 1);
    }

    public void inverse_complex_fouriernd(double data[], int nn[]) {
        int i;
        double scale;
        auxiliary_complex_fouriernd(data, nn, -1);
        scale = data.length / 2;
        for (i = 0; i < data.length; i++) {
            data[i] /= scale;
        }

    }

    public void auxiliary_complex_fouriernd(double data[], int nn[],
                                            int isign) {
        int idim;
        int i1, i2, i3, i2rev, i3rev, ip1, ip2, ip3, ifp1, ifp2;
        int ibit, k1, k2, n, nprev, nrem, ntot;
        double tempi, tempr;
        double theta, wi, wpi, wpr, wr, wtemp;
        double wswap;

        int ndim = nn.length;

        // need to check all the dimensions are correct powers of two and
        // consistent

        for (ntot = 1, idim = 0; idim < ndim; idim++)
            ntot *= nn[idim];
        nprev = 1;
        for (idim = ndim; idim >= 1; idim--) {
            n = nn[idim - 1];
            nrem = ntot / (n * nprev);
            ip1 = nprev << 1;
            ip2 = ip1 * n;
            ip3 = ip2 * nrem;
            i2rev = 1;
            for (i2 = 1; i2 <= ip2; i2 += ip1) {
                if (i2 < i2rev) {
                    for (i1 = i2; i1 <= i2 + ip1 - 2; i1 += 2) {
                        for (i3 = i1; i3 <= ip3; i3 += ip2) {
                            i3rev = i2rev + i3 - i2;
                            wswap = data[i3 - 1];
                            data[i3 - 1] = data[i3rev - 1];
                            data[i3rev - 1] = wswap;
                            wswap = data[i3];
                            data[i3] = data[i3rev];
                            data[i3rev] = wswap;
                        }
                    }
                }
                ibit = ip2 >>> 1;
                while (ibit >= ip1 && i2rev > ibit) {
                    i2rev -= ibit;
                    ibit >>>= 1;
                }
                i2rev += ibit;
            }
            ifp1 = ip1;
            while (ifp1 < ip2) {
                ifp2 = ifp1 << 1;
                theta = isign * Math.PI / (ifp2 / ip1);
                wtemp = Math.sin(0.5 * theta);
                wpr = -2.0 * wtemp * wtemp;
                wpi = Math.sin(theta);
                wr = 1.0;
                wi = 0.0;
                for (i3 = 1; i3 <= ifp1; i3 += ip1) {
                    for (i1 = i3; i1 <= i3 + ip1 - 2; i1 += 2) {
                        for (i2 = i1; i2 <= ip3; i2 += ifp2) {
                            k1 = i2;
                            k2 = k1 + ifp1;
                            tempr = wr * data[k2 - 1] - wi * data[k2];
                            tempi = wr * data[k2] + wi * data[k2 - 1];
                            data[k2 - 1] = data[k1 - 1] - tempr;
                            data[k2] = data[k1] - tempi;
                            data[k1 - 1] += tempr;
                            data[k1] += tempi;
                        }
                    }
                    wtemp = wr;
                    wr = wr * wpr - wi * wpi + wr;
                    wi = wi * wpr + wtemp * wpi + wi;
                }
                ifp1 = ifp2;
            }
            nprev *= n;
        }
    }

    double X = 314159265.0; // Inital seed

    final double A = 1220703125.0; // Multiplier

    double R23, R46, T23, T46;

    void init() {
        int i;
        R23 = 1.0;
        R46 = 1.0;
        T23 = 1.0;
        T46 = 1.0;

        for (i = 1; i <= 23; i++) {
            R23 = 0.50 * R23;
            T23 = 2.0 * T23;
        }
        for (i = 1; i <= 46; i++) {
            R46 = 0.50 * R46;
            T46 = 2.0 * T46;
        }
    }

    double next() {
        X = mult(A, X);
        return (R46 * X);
    }

    double mult(double A, double X) {
        double T1, T2, T3, T4;
        double A1;
        double A2;
        double X1;
        double X2;
        double Z;
        int j;
        /* Break A into two parts such that A = 2^23 * A1 + A2 and set X = N. */

        T1 = R23 * A;
        j = (int) T1;
        A1 = j;
        A2 = A - T23 * A1;

        /*
         * Break X into two parts such that X = 2^23 * X1 + X2, compute Z = A1 *
         * X2 + A2 * X1 (mod 2^23), and then X = 2^23 * Z + A2 * X2 (mod 2^46).
         */

        T1 = R23 * X;
        j = (int) T1;
        X1 = j;
        X2 = X - T23 * X1;
        T1 = A1 * X2 + A2 * X1;

        j = (int) (R23 * T1);
        T2 = j;
        Z = T1 - T23 * T2;
        T3 = T23 * Z + A2 * X2;
        j = (int) (R46 * T3);
        T4 = j;
        X = T3 - T46 * T4;
        return (X);
    }

}
