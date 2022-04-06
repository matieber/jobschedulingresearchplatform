package edu.benchmarkandroid.utils;

import android.os.Build;
import android.util.Log;
import edu.benchmarkandroid.MainActivity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class CPUUtils {
    private static final String TAG = "CPUUtils";


    public static double readUsage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            return readUsageFromCpuUsageSample();
        else
            return readUsageFromProcStat();
    }

    private static double readUsageFromCpuUsageSample() {

        String dir = MainActivity.PATH + "cpu-usage-sample-1.txt";
        String dir2 = MainActivity.PATH + "cpu-usage-sample-2.txt";


        File f1 = new File(dir);

        File f2 = new File(dir2);

        String[] toks = readFile(f1);

        long idle1 = Long.parseLong(toks[5]);
        long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);


        toks = readFile(f2);

        long idle2 = Long.parseLong(toks[5]);
        long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);


        return (double) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

    }

    private static String[] readFile(File f) {
        RandomAccessFile reader;
        String[] toks = {" "};
        while (toks.length < 12)  // necesito 12 elementos en el arreglo
            if (f.exists()) {
                try {
                    reader = new RandomAccessFile(f, "r");
                    if (f.length() != 0) { //reviso que el tamaño del archivo no sea 0b
                        String load = reader.readLine();
                        if (load != null) {
                            toks = load.split(" ");
                        } else {
                            reader.seek(0);
                        }
                    }
                    reader.close();
                } catch (IOException e) {
                    Log.d(TAG, "readFile: error al leer el archivo");
                }

            }
        return toks;
    }

    private static float readUsageFromProcStat() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" ");

            long idle1 = Long.parseLong(toks[5]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (InterruptedException e) {
            }

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" ");

            long idle2 = Long.parseLong(toks[5]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float) (cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }


    public static int getMaxCPUFreqMHz() {

        int maxFreq = -1;
        try {

            RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state", "r");

            boolean done = false;
            while (!done) {
                String line = reader.readLine();
                if (null == line) {
                    done = true;
                    break;
                }

                String[] splits = line.split("\\s+");
                assert (splits.length == 2);
                int timeInState = Integer.parseInt(splits[1]);
                if (timeInState > 0) {
                    int freq = Integer.parseInt(splits[0]) / 1000;
                    if (freq > maxFreq) {
                        maxFreq = freq;
                    }
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return maxFreq;
    }

}
