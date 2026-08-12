package com.hushwake.app.domain;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public final class AlarmTimeCalculatorTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    public void oneTimeAlarmUsesTodayWhenTimeIsStillFuture() {
        Instant now = ZonedDateTime.of(2026, 8, 13, 8, 0, 0, 0, ZONE).toInstant();
        Alarm alarm = oneTimeAt(ZonedDateTime.of(2026, 8, 13, 9, 30, 0, 0, ZONE).toInstant());

        Instant next = AlarmTimeCalculator.next(alarm, now, ZONE);

        assertEquals(ZonedDateTime.of(2026, 8, 13, 9, 30, 0, 0, ZONE).toInstant(), next);
    }

    @Test
    public void elapsedOneTimeAlarmDoesNotRollToTomorrow() {
        Alarm alarm = oneTimeAt(ZonedDateTime.of(2026, 8, 13, 9, 30, 0, 0, ZONE).toInstant());
        Instant now = ZonedDateTime.of(2026, 8, 13, 9, 30, 0, 0, ZONE).toInstant();

        org.junit.Assert.assertThrows(
                IllegalStateException.class, () -> AlarmTimeCalculator.next(alarm, now, ZONE));
    }

    @Test
    public void weeklyAlarmSelectsEarliestEnabledWeekday() {
        int mondayAndFriday = Alarm.weekdayBit(1) | Alarm.weekdayBit(5);
        Alarm alarm = alarmAt(7, 15, mondayAndFriday);
        Instant thursday = ZonedDateTime.of(2026, 8, 13, 12, 0, 0, 0, ZONE).toInstant();

        Instant next = AlarmTimeCalculator.next(alarm, thursday, ZONE);

        assertEquals(ZonedDateTime.of(2026, 8, 14, 7, 15, 0, 0, ZONE).toInstant(), next);
    }

    @Test
    public void weeklyAlarmSkipsElapsedOccurrenceOnSameDay() {
        int thursday = Alarm.weekdayBit(4);
        Alarm alarm = alarmAt(7, 15, thursday);
        Instant now = ZonedDateTime.of(2026, 8, 13, 7, 16, 0, 0, ZONE).toInstant();

        Instant next = AlarmTimeCalculator.next(alarm, now, ZONE);

        assertEquals(ZonedDateTime.of(2026, 8, 20, 7, 15, 0, 0, ZONE).toInstant(), next);
    }

    @Test
    public void weeklyAlarmResolvesDstSpringGapToFirstValidWallTime() {
        ZoneId newYork = ZoneId.of("America/New_York");
        Alarm alarm = alarmAt(2, 30, Alarm.weekdayBit(7));
        Instant beforeGap =
                ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, newYork).toInstant();

        Instant next = AlarmTimeCalculator.next(alarm, beforeGap, newYork);

        assertEquals(
                ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork).toInstant(), next);
    }

    private static Alarm alarmAt(int hour, int minute, int repeatMask) {
        return new Alarm(
                1L,
                hour,
                minute,
                repeatMask,
                "",
                "soft_chime",
                50,
                15,
                true,
                5,
                120,
                true,
                0L,
                1L,
                1L);
    }

    private static Alarm oneTimeAt(Instant target) {
        ZonedDateTime local = target.atZone(ZONE);
        return new Alarm(
                1L,
                local.getHour(),
                local.getMinute(),
                0,
                "",
                "soft_chime",
                50,
                15,
                true,
                5,
                120,
                true,
                local.toLocalDate().toEpochDay(),
                1L,
                1L);
    }
}
