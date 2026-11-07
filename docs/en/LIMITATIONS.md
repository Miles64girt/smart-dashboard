# Limitations

Smart Dashboard does one job and does it without asking for much. This page lists everything it deliberately **cannot** do and every honest caveat, so there are no surprises after installation. None of these is a bug to report — they are design decisions and physical limits of the target devices.

> ⚠️ **Tested on one tablet only.** Everything below applies first and foremost to the tested configuration: Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. Other devices have not been tested.

## Contents

- [1. Tested on a single device](#1-tested-on-a-single-device)
- [2. No full power-off](#2-no-full-power-off)
- [3. No auto-on from a powered-off state](#3-no-auto-on-from-a-powered-off-state)
- [4. No landscape orientation](#4-no-landscape-orientation)
- [5. Weather depends on wttr.in](#5-weather-depends-on-wttrin)
- [6. Wrong time zone on some firmwares](#6-wrong-time-zone-on-some-firmwares)
- [7. OEM autostart restrictions](#7-oem-autostart-restrictions)
- [8. No ambient light sensor, no automatic brightness](#8-no-ambient-light-sensor-no-automatic-brightness)
- [9. An old Android without security updates](#9-an-old-android-without-security-updates)
- [10. Factory reset protection (FRP)](#10-factory-reset-protection-frp)
- [11. Sound is muted by default](#11-sound-is-muted-by-default)
- [12. Signing key and update compatibility](#12-signing-key-and-update-compatibility)
- [Out of scope](#out-of-scope)

## 1. Tested on a single device

Smart Dashboard was developed and verified on **exactly one tablet**: Huawei MediaPad T3 7 (**BG2-U01**), Android 7.0 (API 24), 32-bit, 1 GB RAM, 600×1024, EMUI 5.1. It has **not** been tested on any other model, resolution, Android version or firmware.

What that means in practice:

- It may work on other Android 7+ tablets with a portrait screen — but that is a guess, not a tested claim.
- Scaling, alarms, autostart, brightness and the accessibility service can behave differently on other firmware.
- Test your device with the checklist in [TESTING.md](TESTING.md) before trusting the panel.

## 2. No full power-off

At 21:00 the app switches to night mode and the screen turns off — but the **tablet itself stays on**. A regular Android 7 app cannot power off the device; that requires root or a system-level (signature) permission, and this project deliberately avoids both.

The only way to get a real power-off is the **⏻** button and the system power menu by hand (or a hardware power button). The morning schedule can then only wake the tablet from **sleep**, not from an off state — see the next point.

## 3. No auto-on from a powered-off state

If the tablet is completely powered off, **nothing in this app can turn it on**. Waking a device from OFF needs an RTC alarm boot capability in the firmware/bootloader; the reference tablet does not have it, and the app cannot add it without root and a system modification.

Practical alternatives:

- keep the tablet powered all the time (screen off at night is enough, and the panel stays ready);
- use a **smart plug** with a schedule to cut and restore power — but note that the tablet must then boot and start the app by itself, which depends on the OEM autostart settings;
- accept pressing the power button in the morning.

## 4. No landscape orientation

The interface is designed for **portrait** — two vertical halves on a 600×1024 screen. Landscape is intentionally disabled in the manifest (`screenOrientation="portrait"`):

- the layout would not fit 1024×600 without a redesign;
- the reference firmware handles forced rotation inconsistently;
- tablets used as wall panels are normally mounted vertically.

The app does support **upside-down portrait**: it detects the 180° flip with the accelerometer and rotates its drawing, so you can mount the tablet with the charging port at the top. Landscape and auto-rotation are out of scope; if you need a landscape dashboard, this is not the project for it.

## 5. Weather depends on wttr.in

Weather comes from **wttr.in** — a free public service without an API key. Consequences:

- If wttr.in is unreachable (service problems, blocked network, no internet), the panel shows the **last cached data** with the note `Нет сети · данные ЧЧ:ММ`, or a "no data" state on the very first launch.
- There is **no guarantee of service availability**. wttr.in is run by volunteers/community infrastructure and can change or disappear.
- Retries use exponential backoff (1 → 2 → 4 → 8 → 16 → 30 minutes), so data can be stale for up to about half an hour during an outage.
- An **Open-Meteo fallback** exists in the code, but it is used only when you disable auto-location and set explicit coordinates, and on the reference tablet's network it was unreachable (TCP timeout at the provider level). Treat it as a best-effort reserve, not a guaranteed second source.

## 6. Wrong time zone on some firmwares

On the reference tablet the **system time zone was locked to GMT** and could not be changed over ADB without root. Cheap Android 7 tablets sometimes reset their time zone after a reboot.

The app handles this through `DeviceConfig.TIME_ZONE`: set an IANA zone (for example `Europe/Moscow`) and all dates, event times, the "today" range and the 08:00/20:00/21:00 schedule are computed in that zone regardless of the system setting.

Caveat: the `TIME_ZONE` constant only affects the app's own calculations. It does not fix other apps, the lock screen clock or the system. See [ADAPT.md](ADAPT.md#time-zone).

## 7. OEM autostart restrictions

On Huawei/EMUI (and some other Android 7 builds) the system blocks background starts unless the user explicitly allows them:

- without **autostart** permission, the panel may not start after a reboot;
- without a **battery optimization exemption**, exact alarms (08:00, 20:00, 21:00) may be delayed or skipped;
- the OEM "phone manager" may kill the app while the screen is off.

The app cannot override these vendor policies. You must configure them manually — see [INSTALL.md](INSTALL.md#step-6-huawei--emui-autostart-and-battery). Behaviour on other vendors' firmware (Xiaomi, Samsung, OPPO, etc.) was not tested at all.

## 8. No ambient light sensor, no automatic brightness

The reference tablet has **no ambient light sensor**, so real auto-brightness is physically impossible; the app sets a fixed window brightness instead: `DAY_BRIGHTNESS` (default 0.62) during the day and `NIGHT_BRIGHTNESS` (default 0.02) at night. Both are configurable in [`DeviceConfig.java`](../../app/src/ru/home/dashboard/DeviceConfig.java).

If your tablet has a light sensor, the app will **not** use it. Adjust the constants for your screen and room conditions, or accept the fixed values.

## 9. An old Android without security updates

Android 7.0 is out of security support. A tablet left on a wall with an old OS should **not** be used for sensitive data:

- do not sign in to important accounts on the panel tablet;
- do not use it for banking, passwords or private documents;
- keep the tablet on a trusted home network.

The app itself is small and privacy-friendly (see [ARCHITECTURE.md](ARCHITECTURE.md#permissions-and-privacy)), but it cannot make the operating system secure.

## 10. Factory reset protection (FRP)

If the tablet has a **Google account** added (needed for Google Calendar events), Android's Factory Reset Protection is active. After a factory reset the tablet will ask for the previously added Google account — otherwise it cannot be set up again.

This is normal Android behaviour, not something the app changes. If you are preparing the tablet for resale or giving it away, remove the Google account first via **Settings → Accounts**.

## 11. Sound is muted by default

With `MUTE_SOUND = true` (the default) the app puts the device into silent mode and sets all audio streams to zero on every start. This is intended for a panel that never makes noise — but it means the tablet will not ring or play notifications while the panel is running.

To keep the normal sound, set `MUTE_SOUND = false` in `DeviceConfig.java` and rebuild. Note that the interface is designed to be silent and the app does not manage volumes beyond the start-up mute.

## 12. Signing key and update compatibility

- The APK published in this repository is signed with the **author's** key.
- A build made from source is signed with **your own** key — it will not install over the published APK (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); uninstall first.
- The first build generates `build/keystore.jks` and `build/keystore.properties`. **Keep both files**: without them you cannot update an installed app, and you will have to uninstall (losing custom packing items and cache).
- The signing key is not a security proof of identity; it only guarantees update continuity.

See [BUILD.md](BUILD.md#the-signing-key-read-this) for details.

## Out of scope

The following are **not** planned and not supported by this project:

| Not supported | Why |
|---|---|
| Full device power-off / power-on from OFF | Needs root or a bootloader feature; the app stays a normal user app ([2](#2-no-full-power-off), [3](#3-no-auto-on-from-a-powered-off-state)) |
| Landscape layout and auto-rotation | The panel is designed for portrait; a landscape layout would be a different UI project ([4](#4-no-landscape-orientation)) |
| Arbitrary widgets, dashboards, multiple pages | The app is a single fixed screen with weather, calendar and a packing list — not a widget platform |
| Other data sources: news, email, messages, stocks, smart-home devices | Out of scope; every extra source would add permissions, libraries and failure modes |
| Multi-day weather forecast | Only today's weather is shown by design |
| Phone-call/SMS integration, notifications | This is a wall panel, not a phone companion |
| Cloud sync, accounts, personal API keys | Everything is local; the only network traffic is weather |
| Root features, custom ROM requirements, GMS | The project explicitly avoids all of them |
| Devices below Android 7.0 (API 24) | Not supported by the build (`minSdkVersion` 24) |
| In-app language switch | The on-screen interface is currently Russian; the project documentation is available in English and Russian |
| Support/guarantee for third-party devices | The app was tested on one tablet only ([1](#1-tested-on-a-single-device)) |

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
