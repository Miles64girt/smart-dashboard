package ru.home.dashboard;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Чтение событий на сегодня через официальный Android API CalendarContract.
 * Авторизация Google выполняется на уровне ОС (аккаунт добавлен в настройках Android),
 * приложение не запрашивает и не хранит пароль Google и не хранит OAuth-токены.
 */
public final class CalendarRepository {

    public static final int MAX_EVENTS = 12;

    public static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<EventItem> loadToday(Context ctx) {
        Calendar cal = Calendar.getInstance(TimeUtil.ZONE);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long dayStart = cal.getTimeInMillis();
        return loadRange(ctx, dayStart, dayStart + 24L * 60L * 60L * 1000L);
    }

    /** События в произвольном диапазоне (начала/конца суток в часовом поясе панели). */
    public static List<EventItem> loadRange(Context ctx, long dayStart, long dayEnd) {
        List<EventItem> out = new ArrayList<EventItem>();
        if (!hasPermission(ctx)) return out;

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dayStart);
        ContentUris.appendId(builder, dayEnd);
        Uri uri = builder.build();

        String[] projection = new String[] {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION
        };

        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    uri, projection, null, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (c == null) return out;
            while (c.moveToNext() && out.size() < MAX_EVENTS) {
                String title = c.getString(0);
                if (title == null) title = "";
                title = title.trim();
                if (title.length() == 0) continue;
                String desc = c.getString(1);
                long begin = c.getLong(2);
                long end = c.getLong(3);
                boolean allDay = c.getInt(4) == 1;
                if (desc == null) desc = "";
                desc = desc.replace('\n', ' ').replace('\r', ' ').trim();
                out.add(new EventItem(title, desc, begin, end, allDay));
            }
        } catch (Exception ignored) {
            // нет аккаунта / синхронизация недоступна — правая часть просто останется пустой
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}
