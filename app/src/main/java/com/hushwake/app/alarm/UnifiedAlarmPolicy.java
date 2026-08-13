package com.hushwake.app.alarm;

/** Product-wide alarm behavior that is intentionally not exposed as per-alarm settings. */
public final class UnifiedAlarmPolicy {
    public static final int APP_GAIN_PERCENT = 100;
    public static final int FADE_IN_SECONDS = 15;
    public static final boolean VIBRATE_WHEN_BLOCKED = true;
    public static final int SNOOZE_MINUTES = 5;
    public static final int MAX_RING_SECONDS = 120;

    private UnifiedAlarmPolicy() {}
}
