# Testing: what was verified and how to test your own device

Smart Dashboard was built for a specific old tablet and verified on it in person. This page documents **what exactly was tested**, and gives you a checklist for testing the app on **your** device before you trust it.

> ⚠️ **Tested on one tablet only.** All the ✅ results below are from a **Huawei MediaPad T3 7 (BG2-U01)**, Android 7.0, 600×1024. Other devices have not been tested. Test on your own tablet and use the app at your own risk.

## Test environment

| Item | Value |
|---|---|
| Device | Huawei MediaPad T3 7 (**BG2-U01**) |
| OS | Android 7.0 (API 24), EMUI 5.1 |
| CPU / RAM | 32-bit, 1 GB |
| Screen | 600×1024 px, 213 dpi, portrait |
| App version | 1.3, package `ru.home.dashboard` |
| Signature | Verified with `apksigner verify` |
| Storage | Weather cache and settings in the app's `SharedPreferences` |
| Calendar | A Google account added in the tablet's system settings; events read via `CalendarContract` |

The demo screenshots in [docs/screens/](../screens/) are anonymized: the city, calendar events and packing items were replaced with neutral demo data. They show the layout, not the tester's real life.

## Tested scenarios

| # | Scenario | What was checked | Result |
|---|---|---|---|
| 1 | **Launch** | The app opens from the launcher and the dashboard appears in well under a second; no crash | ✅ |
| 2 | **Full screen** | No status bar, no navigation bar; both halves fill the screen | ✅ |
| 3 | **50/50 split** | Left half = weather, right half = calendar, vertical divider in the middle | ✅ |
| 4 | **Weather** | Current and "feels like" temperature, daily min/max, condition with a drawn icon, rain probability, wind, humidity, sunrise, sunset, daylight length | ✅ |
| 5 | **What to wear** | All five rows (clothes, outerwear, umbrella, shoes, accessories) are filled and make sense for the day's weather | ✅ |
| 6 | **Calendar** | Today's events are read from the system calendar, sorted by start time, shown with time, title and description | ✅ |
| 7 | **Empty calendar day** | The right half stays completely empty — no placeholders or "no events" text | ✅ |
| 8 | **Many events (10)** | Cards shrink automatically; all ten events fit without overlapping | ✅ |
| 9 | **Offline** | With the network off, the last cached weather is shown with a `Нет сети · данные ЧЧ:ММ` note in the bottom-left corner | ✅ |
| 10 | **Network restored** | When the connection comes back, weather and calendar refresh immediately without a restart | ✅ |
| 11 | **Reboot + autostart** | After a reboot the panel opens by itself, and the daily alarms are re-registered | ✅ |
| 12 | **Orientation** | Normal portrait works; turning the tablet upside down flips the drawing on the accelerometer; landscape is locked off | ✅ |
| 13 | **Night mode (21:00)** | The screen dims, the night hint is shown, and the screen turns off after the system timeout | ✅ |
| 14 | **Morning wake (08:00)** | The alarm wakes the screen and shows the panel in day mode | ✅ |
| 15 | **Automatic refresh** | Weather and calendar update by timer without any user action (weather at most every 30 min, calendar every 5 min) | ✅ |
| 16 | **Evening packing list (20:00)** | The window opens over a blurred dashboard, is skipped on Saturday, and can be opened manually with **≡** | ✅ |
| 17 | **Custom packing items** | Adding an item with `+  Добавить свой пункт`, checking it off (strikethrough), long-press to delete | ✅ |
| 18 | **Success animation** | A green check mark and a short fireworks animation, the text `Всё собрано!` / `Можно ложиться спать`, auto-close after about 5.6 s | ✅ |
| 19 | **Accessibility service** | With the service enabled, **⏻** opens the standard Android power menu; with it disabled, a toast explains how to enable it | ✅ |
| 20 | **Long run** | About 35 minutes of continuous work: no crashes, no ANRs, about 12 MB PSS and nearly 0% CPU in idle | ✅ |

The night/morning alarms were additionally verified end-to-end with a temporary test alarm (a wake-up a few seconds later instead of waiting for 08:00) — the screen woke up and the panel appeared as expected.

## Self-test checklist for your own device

Run through this list **before** you mount the tablet on a wall and forget about it. Allow yourself 20–30 minutes.

