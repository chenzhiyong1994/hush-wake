package com.hushwake.app.noise;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SleepSoundCatalogTest {
    @Test
    public void curatedLibraryContainsEightRealRecordings() {
        assertEquals(8, SleepSoundCatalog.all().size());
        assertEquals("morning", SleepSoundCatalog.normalizeId("morning"));
        assertEquals("crickets", SleepSoundCatalog.normalizeId("crickets"));
        assertEquals("wind", SleepSoundCatalog.normalizeId("wind"));
        assertEquals("ocean", SleepSoundCatalog.normalizeId("ocean"));
        assertEquals("thunder", SleepSoundCatalog.normalizeId("thunder"));
    }

    @Test
    public void unknownSavedValueFallsBackToRain() {
        assertEquals("rain", SleepSoundCatalog.normalizeId("missing"));
    }
}
