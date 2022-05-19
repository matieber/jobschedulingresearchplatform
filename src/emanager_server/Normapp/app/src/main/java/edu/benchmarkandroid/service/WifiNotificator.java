package edu.benchmarkandroid.service;

public class WifiNotificator {

    private static WifiNotificator instance;

    private int signalStrength;

    public WifiNotificator() {
        signalStrength = 0;
    }

    public synchronized static WifiNotificator getInstance() {
        if (instance == null) instance = new WifiNotificator();
        return instance;
    }

    public synchronized void updateSignalStrength(int signalstrength) {
        this.signalStrength = signalstrength;
    }

    public synchronized int getCurrentSignalStrength() {
        return signalStrength;
    }
}

