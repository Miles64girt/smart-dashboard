# Building Smart Dashboard from source

Smart Dashboard is built **without Gradle and without Android Studio**: a PowerShell script calls the Android command-line tools directly (`aapt2 → javac → d8 → zipalign → apksigner`). The whole app is a handful of Java files and a few resources, so the build takes seconds once the toolchain is installed.

> ⚠️ **Tested on one tablet only.** The build itself is reproducible, but the resulting app was verified only on a Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. Test it on your own device; use it at your own risk. See [TESTING.md](TESTING.md).

## Contents

- [Requirements](#requirements)
- [First-time environment setup](#first-time-environment-setup)
- [Build the APK](#build-the-apk)
- [Install the APK over ADB](#install-the-apk-over-adb)
- [The signing key (read this)](#the-signing-key-read-this)
- [Build outputs](#build-outputs)
- [Clean rebuild](#clean-rebuild)
- [Common errors](#common-errors)
- [Manual commands (if you prefer)](#manual-commands-if-you-prefer)

## Requirements

| Requirement | Details |
|---|---|
| OS | Windows 7 or newer (verified on Windows 10/11; the scripts are PowerShell, macOS/Linux are not supported out of the box) |
| PowerShell | **5.1 or newer** — the built-in Windows PowerShell is enough, PowerShell 7 also works |
| Internet | Required **only once**, to download the JDK and Android tools (about 320 MB) |
| Disk space | About 1 GB for the toolchain and temporary build files |
| ADB | Only if you want to install via USB. The build itself does not need a device |
| Nothing else | No Android Studio, no Gradle, no SDK Manager, no Java installed beforehand |

Two directory rules matter:

- The **toolchain** (`BuildRoot`, by default `C:\AndroidBuild`) and the **staging directory** (`StageRoot`, by default `%TEMP%\smart-dashboard-build`) must be in **ASCII paths** — `aapt2` does not tolerate non-ASCII characters in paths.
- The project folder itself **may** contain non-ASCII characters: the build copies the sources to the ASCII staging directory before running the Android tools. If your Windows `TEMP` path contains non-ASCII characters (for example a Cyrillic user name), pass an ASCII `-StageRoot` manually.

## First-time environment setup

Run the setup script **once**:

```powershell
cd C:\path\to\smart-dashboard
powershell -ExecutionPolicy Bypass -File .\scripts\setup_build_env.ps1
```

It downloads and unpacks the official archives into `C:\AndroidBuild`:

| Component | Version | Purpose |
|---|---|---|
| JDK | **17** (Adoptium/Temurin) | Runs `keytool`, `javac`, `d8`, `apksigner` |
| Android build-tools | **34** | `aapt2`, `d8`, `zipalign`, `apksigner` |
| Android platform | **API 24** (`android.jar`) | Compile against Android 7.0 |

The download is about **320 MB**, so give it a few minutes. Re-running the script is safe: already-downloaded archives are skipped, and the script prints a check table of every tool it found.

To install the environment somewhere else, pass `-Base`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup_build_env.ps1 -Base D:\AndroidBuild
```

## Build the APK

```powershell
powershell -ExecutionPolicy Bypass -File .\build\build.ps1
```

What the script does:

1. Checks that `java`, `javac`, `keytool`, `aapt2`, `d8`, `zipalign`, `apksigner` and `android.jar` are available (and tells you to run `setup_build_env.ps1` if not).
2. Copies the sources into an ASCII staging directory (`%TEMP%\smart-dashboard-build\work` by default; override with `-StageRoot`), because `aapt2` does not tolerate non-ASCII characters in paths.
3. Compiles and links resources and the manifest with `aapt2`.
4. Compiles the Java sources with `javac` against `android.jar` (API 24).
5. Converts the classes to DEX with `d8` (`--min-api 24`) and packs `classes.dex` into the APK.
6. Aligns the APK with `zipalign`.
7. Signs it with `apksigner` and verifies the signature.
8. Copies the result to `apk\SmartDashboard-1.3.apk` and writes a log to `logs\build.log`.

The first build takes a couple of minutes (mostly checks); later builds take seconds.

### Script parameters

| Parameter | Default | Meaning |
|---|---|---|
| `-ProjRoot` | the repository root (the parent of `build\`) | Project directory to build |
| `-BuildRoot` | `C:\AndroidBuild` | Where the JDK, build-tools and platform live |
| `-StageRoot` | `%TEMP%\smart-dashboard-build` | ASCII directory for the temporary copy of the sources and build artifacts. Must not be inside the project; use it when `TEMP` contains non-ASCII characters |
| `-Adb` | empty — search `PATH` and `C:\platform-tools\adb.exe` | Path to `adb.exe`; only needed with `-Install` |
| `-Install` | off | Install the built APK on a connected tablet over ADB |

Examples:

```powershell
# Build and install in one step
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Install

# Custom toolchain location, explicit ASCII staging and a non-default adb path
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -BuildRoot D:\AndroidBuild -StageRoot C:\temp\sd-build -Adb D:\platform-tools\adb.exe -Install
```

The version in this repository is **1.3** (`versionCode 4`). If you want your own version number, change `android:versionCode` / `android:versionName` in [`app/AndroidManifest.xml`](../../app/AndroidManifest.xml) and the version constants at the top of the build script.

## Install the APK over ADB

The script can do it for you:

```powershell
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Install
```

Or install manually:

```bat
adb install -r apk\SmartDashboard-1.3.apk
adb shell pm grant ru.home.dashboard android.permission.READ_CALENDAR
adb shell am start -n ru.home.dashboard/.MainActivity
```

Useful related commands:

```bat
:: Doze whitelist (Huawei/EMUI may otherwise throttle alarms)
adb shell dumpsys deviceidle whitelist +ru.home.dashboard

:: Check that the app is running
adb shell ps | findstr ru.home.dashboard

:: Watch the log while the app runs
adb logcat -s SmartDashboard
```

A ready-made helper script for installation, permissions and tuning is [scripts/install_deploy.ps1](../../scripts/install_deploy.ps1) (documented in [INSTALL.md](INSTALL.md)).

## The signing key (read this)

The first build **creates the signing key for you**:

| File | What it is |
|---|---|
| `build/keystore.jks` | Your private signing key |
| `build/keystore.properties` | The random passwords and alias for that key |

**Both files are generated locally on the first build and never committed.** They are excluded by [.gitignore](../../.gitignore) (`*.jks`, `build/keystore.properties`, `*.idsig`).

**Keep both files.** Android only installs an update over an installed app when the new APK is signed with the **same key**. If you lose `keystore.jks` or `keystore.properties`, you will not be able to update an already-installed app — you will have to uninstall it (losing the app's settings: custom packing items, weather cache) and install the new build from scratch.

- If the keystore exists but `keystore.properties` is missing, the script stops with a clear error instead of generating a new key (that would silently break future updates).
- If `keystore.jks` is missing but `keystore.properties` exists, the script creates a new key using the saved password.
- To build an installable update after moving the project to another computer, copy **both** files along with the sources.
- Do not publish these files, do not add them to git, and do not share the password.

The APK attached to this repository's releases is signed with the **author's** key. A build you make yourself is signed with **your** key, so it will not install over the published APK — uninstall the published version first. See [FAQ.md](FAQ.md#why-do-i-get-app-not-installed-or-a-signature-conflict-when-updating).

## Build outputs

| Path | What |
|---|---|
| `apk/SmartDashboard-1.3.apk` | The signed, ready-to-install APK |
| `logs/build.log` | Full build log (git-ignored) |
| `%TEMP%\smart-dashboard-build` | Temporary staging directory (safe to delete; recreated on the next build) |

Check the signature yourself at any time:

```bat
C:\AndroidBuild\buildtools\build-tools\34.0.0\apksigner.bat verify --print-certs apk\SmartDashboard-1.3.apk
```

(The exact `build-tools` subdirectory depends on how the archive was unpacked; `apksigner` also works from `PATH` if build-tools is installed there.)

## Clean rebuild

If a build behaves strangely, remove the temporary files and build again:

```powershell
Remove-Item -Recurse -Force "$env:TEMP\smart-dashboard-build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .\logs -ErrorAction SilentlyContinue
powershell -ExecutionPolicy Bypass -File .\build\build.ps1
```

**Do not delete** `build\keystore.jks` and `build\keystore.properties` — that is your signing key. The staging directory can be deleted at any time; it is recreated automatically.

## Common errors

| Error / symptom | Cause | Fix |
|---|---|---|
| The script says a tool is missing (`aapt2`, `d8`, `apksigner`, `javac`, `keytool` or `android.jar`) | The build environment is not installed or `-BuildRoot` points to the wrong place | Run `scripts\setup_build_env.ps1` and check the `[OK]` lines it prints |
| `StageRoot contains non-ASCII characters` (Russian: «содержит не-ASCII символы») | `%TEMP%` or `-StageRoot` is a non-ASCII path (e.g. a Cyrillic Windows user name) | Pass an ASCII staging path: `-StageRoot C:\temp\sd-build` |
| `StageRoot must not be inside the project` | The staging directory points inside the repository | Choose a directory outside the project, e.g. `C:\temp\sd-build` |
| `aapt2` fails with a path or daemon error | `-BuildRoot` or `-StageRoot` contains non-ASCII characters | Keep both in ASCII paths; the default `C:\AndroidBuild` and `%TEMP%` (when ASCII) are safe |
| `javac: invalid target release` or a Java version error | An old JDK is used instead of the downloaded JDK 17 | Run the build through the script (it uses the JDK from `BuildRoot`); remove an old `JAVA_HOME` from the environment or pass the correct `-BuildRoot` |
| Download fails during `setup_build_env.ps1` | No internet, a proxy or a temporary outage | Check the connection and run it again; configure a corporate proxy in Windows if needed |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed app is signed with a different key | Uninstall the old version, then install; keep the same `keystore.jks` for future updates |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The new `versionCode` is lower than the installed one | Build with a higher `versionCode`, or uninstall the newer version first |
| `INSTALL_FAILED_OLDER_SDK` | The device runs Android older than 7.0 | Not supported: `minSdkVersion` is 24 |
| `adb: no devices/emulators found` | The tablet is not connected or the USB driver is missing | Try another cable/port, enable USB debugging, check `adb devices` |
| `adb: device unauthorized` | The debugging authorization was not confirmed on the tablet | Unlock the tablet and tap **Allow** in the debugging dialog |
| `adb: command not found` | `adb` is not in `PATH` and platform-tools are not in `C:\platform-tools` | Pass `-Adb C:\path\to\adb.exe`, or install Android platform-tools |
| `... cannot be loaded because running scripts is disabled` | PowerShell execution policy | Run with `powershell -ExecutionPolicy Bypass -File ...` |
| `PowerShell 5.1 or newer is required` | An old PowerShell version | Update Windows Management Framework or use Windows 10/11 |
| The APK builds but the app shows no weather | The device has no internet, or wttr.in is unreachable from that network | See [FAQ.md](FAQ.md#why-is-there-no-weather) |
| The APK builds but the calendar is empty | Permission not granted or no calendar account on the device | See [FAQ.md](FAQ.md#why-does-the-right-half-show-no-events) |

If the build fails for a reason not listed here, read `logs\build.log` — it contains the exact command line and the full output of the failing tool.

## Manual commands (if you prefer)

The build script is a thin wrapper around standard Android tools. The essential steps, for reference:

```bat
:: 1. Compile resources
aapt2 compile --dir app\res -o work\res.zip

:: 2. Link resources and the manifest, produce the base APK
aapt2 link -o work\base.apk -I android.jar --manifest app\AndroidManifest.xml work\res.zip --auto-add-overlay

:: 3. Compile Java against android.jar
javac -source 1.8 -target 1.8 -classpath android.jar -d work\classes app\src\ru\home\dashboard\*.java

:: 4. Convert to DEX
d8 --min-api 24 --lib android.jar --output work\dex work\classes\ru\home\dashboard\*.class

:: 5. Pack classes.dex into the APK, align it
zipalign -f 4 work\base.apk work\aligned.apk

:: 6. Sign and verify
apksigner sign --ks build\keystore.jks --ks-pass file:... --out apk\SmartDashboard-1.3.apk work\aligned.apk
apksigner verify apk\SmartDashboard-1.3.apk
```

Use the script for real builds: it takes care of the staging directory, the keystore properties, version naming, logs and errors.

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
