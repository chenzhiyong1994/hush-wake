package com.hushwake.app.reliability;

/** Decides when alarm creation must explain device-specific background wake settings. */
public final class BackgroundWakePromptPolicy {
    private BackgroundWakePromptPolicy() {}

    public static boolean shouldShow(boolean scheduled, boolean alreadyAcknowledged) {
        return scheduled && !alreadyAcknowledged;
    }
}
