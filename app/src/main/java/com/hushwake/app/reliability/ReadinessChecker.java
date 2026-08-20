package com.hushwake.app.reliability;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.os.PowerManager;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.audio.AudioRouteInspector;
import com.hushwake.app.audio.DeviceFingerprint;
import com.hushwake.app.audio.DeviceIdentity;
import com.hushwake.app.audio.PrivatePlaybackEngine;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.data.DeviceVerificationRepository;
import com.hushwake.app.domain.DeviceVerification;
import com.hushwake.app.domain.DeviceVerificationPolicy;
import com.hushwake.app.platform.PlatformVersion;
import java.time.Instant;

public final class ReadinessChecker {
    public record Status(
            boolean exactAlarm,
            boolean notifications,
            boolean fullScreen,
            boolean backgroundAllowed,
            boolean standbyAllowed,
            boolean batteryOptimizationExempt,
            boolean oemAutostartConfirmed,
            boolean bluetoothPermission,
            boolean mediaVolume,
            String output,
            boolean outputSelectable,
            boolean headsetConnected,
            boolean deviceVerified,
            boolean outputPolicyAcknowledged,
            boolean testAlarmPassed,
            String scheduleIssue) {
        public boolean readyForSound() {
            return exactAlarm
                    && notifications
                    && bluetoothPermission
                    && mediaVolume
                    && outputSelectable
                    && (!headsetConnected || deviceVerified)
                    && outputPolicyAcknowledged;
        }

        public boolean fullyReady() {
            return readyForSound()
                    && fullScreen
                    && backgroundAllowed
                    && standbyAllowed
                    && batteryOptimizationExempt
                    && oemAutostartConfirmed;
        }
    }

    private ReadinessChecker() {}

    public static Status inspect(Context context) {
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        boolean notifications =
                Build.VERSION.SDK_INT < 33
                        || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                == PackageManager.PERMISSION_GRANTED;
        notifications = notifications && notificationManager.areNotificationsEnabled();
        boolean fullScreen =
                Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent();
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        boolean backgroundAllowed = !activityManager.isBackgroundRestricted();
        UsageStatsManager usageStatsManager = context.getSystemService(UsageStatsManager.class);
        boolean standbyAllowed =
                usageStatsManager.getAppStandbyBucket()
                        != UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        boolean batteryOptimizationExempt =
                powerManager.isIgnoringBatteryOptimizations(context.getPackageName());
        boolean bluetoothGranted =
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
        AudioRouteInspector inspector = new AudioRouteInspector();
        AudioRouteInspector.Snapshot audio =
                inspector.snapshot(context.getSystemService(AudioManager.class));
        int outputType =
                audio.preferredTarget() == null
                        ? AudioDeviceInfo.TYPE_UNKNOWN
                        : audio.preferredTarget().getType();
        boolean bluetoothNeeded =
                outputType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || outputType == AudioDeviceInfo.TYPE_BLE_HEADSET;
        boolean bluetooth = bluetoothGranted;
        boolean headsetConnected = !audio.personalOutputTypes().isEmpty();
        boolean outputSelectable = audio.personalOutputTypes().size() <= 1;
        DeviceIdentity identity = DeviceFingerprint.create(context, audio.preferredTarget());
        DeviceVerification record =
                identity == null
                        ? null
                        : new DeviceVerificationRepository(context).find(identity.hash());
        boolean verified =
                identity != null
                        && DeviceVerificationPolicy.isValid(
                                record,
                                identity.hash(),
                                PlatformVersion.androidMajor(),
                                PrivatePlaybackEngine.AUDIO_ENGINE_VERSION,
                                Instant.now());
        AppPreferences preferences = new AppPreferences(context);
        return new Status(
                new AlarmScheduler(context).canScheduleExact(),
                notifications,
                fullScreen,
                backgroundAllowed,
                standbyAllowed,
                batteryOptimizationExempt,
                preferences.oemAutostartConfirmed(Build.MANUFACTURER),
                bluetooth,
                audio.mediaVolume() > 0,
                !headsetConnected
                        ? "智能外放 · 手机扬声器"
                        : outputSelectable
                                ? String.join(" + ", audio.personalOutputTypes())
                                : "多个耳机 · " + String.join(" + ", audio.personalOutputTypes()),
                outputSelectable,
                headsetConnected,
                verified,
                preferences.outputPolicyAcknowledged(),
                preferences.testAlarmPassed(),
                preferences.lastScheduleIssue());
    }
}
