package com.friday.ai;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationService extends NotificationListenerService {

    public static String lastNotification = "";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            android.os.Bundle extras = sbn.getNotification().extras;
            String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
            String text  = extras.getString(android.app.Notification.EXTRA_TEXT, "");
            if (!title.isEmpty() || !text.isEmpty()) {
                lastNotification = "From " + getAppName(pkg) + ": " + title + " — " + text;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String getAppName(String pkg) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }
}
