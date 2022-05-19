package edu.benchmarkandroid.utils;


import android.util.Log;
import edu.benchmarkandroid.MainActivity;

import java.io.*;

public class Logger {

    private static Logger INSTANCE;


    private static final String TAG = "Logger";

    private BufferedWriter bw;

    private int counter = 0;
    public static String fname = "";

    private Logger(String fname) throws FileNotFoundException {
        this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(fname))));
    }

    public static void init(String fname) {
        Logger.fname = MainActivity.PATH + fname;
        Log.d(TAG, "init: " + fname);
    }

    public static Logger getInstance() throws FileNotFoundException {
        if (INSTANCE == null) INSTANCE = new Logger(fname);
        return INSTANCE;
    }

    public String getFileName() {
        return fname;
    }

    public void write(String s) {
        try {
            bw.write(Long.toString(System.currentTimeMillis()) + "," + s + "\n");
            counter++;
            if (counter > 10) {
                bw.flush();
                counter = 0;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error adding line", e);
        }
    }

    public void flush() {
        try {
            bw.flush();
        } catch (IOException e) {
            Log.e(TAG, "Error writting file", e);
        }
    }


    public void finish() {
        try {
            this.flush();
            bw.close();
        } catch (Throwable throwable) {
            Log.d(TAG, "no logger initiated");
        }
        fname = "";
        INSTANCE = null;
    }


}