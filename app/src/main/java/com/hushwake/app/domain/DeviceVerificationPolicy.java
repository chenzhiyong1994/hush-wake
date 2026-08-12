package com.hushwake.app.domain;

import java.time.Duration;
import java.time.Instant;

public final class DeviceVerificationPolicy {
    public static final Duration VALIDITY = Duration.ofDays(90);

    private DeviceVerificationPolicy() {}

    public static boolean isValid(
            DeviceVerification record,
            String currentDeviceHash,
            int currentAndroidMajor,
            int currentAudioEngineVersion,
            Instant now) {
        if (record == null
                || now == null
                || !record.passed()
                || !record.deviceHash().equals(currentDeviceHash)
                || record.androidMajor() != currentAndroidMajor
                || record.audioEngineVersion() != currentAudioEngineVersion
                || now.isBefore(record.verifiedAt())) {
            return false;
        }
        return !now.isAfter(record.verifiedAt().plus(VALIDITY));
    }
}
