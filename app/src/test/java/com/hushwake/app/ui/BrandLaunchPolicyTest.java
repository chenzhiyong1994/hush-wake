package com.hushwake.app.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrandLaunchPolicyTest {
    @Test
    public void ordinaryColdLauncherStartShowsTheBrandTransition() {
        assertTrue(BrandLaunchPolicy.shouldShow(false, true, "IDLE"));
        assertTrue(BrandLaunchPolicy.shouldShow(false, true, "STOPPED"));
    }

    @Test
    public void restoredAndDeepLinkedScreensSkipTheBrandTransition() {
        assertFalse(BrandLaunchPolicy.shouldShow(true, true, "IDLE"));
        assertFalse(BrandLaunchPolicy.shouldShow(false, false, "IDLE"));
    }

    @Test
    public void anActiveAlarmAlwaysSkipsTheDecorativeTransition() {
        assertFalse(BrandLaunchPolicy.shouldShow(false, true, "STARTING"));
        assertFalse(BrandLaunchPolicy.shouldShow(false, true, "AUDIBLE"));
        assertFalse(BrandLaunchPolicy.shouldShow(false, true, "BLOCKED"));
    }
}
