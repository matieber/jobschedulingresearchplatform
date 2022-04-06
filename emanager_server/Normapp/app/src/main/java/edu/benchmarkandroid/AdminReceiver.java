package edu.benchmarkandroid;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class AdminReceiver extends DeviceAdminReceiver {

    /**
     * This class is for allowing the application to perform
     * actions that require administrator privileges. For more information
     * about the topic read the Android documentation:
     * https://developer.android.com/guide/topics/admin/device-admin#manifest
     */
    @Override
    public void onReceive(Context context, Intent intent) {

    }
}