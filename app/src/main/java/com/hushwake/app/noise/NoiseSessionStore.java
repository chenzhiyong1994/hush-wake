package com.hushwake.app.noise;

import android.content.Context;
import android.content.SharedPreferences;

public final class NoiseSessionStore {
    public record Snapshot(
            String state,
            String soundId,
            int volumePercent,
            long endsAtEpochMs,
            int fadeSeconds,
            String detail) {}

    private final SharedPreferences values;

    public NoiseSessionStore(Context context) {
        values = context.getSharedPreferences("hushwake_noise_session", Context.MODE_PRIVATE);
    }

    public void save(Snapshot snapshot) {
        values.edit()
                .putString("state", snapshot.state())
                .putString("sound_id", snapshot.soundId())
                .putInt("volume", snapshot.volumePercent())
                .putLong("ends_at", snapshot.endsAtEpochMs())
                .putInt("fade", snapshot.fadeSeconds())
                .putString("detail", snapshot.detail())
                .apply();
    }

    public Snapshot load() {
        return new Snapshot(
                values.getString("state", "stopped"),
                values.getString("sound_id", "rain"),
                values.getInt("volume", 30),
                values.getLong("ends_at", 0L),
                values.getInt("fade", 15),
                values.getString("detail", "尚未播放"));
    }

    public void clear() {
        values.edit().clear().commit();
    }
}
