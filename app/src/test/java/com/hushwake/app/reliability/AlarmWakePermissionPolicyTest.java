package com.hushwake.app.reliability;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AlarmWakePermissionPolicyTest {
    @Test
    public void reportsEachMissingWakeRequirementInActionableOrder() {
        assertEquals(
                AlarmWakePermissionPolicy.Issue.EXACT_ALARM,
                AlarmWakePermissionPolicy.firstIssue(
                        false, false, false, false, false, false, false));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.NOTIFICATIONS,
                AlarmWakePermissionPolicy.firstIssue(true, false, false, false, false, false, false));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.FULL_SCREEN,
                AlarmWakePermissionPolicy.firstIssue(true, true, false, true, true, true, true));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.BACKGROUND_RESTRICTED,
                AlarmWakePermissionPolicy.firstIssue(true, true, true, false, true, true, true));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.STANDBY_RESTRICTED,
                AlarmWakePermissionPolicy.firstIssue(true, true, true, true, false, true, true));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.BATTERY_OPTIMIZATION,
                AlarmWakePermissionPolicy.firstIssue(true, true, true, true, true, false, true));
        assertEquals(
                AlarmWakePermissionPolicy.Issue.OEM_AUTOSTART_UNCONFIRMED,
                AlarmWakePermissionPolicy.firstIssue(true, true, true, true, true, true, false));
    }

    @Test
    public void allRequirementsSatisfiedNeedsNoPrompt() {
        assertEquals(
                AlarmWakePermissionPolicy.Issue.NONE,
                AlarmWakePermissionPolicy.firstIssue(true, true, true, true, true, true, true));
    }
}
