package com.hushwake.app.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Single local fact store. The application intentionally has no network or account layer. */
public final class HushWakeDatabase extends SQLiteOpenHelper {
    private static final String NAME = "hushwake.db";
    private static final int VERSION = 4;
    private static volatile HushWakeDatabase instance;

    public static HushWakeDatabase get(Context context) {
        if (instance == null) {
            synchronized (HushWakeDatabase.class) {
                if (instance == null) {
                    instance = new HushWakeDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private HushWakeDatabase(Context context) {
        super(context, NAME, null, VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE alarms ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "hour INTEGER NOT NULL CHECK(hour BETWEEN 0 AND 23),"
                        + "minute INTEGER NOT NULL CHECK(minute BETWEEN 0 AND 59),"
                        + "repeat_mask INTEGER NOT NULL CHECK(repeat_mask BETWEEN 0 AND 127),"
                        + "label TEXT NOT NULL DEFAULT '',"
                        + "sound_id TEXT NOT NULL,"
                        + "volume_percent INTEGER NOT NULL CHECK(volume_percent BETWEEN 0 AND 100),"
                        + "fade_in_seconds INTEGER NOT NULL,"
                        + "vibration_enabled INTEGER NOT NULL,"
                        + "snooze_minutes INTEGER NOT NULL,"
                        + "max_ring_seconds INTEGER NOT NULL,"
                        + "enabled INTEGER NOT NULL,"
                        + "one_time_epoch_day INTEGER NOT NULL DEFAULT -9223372036854775808,"
                        + "snooze_target_epoch_ms INTEGER NOT NULL DEFAULT -9223372036854775808,"
                        + "created_at INTEGER NOT NULL,"
                        + "updated_at INTEGER NOT NULL)" );
        database.execSQL(
                "CREATE TABLE device_verifications ("
                        + "device_hash TEXT PRIMARY KEY,"
                        + "device_type TEXT NOT NULL,"
                        + "android_major INTEGER NOT NULL,"
                        + "audio_engine_version INTEGER NOT NULL,"
                        + "verified_at INTEGER NOT NULL,"
                        + "passed INTEGER NOT NULL)" );
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN one_time_epoch_day INTEGER NOT NULL DEFAULT -9223372036854775808");
            database.execSQL("UPDATE alarms SET enabled = 0 WHERE repeat_mask = 0");
        }
        if (oldVersion < 3) {
            database.execSQL("DROP INDEX IF EXISTS playback_events_created_at");
            database.execSQL("DROP TABLE IF EXISTS playback_events");
        }
        if (oldVersion < 4) {
            database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN snooze_target_epoch_ms INTEGER NOT NULL DEFAULT -9223372036854775808");
        }
    }

    /** Deletes every user-owned row, resets local row counters, and removes recoverable free pages. */
    public void clearAllUserData() {
        SQLiteDatabase database = getWritableDatabase();
        database.execSQL("PRAGMA secure_delete=ON");
        database.beginTransaction();
        try {
            database.delete("device_verifications", null, null);
            database.delete("alarms", null, null);
            database.execSQL("DELETE FROM sqlite_sequence WHERE name = 'alarms'");
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        try {
            try (Cursor ignored = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                ignored.moveToFirst();
            }
            database.execSQL("VACUUM");
            try (Cursor ignored = database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)) {
                ignored.moveToFirst();
            }
        } catch (SQLiteException ignored) {
            // Rows are already gone; compaction is best-effort on vendor SQLite builds.
        }
    }
}
