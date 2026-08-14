package com.hushwake.app.alarm;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;
import org.junit.Test;

public final class AlarmTimeSelectionTest {
    @Test
    public void quickPresetCrossesMidnightWithoutLosingMinutes() {
        AlarmTimeSelection selection =
                AlarmTimeSelection.after(LocalTime.of(23, 50, 42), 15);

        assertEquals(0, selection.hour());
        assertEquals(5, selection.minute());
        assertEquals("00:05", selection.display());
    }

    @Test
    public void pickerSelectionAlwaysUsesTwoDigitTwentyFourHourTime() {
        AlarmTimeSelection selection = AlarmTimeSelection.of(6, 7);

        assertEquals("06:07", selection.display());
    }

    @Test
    public void suggestedTimeIsOneHourAheadAndDropsSeconds() {
        AlarmTimeSelection selection =
                AlarmTimeSelection.suggested(LocalTime.of(22, 18, 59));

        assertEquals("23:18", selection.display());
    }
}
