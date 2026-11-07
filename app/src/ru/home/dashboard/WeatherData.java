package ru.home.dashboard;

import org.json.JSONArray;
import org.json.JSONObject;

/** Погода на сегодня (Open-Meteo, WMO weather code). */
public final class WeatherData {

    public long fetchedAt;
    public double temp;
    public double feels;
    public double tmin;
    public double tmax;
    public double windMs;
    public double humidity;
    public double precipProb;
    public int code;
    public boolean isDay;
    public String sunrise = "";
    public String sunset = "";
    /** Название города из ответа wttr.in (nearest_area); пусто, если неизвестно. */
    public String city = "";

    /** Разбор ответа /v1/forecast (current + daily, timezone из TimeUtil). */
    public static WeatherData parse(JSONObject root) throws Exception {
        WeatherData d = new WeatherData();
        JSONObject cur = root.getJSONObject("current");
        d.temp = cur.getDouble("temperature_2m");
        d.feels = cur.getDouble("apparent_temperature");
        d.humidity = cur.getDouble("relative_humidity_2m");
        d.code = cur.getInt("weather_code");
        d.windMs = cur.getDouble("wind_speed_10m") / 3.6; // км/ч -> м/с
        d.isDay = cur.optInt("is_day", 1) == 1;

        JSONObject daily = root.getJSONObject("daily");
        JSONArray tmax = daily.getJSONArray("temperature_2m_max");
        JSONArray tmin = daily.getJSONArray("temperature_2m_min");
        JSONArray sr = daily.getJSONArray("sunrise");
        JSONArray ss = daily.getJSONArray("sunset");
        JSONArray pp = daily.optJSONArray("precipitation_probability_max");
        if (tmax.length() > 0) d.tmax = tmax.getDouble(0);
        if (tmin.length() > 0) d.tmin = tmin.getDouble(0);
        if (sr.length() > 0) d.sunrise = hhmm(sr.getString(0));
        if (ss.length() > 0) d.sunset = hhmm(ss.getString(0));
        if (pp != null && pp.length() > 0) d.precipProb = pp.getDouble(0);

        d.fetchedAt = System.currentTimeMillis();
        return d;
    }

    private static String hhmm(String iso) {
        // "2026-11-06T07:07" -> "07:07"
        int t = iso.indexOf('T');
        if (t >= 0 && iso.length() >= t + 6) return iso.substring(t + 1, t + 6);
        return iso;
    }

    /** Разбор ответа wttr.in (?format=j1): current_condition + weather[0]. */
    public static WeatherData parseWttr(JSONObject root) throws Exception {
        WeatherData d = new WeatherData();

        JSONObject cur = root.getJSONArray("current_condition").getJSONObject(0);
        d.temp = num(cur.optString("temp_C", "0"));
        d.feels = num(cur.optString("FeelsLikeC", "0"));
        d.humidity = num(cur.optString("humidity", "0"));
        d.windMs = num(cur.optString("windspeedKmph", "0")) / 3.6;
        d.code = wwoToWmo((int) num(cur.optString("weatherCode", "113")));

        JSONObject today = root.getJSONArray("weather").getJSONObject(0);
        d.tmin = num(today.optString("mintempC", "0"));
        d.tmax = num(today.optString("maxtempC", "0"));

        JSONObject astro = today.getJSONArray("astronomy").getJSONObject(0);
        d.sunrise = ampm24(astro.optString("sunrise", ""));
        d.sunset = ampm24(astro.optString("sunset", ""));
        d.isDay = isDayNow(d.sunrise, d.sunset);

        // Название города — для подписи на панели (при автоопределении по IP).
        try {
            JSONArray nearest = root.optJSONArray("nearest_area");
            if (nearest != null && nearest.length() > 0) {
                JSONArray area = nearest.getJSONObject(0).optJSONArray("areaName");
                if (area != null && area.length() > 0) {
                    d.city = area.getJSONObject(0).optString("value", "").trim();
                }
            }
        } catch (Exception ignored) {
        }

        double maxPrecip = 0;
        JSONArray hourly = today.optJSONArray("hourly");
        if (hourly != null) {
            for (int i = 0; i < hourly.length(); i++) {
                JSONObject h = hourly.getJSONObject(i);
                double p = Math.max(num(h.optString("chanceofrain", "0")),
                        num(h.optString("chanceofsnow", "0")));
                double th = num(h.optString("chanceofthunder", "0"));
                if (th > p) p = th;
                if (p > maxPrecip) maxPrecip = p;
            }
        }
        d.precipProb = maxPrecip;
        d.fetchedAt = System.currentTimeMillis();
        return d;
    }

    /** День/ночь по восходу и закату в часовом поясе панели. */
    private static boolean isDayNow(String sunrise, String sunset) {
        int sr = minutes(sunrise);
        int ss = minutes(sunset);
        java.util.Calendar cal = java.util.Calendar.getInstance(TimeUtil.ZONE);
        int now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        if (sr < 0 || ss < 0 || ss == sr) return now >= 6 * 60 && now < 21 * 60;
        return now >= sr && now < ss;
    }

