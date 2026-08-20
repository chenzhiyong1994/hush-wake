package com.hushwake.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.hushwake.app.domain.Alarm;
import java.util.ArrayList;
import java.util.List;

public final class AlarmRepository {
    private static final String[] COLUMNS = {
        "id", "hour", "minute", "repeat_mask", "label", "sound_id", "volume_percent",
        "fade_in_seconds", "vibration_enabled", "snooze_minutes", "max_ring_seconds",
        "enabled", "one_time_epoch_day", "created_at", "updated_at", "snooze_target_epoch_ms"
    };

    private final HushWakeDatabase database;

    public AlarmRepository(Context context) {
        database = HushWakeDatabase.get(context);
    }

    public Alarm save(Alarm alarm) {
        SQLiteDatabase writable = database.getWritableDatabase();
        ContentValues values = valuesOf(alarm);
        if (alarm.id() == 0L) {
            long id = writable.insertOrThrow("alarms", null, values);
            return alarm.withId(id);
        }
        int updated =
                writable.update(
                        "alarms", values, "id = ?", new String[] {Long.toString(alarm.id())});
        if (updated != 1) {
            throw new IllegalStateException("Alarm no longer exists: " + alarm.id());
        }
        return alarm;
    }

    public Alarm find(long id) {
        try (Cursor cursor =
                database.getReadableDatabase()
                        .query(
                                "alarms",
                                COLUMNS,
                                "id = ?",
                                new String[] {Long.toString(id)},
                                null,
                                null,
                                null)) {
            return cursor.moveToFirst() ? read(cursor) : null;
        }
    }

    public List<Alarm> listAll() {
        List<Alarm> alarms = new ArrayList<>();
        try (Cursor cursor =
                database.getReadableDatabase()
                        .query("alarms", COLUMNS, null, null, null, null, "hour, minute, id")) {
            while (cursor.moveToNext()) {
                alarms.add(read(cursor));
            }
        }
        return alarms;
    }

    public List<Alarm> listEnabled() {
        List<Alarm> alarms = new ArrayList<>();
        try (Cursor cursor =
                database.getReadableDatabase()
                        .query(
                                "alarms",
                                COLUMNS,
                                "enabled = 1",
                                null,
                                null,
                                null,
                                "hour, minute, id")) {
            while (cursor.moveToNext()) {
                alarms.add(read(cursor));
            }
        }
        return alarms;
    }

    public void delete(long id) {
        database.getWritableDatabase()
                .delete("alarms", "id = ?", new String[] {Long.toString(id)});
    }

    public void deleteAll() {
        database.getWritableDatabase().delete("alarms", null, null);
    }

    private static ContentValues valuesOf(Alarm alarm) {
        ContentValues values = new ContentValues();
        values.put("hour", alarm.hour());
        values.put("minute", alarm.minute());
        values.put("repeat_mask", alarm.repeatMask());
        values.put("label", alarm.label());
        values.put("sound_id", alarm.soundId());
        values.put("volume_percent", alarm.volumePercent());
        values.put("fade_in_seconds", alarm.fadeInSeconds());
        values.put("vibration_enabled", alarm.vibrationEnabled() ? 1 : 0);
        values.put("snooze_minutes", alarm.snoozeMinutes());
        values.put("max_ring_seconds", alarm.maxRingSeconds());
        values.put("enabled", alarm.enabled() ? 1 : 0);
        values.put("one_time_epoch_day", alarm.oneTimeEpochDay());
        values.put("created_at", alarm.createdAtEpochMs());
        values.put("updated_at", alarm.updatedAtEpochMs());
        values.put("snooze_target_epoch_ms", alarm.snoozeTargetEpochMs());
        return values;
    }

    private static Alarm read(Cursor cursor) {
        return new Alarm(
                cursor.getLong(0),
                cursor.getInt(1),
                cursor.getInt(2),
                cursor.getInt(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getInt(6),
                cursor.getInt(7),
                cursor.getInt(8) != 0,
                cursor.getInt(9),
                cursor.getInt(10),
                cursor.getInt(11) != 0,
                cursor.getLong(12),
                cursor.getLong(15),
                cursor.getLong(13),
                cursor.getLong(14));
    }
}
