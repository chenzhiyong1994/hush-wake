package com.hushwake.app.alarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AlarmHomeRefreshPolicyTest {
    @Test
    public void stoppedRingingSessionRefreshesTheVisibleAlarmList() {
        assertTrue(AlarmHomeRefreshPolicy.shouldRefresh("alarms", "STOPPED"));
    }

    @Test
    public void alarmUpdatesDoNotReplaceTheVisibleSleepSoundScreen() {
        assertFalse(AlarmHomeRefreshPolicy.shouldRefresh("noise", "STOPPED"));
    }
}
