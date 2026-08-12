package com.hushwake.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PlaybackEventRepository {
    public record Event(
            long id,
            long alarmId,
            String eventType,
            String result,
            String reasonCode,
            String verificationLevel,
            long latencyMs,
            Instant createdAt) {}

    private final HushWakeDatabase database;

    public PlaybackEventRepository(Context context) {
        database = HushWakeDatabase.get(context);
    }

    public void record(
            long alarmId,
            String eventType,
            String result,
            String reasonCode,
            String verificationLevel,
            long latencyMs) {
        ContentValues values = new ContentValues();
        if (alarmId > 0L) values.put("alarm_id", alarmId);
        values.put("event_type", safe(eventType));
        values.put("result", safe(result));
        values.put("reason_code", safe(reasonCode));
        values.put("verification_level", safe(verificationLevel));
        values.put("latency_ms", latencyMs);
        values.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertOrThrow("playback_events", null, values);
        prune();
    }

    public List<Event> recent() {
        List<Event> events = new ArrayList<>();
        try (Cursor cursor =
                database.getReadableDatabase()
                        .query(
                                "playback_events",
                                new String[] {
                                    "id", "alarm_id", "event_type", "result", "reason_code",
                                    "verification_level", "latency_ms", "created_at"
                                },
                                null,
                                null,
                                null,
                                null,
                                "created_at DESC, id DESC",
                                "30")) {
            while (cursor.moveToNext()) {
                events.add(
                        new Event(
                                cursor.getLong(0),
                                cursor.isNull(1) ? 0L : cursor.getLong(1),
                                cursor.getString(2),
                                cursor.getString(3),
                                cursor.getString(4),
                                cursor.getString(5),
                                cursor.getLong(6),
                                Instant.ofEpochMilli(cursor.getLong(7))));
            }
        }
        return events;
    }

    public void clear() {
        database.getWritableDatabase().delete("playback_events", null, null);
    }

    private void prune() {
        long oldest = Instant.now().minus(Duration.ofDays(30)).toEpochMilli();
        database.getWritableDatabase()
                .delete("playback_events", "created_at < ?", new String[] {Long.toString(oldest)});
        database.getWritableDatabase()
                .execSQL(
                        "DELETE FROM playback_events WHERE id NOT IN "
                                + "(SELECT id FROM playback_events ORDER BY created_at DESC, id DESC LIMIT 30)");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
