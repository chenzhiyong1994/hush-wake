package com.hushwake.app.alarm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AlarmSoundCatalogTest {
    @Test
    public void curatedLibraryContainsSixRecordedSounds() {
        assertEquals(6, AlarmSoundCatalog.all().size());
        assertEquals("deep_bell", AlarmSoundCatalog.normalizeId("deep_bell"));
        assertEquals("wind_chimes", AlarmSoundCatalog.normalizeId("wind_chimes"));
        assertEquals("morning_birds", AlarmSoundCatalog.normalizeId("morning_birds"));
    }

    @Test
    public void legacyIdsRemainValidAfterReplacingTheSynthesizer() {
        assertEquals("soft_chime", AlarmSoundCatalog.normalizeId("soft_chime"));
        assertEquals("bright_chime", AlarmSoundCatalog.normalizeId("bright_chime"));
        assertEquals("horizon", AlarmSoundCatalog.normalizeId("horizon"));
    }
}
