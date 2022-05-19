package edu.benchmarkandroid.service;

import android.content.ContextWrapper;
import edu.benchmarkandroid.utils.Logger;

class BenchmarkProgressUpdater extends ProgressUpdater {

    BenchmarkProgressUpdater(ContextWrapper contextWrapper, String updateAction, String endAction, String variant, Logger logger) {
        super(contextWrapper, updateAction, endAction, variant, logger);
    }

    @Override
    String specificUpdateMessage(String msg) {
        return ("Run stage: \n" + msg);
    }
}
