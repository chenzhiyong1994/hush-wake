package com.hushwake.app.noise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SleepSoundSwitchPolicyTest {
    @Test
    public void playingSessionSwitchesImmediatelyWithoutResettingItsTimer() {
        SleepSoundSwitchPolicy.Decision decision =
                SleepSoundSwitchPolicy.decide(
                        "rain", "stream", false, 987_654_321L, 15);

        assertTrue(decision.changed());
        assertTrue(decision.restartPlaybackNow());
        assertEquals("stream", decision.soundId());
        assertEquals(987_654_321L, decision.endsAtEpochMs());
        assertEquals(15, decision.fadeSeconds());
    }

    @Test
    public void pausedSessionKeepsPausedAndUsesNewSoundOnResume() {
        SleepSoundSwitchPolicy.Decision decision =
                SleepSoundSwitchPolicy.decide(
                        "rain", "fireplace", true, 123_456L, 30);

        assertTrue(decision.changed());
        assertFalse(decision.restartPlaybackNow());
        assertTrue(decision.paused());
        assertEquals("fireplace", decision.soundId());
    }

    @Test
    public void selectingCurrentSoundDoesNotRestartPlayback() {
        SleepSoundSwitchPolicy.Decision decision =
                SleepSoundSwitchPolicy.decide("rain", "rain", false, 321L, 5);

        assertFalse(decision.changed());
        assertFalse(decision.restartPlaybackNow());
    }
}
