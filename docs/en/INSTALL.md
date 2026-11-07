# Installing Smart Dashboard (ready APK)

This guide is for installing the **already built and signed** APK on your tablet. No computer, SDK or build tools are required. If you want to build the APK yourself, read [BUILD.md](BUILD.md) instead.

> ⚠️ **Tested on one tablet only.** Smart Dashboard was developed and verified on a Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices — test it on your own tablet before relying on it, and use it at your own risk. See [TESTING.md](TESTING.md).

## Contents

- [What you need](#what-you-need)
- [Step 1. Download the APK](#step-1-download-the-apk)
- [Step 2. Allow installation from unknown sources](#step-2-allow-installation-from-unknown-sources)
- [Step 3. Install the APK](#step-3-install-the-apk)
- [Step 4. Grant the calendar permission](#step-4-grant-the-calendar-permission)
- [Step 5. Optional: accessibility service for the power button](#step-5-optional-accessibility-service-for-the-power-button)
- [Step 6. Huawei / EMUI: autostart and battery](#step-6-huawei--emui-autostart-and-battery)
- [Step 7. Launch and verify](#step-7-launch-and-verify)
- [Install over ADB (optional)](#install-over-adb-optional)
- [Updating to a new version](#updating-to-a-new-version)
- [Uninstalling](#uninstalling)
- [If the APK will not install](#if-the-apk-will-not-install)

## What you need

| Requirement | Details |
|---|---|
| Device | An Android tablet with **Android 7.0 (API 24) or newer** |
| Screen | Portrait, ideally 600×1024 or a similar aspect ratio — the layout is designed for it |
| Free space | Negligible: the APK is about 60 KB |
| Internet | Needed for the weather; the calendar is read locally |
| APK file | [`apk/SmartDashboard-1.3.apk`](../../apk/SmartDashboard-1.3.apk) from this repository or the [Releases page](https://github.com/Miles64girt/smart-dashboard/releases) |
| Google account (optional) | Only if you want calendar events. The app reads the system calendar database, so whatever account Android already has synced will be visible |

## Step 1. Download the APK

Pick whichever is easiest for you:

- **On the tablet:** open the repository page in the tablet browser, go to `apk/` and download `SmartDashboard-1.3.apk`.
- **On a computer:** download the file, then copy it to the tablet over USB (MTP), a cloud drive or a USB flash drive.
- **From Releases:** download the APK attached to the `v1.3` release.

Keep the file somewhere you can find it, for example the tablet's `Download` folder.

## Step 2. Allow installation from unknown sources

Android blocks APKs that do not come from Google Play until you allow it.

1. Open **Settings**.
2. Go to **Security** (on some Huawei/EMUI builds: **Settings → Security → More settings**).
3. Turn on **Unknown sources** / **Install apps from unknown sources**.
   On newer Android versions the switch is per app: **Settings → Apps → Special access → Install unknown apps**, then allow it for the app you will use to open the APK (Files, Chrome, etc.).
4. Confirm the warning.

## Step 3. Install the APK

1. Open the **Files** app (or your browser's Downloads list) and tap `SmartDashboard-1.3.apk`.
2. If a **Play Protect** warning appears — for example *"Unsafe app blocked"* or *"This app was not scanned"* — tap **More details → Install anyway**.
   This is normal for a sideloaded app. Play Protect only knows apps it has scanned; Smart Dashboard is built from the sources in this repository and has no third-party libraries and no network activity except weather requests.
3. Tap **Install** and wait a few seconds.
4. When the installer finishes, tap **Open** (or find **Smart Dashboard** in the app drawer).

> If you see **"App not installed"** or **"The package conflicts with an existing package"**, an older build signed with a different key is already installed. Uninstall it first (see [If the APK will not install](#if-the-apk-will-not-install)); this deletes the app's local settings.

## Step 4. Grant the calendar permission

On the first launch the app requests the calendar permission:

1. A system dialog appears: *"Allow Smart Dashboard to access your calendar?"* — tap **Allow**.
2. If you accidentally denied it, open **Settings → Apps → Smart Dashboard → Permissions → Calendar** and allow it.
3. If the dialog never appears, or the calendar is empty, check that the tablet actually has a calendar account with events. See [FAQ.md](FAQ.md#why-does-the-right-half-show-no-events).

The permission is used **only to read** today's events through the official Android `CalendarContract` API. The app never asks for your Google password and never stores OAuth tokens.

## Step 5. Optional: accessibility service for the power button

The **⏻** button in the bottom-right corner opens the standard Android power menu. A normal Android 7 app cannot do that directly, so the button uses a small accessibility service included in the app.

1. Open **Settings → Accessibility** (on EMUI: **Settings → Smart assistance → Accessibility**).
2. Find **Smart Dashboard** in the list of services.
3. Turn it on and confirm.
4. The description shown by Android explains what it does: it only shows the system power dialog when you press the button in the panel.

If the service is off, everything else keeps working. Pressing **⏻** will show a toast asking you to enable the service and open the accessibility settings.

> The service **does not read the screen, does not click anything and does not collect data**. It has no accessibility event handling of its own; it only calls the system "show power dialog" action.

## Step 6. Huawei / EMUI: autostart and battery

On Huawei and Honor tablets, EMUI aggressively kills background apps. Two things need to be allowed, otherwise the panel may not start after a reboot and the 08:00/21:00 alarms may be delayed:

1. **Autostart**
   - **Settings → Battery → App launch** (may also be called **Launch**, **Startup manager**, or **Phone Manager → Autostart**).
   - Find **Smart Dashboard**, turn **Manage automatically** off and enable **Auto-launch**, **Secondary launch** and **Run in background**.
2. **Battery optimization / protected apps**
   - **Settings → Battery → Battery optimization** → find Smart Dashboard → **Don't optimize** (or *Not optimized*).
   - On some builds: **Phone Manager → Protected apps** → allow Smart Dashboard.
3. **Doze whitelist (optional, via ADB)**
   ```bat
   adb shell dumpsys deviceidle whitelist +ru.home.dashboard
   ```
   This is the same thing the helper script does (see [scripts/install_deploy.ps1](../../scripts/install_deploy.ps1)).

After these steps, reboot the tablet once to verify that the panel starts by itself.

## Step 7. Launch and verify

Open **Smart Dashboard** from the app drawer. You should see:

- the dashboard in **full-screen mode** (no status bar, no navigation bar);
- **weather** on the left after 5–15 seconds (internet required);
- the **"What to wear today"** block under the weather;
- **today's events** on the right half, or an empty right half if the day has no events;
- the **≡** and **⏻** buttons in the bottom-right corner.

Check the screen behaviour:

- at night (after 21:00) the screen dims and shows **`касание — включить экран`**; a touch wakes it for 5 minutes;
- the app appears automatically after a reboot (may take a few seconds);
- the panel follows the tablet being rotated 180° (it supports normal and upside-down portrait, not landscape).

For a complete checklist, including the evening list and alarms, see [TESTING.md](TESTING.md).

## Install over ADB (optional)

If USB debugging is enabled and `adb` is available, everything can be done from PowerShell (run these commands from the repository root):

```powershell
# 1. Install or update the APK
adb install -r .\apk\SmartDashboard-1.3.apk

# 2. Grant the calendar permission
adb shell pm grant ru.home.dashboard android.permission.READ_CALENDAR

# 3. Exclude the app from battery optimization (Doze)
adb shell dumpsys deviceidle whitelist +ru.home.dashboard

# 4. Launch the panel
adb shell am start -n ru.home.dashboard/.MainActivity

# 5. Check that the app is on screen
adb shell dumpsys activity activities | findstr mResumedActivity

# 6. Uninstall
adb uninstall ru.home.dashboard
```

The helper script [`scripts/install_deploy.ps1`](../../scripts/install_deploy.ps1) does all of this in one go: it finds `adb` and the connected device, installs the newest `apk\SmartDashboard-*.apk`, grants `READ_CALENDAR`, adds the Doze whitelist entry, enables the accessibility service, sets `screen_brightness_mode=1` and disables system touch sounds, launches the app and prints a status summary.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install_deploy.ps1

# If adb is not in PATH or several devices are connected:
powershell -ExecutionPolicy Bypass -File .\scripts\install_deploy.ps1 -Adb "C:\platform-tools\adb.exe" -Serial "<device-serial>"
```

> Enabling the accessibility service over ADB works differently on different firmware. If the **⏻** button does not respond after the script, enable the service manually — [Step 5](#step-5-optional-accessibility-service-for-the-power-button).

## Updating to a new version

- Install the new APK **over** the old one (`adb install -r ...` or just tap the new APK). Android replaces the app and keeps its local settings (your custom packing items and the weather cache).
- The update must be **signed with the same key** as the installed version. The APK in this repository is signed with the author's key; a build you make yourself is signed with *your* key and will not install over it. In that case uninstall the old version first — this deletes the app's settings.
- Check [CHANGELOG.md](../../CHANGELOG.md) before updating.

## Uninstalling

- **On the tablet:** **Settings → Apps → Smart Dashboard → Uninstall**.
- **Or over ADB:**
  ```bat
  adb uninstall ru.home.dashboard
  ```
- **Or with the helper script:** [scripts/uninstall.ps1](../../scripts/uninstall.ps1) (it finds `adb` automatically).

Uninstalling removes all the app's local data: custom packing items, the weather cache, preferences. It does not touch your calendar, accounts or system settings. If you enabled the accessibility service, it disappears from the accessibility list together with the app.

## If the APK will not install

| Symptom | Likely cause and fix |
|---|---|
| "Install blocked" / "Unknown sources" | Installation from unknown sources is still disabled — repeat [Step 2](#step-2-allow-installation-from-unknown-sources) |
| Play Protect offers only "Don't install" | Tap **More details** first, then **Install anyway**; or temporarily disable Play Protect in the Play Store settings (you can re-enable it afterwards) |
| "App not installed" / "Package conflicts with an existing package" | A different build is already installed. Uninstall it, then install again. See [FAQ.md](FAQ.md#why-do-i-get-app-not-installed-or-a-signature-conflict-when-updating) |
| "There was a problem parsing the package" | The file is incomplete or corrupted — download it again, and check that the size looks plausible (tens of KB, not 0) |
| Installs but does not start | The device may be below Android 7.0 (minSdk 24). Check **Settings → About tablet → Android version** |
| App starts but no weather | Check the internet connection and the time zone. See [FAQ.md](FAQ.md#why-is-there-no-weather) |
| App starts but the right half is empty | No calendar permission, no calendar account, or no events today. See [FAQ.md](FAQ.md#why-does-the-right-half-show-no-events) |
| Autostart after reboot does not work | The Huawei/EMUI autostart settings were not allowed — repeat [Step 6](#step-6-huawei--emui-autostart-and-battery) |

More answers: [FAQ.md](FAQ.md) · Full limitations: [LIMITATIONS.md](LIMITATIONS.md) · Adaptation to your device: [ADAPT.md](ADAPT.md).

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
