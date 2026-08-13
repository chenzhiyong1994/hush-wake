package com.hushwake.app.noise;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NoiseTimerPresentationTest {
    @Test
    public void activeSessionShowsOneLiveRemainingTimeInsteadOfTheOriginalPreset() {
        NoiseTimerPresentation.ViewState state =
                NoiseTimerPresentation.resolve(
                        "playing", 1_000_000L + 17L * 60_000L + 1L, 1_000_000L, 30, 15);

        assertTrue(state.active());
        assertEquals("约 18 分钟后结束", state.remainingLabel());
        assertEquals("15 秒渐隐", state.fadeLabel());
        assertFalse(state.showNextSessionSettings());
    }

    @Test
    public void stoppedSessionShowsTheSavedSettingsWithoutPretendingTheyAreRemainingTime() {
        NoiseTimerPresentation.ViewState state =
                NoiseTimerPresentation.resolve("stopped", 0L, 1_000_000L, 30, 15);

        assertFalse(state.active());
        assertEquals("尚未开始", state.remainingLabel());
        assertTrue(state.showNextSessionSettings());
    }

    @Test
    public void continuousPlaybackNamesItsEightHourSafetyLimit() {
        NoiseTimerPresentation.ViewState state =
                NoiseTimerPresentation.resolve("stopped", 0L, 1_000_000L, 0, 0);

        assertEquals("持续播放（最长 8 小时）", state.savedTimerLabel());
    }
}
