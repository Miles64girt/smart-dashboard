package ru.home.dashboard;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Служба доступности нужна только для показа системного меню питания
 * (на Android 7 обычное приложение не может выключить планшет напрямую).
 * Пользователь нажимает кнопку питания в панели -> появляется штатное меню
 * Android, где остаётся выбрать «Выключить».
 */
public class PowerAccessibilityService extends AccessibilityService {

    private static volatile PowerAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // не используется
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        android.util.Log.i("SmartDashboard", "PowerAccessibilityService connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static boolean isReady() {
        return instance != null;
    }

    /** Показать системное меню питания. Возвращает false, если служба не включена. */
    public static boolean showPowerDialog() {
        PowerAccessibilityService s = instance;
        if (s == null) return false;
        try {
            return s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
        } catch (Throwable t) {
            return false;
        }
    }
}
