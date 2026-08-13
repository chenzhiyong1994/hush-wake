package com.hushwake.app.audio;

/** Selects the output contract for a new smart-output playback session. */
public final class SmartOutputPolicy {
    public enum Mode {
        PUBLIC_MEDIA,
        PRIVATE_HEADSET,
        BLOCKED
    }

    private SmartOutputPolicy() {}

    public static Mode choose(int mediaVolume, int personalOutputCount) {
        if (mediaVolume <= 0 || personalOutputCount < 0 || personalOutputCount > 1) {
            return Mode.BLOCKED;
        }
        return personalOutputCount == 0 ? Mode.PUBLIC_MEDIA : Mode.PRIVATE_HEADSET;
    }

    public static Mode reselectAfterDeviceChange(
            Mode current, int mediaVolume, int personalOutputCount) {
        if (current == null) {
            return Mode.BLOCKED;
        }
        if (current == Mode.PRIVATE_HEADSET && personalOutputCount == 0) {
            return Mode.BLOCKED;
        }
        return choose(mediaVolume, personalOutputCount);
    }

    public static Mode confirmPublicRoute(
            int mediaVolume, int personalOutputCount, boolean routedToPhoneSpeaker) {
        Mode selected = choose(mediaVolume, personalOutputCount);
        return selected == Mode.PUBLIC_MEDIA && routedToPhoneSpeaker
                ? Mode.PUBLIC_MEDIA
                : Mode.BLOCKED;
    }
}
