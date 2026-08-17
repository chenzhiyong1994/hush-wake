package com.hushwake.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
}
