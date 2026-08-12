package com.hushwake.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import com.hushwake.app.domain.DeviceVerification;
import java.time.Instant;

public final class DeviceVerificationRepository {
    private final HushWakeDatabase database;

    public DeviceVerificationRepository(Context context) {
        database = HushWakeDatabase.get(context);
    }

    public void save(DeviceVerification verification) {
        ContentValues values = new ContentValues();
        values.put("device_hash", verification.deviceHash());
        values.put("device_type", verification.deviceType());
        values.put("android_major", verification.androidMajor());
        values.put("audio_engine_version", verification.audioEngineVersion());
        values.put("verified_at", verification.verifiedAt().toEpochMilli());
        values.put("passed", verification.passed() ? 1 : 0);
        database.getWritableDatabase()
                .insertWithOnConflict(
                        "device_verifications", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    public DeviceVerification find(String deviceHash) {
        if (deviceHash == null || deviceHash.isBlank()) {
            return null;
        }
        try (Cursor cursor =
                database.getReadableDatabase()
                        .query(
                                "device_verifications",
                                new String[] {
                                    "device_hash", "device_type", "android_major",
                                    "audio_engine_version", "verified_at", "passed"
                                },
                                "device_hash = ?",
                                new String[] {deviceHash},
                                null,
                                null,
                                null)) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new DeviceVerification(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getInt(3),
                    Instant.ofEpochMilli(cursor.getLong(4)),
                    cursor.getInt(5) != 0);
        }
    }

    public void deleteAll() {
        database.getWritableDatabase().delete("device_verifications", null, null);
    }
}
