# FAQ

Short answers to the questions people ask most often about Smart Dashboard. For deeper details follow the links to the other documents.

> ⚠️ **Tested on one tablet only.** Smart Dashboard was developed and verified on a Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet; use it at your own risk.

## Contents

- [Why doesn't the panel start by itself after a reboot?](#why-doesnt-the-panel-start-by-itself-after-a-reboot)
- [Why is there no weather?](#why-is-there-no-weather)
- [Why does the right half show no events?](#why-does-the-right-half-show-no-events)
- [Why doesn't the screen wake up in the morning or turn off at night?](#why-doesnt-the-screen-wake-up-in-the-morning-or-turn-off-at-night)
- [Why does Play Protect say the app is unsafe?](#why-does-play-protect-say-the-app-is-unsafe)
- [Why do I get "App not installed" or a signature conflict when updating?](#why-do-i-get-app-not-installed-or-a-signature-conflict-when-updating)
- [Does Smart Dashboard drain the battery?](#does-smart-dashboard-drain-the-battery)
- [Will the always-on screen burn in?](#will-the-always-on-screen-burn-in)
- [Can I run it on a phone or another tablet?](#can-i-run-it-on-a-phone-or-another-tablet)
- [Do I need root, a custom ROM or Google Play Services?](#do-i-need-root-a-custom-rom-or-google-play-services)
- [Why is the packing list empty?](#why-is-the-packing-list-empty)
- [How do I add or remove packing items?](#how-do-i-add-or-remove-packing-items)
- [Is it safe? What data does it send?](#is-it-safe-what-data-does-it-send)
- [Why is the APK only about 53 KB?](#why-is-the-apk-only-about-53-kb)
- [How do I update to a new version?](#how-do-i-update-to-a-new-version)

## Why doesn't the panel start by itself after a reboot?

Autostart is implemented through `BOOT_COMPLETED`, and it works on the reference tablet — but on Huawei/EMUI (and some other Android 7 builds) the system blocks background launches unless you allow them:

1. **Settings → Battery → App launch** (EMUI): turn off "Manage automatically" for Smart Dashboard and enable **Auto-launch**, **Secondary launch** and **Run in background**.
2. **Settings → Battery → Battery optimization** (or **Phone Manager → Protected apps**): exclude Smart Dashboard from optimization.

Then reboot and check again. If it still does not start, open the app manually once after the reboot and compare with the checklist in [INSTALL.md](INSTALL.md#step-6-huawei--emui-autostart-and-battery). Note that the app cannot start from a **fully powered-off** device — it only starts after Android has booted.

## Why is there no weather?

A few normal reasons, in order of probability:

- **No internet on the tablet.** The app has no weather to show on the very first launch without a connection. After one successful load it keeps the last data and shows the amber `Нет сети · данные ЧЧ:ММ` note.
- **wttr.in is temporarily unreachable.** It is a free public service without an API key. The app retries with a backoff of 1 → 2 → 4 → 8 → 16 → 30 minutes; turning Wi-Fi off and on forces an immediate retry.
- **Wrong time zone.** The forecast itself does not depend on the time zone, but the "as of HH:MM" label and the sunrise/sunset may look wrong. If your system time zone is broken, set `DeviceConfig.TIME_ZONE` explicitly — see [ADAPT.md](ADAPT.md#time-zone).
- **The TLS issue on Android 7.0.** The app already bundles the official ISRG roots for the weather domains, so this should not happen; it is listed for completeness in [ARCHITECTURE.md](ARCHITECTURE.md#android-7-specifics).

Check the log if you have ADB: `adb logcat -s SmartDashboard` shows weather fetch errors.

## Why does the right half show no events?

The right half shows **only today's events from the system calendar**. Check, in order:

1. **Calendar permission.** If you denied the system dialog, re-enable it: **Settings → Apps → Smart Dashboard → Permissions → Calendar**.
2. **Calendar account.** The app reads the system calendar provider. If the tablet has no account with events, the half stays empty. Open any calendar app on the same tablet — if it also shows nothing, the issue is the account, not Smart Dashboard.
3. **"Today" in the configured time zone.** Events are selected between today 00:00 and tomorrow 00:00 in the app's time zone; an event just after midnight may belong to the next day.
4. **Empty day is a feature.** When there are genuinely no events, nothing is drawn — no placeholder text.
5. **Up to 12 events.** More than 12 events in a day are not shown; the app is designed for a glanceable panel.

More context: [ARCHITECTURE.md](ARCHITECTURE.md#data-flow-calendar).

## Why doesn't the screen wake up in the morning or turn off at night?

The schedule relies on exact alarms (`AlarmManager`). They can be delayed or blocked when:

- the app is **battery-optimized** by the system (EMUI is aggressive here);
- **autostart** is not allowed for the app, so the alarms are not restored after a reboot;
- the configured **time zone is wrong**, so 08:00/21:00 happen at the wrong real time;
- the device is in a very deep sleep state and the firmware ignores wake locks (less common, but possible on old tablets).

Fix the Huawei/EMUI settings first ([INSTALL.md](INSTALL.md#step-6-huawei--emui-autostart-and-battery)) and check `DeviceConfig.TIME_ZONE`. Also remember the honest limitation: the app **turns the screen off**, it does not power off the tablet; and it cannot wake a powered-off device.

## Why does Play Protect say the app is unsafe?

Because the APK is **sideloaded** — installed not from Google Play. Play Protect often shows "unsafe app blocked" or "this app was not scanned" for any app it has not seen before. Tap **More details → Install anyway**.

Smart Dashboard is not obfuscated, is built from the public sources in this repository, contains no third-party libraries and talks only to the weather services. If you want, build it yourself — then the APK is signed with your own key and you know exactly what is inside: [BUILD.md](BUILD.md).

## Why do I get "App not installed" or a signature conflict when updating?

Android refuses to install an APK over an existing app when the two are **signed with different keys**. This happens when:

- you installed the APK from this repository and now try to install an APK you built yourself (your key ≠ the author's key);
- you built on another computer and lost the original `build/keystore.jks` / `build/keystore.properties`;
- you installed an older custom build signed with a different key.

Fix: **uninstall the old version**, then install the new one. This deletes the app's local settings (custom packing items, weather cache), but nothing else. To avoid this in the future, keep the signing key safe — [BUILD.md](BUILD.md#the-signing-key-read-this).

## Does Smart Dashboard drain the battery?

The app itself is very light: about **12 MB PSS** and close to **0% CPU when idle**. The real power consumer is the **screen**, which is on all day by design. Practical advice from long-term use:

- keep the tablet plugged in, or use a smart plug with a schedule (for example power on 08:00–21:00);
- night mode (dim, screen off) keeps consumption low overnight;
- a reboot once a week is a good habit on an old Android device;
- if the battery is worn out, the tablet may need to stay on the charger permanently.

## Will the always-on screen burn in?

It depends on the panel:

- The reference tablet has an **LCD** screen, where long static images do not burn in permanently (temporary image retention can happen, but it fades).
- On **OLED/AMOLED** devices, static elements *can* cause permanent burn-in. If you run the panel on an OLED tablet: keep the brightness lower, use night mode, and do not leave the same static layout on 24/7 for months.

The app dims the screen at night and turns it off, which reduces the risk on any panel.

## Can I run it on a phone or another tablet?

It may work on any device with **Android 7.0+** and a portrait screen, but it was **not tested on anything except the Huawei MediaPad T3 7**. Notes:

- the layout is designed for 600×1024 and scales by `min(width/600, height/1024)`; a phone screen will work but the text will be small;
- landscape is not supported;
- OEM battery managers may block the alarms and autostart;
- an old device without security updates is not the best choice for sensitive data.

Read [ADAPT.md](ADAPT.md) for the config fields and the checklist to run on your device before trusting it.

## Do I need root, a custom ROM or Google Play Services?

**No.**

- No root, no bootloader unlock, no system modification.
- No Google Play Services dependency: the app does not use GMS, Play Services auth or Google Play.
- A Google account is only useful as a **source of calendar events** — but any calendar account that Android has synced will do, and the app reads it through the system `CalendarContract` API without ever seeing your password.
- Weather works without any account or API key.

## Why is the packing list empty?

This is intentional since version **1.3**. The list used to contain the author's personal items; they were removed from the code so that the public app ships clean. Everyone adds their own items:

- open the list with **≡** or wait for the evening window at 20:00;
- tap **`+  Добавить свой пункт`** and type an item (for example "Keys" or "Charger");
- items are saved locally on the device and survive app updates.

If you want a few default items in **your own** build, set `DEFAULT_PACKING_ITEMS` in [`DeviceConfig.java`](../../app/src/ru/home/dashboard/DeviceConfig.java) — see [ADAPT.md](ADAPT.md#packing-list).

## How do I add or remove packing items?

- **Add:** open the evening window (**≡** or 20:00), tap **`+  Добавить свой пункт`**, type the item and confirm.
- **Remove:** press and **hold** your custom item for about a second — it is deleted with a short toast.
- **Check off:** tap an item once; tap again to uncheck.
- Default/calendar-derived items cannot be deleted (there are none in a clean v1.3 build unless you add them in `DeviceConfig`).

## Is it safe? What data does it send?

- **No account credentials.** The app never asks for a Google password and does not store OAuth tokens.
- **Calendar is read-only** through the system `CalendarContract` provider.
- **The only network traffic** is weather requests to wttr.in (with an optional Open-Meteo fallback when you set coordinates manually). There is no account, no API key, no analytics and no ads. In auto-location mode no coordinates are sent — wttr.in derives the city from your public IP; the standard HTTP `User-Agent` includes the app version, the Android version and the device model.
- **No root and no system changes**; uninstalling removes everything the app stored.
- **Accessibility service** is used only to show the system power dialog; it has no window-content access and does not click anything.

Full breakdown: [ARCHITECTURE.md](ARCHITECTURE.md#permissions-and-privacy).

## Why is the APK only about 53 KB?

Because there is almost nothing inside: no AndroidX, no image assets, no native libraries, no Gradle dependencies. The UI is drawn with Android's built-in `Canvas`, the network requests use `HttpURLConnection`, and the JSON uses Android's built-in `org.json`. The result is an APK of **about 53 KB**, about **12 MB PSS** at runtime and a panel that starts almost instantly on a 1 GB tablet.

## How do I update to a new version?

- Download the new APK from the repository's `apk/` folder or the [Releases page](https://github.com/Miles64girt/smart-dashboard/releases/latest).
- Install it over the old one — the update keeps your custom packing items and cache, **as long as the signing key is the same**.
- Check [CHANGELOG.md](../../CHANGELOG.md) before updating.
- If you see "App not installed", the keys differ — uninstall and reinstall (losing local settings). See [the signature question above](#why-do-i-get-app-not-installed-or-a-signature-conflict-when-updating).

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
