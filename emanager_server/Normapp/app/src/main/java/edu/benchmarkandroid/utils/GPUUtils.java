package edu.benchmarkandroid.utils;

import android.util.Log;
import edu.benchmarkandroid.MainActivity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class GPUUtils {
    private static final String TAG = "GPUUtils";

    public static double readUsage() {
        String dir = MainActivity.PATH + "gpu-usage.txt";
        File f1 = new File(dir);
        return readFile(f1);
    }

    private static double readFile(File f) {
        RandomAccessFile reader = null;
        double gpuUsage = 0;
        if (f.exists()) {
            try {
                reader = new RandomAccessFile(f, "r");
                if (f.length() != 0) { //reviso que el tamaño del archivo no sea 0b
                    String load = reader.readLine();
                    if (load != null) {
                        gpuUsage = Double.parseDouble(load.trim());
                    } else {
                        reader.seek(0);
                    }
                }
                reader.close();
            } catch (IOException e) {
                Log.d(TAG, "readFile: error al procesar el archivo");
            }
        }
        return gpuUsage;
    }

}
