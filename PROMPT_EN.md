# AI prompt: adapt Smart Dashboard to your device

This file is for people who want to install the dashboard on **their own** tablet
without editing the code by hand. Copy the prompt from the block below into an AI
chat (ChatGPT, Claude, DeepSeek, Gemini — any assistant that can read code and
links) and fill in the angle-bracket placeholders.

## How to use

1. Open the repository: **https://github.com/Miles64girt/smart-dashboard**
   Make sure it opens without signing in.
2. Copy the **entire code block** below (use the "Copy" button in its top-right corner).
3. Replace every `<angle-bracket value>` with your own. If you do not know
   something, write "I don't know" — let the AI tell you where to look.
4. Send it to the AI. If the AI cannot browse GitHub, ask it to say so and attach
   `README.md` and `app/src/ru/home/dashboard/DeviceConfig.java` from the repo.
5. **Back up the project folder** before applying any changes.
6. Build the APK (`build/build.ps1` or per the AI's instructions), install it and
   run the test checklist. The app was verified only on the author's tablet, so
   test it on your own device.

## The prompt (copy it as a whole)

```text
You are an Android developer and an engineer who adapts existing apps to specific devices. I want to run the open-source app Smart Dashboard on my old tablet and adapt it to my device. Work carefully, make minimal changes, do not rewrite the project from scratch and do not add anything unnecessary.

REPOSITORY LINK:
https://github.com/Miles64girt/smart-dashboard

First open the link (if you have internet access) and study:
- README.md and README.en.md;
- docs/en/ADAPT.md (how to adapt) and docs/en/BUILD.md (how to build);
- app/src/ru/home/dashboard/DeviceConfig.java (all settings);
- app/AndroidManifest.xml;
- app/src/ru/home/dashboard/DashboardView.java and PackingOverlayView.java (layout);
- app/src/ru/home/dashboard/WeatherRepository.java (weather), CalendarRepository.java (calendar), MainActivity.java (screen and schedule).
If you cannot open the link, say so honestly and ask me to paste README.md and DeviceConfig.java. Do not invent code you have not seen.

PROJECT IN SHORT: a native Android app (Java, no Gradle dependencies, no AndroidX, no Google Play Services, no root). It draws a full-screen information panel on a Canvas: weather and clothing advice on the left, today's Google Calendar events on the right; at 20:00 it shows an evening packing checklist that the user fills in themselves (the list is EMPTY by default). Schedule: screen wakes at 08:00 and sleeps at 21:00. Everything is configured in a single file, DeviceConfig.java. The author verified the app only on Huawei MediaPad T3 7 (BG2-U01, Android 7.0, 600×1024 screen); it has not been tested on other devices.

MY DEVICE:
- model and SoC: <E.G. Samsung Galaxy Tab A 8.0, Snapdragon 425>
- Android version / firmware: <E.G. Android 9, One UI 1.0>
- screen resolution and density: <E.G. 1280×800, 189 dpi>
- how I want to mount the tablet: <portrait / upside-down portrait / landscape>
- RAM: <E.G. 2 GB>
- root / Google account on the device: <yes/no, which>
- my time zone: <E.G. Europe/Berlin>
- my city: <E.G. Berlin> (or "detect automatically")
- I want the screen to wake and sleep at: <E.G. 07:30 and 22:30>
- do I need silent mode: <yes/no>
- do I need the "Notebook (tomorrow's calendar subjects)" item: <yes/no>
- my packing list (optional, I will add it in the app): <...>
- my desktop OS for building: <Windows 10/11, macOS, Linux>

WHAT TO DO:
1. Check whether my device is compatible with the app: minSdk 24 (Android 7.0), 32-bit build, no native libraries, NoActionBar theme, portrait orientation, permissions (calendar, internet, boot, WAKE_LOCK, accessibility service). Say clearly what may not work.
2. Adapt app/src/ru/home/dashboard/DeviceConfig.java to my answers: TIME_ZONE, AUTO_LOCATION / LATITUDE / LONGITUDE / CITY, DAY_START_HOUR, DAY_END_HOUR, PACKING_HOUR, PACKING_SKIP_SATURDAY, MUTE_SOUND, DAY_BRIGHTNESS / NIGHT_BRIGHTNESS, DEFAULT_PACKING_ITEMS, NOTEBOOK_FROM_CALENDAR. Explain every change.
3. If my resolution/aspect ratio differs noticeably from 600×1024, adapt the layout: DashboardView.java and PackingOverlayView.java compute scale as min(width/DESIGN_WIDTH, height/DESIGN_HEIGHT) while coordinates are defined in a 600×1024 system. Propose minimal coordinate/size changes for my device and provide the modified files.
4. Packing list: do not hard-code my personal belongings into the source and never add personal data. The app's list is empty by default and I add my items via the "+ Add your own item" button — they persist on the device. If I did list items, offer both options: add them through the UI (recommended) or put them in DEFAULT_PACKING_ITEMS (optional).
5. Check and, if needed, adjust for my firmware: autostart after reboot, battery optimization / Doze exemption, background execution permission, "protected apps" (Huawei/Xiaomi/Samsung), the accessibility service for the power button, behaviour while the screen is off.
6. Do not add third-party libraries, AndroidX, Google Play Services, do not raise minSdk, do not enable root, do not add telemetry or network requests other than weather. Keep it lightweight (Canvas, no XML layouts) and keep the project's style.
7. Build: the project is built by build/build.ps1 on Windows (JDK 17 + Android build-tools 34 + platform API 24, no Gradle). Give me exact step-by-step build and adb install instructions for my OS. If I am on macOS/Linux, propose equivalent commands and clearly mark that the author did not test that path. A rebuild after the changes is mandatory.
8. Testing: make a 10–15 item checklist for my device: launch, fullscreen, permissions, weather, calendar, 08:00/21:00 mode (or mine), reboot + autostart, offline, screen flip, evening list, success animation, power button, memory/battery, screen burn-in. Explain how to roll back the changes and uninstall the app.

CONSTRAINTS AND HONESTY:
- The app was verified by the author ONLY on one tablet (Huawei MediaPad T3 7, BG2-U01, Android 7.0, 600×1024). Do not promise that it will "definitely work" on my device; warn about risks and things to test.
- Do not make up facts about the code or APIs: if you cannot see something, ask for the file or clarify.
- Do not change the MIT license and do not remove the author credit.
- Do not suggest root, bootloader unlocking, custom firmware, shady APK patching or apps from untrusted sources.
- If my tablet does not support portrait orientation or has an unusual screen, propose an honest solution instead of promising a perfect fit.

ANSWER FORMAT:
1) Short verdict: is my device compatible and what will have to change.
2) Table: parameter → before → after → why.
3) Complete modified files (or a unified diff) — only the files actually touched, with repo-relative paths.
4) Step-by-step build and install instructions for my OS (with commands).
5) Test checklist and rollback plan.
6) Risks and what may not work — be honest.
```

## If the AI cannot open links

Attach these files from the repository:
`README.md`, `app/src/ru/home/dashboard/DeviceConfig.java`,
`app/src/ru/home/dashboard/DashboardView.java`,
`app/AndroidManifest.xml`. That is enough to start the adaptation.

## What to check in the AI's answer

- It did not invent files or classes that do not exist in the repository.
- Its promises match the actual code (e.g. do not accept "let's add widgets" if there are none).
- The changes are minimal and do not break the no-root / no-Google-Play-Services design.
- It includes build instructions and a test checklist.
- It does not suggest root, custom firmware or shady APKs.

> ⚠️ An AI answer is a draft. Test it on your own device. The author does not
> guarantee that the app works on other models: it was verified only on
> Huawei MediaPad T3 7 (BG2-U01).
