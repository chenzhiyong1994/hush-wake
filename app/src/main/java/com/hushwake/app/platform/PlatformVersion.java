package com.hushwake.app.platform;

import android.os.Build;

public final class PlatformVersion {
    private PlatformVersion() {}

    public static int androidMajor() {
        String release = Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE;
        int dot = release.indexOf('.');
        String major = dot >= 0 ? release.substring(0, dot) : release;
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException ignored) {
            return Build.VERSION.SDK_INT;
        }
    }
}
