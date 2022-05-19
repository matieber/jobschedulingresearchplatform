package edu.benchmarkandroid.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

public class BatteryUtils {

    public static String getBatteryStatus(Context context) {
        String status = null;
        if (isCharging(context))
            status = "Charging";
        else
            status = "Discharging";
        return status;
    }

    /**
     * public static boolean isCharging(Context context){
     * IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
     * Intent batteryStatus = context.registerReceiver(null, ifilter);
     * <p>
     * int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
     * boolean isCharging =
     * status == BatteryManager.BATTERY_STATUS_CHARGING ||
     * status == BatteryManager.BATTERY_STATUS_FULL;
     * return isCharging;
     * }
     **/

    //NOTE: this method is not used because is bm.isCharging is not reliable.
    public static boolean isCharging(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            return bm.isCharging();
        } else {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent intent = context.registerReceiver(null, filter);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                return true;
            }
        }
        return false;
    }


    public static int getBatteryCapacity(Context context) {

        Object mPowerProfile_ = null;
        double batteryCapacity = 0;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
        try {
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS).getConstructor(Context.class).newInstance(context);

        } catch (Exception e) {

            // Class not found?
            e.printStackTrace();
        }

        try {

            // Invoke PowerProfile method "getAveragePower" with param
            // "battery.capacity"
            batteryCapacity = (Double) Class.forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", java.lang.String.class)
                    .invoke(mPowerProfile_, "battery.capacity");

        } catch (Exception e) {

            // Something went wrong
            e.printStackTrace();
        }

        return (int) batteryCapacity;
    }


    public static double getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level / (double) scale;

        return batteryPct;
    }

    public static boolean isPlugged(Context context) {
        boolean isPlugged = false;
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB;
        return isPlugged;
    }

}
