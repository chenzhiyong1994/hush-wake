package com.hushwake.app.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public final class DeviceVerificationPolicyTest {
    private static final Instant VERIFIED = Instant.parse("2026-05-15T00:00:00Z");

    @Test
    public void matchingPassedRecordIsValidForNinetyDays() {
        DeviceVerification record =
                new DeviceVerification("hash-a", "蓝牙 A2DP 设备", 14, 2, VERIFIED, true);

        assertTrue(
                DeviceVerificationPolicy.isValid(
                        record,
                        "hash-a",
                        14,
                        2,
                        VERIFIED.plus(Duration.ofDays(90))));
    }

    @Test
    public void recordExpiresAfterNinetyDaysOrEnvironmentChange() {
        DeviceVerification record =
                new DeviceVerification("hash-a", "蓝牙 A2DP 设备", 14, 2, VERIFIED, true);

        assertFalse(
                DeviceVerificationPolicy.isValid(
                        record,
                        "hash-a",
                        14,
                        2,
                        VERIFIED.plus(Duration.ofDays(90)).plusMillis(1)));
        assertFalse(DeviceVerificationPolicy.isValid(record, "hash-b", 14, 2, VERIFIED));
        assertFalse(DeviceVerificationPolicy.isValid(record, "hash-a", 15, 2, VERIFIED));
        assertFalse(DeviceVerificationPolicy.isValid(record, "hash-a", 14, 3, VERIFIED));
    }
}
