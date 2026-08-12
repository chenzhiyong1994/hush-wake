package com.hushwake.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

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
}
