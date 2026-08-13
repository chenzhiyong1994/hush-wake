package com.hushwake.app.audio;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SmartOutputPolicyTest {
    @Test
    public void noHeadsetUsesNormalSystemMediaOutput() {
        assertEquals(
                SmartOutputPolicy.Mode.PUBLIC_MEDIA,
                SmartOutputPolicy.choose(4, 0));
    }

    @Test
    public void oneHeadsetUsesThePrivateHeadsetGuard() {
        assertEquals(
                SmartOutputPolicy.Mode.PRIVATE_HEADSET,
                SmartOutputPolicy.choose(4, 1));
    }

    @Test
    public void multipleHeadsetsRemainBlockedBecauseTheTargetIsAmbiguous() {
        assertEquals(
                SmartOutputPolicy.Mode.BLOCKED,
                SmartOutputPolicy.choose(4, 2));
    }

    @Test
    public void zeroSystemMediaVolumeRemainsBlockedInEveryMode() {
        assertEquals(
                SmartOutputPolicy.Mode.BLOCKED,
                SmartOutputPolicy.choose(0, 0));
    }

    @Test
    public void connectingAHeadsetLeavesPublicOutputAndEntersThePrivateGuard() {
        assertEquals(
                SmartOutputPolicy.Mode.PRIVATE_HEADSET,
                SmartOutputPolicy.reselectAfterDeviceChange(
                        SmartOutputPolicy.Mode.PUBLIC_MEDIA, 4, 1));
    }

    @Test
    public void disconnectingTheActiveHeadsetNeverFallsBackToTheSpeaker() {
        assertEquals(
                SmartOutputPolicy.Mode.BLOCKED,
                SmartOutputPolicy.reselectAfterDeviceChange(
                        SmartOutputPolicy.Mode.PRIVATE_HEADSET, 4, 0));
    }

    @Test
    public void publicModeRequiresTheActualPhoneSpeakerRoute() {
        assertEquals(
                SmartOutputPolicy.Mode.PUBLIC_MEDIA,
                SmartOutputPolicy.confirmPublicRoute(4, 0, true));
        assertEquals(
                SmartOutputPolicy.Mode.BLOCKED,
                SmartOutputPolicy.confirmPublicRoute(4, 0, false));
    }
}
