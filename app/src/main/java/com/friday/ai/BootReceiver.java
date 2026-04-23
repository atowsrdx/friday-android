package com.friday.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = ctx.getSharedPreferences("friday_prefs", Context.MODE_PRIVATE);
            if (prefs.getBoolean("setup_done", false)) {
                Intent service = new Intent(ctx, FridayService.class);
                ctx.startForegroundService(service);
            }
        }
    }
}
