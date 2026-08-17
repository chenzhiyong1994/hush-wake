package com.hushwake.app.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlaybackGainEnvelopeTest {
    @Test
    public void alarmIsAudibleAsSoonAsItsRouteIsVerified() {
        float initial = PlaybackGainEnvelope.alarmGain(100, 15, 0L);

        assertTrue(initial > 0f);
        assertEquals(0.25f, initial, 0.001f);
    }

    @Test
    public void alarmStillReachesTheRequestedGainAfterFadeIn() {
        assertEquals(1f, PlaybackGainEnvelope.alarmGain(100, 15, 15_000L), 0.001f);
        assertEquals(0.5f, PlaybackGainEnvelope.alarmGain(50, 0, 0L), 0.001f);
    }
}
