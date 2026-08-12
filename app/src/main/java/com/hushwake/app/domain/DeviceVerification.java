package com.hushwake.app.domain;

import java.time.Instant;

/** Local-only record bound to an irreversible per-install headset fingerprint. */
public record DeviceVerification(
        String deviceHash,
        String deviceType,
        int androidMajor,
        int audioEngineVersion,
        Instant verifiedAt,
        boolean passed) {
    public DeviceVerification {
        if (deviceHash == null || deviceHash.isBlank() || verifiedAt == null) {
            throw new IllegalArgumentException("A device hash and verification time are required");
        }
        deviceType = deviceType == null ? "" : deviceType;
    }
}