- [ ] **1. Install and launch.** The APK installs and the app opens; it does not crash on the first launch.
- [ ] **2. Full screen.** There are no status or navigation bars; nothing is clipped at the screen edges.
- [ ] **3. Screen stays on.** During the day the screen does not turn off by itself while the app is open.
- [ ] **4. Weather loads.** With an internet connection, weather appears within a minute; the values look plausible.
- [ ] **5. Weather details.** Temperature, "feels like", min/max, wind, humidity, sunrise/sunset and daylight are shown and reasonable for your area.
- [ ] **6. What to wear.** All five rows are filled in and match today's weather.
- [ ] **7. Calendar permission and events.** The calendar permission dialog appears; after allowing it, today's events are shown in time order with correct times.
- [ ] **8. Empty calendar.** If you pick a day without events, the right half is completely empty (no error text).
- [ ] **9. Busy calendar.** A day with 8–12 events still shows all cards without overlap or clipping.
- [ ] **10. Offline and back online.** Turn Wi-Fi off: cached weather plus the offline note. Turn it on: data refreshes by itself.
- [ ] **11. Reboot.** Reboot the tablet: the panel starts automatically after boot, and the screen wakes by the schedule.
- [ ] **12. Night mode.** At 21:00 the screen dims and turns off; a touch wakes it for 5 minutes and then it goes dark again.
- [ ] **13. Morning wake.** The 08:00 alarm actually wakes the screen. To test without waiting, temporarily set `DAY_START_HOUR` to the next hour in `DeviceConfig.java`, rebuild and watch (see [BUILD.md](BUILD.md)). The source also contains an acceptance-test hook, `-e test_alarm 60`, which schedules a wake-up 60 seconds later; it is not used in normal operation.
- [ ] **14. Evening list.** With `PACKING_HOUR` set to the next hour (or via **≡**), the evening window appears, shows the empty state, accepts a new item, checks items off and plays the success animation.
- [ ] **15. Orientation, buttons, power.** Rotating the tablet 180° flips the drawing; **≡** and **⏻** are tappable; **⏻** opens the system power menu with the accessibility service enabled.

If any of these fail, check [Troubleshooting](#troubleshooting), [FAQ.md](FAQ.md) and [LIMITATIONS.md](LIMITATIONS.md) before assuming the app is broken.

## Troubleshooting

| Symptom | What to check |
|---|---|
| **No weather** | Internet works? wttr.in reachable from the tablet's network? System time zone correct or set explicitly? See [FAQ.md](FAQ.md#why-is-there-no-weather) |
| **No calendar events** | Calendar permission granted? Is there at least one calendar account with events on the tablet? Try another calendar app on the same device to confirm events exist |
| **The panel does not start after a reboot** | Allow autostart and exclude the app from battery optimization (Huawei/EMUI: [INSTALL.md](INSTALL.md#step-6-huawei--emui-autostart-and-battery)) |
| **The screen does not wake at 08:00 or turn off at 21:00** | Battery optimization may delay exact alarms; check the Doze whitelist and the autostart settings. Also check that `TIME_ZONE` is correct |
| **The night screen never turns off** | Some firmwares keep the screen on when a "keep screen on" flag is left behind by another app; reboot and retest. Also check that `DAY_END_HOUR` is in the past for today |
| **Weather is stale** | The refresh interval is 30 minutes; the retry backoff can reach 30 minutes if wttr.in is failing. Toggle the network to force a refresh |
| **Screen dims but the panel is unreadable at night** | Increase `NIGHT_BRIGHTNESS` in `DeviceConfig.java` and rebuild |
| **The layout is clipped or has big margins** | Your screen aspect ratio differs from 600×1024; read [ADAPT.md](ADAPT.md#unusual-screens) |
| **"App not installed" on update** | The installed app was signed with a different key; uninstall it first. See [FAQ.md](FAQ.md#why-do-i-get-app-not-installed-or-a-signature-conflict-when-updating) |
| **App crashes on start** | Check `adb logcat -s SmartDashboard` and `adb logcat -d | findstr "FATAL EXCEPTION"`. Report the crash with the device model and Android version |

## How to uninstall and roll back

- On the tablet: **Settings → Apps → Smart Dashboard → Uninstall**.
- Over ADB: `adb uninstall ru.home.dashboard`.
- Or run [scripts/uninstall.ps1](../../scripts/uninstall.ps1).

Uninstalling removes the app, its settings and its cache. Your calendar, accounts and system settings are not touched. Everything the app does is reversible: it does not modify the system partition, does not require root and does not flash anything.

To go back to a previous version of the app: uninstall the current one and install the older APK. Downgrading over an existing installation is blocked by Android.

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
