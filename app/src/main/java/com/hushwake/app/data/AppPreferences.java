package com.hushwake.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.Base64;

/** Small local settings that are not relational application data. */
public final class AppPreferences {
    private static final String NAME = "hushwake_preferences";
    private static final String KEY_INSTALL_SALT = "install_salt";
    private final SharedPreferences values;

    public AppPreferences(Context context) {
        values = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean privacyPrincipleAcknowledged() {
        return values.getBoolean("privacy_principle_acknowledged", false);
    }

    public void setPrivacyPrincipleAcknowledged(boolean acknowledged) {
        values.edit().putBoolean("privacy_principle_acknowledged", acknowledged).apply();
    }

    public boolean vibrationWarningAcknowledged() {
        return values.getBoolean("vibration_warning_acknowledged", false);
    }

    public void setVibrationWarningAcknowledged(boolean acknowledged) {
        values.edit().putBoolean("vibration_warning_acknowledged", acknowledged).apply();
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

    public int defaultAlarmVolume() { return values.getInt("default_alarm_volume", 50); }
    public int defaultAlarmFadeSeconds() { return values.getInt("default_alarm_fade", 15); }
    public boolean defaultVibration() { return values.getBoolean("default_vibration", true); }
    public int defaultSnoozeMinutes() { return values.getInt("default_snooze", 5); }
    public int defaultMaxRingSeconds() { return values.getInt("default_max_ring", 120); }
    public int noiseVolume() { return values.getInt("noise_volume", 30); }
    public int noiseTimerMinutes() { return values.getInt("noise_timer", 30); }
    public int noiseFadeSeconds() { return values.getInt("noise_fade", 15); }
    public String noiseSoundId() { return values.getString("noise_sound", "rain"); }

    public void saveAlarmDefaults(
            int volume, int fadeSeconds, boolean vibration, int snooze, int maxRingSeconds) {
        values.edit()
                .putInt("default_alarm_volume", volume)
                .putInt("default_alarm_fade", fadeSeconds)
                .putBoolean("default_vibration", vibration)
                .putInt("default_snooze", snooze)
                .putInt("default_max_ring", maxRingSeconds)
                .apply();
    }

    public void saveNoiseDefaults(int volume, int timerMinutes, int fadeSeconds, String soundId) {
        values.edit()
                .putInt("noise_volume", volume)
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
