package com.hushwake.app.alarm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AlarmStopPolicyTest {
    @Test
    public void disablingTheAlarmThatIsCurrentlyRingingStopsThatOccurrence() {
        assertTrue(AlarmStopPolicy.shouldStop(42L, false, 42L, "AUDIBLE"));
    }

    @Test
    public void disablingAnotherAlarmDoesNotStopTheCurrentOccurrence() {
        assertFalse(AlarmStopPolicy.shouldStop(41L, false, 42L, "AUDIBLE"));
    }

    @Test
    public void leavingAnAlarmEnabledDoesNotStopTheCurrentOccurrence() {
        assertFalse(AlarmStopPolicy.shouldStop(42L, true, 42L, "AUDIBLE"));
    }

    @Test
    public void stoppedOrIdleSessionsNeedNoAdditionalStopCommand() {
        assertFalse(AlarmStopPolicy.shouldStop(42L, false, 42L, "STOPPED"));
        assertFalse(AlarmStopPolicy.shouldStop(42L, false, 42L, "IDLE"));
    }
}