    private static int minutes(String hhmm) {
        if (hhmm == null) return -1;
        int idx = hhmm.indexOf(':');
        if (idx <= 0) return -1;
        try {
            int h = Integer.parseInt(hhmm.substring(0, idx));
            int m = Integer.parseInt(hhmm.substring(idx + 1, Math.min(idx + 3, hhmm.length())));
            return h * 60 + m;
        } catch (Exception e) {
            return -1;
        }
    }

    private static double num(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** "07:07 AM" -> "07:07", "04:19 PM" -> "16:19", "07:07" -> "07:07". */
    private static String ampm24(String s) {
        if (s == null) return "";
        s = s.trim();
        try {
            boolean pm = s.toUpperCase().endsWith("PM");
            boolean am = s.toUpperCase().endsWith("AM");
            String t = s.replace("AM", "").replace("PM", "").replace("am", "").replace("pm", "").trim();
            int idx = t.indexOf(':');
            if (idx <= 0) return s;
            int h = Integer.parseInt(t.substring(0, idx));
            String mm = t.substring(idx + 1).trim();
            if (mm.length() > 2) mm = mm.substring(0, 2);
            if (pm && h < 12) h += 12;
            if (am && h == 12) h = 0;
            return String.format(java.util.Locale.US, "%02d:%s", h, mm);
        } catch (Exception e) {
            return s;
        }
    }

    /** Коды WorldWeatherOnline (wttr.in) -> коды WMO (наш внутренний стандарт). */
    private static int wwoToWmo(int w) {
        switch (w) {
            case 113: return 0;
            case 116: return 2;
            case 119: return 3;
            case 122: return 3;
            case 143: return 45;
            case 248: case 260: return 45;
            case 176: case 263: case 266: case 281: case 284: return 61;
            case 293: case 296: case 299: case 302: return 63;
            case 305: case 308: return 65;
            case 311: case 314: return 66;
            case 317: case 320: return 66;
            case 179: case 182: return 71;
            case 185: case 362: case 365: return 66;
            case 227: case 230: return 75;
            case 323: case 326: case 368: return 71;
            case 329: case 332: case 371: return 73;
            case 335: case 338: return 75;
            case 350: case 374: case 377: return 77;
            case 200: case 386: case 389: case 392: case 395: return 95;
            case 353: case 356: case 359: return 80;
            default: return 3;
        }
    }

    public String condition() {
        return describe(code);
    }

    public String[] clothing() {
        return ClothingAdvisor.advise(this);
    }

    /** Описание кода WMO по-русски. */
    public static String describe(int c) {
        switch (c) {
            case 0: return "Ясно";
            case 1: return "Преимущественно ясно";
            case 2: return "Переменная облачность";
            case 3: return "Пасмурно";
            case 45: case 48: return "Туман";
            case 51: return "Слабая морось";
            case 53: return "Морось";
            case 55: return "Сильная морось";
            case 56: case 57: return "Ледяная морось";
            case 61: return "Небольшой дождь";
            case 63: return "Дождь";
            case 65: return "Сильный дождь";
            case 66: case 67: return "Ледяной дождь";
            case 71: return "Небольшой снег";
            case 73: return "Снег";
            case 75: return "Сильный снег";
            case 77: return "Снежные зёрна";
            case 80: return "Небольшой ливень";
            case 81: return "Ливень";
            case 82: return "Сильный ливень";
            case 85: return "Небольшой снегопад";
            case 86: return "Сильный снегопад";
            case 95: return "Гроза";
            case 96: case 99: return "Гроза с градом";
            default: return "Погода";
        }
    }

    public boolean hasPrecipitation() {
        return code >= 51 && code <= 99 && code != 77;
    }

    public boolean isSnow() {
        return code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86;
    }

    // ------------------------------------------------------------ кэш на диск

    public String toStorageJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("fetchedAt", fetchedAt);
            o.put("temp", temp);
            o.put("feels", feels);
            o.put("tmin", tmin);
            o.put("tmax", tmax);
            o.put("windMs", windMs);
            o.put("humidity", humidity);
            o.put("precipProb", precipProb);
            o.put("code", code);
            o.put("isDay", isDay);
            o.put("sunrise", sunrise);
            o.put("sunset", sunset);
            o.put("city", city);
            return o.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static WeatherData fromStorageJson(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            WeatherData d = new WeatherData();
            d.fetchedAt = o.optLong("fetchedAt", 0L);
            d.temp = o.optDouble("temp", 0);
            d.feels = o.optDouble("feels", 0);
            d.tmin = o.optDouble("tmin", 0);
            d.tmax = o.optDouble("tmax", 0);
            d.windMs = o.optDouble("windMs", 0);
            d.humidity = o.optDouble("humidity", 0);
            d.precipProb = o.optDouble("precipProb", 0);
            d.code = o.optInt("code", 0);
            d.isDay = o.optBoolean("isDay", true);
            d.sunrise = o.optString("sunrise", "");
            d.sunset = o.optString("sunset", "");
            d.city = o.optString("city", "");
            return d.fetchedAt > 0 ? d : null;
        } catch (Exception e) {
            return null;
        }
    }
}
