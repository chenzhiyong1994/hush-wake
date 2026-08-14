package com.hushwake.app.alarm;

import java.time.LocalTime;
import java.util.Locale;

/** User-facing 24-hour time selection shared by the alarm editor and quick presets. */
public record AlarmTimeSelection(int hour, int minute) {
    public AlarmTimeSelection {
        LocalTime.of(hour, minute);
    }

    public static AlarmTimeSelection of(int hour, int minute) {
        return new AlarmTimeSelection(hour, minute);
    }

    public static AlarmTimeSelection suggested(LocalTime now) {
        return from(now.plusHours(1));
    }

    public static AlarmTimeSelection after(LocalTime now, int minutes) {
        if (minutes <= 0) throw new IllegalArgumentException("Minutes must be positive");
        return from(now.plusMinutes(minutes));
    }

    private static AlarmTimeSelection from(LocalTime time) {
        return new AlarmTimeSelection(time.getHour(), time.getMinute());
    }

    public String display() {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }
}
