package com.hushwake.app.alarm;

/** Decides whether a user change should stop the currently ringing occurrence. */
public final class AlarmStopPolicy {
    private AlarmStopPolicy() {}

    public static boolean shouldStop(
            long changedAlarmId,
            boolean enabledAfterChange,
            long activeAlarmId,
            String activeState) {
        return !enabledAfterChange
                && changedAlarmId > 0L
                && changedAlarmId == activeAlarmId
                && activeState != null
                && !"IDLE".equals(activeState)
                && !"STOPPED".equals(activeState);
    }
}
