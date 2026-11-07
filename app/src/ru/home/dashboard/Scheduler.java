package ru.home.dashboard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** Ежедневное расписание: утро — рабочий режим, вечер — ночной (экран гаснет). */
public final class Scheduler {

    public static final int REQ_MORNING = 1001;
    public static final int REQ_NIGHT = 1002;
    public static final int REQ_PACKING = 1004;

    private Scheduler() {
    }

    /** Тестовый будильник через N секунд (для приёмочных проверок). */
    public static void scheduleTest(Context ctx, int seconds, String mode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(ctx, ScheduleReceiver.class);
        intent.putExtra("mode", mode);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + seconds * 1000L, pi);
    }

    public static void scheduleAll(Context ctx) {
        schedule(ctx, DeviceConfig.DAY_START_HOUR, 0, "day", REQ_MORNING);
        schedule(ctx, DeviceConfig.PACKING_HOUR, 0, "packing", REQ_PACKING);
        schedule(ctx, DeviceConfig.DAY_END_HOUR, 0, "night", REQ_NIGHT);
    }

    private static void schedule(Context ctx, int hour, int minute, String mode, int requestCode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance(TimeUtil.ZONE);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(ctx, ScheduleReceiver.class);
        intent.putExtra("mode", mode);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }
}
