package edu.benchmarkandroid.service;


import android.util.Log;

public class BatteryNotificator {
    private static BatteryNotificator instance;
    private double currentLevel;
    //previousLevel is just for make an internal check against spurious battery level updates
    private double previousLevel = 0.0;
    private double lastReportedLevel = -1;
    private boolean plugged = false;
    private static final String TAG = "BatteryNotificator";

    private BatteryNotificator() {
    }

    public synchronized static BatteryNotificator getInstance() {
        if (instance == null) instance = new BatteryNotificator();
        return instance;
    }

    public synchronized void updateBatteryLevel(double level) {
        //check spurious updates that android framework sometimes issues with the
        //value of 1, i.e, 100% battery level. Besides, consider that battery level can eventually
        //jump two battery levels (that's why 0.02 in the comparison)
        double magChange = Math.abs(level - previousLevel);
        Log.d(TAG, "Should update battery level? : previousLevel=" + previousLevel + "; newLevel="+ level + "; currentLevel=" + this.currentLevel + "abs(newLevel - previousLevel)=" + magChange);
        if ( this.previousLevel == 0.0 ||
                ( this.previousLevel != level && magChange <= 0.025 ) ) {
            this.previousLevel = this.previousLevel == 0.0 ? level : this.currentLevel;
            this.currentLevel = level;
        }
    }

    public synchronized void updateLastReportedLevel(double level) {
        this.lastReportedLevel = level;
    }

    public synchronized void updateDevicePlug(boolean plugged) {
        this.plugged = plugged;
    }

    public synchronized double getCurrentLevel() {
        return this.currentLevel;
    }
    public synchronized double getPreviousLevel() {
        return this.previousLevel;
    }
    public synchronized double getLastReportedLevel() {
        return this.lastReportedLevel;
    }
    public synchronized boolean isDevicePlugged() { return this.plugged; }

    public synchronized boolean hasCurrentLevelBeenReported() {
        return getCurrentLevel() == getLastReportedLevel();
    }
}
