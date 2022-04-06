package edu.benchmarkandroid.service;

public class ScreenStateNotificator {

    private static ScreenStateNotificator instance = null;
    private boolean isScreenOn = false;

    public synchronized static ScreenStateNotificator getInstance() {
        if (instance == null) instance = new ScreenStateNotificator();
        return instance;
    }

    public synchronized boolean isScreenOn(){
        return this.isScreenOn;
    }

    public synchronized void setIsScreenOn(boolean so){
        this.isScreenOn = so;
    }
}
