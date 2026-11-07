package ru.home.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** Срабатывает в 08:00 (включить экран) и в 21:00 (ночной режим, экран гаснет). */
public class ScheduleReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = intent != null ? intent.getStringExtra("mode") : null;

        if ("packing".equals(mode)) {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeUtil.ZONE);
            boolean saturday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SATURDAY;
            if (!(DeviceConfig.PACKING_SKIP_SATURDAY && saturday)) {
                try {
                    Intent i = new Intent(context, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra("scheduled_packing", true);
                    context.startActivity(i);
                } catch (Exception ignored) {
                }
            } else {
                android.util.Log.i("SmartDashboard", "packing alarm on Saturday skipped");
            }
            try { Scheduler.scheduleAll(context); } catch (Exception ignored) {}
            return;
        }

        boolean morning = "day".equals(mode);

        if (morning) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    @SuppressWarnings("deprecation")
                    PowerManager.WakeLock wl = pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "dashboard:wake");
                    wl.acquire(15000L);
                }
            } catch (Exception ignored) {
            }
        }

        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("force_mode", morning ? "day" : "night");
            context.startActivity(i);
        } catch (Exception ignored) {
        }

        try {
            Scheduler.scheduleAll(context);
        } catch (Exception ignored) {
        }
    }
}
