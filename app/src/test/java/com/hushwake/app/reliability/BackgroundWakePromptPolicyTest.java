package com.hushwake.app.reliability;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackgroundWakePromptPolicyTest {
    @Test
    public void firstSuccessfullyScheduledAlarmShowsSetupPrompt() {
        assertTrue(BackgroundWakePromptPolicy.shouldShow(true, false));
    }

    @Test
    public void failedScheduleDoesNotShowMisleadingSetupPrompt() {
        assertFalse(BackgroundWakePromptPolicy.shouldShow(false, false));
    }

    @Test
    public void acceptedSetupDoesNotPromptAgain() {
        assertFalse(BackgroundWakePromptPolicy.shouldShow(true, true));
    }
}
