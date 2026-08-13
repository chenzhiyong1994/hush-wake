package com.hushwake.app.domain;

import com.hushwake.app.alarm.UnifiedAlarmPolicy;
import java.util.Objects;

/** Immutable user alarm configuration. Repeat bits follow ISO weekdays: Monday bit 0. */
public final class Alarm {
    private final long id;
    private final int hour;
    private final int minute;
    private final int repeatMask;
    private final String label;
    private final String soundId;
    private final int volumePercent;
    private final int fadeInSeconds;
    private final boolean vibrationEnabled;
    private final int snoozeMinutes;
    private final int maxRingSeconds;
    private final boolean enabled;
    private final long oneTimeEpochDay;
    private final long createdAtEpochMs;
    private final long updatedAtEpochMs;

    public Alarm(
            long id,
            int hour,
            int minute,
            int repeatMask,
            String label,
            String soundId,
            int volumePercent,
            int fadeInSeconds,
            boolean vibrationEnabled,
            int snoozeMinutes,
            int maxRingSeconds,
            boolean enabled,
            long oneTimeEpochDay,
            long createdAtEpochMs,
            long updatedAtEpochMs) {
        if (id < 0L || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Invalid alarm identity or time");
        }
        if (repeatMask < 0 || repeatMask > 0x7f) {
            throw new IllegalArgumentException("Repeat mask must contain seven weekday bits");
        }
        String safeLabel = label == null ? "" : label.trim();
        if (safeLabel.codePointCount(0, safeLabel.length()) > 30) {
            throw new IllegalArgumentException("Alarm label is longer than 30 characters");
        }
        if (soundId == null || soundId.isBlank()) {
            throw new IllegalArgumentException("Sound is required");
        }
        if (volumePercent < 0 || volumePercent > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        if (!(fadeInSeconds == 0
                || fadeInSeconds == 15
                || fadeInSeconds == 30
                || fadeInSeconds == 60)) {
            throw new IllegalArgumentException("Unsupported fade-in duration");
        }
        if (!(snoozeMinutes == 0
                || snoozeMinutes == 3
                || snoozeMinutes == 5
                || snoozeMinutes == 10)) {
            throw new IllegalArgumentException("Unsupported snooze duration");
        }
        if (!(maxRingSeconds == 30
                || maxRingSeconds == 60
                || maxRingSeconds == 120
                || maxRingSeconds == 300)) {
            throw new IllegalArgumentException("Unsupported maximum ring duration");
        }
        if (repeatMask == 0 && enabled && oneTimeEpochDay == Long.MIN_VALUE) {
            throw new IllegalArgumentException("Enabled one-time alarms require a target date");
        }
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.repeatMask = repeatMask;
        this.label = safeLabel;
        this.soundId = soundId;
        this.volumePercent = volumePercent;
        this.fadeInSeconds = fadeInSeconds;
        this.vibrationEnabled = vibrationEnabled;
        this.snoozeMinutes = snoozeMinutes;
        this.maxRingSeconds = maxRingSeconds;
        this.enabled = enabled;
        this.oneTimeEpochDay = repeatMask == 0 ? oneTimeEpochDay : Long.MIN_VALUE;
        this.createdAtEpochMs = createdAtEpochMs;
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public static Alarm newDefault(int hour, int minute, long nowEpochMs) {
        return new Alarm(
                0L,
                hour,
                minute,
                0,
                "",
                "soft_chime",
                UnifiedAlarmPolicy.APP_GAIN_PERCENT,
                UnifiedAlarmPolicy.FADE_IN_SECONDS,
                UnifiedAlarmPolicy.VIBRATE_WHEN_BLOCKED,
                UnifiedAlarmPolicy.SNOOZE_MINUTES,
                UnifiedAlarmPolicy.MAX_RING_SECONDS,
                true,
                nextOneTimeEpochDay(
                        hour, minute, java.time.Instant.ofEpochMilli(nowEpochMs)),
                nowEpochMs,
                nowEpochMs);
    }

    public static int weekdayBit(int isoDayOfWeek) {
        if (isoDayOfWeek < 1 || isoDayOfWeek > 7) {
            throw new IllegalArgumentException("ISO weekday must be 1 through 7");
        }
        return 1 << (isoDayOfWeek - 1);
    }

    public boolean repeatsOn(int isoDayOfWeek) {
        return (repeatMask & weekdayBit(isoDayOfWeek)) != 0;
    }

    public boolean isRepeating() {
        return repeatMask != 0;
    }

    public Alarm withId(long newId) {
        return copy(newId, enabled, oneTimeEpochDay, createdAtEpochMs, updatedAtEpochMs);
    }

    public Alarm withEnabled(boolean newEnabled, long nowEpochMs) {
        long targetDate = oneTimeEpochDay;
        if (newEnabled
                && repeatMask == 0
                && (targetDate == Long.MIN_VALUE
                        || nextOneTimeInstant(
                                                targetDate,
                                                hour,
                                                minute,
                                                java.time.ZoneId.systemDefault())
                                        .toEpochMilli()
                                <= nowEpochMs)) {
            targetDate = nextOneTimeEpochDay(
                    hour, minute, java.time.Instant.ofEpochMilli(nowEpochMs));
        }
        return copy(id, newEnabled, targetDate, createdAtEpochMs, nowEpochMs);
    }

    private Alarm copy(
            long newId, boolean newEnabled, long oneTimeDate, long createdAt, long updatedAt) {
        return new Alarm(
                newId,
                hour,
                minute,
                repeatMask,
                label,
                soundId,
                volumePercent,
                fadeInSeconds,
                vibrationEnabled,
                snoozeMinutes,
                maxRingSeconds,
                newEnabled,
                oneTimeDate,
                createdAt,
                updatedAt);
    }

    public long id() { return id; }
    public int hour() { return hour; }
    public int minute() { return minute; }
    public int repeatMask() { return repeatMask; }
    public String label() { return label; }
    public String soundId() { return soundId; }
    public int volumePercent() { return volumePercent; }
    public int fadeInSeconds() { return fadeInSeconds; }
    public boolean vibrationEnabled() { return vibrationEnabled; }
    public int snoozeMinutes() { return snoozeMinutes; }
    public int maxRingSeconds() { return maxRingSeconds; }
    public boolean enabled() { return enabled; }
    public long oneTimeEpochDay() { return oneTimeEpochDay; }
    public long createdAtEpochMs() { return createdAtEpochMs; }
    public long updatedAtEpochMs() { return updatedAtEpochMs; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Alarm alarm)) return false;
        return id == alarm.id
                && hour == alarm.hour
                && minute == alarm.minute
                && repeatMask == alarm.repeatMask
                && volumePercent == alarm.volumePercent
                && fadeInSeconds == alarm.fadeInSeconds
                && vibrationEnabled == alarm.vibrationEnabled
                && snoozeMinutes == alarm.snoozeMinutes
                && maxRingSeconds == alarm.maxRingSeconds
                && enabled == alarm.enabled
                && oneTimeEpochDay == alarm.oneTimeEpochDay
                && createdAtEpochMs == alarm.createdAtEpochMs
                && updatedAtEpochMs == alarm.updatedAtEpochMs
                && label.equals(alarm.label)
                && soundId.equals(alarm.soundId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, hour, minute, repeatMask, label, soundId, volumePercent, fadeInSeconds,
                vibrationEnabled, snoozeMinutes, maxRingSeconds, enabled, oneTimeEpochDay, createdAtEpochMs,
                updatedAtEpochMs);
    }

    public static long nextOneTimeEpochDay(int hour, int minute, java.time.Instant after) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.ZonedDateTime now = after.atZone(zone);
        java.time.LocalDate targetDate = now.toLocalDate();
        java.time.ZonedDateTime target =
                java.time.ZonedDateTime.of(targetDate, java.time.LocalTime.of(hour, minute), zone);
        if (!target.toInstant().isAfter(after)) target = target.plusDays(1);
        return target.toLocalDate().toEpochDay();
    }

    public static java.time.Instant nextOneTimeInstant(
            long epochDay, int hour, int minute, java.time.ZoneId zone) {
        return java.time.ZonedDateTime.of(
                        java.time.LocalDate.ofEpochDay(epochDay),
                        java.time.LocalTime.of(hour, minute),
                        zone)
                .toInstant();
    }
}
