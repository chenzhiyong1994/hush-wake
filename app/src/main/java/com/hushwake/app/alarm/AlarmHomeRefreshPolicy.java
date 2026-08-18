package com.hushwake.app.alarm;

/** Keeps the visible alarm list in sync with the authoritative ringing session. */
public final class AlarmHomeRefreshPolicy {
    private AlarmHomeRefreshPolicy() {}

    public static boolean shouldRefresh(String visibleScreen, String sessionState) {
        return "alarms".equals(visibleScreen)
                && sessionState != null
                && !sessionState.isBlank();
    }
}
