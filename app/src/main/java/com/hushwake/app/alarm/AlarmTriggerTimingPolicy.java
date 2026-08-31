package com.hushwake.app.alarm;

/** Classifies an alarm broadcast against its registered wall-clock trigger. */
public final class AlarmTriggerTimingPolicy {
    public enum Decision {
        RING,
        RESCHEDULE_EARLY,
        INVALID,
        MISSED
    }

    private static final long MISSED_WINDOW_MS = 5L * 60L * 1_000L;

    private AlarmTriggerTimingPolicy() {}

    public static Decision evaluate(long scheduledAtEpochMs, long receivedAtEpochMs) {
        if (scheduledAtEpochMs <= 0L) return Decision.INVALID;
        if (receivedAtEpochMs < scheduledAtEpochMs) return Decision.RESCHEDULE_EARLY;
        long lateness = receivedAtEpochMs - scheduledAtEpochMs;
        return lateness > MISSED_WINDOW_MS ? Decision.MISSED : Decision.RING;
    }
}
