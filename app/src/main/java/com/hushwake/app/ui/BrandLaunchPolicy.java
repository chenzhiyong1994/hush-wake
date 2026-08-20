package com.hushwake.app.ui;

/** Keeps the decorative cold-start sequence away from urgent or resumed app flows. */
public final class BrandLaunchPolicy {
    private BrandLaunchPolicy() {}

    public static boolean shouldShow(
            boolean restoredActivity, boolean launcherIntent, String alarmState) {
        if (restoredActivity || !launcherIntent) return false;
        return alarmState == null
                || "IDLE".equals(alarmState)
                || "STOPPED".equals(alarmState);
    }
}
