package ru.home.dashboard;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Определяет физический переворот планшета (портрет / перевёрнутый портрет)
 * по акселерометру. Нужен потому, что прошивка BG2-U01 игнорирует
 * screenOrientation="sensorPortrait" и не поворачивает экран сама.
 *
 * Приложение остаётся в обычной портретной ориентации, а отрисовка
 * поворачивается на 180° внутри onDraw (см. DashboardView.setFlipped).
 */
public final class OrientationWatcher implements SensorEventListener {

    public interface Callback {
        void onUpsideDownChanged(boolean upsideDown);
    }

    /** Порог по оси Y (м/с²): вертикально gravity ≈ ±9.8; порог 4.0 допускает наклон до ~66° от вертикали. */
    private static final float THRESHOLD = 4.0f;
    /** Сколько миллисекунд значение должно держаться, чтобы применить переворот. */
    private static final long HOLD_MS = 500L;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Callback callback;

    private float filteredY = 0f;
    private boolean upsideDown = false;
    private int candidate = 0;          // 0 = нормально, 1 = перевёрнут
    private long candidateSince = 0L;
    private boolean started = false;
    private boolean verbose = false;
    private long lastLog = 0L;

    public OrientationWatcher(Context context, Callback callback) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                : null;
        this.callback = callback;
    }

    public void setVerbose(boolean value) {
        verbose = value;
    }

    public void start() {
        if (sensorManager != null && accelerometer != null && !started) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            started = true;
            android.util.Log.i("SmartDashboard", "orientation sensor started");
        }
    }

    public void stop() {
        if (sensorManager != null && started) {
            sensorManager.unregisterListener(this);
            started = false;
            android.util.Log.i("SmartDashboard", "orientation sensor stopped");
        }
    }

    public boolean isUpsideDown() {
        return upsideDown;
    }

    /** Принудительно применить состояние (кэш с прошлого запуска / тест). */
    public void applyImmediately(boolean value) {
        upsideDown = value;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float y = event.values[1];
        // низкочастотный фильтр: отсекаем тряску и короткие колебания
        filteredY = filteredY * 0.75f + y * 0.25f;
        if (verbose) {
            long nowLog = SystemClock.elapsedRealtime();
            if (nowLog - lastLog > 5000L) {
                lastLog = nowLog;
                android.util.Log.i("SmartDashboard", "sensor y=" + y + " filteredY=" + filteredY
                        + " upsideDown=" + upsideDown);
            }
        }

        int want;
        if (filteredY >= THRESHOLD) {
            want = 0;              // устройство в обычном портрете
        } else if (filteredY <= -THRESHOLD) {
            want = 1;              // устройство перевёрнуто
        } else {
            return;                // лежит на столе / промежуточное — ничего не меняем
        }

        long now = SystemClock.elapsedRealtime();
        if (want != candidate) {
            candidate = want;
            candidateSince = now;
            return;
        }
        if (now - candidateSince < HOLD_MS) return;

        boolean newValue = (want == 1);
        if (newValue != upsideDown) {
            upsideDown = newValue;
            android.util.Log.i("SmartDashboard", "upsideDown=" + upsideDown
                    + " (filteredY=" + filteredY + ")");
            if (callback != null) callback.onUpsideDownChanged(upsideDown);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
