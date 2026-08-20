package com.hushwake.app.reliability;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** Best-effort navigation because OEM startup managers are outside the Android API contract. */
public final class OemAutostartNavigator {
    private OemAutostartNavigator() {}

    public static boolean open(Activity activity) {
        for (OemAutostartPolicy.SettingsTarget target :
                OemAutostartPolicy.settingsTargets(Build.MANUFACTURER)) {
            Intent intent =
                    new Intent()
                            .setComponent(
                                    new ComponentName(target.packageName(), target.className()));
            if (tryStart(activity, intent)) return true;
        }
        if (tryStart(activity, new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))) return true;
        if (tryStart(
                activity, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            return true;
        }
        return tryStart(
                activity,
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + activity.getPackageName())));
    }

    private static boolean tryStart(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
