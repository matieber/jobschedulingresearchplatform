package edu.benchmarkandroid.service;


public class ThresholdNotificator {

    private static ThresholdNotificator INSTANCE;
    private double currentLevel;

    private ThresholdNotificator() {
    }

    public synchronized static ThresholdNotificator getInstance() {
        if (INSTANCE == null) INSTANCE = new ThresholdNotificator();
        return INSTANCE;
    }

    public synchronized void updateThresholdLevel(double level) {
        this.currentLevel = level;
    }

    public double getCurrentLevel() {
        return currentLevel;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }
}
