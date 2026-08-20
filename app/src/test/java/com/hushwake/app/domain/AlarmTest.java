package com.hushwake.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public final class AlarmTest {
    @Test
    public void weekdayBitsUseIsoMondayThroughSunday() {
        assertEquals(1, Alarm.weekdayBit(1));
        assertEquals(64, Alarm.weekdayBit(7));
    }

    @Test
    public void invalidUserConfigurationIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Alarm(
                                0,
                                24,
                                0,
                                0,
                                "",
                                "soft_chime",
                                50,
                                15,
                                true,
                                5,
                                120,
                                true,
                                1,
                                0,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Alarm(
                                0,
                                8,
                                0,
                                0,
                                "1234567890123456789012345678901",
                                "soft_chime",
                                50,
                                15,
                                true,
                                5,
                                120,
                                true,
                                1,
                                0,
                                0));
    }

    @Test
    public void newAlarmsUseTheUnifiedSystemMediaPolicy() {
        Alarm alarm = Alarm.newDefault(7, 30, 1_700_000_000_000L);

        assertEquals(100, alarm.volumePercent());
        assertEquals(15, alarm.fadeInSeconds());
        assertTrue(alarm.vibrationEnabled());
        assertEquals(5, alarm.snoozeMinutes());
        assertEquals(120, alarm.maxRingSeconds());
    }

    @Test
    public void savingEditorChangesAlwaysEnablesTheAlarm() {
        long now = 1_700_000_000_000L;
        Alarm disabled = Alarm.newDefault(7, 30, now).withId(42L).withEnabled(false, now + 1L);

        Alarm saved =
                Alarm.savedFromEditor(
                        disabled, 8, 15, 0, "起床", "bright_chime", now + 2L);

        assertEquals(42L, saved.id());
        assertEquals(8, saved.hour());
        assertEquals(15, saved.minute());
        assertTrue(saved.enabled());
    }

    @Test
    public void snoozingChangesTheSameAlarmToAnEnabledFiveMinuteTarget() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        Instant now = ZonedDateTime.of(2026, 8, 20, 13, 6, 0, 0, zone).toInstant();
        Alarm ringing =
                Alarm.newDefault(13, 6, now.minusSeconds(60).toEpochMilli())
                        .withId(42L)
                        .withEnabled(false, now.toEpochMilli());

        Alarm snoozed = ringing.snoozedAt(now, zone);

        assertEquals(42L, snoozed.id());
        assertEquals(13, snoozed.hour());
        assertEquals(11, snoozed.minute());
        assertEquals(
                ZonedDateTime.of(2026, 8, 20, 13, 11, 0, 0, zone).toLocalDate().toEpochDay(),
                snoozed.oneTimeEpochDay());
        assertTrue(snoozed.enabled());
    }

    @Test
    public void snoozingARepeatingAlarmKeepsItsSelectedWeekdays() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        Instant now = ZonedDateTime.of(2026, 8, 20, 13, 6, 0, 0, zone).toInstant();
        int weekdays = Alarm.weekdayBit(1) | Alarm.weekdayBit(2) | Alarm.weekdayBit(3);
        Alarm repeating =
                new Alarm(
                        42L,
                        7,
                        30,
                        weekdays,
                        "工作日",
                        "soft_chime",
                        100,
                        15,
                        true,
                        5,
                        120,
                        true,
                        Long.MIN_VALUE,
                        now.minusSeconds(60).toEpochMilli(),
                        now.minusSeconds(60).toEpochMilli());

        Alarm snoozed = repeating.snoozedAt(now, zone);

        assertEquals(weekdays, snoozed.repeatMask());
        assertTrue(snoozed.enabled());
    }

    @Test
    public void completedSnoozeRestoresRepeatingSchedulingButDisablesOneTimeAlarm() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        Instant now = ZonedDateTime.of(2026, 8, 20, 13, 6, 0, 0, zone).toInstant();
        int thursday = Alarm.weekdayBit(4);
        Alarm repeating =
                new Alarm(
                                1L,
                                7,
                                30,
                                thursday,
                                "",
                                "soft_chime",
                                100,
                                15,
                                true,
                                5,
                                120,
                                true,
                                Long.MIN_VALUE,
                                1L,
                                1L)
                        .snoozedAt(now, zone);
        Alarm oneTime =
                Alarm.newDefault(13, 6, now.minusSeconds(60).toEpochMilli())
                        .withId(2L)
                        .withEnabled(false, now.toEpochMilli())
                        .snoozedAt(now, zone);

        Alarm nextRepeat = repeating.afterOccurrence(now.plusSeconds(300).toEpochMilli());
        Alarm completedOneTime = oneTime.afterOccurrence(now.plusSeconds(300).toEpochMilli());

        assertTrue(nextRepeat.enabled());
        assertFalse(nextRepeat.isSnoozed());
        assertEquals(thursday, nextRepeat.repeatMask());
        assertFalse(completedOneTime.enabled());
    }
}
