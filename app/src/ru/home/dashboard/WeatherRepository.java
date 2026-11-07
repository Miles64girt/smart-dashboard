package ru.home.dashboard;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;

/**
 * Загрузка погоды.
 *
 * Основной источник — wttr.in (бесплатно, без API-ключа, отдаёт текущую погоду,
 * прогноз на сегодня, восход/закат и город одним JSON).
 *
 * Если {@link DeviceConfig#AUTO_LOCATION} = true, город определяется по IP
 * (запрос без координат). Если false — используются координаты из DeviceConfig.
 *
 * Open-Meteo оставлен резервным источником для случая, когда заданы координаты
 * и wttr.in не ответил. На некоторых сетях Open-Meteo недоступен (TCP-таймаут).
 */
public final class WeatherRepository {

    private WeatherRepository() {
    }

    private static String wttrUrl(Prefs prefs) {
        if (DeviceConfig.AUTO_LOCATION) {
            return "https://wttr.in/?format=j1";
        }
        return "https://wttr.in/" + String.format(Locale.US, "%.4f", prefs.lat()) + ","
                + String.format(Locale.US, "%.4f", prefs.lon()) + "?format=j1";
    }

    private static String openMeteoUrl(Prefs prefs) throws Exception {
        return "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + String.format(Locale.US, "%.4f", prefs.lat())
                + "&longitude=" + String.format(Locale.US, "%.4f", prefs.lon())
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
                + "&timezone=" + URLEncoder.encode(TimeUtil.ZONE_ID, "UTF-8")
                + "&forecast_days=1";
    }

    public static WeatherData fetch(Prefs prefs) throws Exception {
        Exception last = null;

        try {
            String body = httpGet(wttrUrl(prefs));
            return WeatherData.parseWttr(new org.json.JSONObject(body));
        } catch (Exception e) {
            android.util.Log.w("SmartDashboard", "wttr.in failed: " + e, e);
            last = e;
        }

        // Резерв: только при явно заданных координатах. При автоопределении города
        // координат нет, и Open-Meteo показал бы погоду другого места.
        if (!DeviceConfig.AUTO_LOCATION) {
            try {
                String body = httpGet(openMeteoUrl(prefs));
                return WeatherData.parse(new org.json.JSONObject(body));
            } catch (Exception e) {
                android.util.Log.w("SmartDashboard", "open-meteo failed: " + e, e);
                last = e;
            }
        }

        throw last != null ? last : new IOException("no weather source");
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", buildUserAgent());
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            in = conn.getInputStream();
            String body = readAll(in);
            if (body == null || body.length() == 0) throw new IOException("empty response");
            if (body.trim().charAt(0) != '{') throw new IOException("not json");
            return body;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private static String buildUserAgent() {
        String model = "unknown";
        try {
            model = String.valueOf(android.os.Build.MODEL);
        } catch (Throwable ignored) {
        }
        return "SmartDashboard/1.3 (Android " + android.os.Build.VERSION.RELEASE
                + "; " + model + "; tablet dashboard)";
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }
}
