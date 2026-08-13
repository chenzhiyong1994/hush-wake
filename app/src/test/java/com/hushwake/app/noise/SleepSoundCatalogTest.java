package com.hushwake.app.noise;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SleepSoundCatalogTest {
    @Test
    public void curatedLibraryContainsSixRealRecordings() {
        assertEquals(6, SleepSoundCatalog.all().size());
        assertEquals("morning", SleepSoundCatalog.normalizeId("morning"));
        assertEquals("crickets", SleepSoundCatalog.normalizeId("crickets"));
        assertEquals("wind", SleepSoundCatalog.normalizeId("wind"));
    }

    @Test
    public void unknownSavedValueFallsBackToRain() {
        assertEquals("rain", SleepSoundCatalog.normalizeId("missing"));
    }
}
