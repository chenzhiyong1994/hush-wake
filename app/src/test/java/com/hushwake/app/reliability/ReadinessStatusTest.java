package com.hushwake.app.reliability;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ReadinessStatusTest {
    @Test
    public void backgroundRestrictedAppIsNotFullyReadyToWake() {
        assertFalse(readyStatus(false).fullyReady());
    }

    @Test
    public void unrestrictedAppCanBeFullyReadyToWake() {
        assertTrue(readyStatus(true).fullyReady());
    }

    private static ReadinessChecker.Status readyStatus(boolean backgroundAllowed) {
        return new ReadinessChecker.Status(
                true,
                true,
                true,
                backgroundAllowed,
                true,
                true,
                "智能外放 · 手机扬声器",
                true,
                false,
                false,
                true,
                true,
                "");
    }
}
