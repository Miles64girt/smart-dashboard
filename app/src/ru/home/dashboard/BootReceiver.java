package ru.home.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Автозапуск панели после включения планшета/перезагрузки и восстановление расписания. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Scheduler.scheduleAll(context);
        } catch (Exception ignored) {
        }
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra("start_reason", "boot");
            context.startActivity(i);
        } catch (Exception ignored) {
            // Если Android/Huawei не разрешил старт из ресивера — панель запустится
            // при следующем касании экрана; дополнительно можно сделать приложение
            // домашним экраном (описано в инструкции).
        }
    }
}
