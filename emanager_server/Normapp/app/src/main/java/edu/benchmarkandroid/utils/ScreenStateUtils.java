package edu.benchmarkandroid.utils;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.view.Display;

public class ScreenStateUtils {

    public static boolean isScreenOn2(Context context) {
        DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        for (Display display : dm.getDisplays()) {
            //Log.d(TAG, "display name:" + display.getName() + " state:" + display.getState());
            if (display.getState() == Display.STATE_ON) {
                return true;
            }
        }
        return false;
    }

    public static boolean isScreenOn(Context context){
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm.isInteractive();
    }

}
