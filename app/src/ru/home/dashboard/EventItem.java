package ru.home.dashboard;

/** Одно событие календаря на сегодня. */
public final class EventItem {
    public String title;
    public String description;
    public long begin;
    public long end;
    public boolean allDay;

    public EventItem(String title, String description, long begin, long end, boolean allDay) {
        this.title = title;
        this.description = description;
        this.begin = begin;
        this.end = end;
        this.allDay = allDay;
    }
}
