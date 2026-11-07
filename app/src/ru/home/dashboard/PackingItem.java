package ru.home.dashboard;

/** Пункт вечернего списка вещей. */
public final class PackingItem {

    public final String id;
    public final String label;
    public final boolean custom;
    public boolean checked;

    public PackingItem(String id, String label, boolean custom, boolean checked) {
        this.id = id;
        this.label = label;
        this.custom = custom;
        this.checked = checked;
    }
}
