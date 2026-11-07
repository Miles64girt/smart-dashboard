# Architecture

Smart Dashboard is intentionally small: one `Activity`, one custom drawing `View`, a handful of plain Java classes, no third-party libraries and no Gradle. This page explains how the pieces fit together, where data comes from and where it goes.

> ⚠️ **Tested on one tablet only.** The architecture is portable in principle, but the app was verified only on a Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. Test on your own device; use it at your own risk.

## Contents

- [Big picture](#big-picture)
- [Components](#components)
- [Startup flow](#startup-flow)
- [Data flow: weather](#data-flow-weather)
- [Data flow: calendar](#data-flow-calendar)
- [Daily schedule](#daily-schedule)
- [Cache and offline mode](#cache-and-offline-mode)
- [Evening packing list](#evening-packing-list)
- [Screen, brightness and orientation](#screen-brightness-and-orientation)
- [Permissions and privacy](#permissions-and-privacy)
- [Why no libraries and no Gradle](#why-no-libraries-and-no-gradle)
- [Android 7 specifics](#android-7-specifics)

## Big picture

```
MainActivity (single activity, no fragments)
 ├─ DashboardView              all dashboard drawing on a Canvas (left/right halves, buttons)
 ├─ PackingOverlayView         evening checklist over a blurred snapshot
 │   └─ PackingListModel       items, checkmarks, custom items, optional notebook
 ├─ WeatherRepository ──────►  wttr.in (HTTPS, JSON)  ──►  WeatherData
 │   └─ Prefs                   weather cache in SharedPreferences
 ├─ ClothingAdvisor            rule-based "what to wear" from the weather
 ├─ CalendarRepository ─────►  CalendarContract.Instances  ──►  EventItem list
 ├─ Scheduler / ScheduleReceiver   08:00 / 20:00 / 21:00 exact alarms
 ├─ BootReceiver               autostart + alarm restoration after reboot
 ├─ OrientationWatcher         accelerometer: normal vs upside-down portrait
 ├─ PowerAccessibilityService  shows the system power dialog for the ⏻ button
 ├─ BlurUtil                   RenderScript blur for the overlay background
 ├─ TimeUtil                   the one place that knows about time zones
 └─ DeviceConfig               compile-time device adaptation constants
```

All the UI is drawn in code. There is no XML layout, no image asset for the dashboard, no animation framework: cards, icons, the blur snapshot and the fireworks are `Canvas` primitives.

## Components

| Class | Responsibility |
|---|---|
| `MainActivity` | The only activity. Sets up the full-screen window, keeps the screen on, owns the render tick, fetches weather/calendar, handles the day/night mode, hosts the packing overlay, requests the calendar permission, receives boot/test intents and re-registers alarms |
| `DashboardView` | Draws the whole dashboard: the two 50/50 halves, the divider, the weather icon, the five "what to wear" rows, the event cards and the corner buttons. Handles the layout scale and the 180° flip |
| `PackingOverlayView` | The evening window: centered card over the blurred background, checklist rows, checkboxes, the "+ add item" row, tap and long-press handling, the success animation and the auto-close timer |
| `PackingListModel` | Builds the list for a given day: default items, custom items from preferences, the optional notebook item from tomorrow's calendar, and per-day checked/done/shown state |
| `PackingItem` | A single checklist row: id, label, whether it is custom, whether it is checked |
| `WeatherRepository` | Performs the HTTPS request to wttr.in (`?format=j1`) — either without coordinates (auto-location by IP) or with the configured coordinates — and parses the JSON into `WeatherData`; with manual coordinates only, falls back to Open-Meteo |
| `WeatherData` | The weather model: temperature, feels-like, min/max, wind, humidity, precipitation probability, WMO code, sunrise/sunset, fetch time; converts to/from the storage JSON |
| `ClothingAdvisor` | Pure function from weather to five clothing recommendations (clothes, outerwear, umbrella, shoes, accessories) |
| `CalendarRepository` | Reads today's events (00:00–24:00 in the configured time zone) from the system calendar provider via `CalendarContract.Instances`; read-only, limited to 12 events |
| `EventItem` | A calendar event: title, description, begin/end timestamps, all-day flag |
| `Scheduler` | Registers the three daily `AlarmManager` alarms (`RTC_WAKEUP`, exact when the OS allows it) |
| `ScheduleReceiver` | Handles a fired alarm: wakes the screen in the morning, switches to night mode in the evening, opens the packing list at 20:00, then re-registers tomorrow's alarms |
| `BootReceiver` | On boot (and after a package update) restores the alarms and starts the dashboard |
| `OrientationWatcher` | Reads the accelerometer to detect whether the tablet is mounted upside down (with a low-pass filter, threshold and 500 ms hold to ignore shaking) |
| `PowerAccessibilityService` | A minimal accessibility service used for one thing: `GLOBAL_ACTION_POWER_DIALOG` when the user taps **⏻** |
| `BlurUtil` | Renders a downscaled (1/4) and blurred copy of the dashboard snapshot for the overlay background |
| `Prefs` | All local storage: weather cache, location, night flag, orientation flag, custom packing items, per-day checklist state |
| `TimeUtil` | The single source of the app's time zone |
| `DeviceConfig` | Compile-time configuration for the target device: time zone, city/coordinates and auto-location, schedule hours, brightness, sound, packing defaults, notebook option, design size |

## Startup flow

1. Android starts `MainActivity` from the launcher, from `BootReceiver` or from `ScheduleReceiver`.
2. The activity switches to immersive full screen, adds the keep-screen-on flag, creates `DashboardView` and an invisible `PackingOverlayView` in a `FrameLayout`.
3. `Prefs` is opened; the last known orientation is applied immediately, then `OrientationWatcher` starts correcting it.
4. `Scheduler.scheduleAll()` registers the three daily alarms.
5. The cached weather JSON is read from `Prefs` and drawn immediately (so the screen is never empty while waiting for the network).
6. If the calendar permission is missing, the system permission dialog is requested.
7. A `CONNECTIVITY_ACTION` receiver is registered, then the first weather and calendar refreshes start on a background executor.
8. A `Handler` ticker runs every 30 seconds: it updates the clock/date, re-evaluates day/night mode, and refreshes weather/calendar if the cached copies are older than their maximum age.

`ScheduleReceiver` and `BootReceiver` may also pass extras to the activity: the boot reason (wake the screen) or a forced day/night mode.

## Data flow: weather

```
MainActivity.refreshWeather()
   → WeatherRepository.fetch()
       a) AUTO_LOCATION = true  (default):
          GET https://wttr.in/?format=j1           (wttr.in derives the city from the public IP)
       b) AUTO_LOCATION = false:
          GET https://wttr.in/{lat},{lon}?format=j1
          if that fails: GET the Open-Meteo forecast for the same coordinates (fallback)
   → JSON parsing (wttr.in condition codes are mapped to WMO codes)
   → WeatherData (also carries the city name returned by wttr.in)
   → Prefs.saveWeather(json, fetchedAt)                (cache survives reboots)
   → DashboardView.setWeather(data, timestamp)
```

- Weather is considered fresh for **30 minutes**; after that the ticker refreshes it.
- Failed requests are retried with exponential backoff: **1 → 2 → 4 → 8 → 16 → 30 minutes** (capped at 30).
- When the network comes back, the receiver resets the backoff and refreshes immediately.
- The weather icon and the "what to wear" block are derived from the same `WeatherData`, so they cannot disagree.
- The request carries no account, no API key and no app-specific identifier. The standard HTTP `User-Agent` is set explicitly and contains the app version, the Android version and the device model — ordinary HTTP metadata, useful for the weather service and for debugging.

## Data flow: calendar

```
MainActivity.refreshCalendar()
   → CalendarRepository.loadToday()
       CalendarContract.Instances over [today 00:00, tomorrow 00:00) in the configured time zone
       → up to 12 events, sorted by start time
   → List<EventItem>
   → DashboardView.setEvents(list, loaded)
```

- The read is **read-only** and uses the official `CalendarContract` content provider, so any calendar account Android has synced (Google, Exchange, local) can be the source.
- The app never stores event data on disk as its own cache; it reads the provider again every 5 minutes and on resume/network changes. Events stay inside the system calendar app.
- If the permission is missing, the right half simply stays empty.
- If there are no events today, the right half is drawn empty — that is the intended design, not an error state.
- Event cards are laid out by available height: the card height and font size shrink as the number of events grows, so a busy day still fits.

## Daily schedule

| Time (in the configured time zone) | Alarm mode | What happens |
|---|---|---|
| 08:00 | `day` | Acquire a wake lock with `ACQUIRE_CAUSES_WAKEUP`, bring the activity up, switch to day mode and keep the screen on |
| 20:00 | `packing` | Open the evening packing list (skipped on Saturday by default, skipped if the list was already shown or completed today) |
| 21:00 | `night` | Switch to night mode: drop the keep-screen-on flag, dim the window; the screen turns off after the system timeout |

- Alarms are registered with `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`; if the firmware rejects exact alarms, the code falls back to a non-exact `set(...)`.
- Every alarm re-registers all three alarms for the next day, and `BootReceiver` restores them after a reboot — so a missed or delayed alarm does not break the schedule permanently.
- `ScheduleReceiver` also handles the Saturday skip: on Saturday the 20:00 packing alarm is ignored and the alarm is re-armed for the next day.

## Cache and offline mode

Everything cached lives in a single `SharedPreferences` file (`Prefs`):

| Key | Content |
|---|---|
| `weather_json`, `weather_time` | Last successful weather data and its timestamp |
| `calendar_time` | When the calendar was last read |
| `lat`, `lon`, `city` | Last known coordinates (used when auto-location is off) and the city label returned by wttr.in |
| `upside_down` | Last known orientation |
| `night` | Last known day/night state |
| `packing_custom` | User's custom packing items (newline-separated) |
| `packing_checked_<date>`, `packing_done_<date>`, `packing_shown_<date>` | Per-day checklist state, so a new day starts clean |

- If a weather request fails and a cached copy exists, the panel shows the cached data plus the amber footer `Нет сети · данные ЧЧ:ММ` ("No network · data as of HH:MM").
- If there is no cache at all, the panel shows a neutral "no data yet" state instead of fake values.
- The cache and settings survive a reboot and an app **update**; they are deleted on uninstall.

## Evening packing list

- The overlay takes a snapshot of the dashboard, blurs it with `RenderScript` (`ScriptIntrinsicBlur`, 1/4 scale, radius 18) and draws a centered card on top.
- Items come from `PackingListModel`:
  - **default items** — empty in v1.3 (`DeviceConfig.DEFAULT_PACKING_ITEMS`);
  - **custom items** — added with `+  Добавить свой пункт`, stored in preferences, deleted by long-press;
  - **optional notebook item** — `DeviceConfig.NOTEBOOK_FROM_CALENDAR`, off by default; subject names are extracted from tomorrow's calendar events with rooms, lesson types and brackets stripped.
- A tap toggles an item: a green checkbox with a tick, the label struck through and dimmed. State is saved per calendar date, so the next evening starts unchecked.
- When every item is checked, the success animation starts (green check mark + particles), then the overlay closes itself after about 5.6 seconds.
- "Already shown" and "already done" flags prevent the scheduled window from opening twice on the same day.

## Screen, brightness and orientation

- **Day:** `FLAG_KEEP_SCREEN_ON` plus window brightness from `DeviceConfig.DAY_BRIGHTNESS`.
- **Night:** the keep-screen-on flag is removed, window brightness drops to `DeviceConfig.NIGHT_BRIGHTNESS`, a dark overlay is drawn with the hint `касание — включить экран`; after the system timeout the screen turns off.
- **Touch at night:** the activity is woken for 5 minutes (`MANUAL_DAY_OVERRIDE`), then night mode returns.
- **Orientation:** the manifest locks the activity to portrait. `OrientationWatcher` reads the accelerometer and, when the tablet is physically upside down, `DashboardView` rotates the whole `Canvas` by 180°. The UI is never re-laid out for landscape.
- No ambient-light sensor is used; the app controls the window brightness itself (the reference tablet has no light sensor).

## Permissions and privacy

| Permission | Why it is needed |
|---|---|
| `INTERNET` | Download the weather |
| `ACCESS_NETWORK_STATE` | Detect whether the network is available and refresh when it returns |
| `READ_CALENDAR` | Read today's events from the system calendar (read-only) |
| `WAKE_LOCK` | Wake the screen on the morning alarm and at boot |
| `RECEIVE_BOOT_COMPLETED` | Start automatically after a reboot |
| `MODIFY_AUDIO_SETTINGS` | Mute the device on start when `DeviceConfig.MUTE_SOUND` is enabled |

Privacy principles:

- **No Google password, no OAuth tokens.** The calendar is read through the system `CalendarContract` API; the account is authorized by Android, not by the app.
- **The calendar is read-only.** The app never creates, edits or deletes events.
- **No root, no bootloader unlock, no system modification, no GMS dependency.**
- **Weather requests send no account data and no API key.** In auto-location mode the app sends no coordinates at all — wttr.in infers the city from the public IP address. In manual mode the request URL contains only the configured coordinates. The HTTP `User-Agent` includes the app version, the Android version and the device model.
- **The accessibility service is used only to show the system power dialog.** It does not read window content (`canRetrieveWindowContent="false"`), does not receive or act on accessibility events, and does not click anything.
- **No analytics, no tracking, no ads.** No data is sent anywhere except the weather services.
- Uninstalling the app removes all its local data.

## Why no libraries and no Gradle

| Alternative | Why it is not used |
|---|---|
| AndroidX / AppCompat / RecyclerView | Megabytes of dependencies and startup work for a single screen |
| Jetpack Compose | A heavy runtime that needs more GPU and memory than an old tablet has |
| OkHttp / Gson / Retrofit | Android already ships `HttpURLConnection` and `org.json`; a weather request and a small parse do not need a library |
| Google Play Services / auth libraries | The app does not need GMS at all, which also makes it work on devices with an outdated GMS and without Google Play |
| Gradle / Android Studio | Not needed for a project this small. A direct `aapt2 → javac → d8 → zipalign → apksigner` build is faster, reproducible and downloads about 320 MB of tools instead of a full IDE and dependency graph |
| SVG/PNG assets | The weather icon, buttons and success animation are drawn with `Canvas` primitives; there is nothing to scale or decode |

The design goal is a panel that starts instantly, uses about **12 MB PSS**, stays near **0% CPU when idle**, and fits in about **60 KB of APK**. Everything above is a direct consequence of that goal.

## Build without Gradle

`build/build.ps1` performs the same steps Gradle would, explicitly:

1. `aapt2 compile` — compile resources.
2. `aapt2 link` — link resources plus `AndroidManifest.xml` against `android.jar` (API 24).
3. `javac` — compile the Java sources.
4. `d8 --min-api 24` — convert classes to DEX (the app is pure Java, so no desugaring surprises).
5. Package `classes.dex` into the APK.
6. `zipalign` — align the archive.
7. `apksigner sign` + `apksigner verify` — sign and check the signature.

Because `aapt2` does not tolerate non-ASCII paths, the build copies the project into an ASCII staging directory (`%TEMP%\smart-dashboard-build` by default, overridable with `-StageRoot`) and runs every tool there. The signing key is generated locally on the first build (`build/keystore.jks` + `build/keystore.properties`), never committed, and must be kept for future updates. Full details: [BUILD.md](BUILD.md).

## Android 7 specifics

- **ISRG (Let's Encrypt) root certificates are missing in Android 7.0** — they arrived in 7.1.1. Both weather endpoints use Let's Encrypt certificates, so the app bundles the official **ISRG Root X1/X2** as extra trust anchors, scoped by `network_security_config.xml` **only** to `wttr.in` and `open-meteo.com`. System trust is not weakened and cleartext HTTP is not allowed.
- **System time zone may be wrong or locked.** On the reference tablet the system zone stayed at GMT and could not be changed over ADB without root. That is why all time handling goes through `TimeUtil` and `DeviceConfig.TIME_ZONE`. If your device has a correct system zone, leave `TIME_ZONE` empty.
- **No boot-by-alarm.** There is no RTC alarm boot on the reference tablet, so the app cannot wake a fully powered-off device; it can only wake a sleeping one.
- **Exact alarms and Doze.** Android 7 allows exact alarms, but OEM battery managers (especially EMUI) may still delay them. Excluding the app from battery optimization and allowing autostart is required for reliable 08:00/21:00 behaviour.
- **Runtime permissions.** `READ_CALENDAR` is requested at runtime (API 23+ behavior); the app is not installed with it granted.
- **targetSdk 24.** The app deliberately targets the same API level it was tested on, avoiding behavior changes from newer target SDKs (restricted broadcasts, background execution limits, notification permission, etc.).
- **No native libraries.** The APK contains no `.so` files, so it works on 32-bit and 64-bit devices without ABI-specific builds.

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
