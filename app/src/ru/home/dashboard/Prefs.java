package ru.home.dashboard;

import android.content.Context;
import android.content.SharedPreferences;

/** Локальные настройки и кэш последних данных (переживают перезагрузку). */
public final class Prefs {

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("dashboard", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------- город

    public double lat() {
        try {
            return Double.parseDouble(sp.getString("lat", String.valueOf(DeviceConfig.LATITUDE)));
        } catch (Exception e) {
            return DeviceConfig.LATITUDE;
        }
    }

    public double lon() {
        try {
            return Double.parseDouble(sp.getString("lon", String.valueOf(DeviceConfig.LONGITUDE)));
        } catch (Exception e) {
            return DeviceConfig.LONGITUDE;
        }
    }

    /** Название города для подписи на панели. */
    public String city() {
        String stored = sp.getString("city", null);
        if (stored != null && stored.trim().length() > 0) return stored.trim();
        return DeviceConfig.AUTO_LOCATION ? "" : DeviceConfig.CITY;
    }

    /** Сохранить определённый/заданный город. */
    public void setCity(String city) {
        if (city == null) return;
        city = city.trim();
        if (city.length() > 0) sp.edit().putString("city", city).apply();
    }

    public void setLocation(double lat, double lon, String city) {
        sp.edit().putString("lat", String.valueOf(lat))
                 .putString("lon", String.valueOf(lon))
                 .putString("city", city == null ? "" : city).apply();
    }

    // ------------------------------------------------------------ погода

    public String weatherJson() {
        return sp.getString("weather_json", null);
    }

    public long weatherTime() {
        return sp.getLong("weather_time", 0L);
    }

    public void saveWeather(String json, long timeMs) {
        sp.edit().putString("weather_json", json).putLong("weather_time", timeMs).apply();
    }

    // --------------------------------------------------------- календарь

    public long calendarTime() {
        return sp.getLong("calendar_time", 0L);
    }

    public void saveCalendarTime(long timeMs) {
        sp.edit().putLong("calendar_time", timeMs).apply();
    }

    // -------------------------------------------------------------- режим

    /** Ночной режим (экран приглушён по расписанию). */
    public boolean isNight() {
        return sp.getBoolean("night", false);
    }

    public void setNight(boolean night) {
        sp.edit().putBoolean("night", night).apply();
    }

    /** Последнее известное положение планшета (true = перевёрнут). */
    public boolean isUpsideDown() {
        return sp.getBoolean("upside_down", false);
    }

    public void setUpsideDown(boolean value) {
        sp.edit().putBoolean("upside_down", value).apply();
    }

    // ------------------------------------------------ вечерний список вещей

    public java.util.LinkedHashSet<String> customPackingItems() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
        String raw = sp.getString("packing_custom", "");
        if (raw != null && raw.length() > 0) {
            for (String s : raw.split("\n")) {
                if (s != null && s.trim().length() > 0) set.add(s.trim());
            }
        }
        return set;
    }

    public void setCustomPackingItems(java.util.Collection<String> items) {
        StringBuilder sb = new StringBuilder();
        if (items != null) {
            for (String s : items) {
                if (s != null && s.trim().length() > 0) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(s.trim());
                }
            }
        }
        sp.edit().putString("packing_custom", sb.toString()).apply();
    }

    public java.util.LinkedHashSet<String> packingChecked(String dateKey) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<String>();
        String raw = sp.getString("packing_checked_" + dateKey, "");
        if (raw != null && raw.length() > 0) {
            for (String s : raw.split("\n")) {
                if (s != null && s.trim().length() > 0) set.add(s.trim());
            }
        }
        return set;
    }

    public void setPackingChecked(String dateKey, java.util.Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids != null) {
            for (String s : ids) {
                if (s != null && s.length() > 0) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(s);
                }
            }
        }
        sp.edit().putString("packing_checked_" + dateKey, sb.toString()).apply();
    }

    public boolean isPackingDone(String dateKey) {
        return sp.getBoolean("packing_done_" + dateKey, false);
    }

    public void setPackingDone(String dateKey, boolean value) {
        sp.edit().putBoolean("packing_done_" + dateKey, value).apply();
    }

    public boolean isPackingShown(String dateKey) {
        return sp.getBoolean("packing_shown_" + dateKey, false);
    }

    public void setPackingShown(String dateKey, boolean value) {
        sp.edit().putBoolean("packing_shown_" + dateKey, value).apply();
    }
}
