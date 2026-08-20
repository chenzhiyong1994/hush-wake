package com.hushwake.app.reliability;

/** Orders the Android capabilities required for a user-visible background alarm. */
public final class AlarmWakePermissionPolicy {
    public enum Issue {
        EXACT_ALARM,
        NOTIFICATIONS,
        FULL_SCREEN,
        BACKGROUND_RESTRICTED,
        STANDBY_RESTRICTED,
        BATTERY_OPTIMIZATION,
        OEM_AUTOSTART_UNCONFIRMED,
        NONE
    }

    private AlarmWakePermissionPolicy() {}

    public static Issue firstIssue(
            boolean exactAlarm,
            boolean notifications,
            boolean fullScreen,
            boolean backgroundAllowed,
            boolean standbyAllowed,
            boolean batteryOptimizationExempt,
            boolean oemAutostartConfirmed) {
        if (!exactAlarm) return Issue.EXACT_ALARM;
        if (!notifications) return Issue.NOTIFICATIONS;
        if (!fullScreen) return Issue.FULL_SCREEN;
        if (!backgroundAllowed) return Issue.BACKGROUND_RESTRICTED;
        if (!standbyAllowed) return Issue.STANDBY_RESTRICTED;
        if (!batteryOptimizationExempt) return Issue.BATTERY_OPTIMIZATION;
        if (!oemAutostartConfirmed) return Issue.OEM_AUTOSTART_UNCONFIRMED;
        return Issue.NONE;
    }

}
