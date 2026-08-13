package com.hushwake.app.noise;

/** Preserves the active sleep session while changing its recording. */
public final class SleepSoundSwitchPolicy {
    private SleepSoundSwitchPolicy() {}

    public record Decision(
            boolean changed,
            boolean restartPlaybackNow,
            boolean paused,
            String soundId,
            long endsAtEpochMs,
            int fadeSeconds) {}

    public static Decision decide(
            String currentSoundId,
            String requestedSoundId,
            boolean paused,
            long endsAtEpochMs,
            int fadeSeconds) {
        String safeRequested = SleepSoundCatalog.normalizeId(requestedSoundId);
        boolean changed = !safeRequested.equals(SleepSoundCatalog.normalizeId(currentSoundId));
        return new Decision(
                changed,
                changed && !paused,
                paused,
                safeRequested,
                endsAtEpochMs,
                fadeSeconds);
    }
}
