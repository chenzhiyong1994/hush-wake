package com.hushwake.app.audio;

/**
 * Tracks the interval between an unsafe route signal and the first application-level mute.
 * A route callback that is subsequently verified as safe must not be reported as a mute event.
 */
final class MuteLatencyTracker {
    private long routeSignalNanos = -1L;

    void onRouteSignal(long nowNanos) {
        routeSignalNanos = nowNanos;
    }

    void onRouteVerifiedSafe() {
        routeSignalNanos = -1L;
    }

    long consumeOnMute(long nowNanos) {
        if (routeSignalNanos < 0L) {
            return -1L;
        }
        long latencyMs = Math.max(0L, (nowNanos - routeSignalNanos) / 1_000_000L);
        routeSignalNanos = -1L;
        return latencyMs;
    }
}
