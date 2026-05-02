package com.student.foodcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        MainActivity.createNotificationChannel(context);
        MainActivity.resetIfNeededInStorage(context);
        MainActivity.sendStockWarnings(context);
        MainActivity.scheduleDailyCheck(context);
    }
}
