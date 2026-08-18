package com.hushwake.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.Base64;

/** Small local settings that are not relational application data. */
public final class AppPreferences {
    private static final String NAME = "hushwake_preferences";
    private static final String KEY_INSTALL_SALT = "install_salt";
    private static final int OUTPUT_POLICY_VERSION = 1;
    private static final int BACKGROUND_WAKE_SETUP_VERSION = 1;
    private final SharedPreferences values;

    public AppPreferences(Context context) {
        values = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean outputPolicyAcknowledged() {
        return values.getInt("output_policy_version", 0) == OUTPUT_POLICY_VERSION;
    }

    public void acknowledgeOutputPolicy() {
        values.edit().putInt("output_policy_version", OUTPUT_POLICY_VERSION).apply();
    }

    public boolean vibrationWarningAcknowledged() {
        return values.getBoolean("vibration_warning_acknowledged", false);
    }

    public void setVibrationWarningAcknowledged(boolean acknowledged) {
        values.edit().putBoolean("vibration_warning_acknowledged", acknowledged).apply();
    }

    public boolean backgroundWakeSetupAcknowledged() {
        return values.getInt("background_wake_setup_version", 0)
                == BACKGROUND_WAKE_SETUP_VERSION;
    }

    public void acknowledgeBackgroundWakeSetup() {
        values.edit()
                .putInt("background_wake_setup_version", BACKGROUND_WAKE_SETUP_VERSION)
                .commit();
    }

    public boolean testAlarmPassed() {
        return values.getBoolean("test_alarm_passed", false);
    }

    public void setTestAlarmPassed(boolean passed) {
        values.edit().putBoolean("test_alarm_passed", passed).apply();
    }

    public void setPendingTestAlarm(long alarmId) {
        values.edit()
                .putLong("pending_test_alarm_id", alarmId)
                .putBoolean("test_alarm_passed", false)
                .commit();
    }

    public boolean isPendingTestAlarm(long alarmId) {
        return alarmId > 0L && values.getLong("pending_test_alarm_id", 0L) == alarmId;
    }

    public void completeTestAlarm(long alarmId, boolean passed) {
        if (!isPendingTestAlarm(alarmId)) return;
        values.edit()
                .remove("pending_test_alarm_id")
                .putBoolean("test_alarm_passed", passed)
                .commit();
    }

    public int noiseTimerMinutes() { return values.getInt("noise_timer", 30); }
    public int noiseFadeSeconds() { return values.getInt("noise_fade", 15); }
    public String noiseSoundId() { return values.getString("noise_sound", "rain"); }

    public void saveNoiseDefaults(int timerMinutes, int fadeSeconds, String soundId) {
        values.edit()
                .putInt("noise_timer", timerMinutes)
                .putInt("noise_fade", fadeSeconds)
                .putString("noise_sound", soundId)
                .apply();
    }

    public String lastScheduleIssue() {
        return values.getString("last_schedule_issue", "");
    }

    public void setLastScheduleIssue(String issue) {
        values.edit().putString("last_schedule_issue", issue == null ? "" : issue).apply();
    }

    public synchronized String installSalt() {
        String existing = values.getString(KEY_INSTALL_SALT, "");
        if (!existing.isBlank()) {
            return existing;
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String encoded = Base64.getEncoder().withoutPadding().encodeToString(random);
        values.edit().putString(KEY_INSTALL_SALT, encoded).commit();
        return encoded;
    }

    public void clearAll() {
        values.edit().clear().commit();
    }
}
