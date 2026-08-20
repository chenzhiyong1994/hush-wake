package com.hushwake.app.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Computes the earliest strictly-future alarm occurrence in the current device time zone. */
public final class AlarmTimeCalculator {
    private AlarmTimeCalculator() {}

    public static Instant next(Alarm alarm, Instant after, ZoneId zone) {
        if (alarm == null || after == null || zone == null) {
            throw new IllegalArgumentException("Alarm, time, and zone are required");
        }
        if (alarm.isSnoozed()) {
            Instant target = Instant.ofEpochMilli(alarm.snoozeTargetEpochMs());
            if (!target.isAfter(after)) {
                throw new IllegalStateException("The snooze target has elapsed");
            }
            return target;
        }
        if (!alarm.isRepeating()) {
            Instant target =
                    Alarm.nextOneTimeInstant(
                            alarm.oneTimeEpochDay(), alarm.hour(), alarm.minute(), zone);
            if (!target.isAfter(after)) {
                throw new IllegalStateException("The one-time alarm target has elapsed");
            }
            return target;
        }
        ZonedDateTime localNow = after.atZone(zone);
        LocalDate start = localNow.toLocalDate();
        LocalTime alarmTime = LocalTime.of(alarm.hour(), alarm.minute());
        int daysToInspect = 8;
        for (int offset = 0; offset < daysToInspect; offset++) {
            LocalDate date = start.plusDays(offset);
            if (!alarm.repeatsOn(date.getDayOfWeek().getValue())) {
                continue;
            }
            Instant candidate = ZonedDateTime.of(date, alarmTime, zone).toInstant();
            if (candidate.isAfter(after)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No future occurrence found");
    }
}
