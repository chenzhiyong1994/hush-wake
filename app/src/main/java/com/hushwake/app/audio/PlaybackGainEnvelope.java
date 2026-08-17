package com.hushwake.app.audio;

/** Gain curve applied only after the output route has been verified as safe. */
public final class PlaybackGainEnvelope {
    private static final float INITIAL_ALARM_FRACTION = 0.25f;

    private PlaybackGainEnvelope() {}

    public static float alarmGain(
            int targetPercent, int fadeInSeconds, long elapsedMs) {
        float target = Math.max(0, Math.min(100, targetPercent)) / 100f;
        if (fadeInSeconds <= 0) return target;
        float progress =
                Math.min(1f, Math.max(0L, elapsedMs) / (fadeInSeconds * 1_000f));
        return target * (INITIAL_ALARM_FRACTION + (1f - INITIAL_ALARM_FRACTION) * progress);
    }
}
