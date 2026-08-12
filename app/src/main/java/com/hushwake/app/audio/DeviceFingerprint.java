package com.hushwake.app.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import com.hushwake.app.data.AppPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Hashes a stable route address in memory; raw names and addresses are never persisted. */
public final class DeviceFingerprint {
    private DeviceFingerprint() {}

    public static DeviceIdentity create(Context context, AudioDeviceInfo device) {
        if (device == null) return null;
        String address;
        try {
            address = device.getAddress();
        } catch (SecurityException permissionMissing) {
            return null;
        }
        if (address == null || address.isBlank() || "0".equals(address)) {
            return null;
        }
        String input =
                new AppPreferences(context).installSalt()
                        + "|"
                        + device.getType()
                        + "|"
                        + address;
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            return new DeviceIdentity(toHex(digest), AudioRouteInspector.typeLabel(device.getType()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = alphabet[value >>> 4];
            result[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }
}
