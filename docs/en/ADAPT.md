# Adapting Smart Dashboard to your device

Smart Dashboard was made for one specific tablet. Almost everything that ties it to that tablet is collected in **one config file** — [`app/src/ru/home/dashboard/DeviceConfig.java`](../../app/src/ru/home/dashboard/DeviceConfig.java). This guide explains every field and what to check after you change it.

> ⚠️ **Tested on one tablet only.** Smart Dashboard was developed and verified on a Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. Adapting the config makes the app *fit* another device, but that does not mean it has been *tested* on it. Test it yourself; use it at your own risk. See [TESTING.md](TESTING.md).

## Contents

- [How the config works](#how-the-config-works)
- [Field reference](#field-reference)
- [Time zone](#time-zone)
- [City and coordinates](#city-and-coordinates)
- [Schedule and brightness](#schedule-and-brightness)
- [Sound](#sound)
- [Packing list](#packing-list)
- [Design size and layout](#design-size-and-layout)
- [Unusual screens](#unusual-screens)
- [Using PROMPT_EN.md with an AI assistant](#using-prompt_enmd-with-an-ai-assistant)
- [Build and verify](#build-and-verify)
- [After-adaptation checklist](#after-adaptation-checklist)

## How the config works

`DeviceConfig.java` is a plain Java file with `public static final` constants. There is no settings UI in the app: you edit the values, rebuild the APK and install it. This keeps the app tiny and avoids a configuration screen that would need its own layout and input handling.

The file is the single place for device-specific values. The rest of the code reads its constants instead of hardcoding a city, a time zone or a schedule.

Typical adaptation workflow:

1. Make a copy of the repository (or a git branch).
2. Edit `DeviceConfig.java`.
3. Build: `powershell -ExecutionPolicy Bypass -File .\build\build.ps1` (see [BUILD.md](BUILD.md)).
4. Install and test on the real device — see [After-adaptation checklist](#after-adaptation-checklist).

## Field reference

All fields are in `app/src/ru/home/dashboard/DeviceConfig.java`.

| Field | Default | Meaning |
|---|---|---|
| `TIME_ZONE` | `""` | IANA time zone for dates, event times, the schedule and day/night mode; empty means the system time zone |
| `AUTO_LOCATION` | `true` | Ask wttr.in to detect the city from the public IP address |
| `LATITUDE`, `LONGITUDE` | `55.7558`, `37.6173` | Coordinates used when `AUTO_LOCATION = false` |
| `CITY` | `"Москва"` | City label shown in the top-right corner of the left half when `AUTO_LOCATION = false` |
| `DAY_START_HOUR` | `8` | Hour when the screen wakes up and the panel switches to day mode |
| `DAY_END_HOUR` | `21` | Hour when the panel switches to night mode and stops keeping the screen on |
| `PACKING_HOUR` | `20` | Hour when the evening packing list is shown |
| `PACKING_SKIP_SATURDAY` | `true` | Do not show the evening packing list on Saturday |
| `MUTE_SOUND` | `true` | Mute the device on app start (silent ringer mode and zeroed audio streams) |
| `DAY_BRIGHTNESS` | `0.62` | Window brightness in day mode (0.0–1.0) |
| `NIGHT_BRIGHTNESS` | `0.02` | Window brightness in night mode (0.0–1.0) |
| `DEFAULT_PACKING_ITEMS` | `{}` | Default packing items; empty by design since v1.3 |
| `NOTEBOOK_FROM_CALENDAR` | `false` | Add a "Notebook (tomorrow's subjects)" item built from tomorrow's calendar events |
| `DESIGN_WIDTH` | `600` | Baseline layout width in pixels |
| `DESIGN_HEIGHT` | `1024` | Baseline layout height in pixels |

## Time zone

```java
public static final String TIME_ZONE = "";
```

- `""` means **use the system time zone**. This is the right choice when the tablet's clock and time zone are correct.
- Otherwise set an IANA name, for example `"Europe/Moscow"`, `"Europe/Berlin"` or `"Asia/Tokyo"`.
- The time zone affects: the date and weekday, all calendar event times, "today" boundaries, the 08:00/20:00/21:00 schedule, and the day/night decision.

**When to set it explicitly:** some cheap tablets have a broken or locked system time zone (it may reset to GMT after every reboot, and ADB cannot change it on a non-rooted device). If the clock in the panel is wrong by a whole number of hours while the system clock looks fine, set `TIME_ZONE` explicitly. This was exactly the situation on the reference tablet — see [LIMITATIONS.md](LIMITATIONS.md#6-wrong-time-zone-on-some-firmwares).

> The value does **not** change the device's system time zone. It only tells the app which zone to use for its own calculations.

## City and coordinates

**Option A — automatic (default):**

```java
public static final boolean AUTO_LOCATION = true;
```

The app asks wttr.in to determine the city from the public IP address and uses that location for the weather. No API key is needed, and the app sends no coordinates in this mode. The city name returned by wttr.in is displayed on the panel automatically. This is convenient, but the IP address may point at your provider's city rather than yours.

**Option B — manual:**

```java
public static final boolean AUTO_LOCATION = false;
public static final double LATITUDE  = 55.7558;
public static final double LONGITUDE = 37.6173;
public static final String CITY = "Москва";
```

- Weather is requested by coordinates (wttr.in `/{lat},{lon}?format=j1`).
- `CITY` is only the **label** drawn on the panel; it does not affect the weather request.
- Four decimal places are enough (about 10 m precision, far more than a weather forecast needs).
- Use any coordinates you like — a city centre is fine; the forecast is not that local.

## Schedule and brightness

```java
public static final int  DAY_START_HOUR   = 8;
public static final int  DAY_END_HOUR     = 21;
public static final int  PACKING_HOUR     = 20;
public static final boolean PACKING_SKIP_SATURDAY = true;
public static final float DAY_BRIGHTNESS   = 0.62f;
public static final float NIGHT_BRIGHTNESS = 0.02f;
```

How it behaves:

- At `DAY_START_HOUR` an exact alarm wakes the screen and switches the panel to day mode; the screen is kept on until `DAY_END_HOUR`.
- At `DAY_END_HOUR` the panel switches to night mode: the screen dims to `NIGHT_BRIGHTNESS`, the keep-screen-on flag is removed and the screen turns off after the system timeout.
- **Any touch at night wakes the screen for 5 minutes** and shows the day mode; then the panel returns to night mode.
- At `PACKING_HOUR` the evening packing list opens over the dashboard (unless it is Saturday and `PACKING_SKIP_SATURDAY = true`).
- Hours are full hours (no minutes field) and are interpreted in `TIME_ZONE`.
- The alarm is re-scheduled every day and after a reboot. If alarms are delayed, check the battery optimization settings — see [INSTALL.md](INSTALL.md#step-6-huawei--emui-autostart-and-battery).

Brightness values are window brightness, not system brightness: `0.0` is the darkest the panel can make the window, `1.0` is full. The reference tablet is comfortable at `0.62` during the day and `0.02` at night.

> The app does not use automatic brightness. It controls the window brightness itself, because the reference tablet has no ambient light sensor. If your tablet has one, it will not be used — set the day/night values manually.

## Sound

```java
public static final boolean MUTE_SOUND = true;
```

When enabled, the app puts the device into silent ringer mode and sets music, ring, alarm, notification and system streams to zero on every start. This is intended for a panel that should never make noise. Set it to `false` if you want the tablet to keep its normal sounds.

## Packing list

```java
public static final String[] DEFAULT_PACKING_ITEMS = {};
public static final boolean NOTEBOOK_FROM_CALENDAR = false;
```

- **Since version 1.3 the default packing list is empty.** There are no personal items in the code. Every user adds their own items in the evening window with **`+  Добавить свой пункт`**; they are stored locally on the device.
- If you want a few default items for your own build, list them in `DEFAULT_PACKING_ITEMS`, for example `{ "Keys", "Charger", "Headphones" }`. Note that the app's interface is Russian, so users of your build will see the strings exactly as written.
- **`NOTEBOOK_FROM_CALENDAR`** (off by default): adds an item like "Notebook (subject 1, subject 2)" with the subjects taken from **tomorrow's** calendar events. The code strips rooms, lesson types and brackets from event titles and removes duplicates. If there are no events tomorrow, the item is not added.

Custom items added by the user always survive an app update; they live in the app's `SharedPreferences`.

## Design size and layout

```java
public static final float DESIGN_WIDTH  = 600f;
public static final float DESIGN_HEIGHT = 1024f;
```

The whole dashboard is drawn on a `Canvas` in coordinates designed for a **600×1024** portrait screen. At runtime the drawing is scaled by:

```
scale = min(screenWidth / DESIGN_WIDTH, screenHeight / DESIGN_HEIGHT)
```

So:

- On a 600×1024 screen the scale is 1.0 — pixel-perfect.
- On a larger 1200×1920 screen the scale is 2.0 — the exact same layout, just bigger.
- On a screen with a different aspect ratio the smaller scale wins and the panel is **not stretched**: you get empty space on one axis instead of distorted text and cards.

These two constants define the coordinate system of the drawing. The layout coordinates in `DashboardView.java` and `PackingOverlayView.java` are written in these units, so **change the design size only together with the layout code** — the config file itself says the same. For a different screen, the built-in scaling is usually enough; if it is not, adapt the drawing code (see [Using PROMPT_EN.md](#using-prompt_enmd-with-an-ai-assistant)).

## Unusual screens

| Situation | What to expect | What to do |
|---|---|---|
| **Portrait, same aspect ratio (600×1024, 1200×1920, 768×1280)** | Everything fits, just scaled | Usually nothing; adjust `DAY_BRIGHTNESS` if needed |
| **Portrait, different aspect ratio (e.g. 800×1280, 1200×1600)** | Scaled by the smaller factor; empty margins on one axis | Accept it, or adapt the layout code together with `DESIGN_WIDTH` / `DESIGN_HEIGHT`; test on the device |
| **Small phone screen (e.g. 720×1280)** | Works but text becomes tiny | Not recommended. Adapting the layout code for a phone is a separate project; a tablet is a better fit |
| **Landscape / auto-rotate** | **Not supported.** The activity is locked to portrait | Mount the tablet in portrait; landscape is out of scope ([LIMITATIONS.md](LIMITATIONS.md#4-no-landscape-orientation)) |
| **Tablet mounted upside down** | Supported: the app detects the 180° flip with the accelerometer and rotates the drawing | Nothing; keep the tablet close to vertical so the accelerometer can tell up from down |
| **No ambient light sensor** | The app controls brightness itself | Set `DAY_BRIGHTNESS` / `NIGHT_BRIGHTNESS` to values that look good on your screen |
| **Wrong system time zone** | Times are off by a whole number of hours | Set `TIME_ZONE` explicitly ([Time zone](#time-zone)) |
| **High-DPI screen** | Everything scales by the smaller factor; no density-dependent breakage | Nothing; the layout is drawn in scaled pixels, not dp |

If your screen is so unusual that the built-in scaling is not enough, the next step is changing the layout code (`DashboardView.java`, `PackingOverlayView.java`) for your exact resolution. That is a code change, not a config change — use [PROMPT_EN.md](../../PROMPT_EN.md) to get started.

## Using PROMPT_EN.md with an AI assistant

[PROMPT_EN.md](../../PROMPT_EN.md) is a ready-to-copy prompt for a general-purpose AI assistant. It is written specifically for adapting this app to another device.

How to use it, as described in the prompt file itself:

1. Open the repository (**https://github.com/Miles64girt/smart-dashboard**) in a browser and make sure it opens without signing in.
2. Copy the **entire prompt code block** from [PROMPT_EN.md](../../PROMPT_EN.md).
3. Replace every `<angle-bracket value>` with facts about your device. If you do not know something, write "I don't know" — a good assistant will tell you where to look.
4. Send it to an AI assistant that can read code. If the assistant cannot browse GitHub, it should say so and ask you to attach [`README.en.md`](../../README.en.md) and [`DeviceConfig.java`](../../app/src/ru/home/dashboard/DeviceConfig.java); attach the other source files if the layout needs changing.
5. **Back up the project folder** before applying any changes.
6. Apply the changes, build the APK and run the test checklist on the real device — never trust an AI answer without testing.

Do not put personal data (real calendar events, account names, serial numbers) into the chat — use neutral examples. The prompt already explains the project, the config contract, the layout baseline and the safety rules, so the assistant does not need access to this repository.

## Build and verify

```powershell
# Build
powershell -ExecutionPolicy Bypass -File .\build\build.ps1

# Build and install on the connected tablet
powershell -ExecutionPolicy Bypass -File .\build\build.ps1 -Install
```

Full build instructions, including the signing key, are in [BUILD.md](BUILD.md).

## After-adaptation checklist

Run through this list on the target device after every adaptation:

- [ ] The app installs and opens in **full screen** (no status bar, no navigation bar).
- [ ] Both halves are visible; nothing is clipped at the edges.
- [ ] The date and weekday are correct for your time zone.
- [ ] The city label and the weather make sense for your location.
- [ ] The "what to wear" block has all five rows and they fit on the screen.
- [ ] Today's calendar events are shown with the right times; an empty day leaves the right half empty.
- [ ] The **≡** and **⏻** buttons are fully visible in the bottom-right corner and respond to taps.
- [ ] At night the screen dims, the night hint is readable, and a touch wakes the screen.
- [ ] The evening packing list opens at the configured hour (or via **≡**) and fits on the screen, including the empty state.
- [ ] Adding a custom item works, long-press deletes it, and the success animation fits.
- [ ] Rotating the tablet 180° flips the drawing correctly.
- [ ] A reboot starts the panel automatically and the schedule alarms still work.
- [ ] The app stays responsive for at least an hour; check memory usage and battery drain.

If something fails, see [FAQ.md](FAQ.md) and [LIMITATIONS.md](LIMITATIONS.md) before changing code.

---

> ⚠️ **Disclaimer.** Smart Dashboard was developed and verified on one tablet only — Huawei MediaPad T3 7 (BG2-U01), Android 7.0, 600×1024. It has not been tested on other devices. Test it on your own tablet before relying on it; use it at your own risk.
