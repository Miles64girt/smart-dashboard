package ru.home.dashboard;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_CALENDAR = 42;
    private static final long TICK_MS = 30000L;
    private static final long WEATHER_MAX_AGE = 30L * 60000L;
    private static final long CALENDAR_MAX_AGE = 5L * 60000L;
    private static final long MANUAL_DAY_OVERRIDE = 5L * 60000L;

    private DashboardView view;
    private Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile boolean weatherLoading = false;
    private volatile boolean calendarLoading = false;
    private long nextWeatherTry = 0L;
    private int weatherFailures = 0;
    private long manualDayUntil = 0L;
    private boolean demoMode = false;
    private boolean offlineTest = false;
    private long weatherMaxAgeOverride = 0L;
    private OrientationWatcher orientationWatcher;
    private int forcedFlip = -1;
    private android.widget.FrameLayout rootView;
    private PackingOverlayView overlay;
    private PackingListModel overlayModel;
    private int subjectOffset = 1;
    private int fakeWeekday = -1;
    private BroadcastReceiver netReceiver;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            view.setNow(now);
            applyTimeMode();
            long maxAge = weatherMaxAgeOverride > 0 ? weatherMaxAgeOverride : WEATHER_MAX_AGE;
            if (now - prefs.weatherTime() > maxAge) refreshWeather(false);
            if (now - prefs.calendarTime() > CALENDAR_MAX_AGE) refreshCalendar(false);
            main.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = new DashboardView(this);
        view.setCity(prefs.city());

        rootView = new android.widget.FrameLayout(this);
        rootView.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new PackingOverlayView(this);
        overlay.setVisibility(View.GONE);
        rootView.addView(overlay, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(rootView);

        view.setListener(new DashboardView.Listener() {
            @Override
            public void onListRequested() {
                showPackingOverlay(true);
            }

            @Override
            public void onPowerRequested() {
                handlePowerButton();
            }
        });

        overlay.setListener(new PackingOverlayView.Listener() {
            @Override
            public void onClosed() {
                hidePackingOverlay();
            }

            @Override
            public void onAddCustomRequested() {
                showAddCustomDialog();
            }

            @Override
            public void onAllChecked() {
                if (overlayModel != null) overlayModel.markDone(true);
                hidePackingOverlay();
            }
        });

        applySilentMode();

        // Переворот планшета определяем сами: прошивка игнорирует sensorPortrait.
        boolean storedFlip = prefs.isUpsideDown();
        view.setFlipped(storedFlip);
        orientationWatcher = new OrientationWatcher(this, new OrientationWatcher.Callback() {
            @Override
            public void onUpsideDownChanged(boolean upsideDown) {
                prefs.setUpsideDown(upsideDown);
                view.setFlipped(upsideDown);
                if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
                    overlay.setFlipped(upsideDown);
                }
            }
        });
        orientationWatcher.applyImmediately(storedFlip);

        setupFullscreen();
        Scheduler.scheduleAll(this);
        loadCachedWeather();

        if (!CalendarRepository.hasPermission(this)) {
            requestPermissions(new String[] { Manifest.permission.READ_CALENDAR }, REQ_CALENDAR);
        }

        registerNetworkReceiver();
        handleIntent(getIntent());
    }

    private void setupFullscreen() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupFullscreen();
        if (view != null) view.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) setupFullscreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if ("boot".equals(intent.getStringExtra("start_reason"))) {
            wakeScreenUp();
        }
        String force = intent.getStringExtra("force_mode");
        if ("day".equals(force)) {
            manualDayUntil = System.currentTimeMillis() + MANUAL_DAY_OVERRIDE;
            wakeScreenUp();
            setDayMode(true);
        } else if ("night".equals(force)) {
            manualDayUntil = 0L;
            setDayMode(false);
        }

        // --- тестовые сценарии (используются при приёмке, в обычной работе не активны) ---
        int demo = intent.getIntExtra("demo_events", 0);
        if (demo > 0) {
            demoMode = true;
            java.util.List<EventItem> list = new java.util.ArrayList<EventItem>();
            java.util.Calendar dcal = java.util.Calendar.getInstance(TimeUtil.ZONE);
            dcal.set(java.util.Calendar.HOUR_OF_DAY, 9);
            dcal.set(java.util.Calendar.MINUTE, 0);
            dcal.set(java.util.Calendar.SECOND, 0);
            dcal.set(java.util.Calendar.MILLISECOND, 0);
            long base = dcal.getTimeInMillis();
            for (int i = 0; i < demo; i++) {
                long b = base + i * 45L * 60000L;
                list.add(new EventItem("Событие " + (i + 1) + ": тестовая встреча",
                        "Проверка компактного отображения", b, b + 2700000L, false));
            }
            view.setEvents(list, true);
        }
        if (intent.getBooleanExtra("demo_empty", false)) {
            demoMode = true;
            view.setEvents(new java.util.ArrayList<EventItem>(), true);
        }
        if (intent.getBooleanExtra("offline", false)) {
            offlineTest = true;
        }
        if (intent.getBooleanExtra("fast_refresh", false)) {
            weatherMaxAgeOverride = 60000L;   // тест автообновления: раз в минуту
        }
        if (intent.getBooleanExtra("sensor_log", false) && orientationWatcher != null) {
            orientationWatcher.setVerbose(true);
        }
        if (intent.hasExtra("flip")) {
            forcedFlip = intent.getIntExtra("flip", 0);
            if (orientationWatcher != null) orientationWatcher.stop();
            view.setFlipped(forcedFlip == 1);
            prefs.setUpsideDown(forcedFlip == 1);
        }

        subjectOffset = intent.getIntExtra("subject_offset", 1);
        fakeWeekday = intent.getIntExtra("fake_weekday", -1);

        if (intent.getBooleanExtra("manual_list", false)) {
            showPackingOverlay(true);
        }
        if (intent.getBooleanExtra("scheduled_packing", false)) {
            maybeShowScheduledPacking();
        }
        String addCustom = intent.getStringExtra("add_custom");
        if (addCustom != null) {
            showPackingOverlay(true);
            if (overlayModel != null) {
                overlayModel.addCustom(addCustom);
                overlay.setModel(overlayModel);
            }
        }
        if (intent.getBooleanExtra("check_all", false)) {
            showPackingOverlay(true);
            if (overlayModel != null) {
                for (PackingItem item : overlayModel.items()) {
                    if (!item.checked) overlayModel.toggle(item.id);
                }
                overlay.setModel(overlayModel);
                overlay.showSuccess();
            }
        }
        int testAlarm = intent.getIntExtra("test_alarm", 0);
        if (testAlarm > 0) {
            Scheduler.scheduleTest(this, testAlarm, "day");
            android.util.Log.i("SmartDashboard", "test alarm scheduled in " + testAlarm + "s");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (forcedFlip < 0 && orientationWatcher != null) orientationWatcher.start();
        applyTimeMode();
        main.removeCallbacks(tick);
        main.post(tick);
        refreshWeather(false);
        refreshCalendar(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        if (orientationWatcher != null) orientationWatcher.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(tick);
        try {
            if (netReceiver != null) unregisterReceiver(netReceiver);
        } catch (Exception ignored) {
        }
        io.shutdownNow();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        long now = System.currentTimeMillis();
        if (now < manualDayUntil) return;
        manualDayUntil = now + MANUAL_DAY_OVERRIDE;
        setDayMode(true);
    }

    // ------------------------------------------------------- вечерний список

    private void applySilentMode() {
        if (!DeviceConfig.MUTE_SOUND) return;
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            try { am.setRingerMode(android.media.AudioManager.RINGER_MODE_SILENT); } catch (Exception ignored) {}
            int[] streams = new int[] {
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.STREAM_RING,
                    android.media.AudioManager.STREAM_ALARM,
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.STREAM_SYSTEM
            };
            for (int stream : streams) {
                try { am.setStreamVolume(stream, 0, 0); } catch (Exception ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private void showPackingOverlay(boolean manual) {
        if (overlay == null) return;
        if (overlay.getVisibility() == View.VISIBLE) return;
        overlayModel = PackingListModel.build(this, subjectOffset);
        overlay.setModel(overlayModel);
        try {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w > 0 && h > 0) {
                android.graphics.Bitmap snap = android.graphics.Bitmap.createBitmap(
                        w, h, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cc = new android.graphics.Canvas(snap);
                view.drawSnapshot(cc);
                android.graphics.Bitmap blur = BlurUtil.blur(this, snap, 18f);
                overlay.setBackgroundBitmap(blur);
                snap.recycle();
            }
        } catch (Throwable ignored) {
        }
        overlay.setFlipped(view.isFlipped());
        overlay.setVisibility(View.VISIBLE);
        overlay.bringToFront();
        overlay.requestFocus();
        if (!manual) overlayModel.markShown();
        android.util.Log.i("SmartDashboard", "packing overlay shown manual=" + manual
                + " items=" + overlayModel.items().size());
    }

    private void hidePackingOverlay() {
        if (overlay != null) overlay.setVisibility(View.GONE);
    }

    private void maybeShowScheduledPacking() {
        int weekday = fakeWeekday > 0 ? fakeWeekday
                : Calendar.getInstance(TimeUtil.ZONE).get(Calendar.DAY_OF_WEEK);
        if (DeviceConfig.PACKING_SKIP_SATURDAY && weekday == Calendar.SATURDAY) {
            android.util.Log.i("SmartDashboard", "packing skipped: Saturday");
            return;
        }
        PackingListModel model = PackingListModel.build(this, subjectOffset);
        if (model.isDone()) {
            android.util.Log.i("SmartDashboard", "packing skipped: already done today");
            return;
        }
        if (fakeWeekday < 0 && model.wasShown()) {
            android.util.Log.i("SmartDashboard", "packing skipped: already shown today");
            return;
        }
        showPackingOverlay(false);
        wakeScreenUp();
    }

    private void showAddCustomDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Например: ключи");
        input.setSingleLine(true);
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Добавить свой пункт")
                .setView(input)
                .setPositiveButton("Добавить", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (overlayModel != null) {
                            overlayModel.addCustom(input.getText().toString());
                            overlay.setModel(overlayModel);
                        }
                    }
                })
                .setNegativeButton("Отмена", null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                android.view.Window w = dialog.getWindow();
                if (w != null) {
                    w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                    w.getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
        });
        dialog.show();
    }

    private void handlePowerButton() {
        android.util.Log.i("SmartDashboard", "power button, serviceReady=" + PowerAccessibilityService.isReady());
        if (PowerAccessibilityService.showPowerDialog()) return;
        android.widget.Toast.makeText(this,
                "Включите службу Smart Dashboard в Настройки → Специальные возможности",
                android.widget.Toast.LENGTH_LONG).show();
        try {
            startActivity(new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        if (overlay != null && overlay.getVisibility() == View.VISIBLE) {
            hidePackingOverlay();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------- режимы

    /** Принудительно зажечь экран (утренний старт, загрузка). */
    private void wakeScreenUp() {
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) {
        }
        try {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                @SuppressWarnings("deprecation")
                android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                        android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                                | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                                | android.os.PowerManager.ON_AFTER_RELEASE,
                        "dashboard:activityWake");
                wl.acquire(10000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void applyTimeMode() {
        if (System.currentTimeMillis() < manualDayUntil) return;
        int hour = Calendar.getInstance(TimeUtil.ZONE).get(Calendar.HOUR_OF_DAY);
        boolean day = hour >= DeviceConfig.DAY_START_HOUR && hour < DeviceConfig.DAY_END_HOUR;
        setDayMode(day);
    }

    private void setDayMode(boolean day) {
        view.setNight(!day);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (day) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            lp.screenBrightness = DeviceConfig.DAY_BRIGHTNESS;
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            lp.screenBrightness = DeviceConfig.NIGHT_BRIGHTNESS;
        }
        getWindow().setAttributes(lp);
        prefs.setNight(!day);
    }

    // -------------------------------------------------------------- данные

    private void loadCachedWeather() {
        String json = prefs.weatherJson();
        if (json == null) return;
        WeatherData d = WeatherData.fromStorageJson(json);
        if (d != null) {
            if (d.city != null && d.city.trim().length() > 0) view.setCity(d.city.trim());
            view.setWeather(d, prefs.weatherTime());
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    private void refreshWeather(boolean force) {
        if (weatherLoading) return;
        if (offlineTest) {
            offlineTest = false;
            view.setOffline(prefs.weatherTime() > 0);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now < nextWeatherTry) return;
        if (!isOnline()) {
            view.setOffline(prefs.weatherTime() > 0);
            nextWeatherTry = now + 2L * 60000L;
            return;
        }
        weatherLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final WeatherData d = WeatherRepository.fetch(prefs);
                    prefs.saveWeather(d.toStorageJson(), d.fetchedAt);
                    weatherFailures = 0;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (d.city != null && d.city.trim().length() > 0) {
                                prefs.setCity(d.city);
                                view.setCity(d.city.trim());
                            } else if (!DeviceConfig.AUTO_LOCATION) {
                                view.setCity(DeviceConfig.CITY);
                            }
                            view.setWeather(d, d.fetchedAt);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("SmartDashboard", "weather fetch failed: " + e, e);
                    weatherFailures++;
                    long delay = 60000L * (1L << Math.min(weatherFailures, 5));
                    if (delay > 30L * 60000L) delay = 30L * 60000L;
                    nextWeatherTry = System.currentTimeMillis() + delay;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setOffline(prefs.weatherTime() > 0);
                        }
                    });
                } finally {
                    weatherLoading = false;
                }
            }
        });
    }

    private void refreshCalendar(boolean force) {
        if (calendarLoading) return;
        if (demoMode) return;
        if (!CalendarRepository.hasPermission(this)) {
            view.setEvents(null, false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - prefs.calendarTime() < CALENDAR_MAX_AGE) return;
        calendarLoading = true;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<EventItem> list = CalendarRepository.loadToday(MainActivity.this);
                    prefs.saveCalendarTime(System.currentTimeMillis());
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setEvents(list, true);
                        }
                    });
                } catch (Exception ignored) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            view.setEvents(null, false);
                        }
                    });
                } finally {
                    calendarLoading = false;
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALENDAR) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                refreshCalendar(true);
            }
        }
    }

    private void registerNetworkReceiver() {
        netReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                nextWeatherTry = 0L;
                refreshWeather(true);
                refreshCalendar(true);
            }
        };
        try {
            registerReceiver(netReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception ignored) {
        }
    }
}
