package com.hushwake.app.audio;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PrivatePlaybackConfigTest {
    @Test
    public void alarmPlaybackIsContinuousUntilTheSessionStops() {
        PrivatePlaybackEngine.Config config =
                new PrivatePlaybackEngine.Config(
                        PrivatePlaybackEngine.Purpose.ALARM, "soft_chime", 100, 15);

        assertTrue(config.continuous());
    }
}
