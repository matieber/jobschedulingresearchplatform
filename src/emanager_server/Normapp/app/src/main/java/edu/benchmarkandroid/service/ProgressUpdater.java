package edu.benchmarkandroid.service;

import android.content.ContextWrapper;
import android.content.Intent;
import edu.benchmarkandroid.utils.Logger;

public abstract class ProgressUpdater {
    private ContextWrapper contextWrapper;
    private String updateAction;
    private String endAction;
    private String variant;
    private Logger logger;
    private String fname;

    ProgressUpdater(ContextWrapper contextWrapper, String updateAction, String endAction, String variant, Logger logger) {
        this.contextWrapper = contextWrapper;
        this.updateAction = updateAction;
        this.endAction = endAction;
        this.variant = variant;
        this.logger = logger;
        this.fname = logger.getFileName();
    }

    public void update(String msg) {
        Intent intent = new Intent();
        intent.setAction(updateAction);
        String message = specificUpdateMessage(msg);
        intent.putExtra("msg", message);
        contextWrapper.sendBroadcast(intent);
        logger.write(msg);
    }

    public void updateLog(String msg) {
        // Agregado por JGrande multihilo
        synchronized (logger) {
            logger.write(msg);
        }
    }

    public void end() {
        Intent intent = new Intent();
        intent.putExtra("variant", variant);
        intent.putExtra("file", fname);
        intent.setAction(endAction);

        try {
            logger.finish();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        contextWrapper.sendBroadcast(intent);
    }

    abstract String specificUpdateMessage(String value);
}
