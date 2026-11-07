package ru.home.dashboard;

import java.util.TimeZone;

/**
 * Единый часовой пояс панели.
 *
 * Берётся из {@link DeviceConfig#TIME_ZONE}; если там пусто — используется
 * системный часовой пояс планшета. Явное значение нужно на устройствах,
 * где системный пояс сбит и не меняется (например, всегда GMT).
 */
public final class TimeUtil {

    /** IANA-идентификатор пояса, который использует приложение. */
    public static final String ZONE_ID;

    /** Часовой пояс панели (для календарей, форматирования и расписания). */
    public static final TimeZone ZONE;

    static {
        String configured = DeviceConfig.TIME_ZONE == null ? "" : DeviceConfig.TIME_ZONE.trim();
        if (configured.length() > 0) {
            ZONE = TimeZone.getTimeZone(configured);
            ZONE_ID = configured;
        } else {
            ZONE = TimeZone.getDefault();
            ZONE_ID = ZONE.getID();
        }
    }

    private TimeUtil() {
    }
}
