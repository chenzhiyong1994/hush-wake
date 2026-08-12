package com.hushwake.app.alarm;

import android.content.Context;
import android.content.SharedPreferences;

/** Coarse process-recovery state for the lock-screen surface; no exact alarm time is stored. */
public final class AlarmSessionStore {
    public record Snapshot(String state, String detail, String label, boolean canSnooze) {}

    private final SharedPreferences values;

    public AlarmSessionStore(Context context) {
        values = context.getSharedPreferences("hushwake_alarm_session", Context.MODE_PRIVATE);
    }

    public void save(Snapshot snapshot) {
        values.edit()
                .putString("state", snapshot.state())
                .putString("detail", snapshot.detail())
                .putString("label", snapshot.label())
                .putBoolean("can_snooze", snapshot.canSnooze())
                .apply();
    }

    public Snapshot load() {
        return new Snapshot(
                values.getString("state", "IDLE"),
                values.getString("detail", "正在建立安全播放会话"),
                values.getString("label", ""),
                values.getBoolean("can_snooze", false));
    }

    public void clear() {
        values.edit().clear().commit();
    }
}
