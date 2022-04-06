package edu.benchmarkandroid.Benchmark;

import android.util.Log;
import edu.benchmarkandroid.service.BatteryNotificator;

public class BatteryStopCondition implements StopCondition {
    String TAG = "BatteryStopCondition";
    private double targetBatteryLevel;
    private BatteryNotificator batteryNotificator;
    private Comp comparator;


    public BatteryStopCondition(double targetBatteryLevel, BatteryNotificator batteryNotificator) {
        double currLevel = batteryNotificator.getCurrentLevel();
        boolean charging = batteryNotificator.isDevicePlugged();
        Log.d(TAG, "endBattLevel:" + targetBatteryLevel + " CurrentLevel:"+ currLevel +
                " Charging:"+charging);
        this.targetBatteryLevel = targetBatteryLevel;
        this.batteryNotificator = batteryNotificator;

        if ((currLevel >= this.targetBatteryLevel && !charging)
                                    || (currLevel < this.targetBatteryLevel && !charging)) {
            Log.d(TAG, "target < level  comparator created");
            this.comparator = new Comp() {
                @Override
                public boolean compare(double target, double level) {
                    return target < level;
                }
            };
        }else {
            Log.d(TAG, "target > level  comparator created");
            this.comparator = new Comp() {
                @Override
                public boolean compare(double target, double level) {
                    return target > level;
                }
            };
        }
    }

    @Override
    public boolean canContinue() {
        return comparator.compare(this.targetBatteryLevel, batteryNotificator.getCurrentLevel());
    }

    private interface Comp {
        boolean compare(double end, double level);
    }

}
