package com.hushwake.app.audio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MuteLatencyTrackerTest {
    @Test
    public void safeInitialRoutingCallbackIsNotReportedAsMuteLatency() {
        MuteLatencyTracker tracker = new MuteLatencyTracker();

        tracker.onRouteSignal(1_000_000_000L);
        tracker.onRouteVerifiedSafe();

        assertEquals(-1L, tracker.consumeOnMute(11_003_000_000L));
    }

    @Test
    public void unsafeRouteSignalMeasuresUntilFirstMute() {
        MuteLatencyTracker tracker = new MuteLatencyTracker();

        tracker.onRouteSignal(2_000_000_000L);

        assertEquals(7L, tracker.consumeOnMute(2_007_900_000L));
        assertEquals(-1L, tracker.consumeOnMute(3_000_000_000L));
    }
}
