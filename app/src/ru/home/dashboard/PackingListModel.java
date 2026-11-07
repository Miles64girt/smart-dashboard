package ru.home.dashboard;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Список вещей на завтра.
 *
 * Стартовые пункты берутся из {@link DeviceConfig#DEFAULT_PACKING_ITEMS}
 * (по умолчанию пусто — каждый добавляет свои вещи сам). Пользовательские
 * пункты хранятся в SharedPreferences и переживают перезагрузку.
 *
 * Опционально (NOTEBOOK_FROM_CALENDAR) добавляется пункт «Тетрадь (предметы
 * из календаря на завтра)» — если на завтра есть события.
 */
public final class PackingListModel {

    public static final String PREFIX_CUSTOM = "custom:";
    private static final String PREFIX_DEFAULT = "default:";

    public final String dateKey;      // день, к которому относятся отметки (сегодня)
    public final int dayOffset;       // на сколько дней вперёд смотрим календарь

    private final Context context;
    private final Prefs prefs;
    private final List<PackingItem> items = new ArrayList<PackingItem>();
    private final LinkedHashSet<String> checked;

    private PackingListModel(Context context, int dayOffset) {
        this.context = context;
        this.prefs = new Prefs(context);
        this.dayOffset = dayOffset;
        this.dateKey = dateKeyForOffset(0);
        this.checked = prefs.packingChecked(dateKey);
    }

    public static PackingListModel build(Context context, int dayOffset) {
        PackingListModel model = new PackingListModel(context, dayOffset);
        model.fill();
        return model;
    }

    public static String dateKeyForOffset(int offsetDays) {
        Calendar cal = Calendar.getInstance(TimeUtil.ZONE);
        cal.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    private void fill() {
        items.clear();

        // 1. Опциональная «Тетрадь» с предметами из календаря на завтра.
        if (DeviceConfig.NOTEBOOK_FROM_CALENDAR) {
            List<String> subjects = subjectsForDay(dayOffset);
            if (!subjects.isEmpty()) {
                StringBuilder sb = new StringBuilder("Тетрадь (");
                for (int i = 0; i < subjects.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(subjects.get(i));
                }
                sb.append(")");
                add("notebook", sb.toString());
            }
        }

        // 2. Стартовые пункты из конфига (по умолчанию их нет).
        for (String label : DeviceConfig.DEFAULT_PACKING_ITEMS) {
            if (label == null) continue;
            String trimmed = label.trim();
            if (trimmed.length() == 0) continue;
            add(PREFIX_DEFAULT + trimmed, trimmed);
        }

        // 3. Свои пункты, добавленные пользователем на устройстве.
        for (String custom : prefs.customPackingItems()) {
            String id = PREFIX_CUSTOM + custom;
            items.add(new PackingItem(id, custom, true, checked.contains(id)));
        }
    }

    private void add(String id, String label) {
        items.add(new PackingItem(id, label, false, checked.contains(id)));
    }

    /** Предметы на указанный день (по календарю, в часовом поясе панели). */
    public List<String> subjectsForDay(int offsetDays) {
        Calendar cal = Calendar.getInstance(TimeUtil.ZONE);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis() + (long) offsetDays * 24L * 60L * 60L * 1000L;
        long end = start + 24L * 60L * 60L * 1000L;
        List<EventItem> events = CalendarRepository.loadRange(context, start, end);
        return extractSubjects(events);
    }

    public static List<String> extractSubjects(List<EventItem> events) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (events != null) {
            for (EventItem e : events) {
                String s = cleanSubject(e.title);
                if (s.length() > 0) result.add(s);
            }
        }
        return new ArrayList<String>(result);
    }

    /** «Математический анализ лек. 8-907 (Иванов И. И.)» -> «Математический анализ». */
    static String cleanSubject(String title) {
        if (title == null) return "";
        String s = title.replaceAll("\\([^)]*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ").trim();
        String[] tokens = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            String norm = token.toLowerCase(Locale.ROOT).replace(".", "").replace(",", "").trim();
            if (norm.equals("лек") || norm.equals("лб") || norm.equals("пр") || norm.equals("сем")
                    || norm.equals("лаб") || norm.equals("практика") || norm.equals("зачет")
                    || norm.equals("экзамен") || norm.equals("кр")) {
                continue;
            }
            if (token.matches("\\d+([-–/]\\d+)?")) continue;
            sb.append(token).append(' ');
        }
        String result = sb.toString().trim();
        result = result.replaceAll("[,;:]+$", "").trim();
        return result;
    }

    public List<PackingItem> items() {
        return items;
    }

    public boolean isChecked(String id) {
        return checked.contains(id);
    }

    /** Переключить пункт. Возвращает true, если теперь отмечено всё. */
    public boolean toggle(String id) {
        if (checked.contains(id)) checked.remove(id);
        else checked.add(id);
        prefs.setPackingChecked(dateKey, checked);
        for (PackingItem it : items) {
            it.checked = checked.contains(it.id);
        }
        boolean all = allChecked();
        prefs.setPackingDone(dateKey, all);
        return all;
    }

    public boolean allChecked() {
        if (items.isEmpty()) return false;
        for (PackingItem it : items) {
            if (!checked.contains(it.id)) return false;
        }
        return true;
    }

    public boolean isDone() {
        return prefs.isPackingDone(dateKey);
    }

    public void markDone(boolean value) {
        prefs.setPackingDone(dateKey, value);
    }

    public boolean wasShown() {
        return prefs.isPackingShown(dateKey);
    }

    public void markShown() {
        prefs.setPackingShown(dateKey, true);
    }

    public void addCustom(String label) {
        if (label == null) return;
        String trimmed = label.trim();
        if (trimmed.length() == 0) return;
        LinkedHashSet<String> custom = prefs.customPackingItems();
        custom.add(trimmed);
        prefs.setCustomPackingItems(custom);
        fill();
    }

    public void removeCustom(String label) {
        LinkedHashSet<String> custom = prefs.customPackingItems();
        custom.remove(label);
        prefs.setCustomPackingItems(custom);
        String id = PREFIX_CUSTOM + label;
        checked.remove(id);
        prefs.setPackingChecked(dateKey, checked);
        items.clear();
        fill();
    }
}
