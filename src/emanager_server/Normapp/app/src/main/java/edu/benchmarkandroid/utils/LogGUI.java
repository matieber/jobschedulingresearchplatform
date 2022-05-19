package edu.benchmarkandroid.utils;

import android.widget.ScrollView;
import android.widget.TextView;

public class LogGUI {

    private static TextView logTextView = null;
    private static StringBuilder stringBuilder;
    private static final int MAX_STRING_BUILDER = 1_000_000;
    private static ScrollView scrollView = null;


    public static void init(TextView logTextView, ScrollView scrollView) {
        LogGUI.logTextView = logTextView;
        LogGUI.scrollView = scrollView;
        stringBuilder = new StringBuilder();
    }


    public static void clearAndLog(String line) {
        stringBuilder = new StringBuilder();
        log(line);
    }

    public static void log(String line) {
        if (logTextView != null) {

            String[] lines = line.split("\n");
            if (lines.length > 1)
                line = lines[lines.length - 1];

            stringBuilder.append(line).append("\n");
            checkStringBuilderLength();
            logTextView.setText(stringBuilder.toString());
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        }

    }


    private static void checkStringBuilderLength() {
        if (stringBuilder != null)
            if (stringBuilder.length() >= MAX_STRING_BUILDER) {
                stringBuilder = stringBuilder.reverse();
                stringBuilder.setLength(MAX_STRING_BUILDER / 2);
                stringBuilder = stringBuilder.reverse();
            }
    }
}
