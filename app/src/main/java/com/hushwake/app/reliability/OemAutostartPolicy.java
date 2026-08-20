package com.hushwake.app.reliability;

import java.util.List;
import java.util.Locale;

/** Vendor-specific navigation hints; Android exposes no API that reads these switches. */
public final class OemAutostartPolicy {
    public record SettingsTarget(String packageName, String className) {}

    private OemAutostartPolicy() {}

    public static String confirmationKey(String manufacturer) {
        return manufacturer == null ? "" : manufacturer.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean requiresManualConfirmation(String manufacturer) {
        String key = confirmationKey(manufacturer);
        return !key.isBlank()
                && !key.equals("unknown")
                && !key.equals("google")
                && !key.equals("aosp")
                && !key.contains("generic");
    }

    public static List<SettingsTarget> settingsTargets(String manufacturer) {
        String key = confirmationKey(manufacturer);
        if (key.contains("xiaomi") || key.contains("redmi") || key.contains("poco")) {
            return List.of(
                    new SettingsTarget(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        }
        if (key.contains("huawei")) {
            return List.of(
                    new SettingsTarget(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    new SettingsTarget(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"));
        }
        if (key.contains("honor")) {
            return List.of(
                    new SettingsTarget(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
        }
        if (key.contains("oppo") || key.contains("realme") || key.contains("oneplus")) {
            return List.of(
                    new SettingsTarget(
                            "com.oplus.safecenter",
                            "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                    new SettingsTarget(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        }
        if (key.contains("vivo") || key.contains("iqoo")) {
            return List.of(
                    new SettingsTarget(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    new SettingsTarget(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"));
        }
        if (key.contains("meizu")) {
            return List.of(
                    new SettingsTarget(
                            "com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"));
        }
        if (key.contains("asus")) {
            return List.of(
                    new SettingsTarget(
                            "com.asus.mobilemanager",
                            "com.asus.mobilemanager.powersaver.PowerSaverSettings"));
        }
        return List.of();
    }
}
