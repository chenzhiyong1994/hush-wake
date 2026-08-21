package com.hushwake.app.alarm;

import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Selects the single focus alarm and removes it from the secondary alarm list. */
public final class AlarmHomeLayoutPolicy {
    public record Layout(
            Alarm focus, Instant focusAt, boolean focusRinging, List<Alarm> others) {
        public Layout {
            others = List.copyOf(others);
        }
    }

    private AlarmHomeLayoutPolicy() {}

    public static Layout arrange(
            List<Alarm> alarms,
            long activeAlarmId,
            String activeState,
            Instant now,
            ZoneId zone) {
        if (alarms == null || now == null || zone == null) {
            throw new IllegalArgumentException("Alarms, time, and zone are required");
        }
        if (isRinging(activeAlarmId, activeState)) {
            for (Alarm alarm : alarms) {
                if (alarm.id() == activeAlarmId) {
                    return new Layout(
                            alarm,
                            activeOccurrenceAt(alarm, now, zone),
                            true,
                            without(alarms, alarm.id()));
                }
            }
        }
        Alarm focus = null;
        Instant focusAt = null;
        for (Alarm alarm : alarms) {
            if (!alarm.enabled()) continue;
            Instant candidate;
            try {
                candidate = AlarmTimeCalculator.next(alarm, now, zone);
            } catch (IllegalStateException elapsed) {
                continue;
            }
            if (focusAt == null || candidate.isBefore(focusAt)) {
                focus = alarm;
                focusAt = candidate;
            }
        }
        return new Layout(
                focus,
                focusAt,
                false,
                focus == null ? alarms : without(alarms, focus.id()));
    }

    private static boolean isRinging(long alarmId, String state) {
        return alarmId > 0L
                && state != null
                && !state.isBlank()
                && !"IDLE".equals(state)
                && !"STOPPED".equals(state);
    }

    private static Instant activeOccurrenceAt(Alarm alarm, Instant now, ZoneId zone) {
        if (!alarm.isRepeating() && alarm.oneTimeEpochDay() != Long.MIN_VALUE) {
            return Alarm.nextOneTimeInstant(
                    alarm.oneTimeEpochDay(), alarm.hour(), alarm.minute(), zone);
        }
        ZonedDateTime localNow = now.atZone(zone);
        ZonedDateTime occurrence =
                ZonedDateTime.of(
                        localNow.toLocalDate(),
                        java.time.LocalTime.of(alarm.hour(), alarm.minute()),
                        zone);
        return occurrence.isAfter(localNow)
                ? occurrence.minusDays(1).toInstant()
                : occurrence.toInstant();
    }

    private static List<Alarm> without(List<Alarm> alarms, long excludedId) {
        List<Alarm> others = new ArrayList<>();
        for (Alarm alarm : alarms) {
            if (alarm.id() != excludedId) others.add(alarm);
        }
        return others;
    }
}
