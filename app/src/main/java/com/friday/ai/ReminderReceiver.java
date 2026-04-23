package com.friday.ai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String subject = intent.getStringExtra("subject");
        if (subject == null) subject = "Reminder";

        NotificationChannel ch = new NotificationChannel("friday_reminder",
                "Friday Reminders", NotificationManager.IMPORTANCE_HIGH);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(ch);

        nm.notify((int) System.currentTimeMillis(),
                new NotificationCompat.Builder(ctx, "friday_reminder")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("⏰ Friday Reminder")
                        .setContentText(subject)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build());
    }
                  }
                  
