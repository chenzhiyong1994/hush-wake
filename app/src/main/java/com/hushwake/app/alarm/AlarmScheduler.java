package com.hushwake.app.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.hushwake.app.MainActivity;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import java.time.Instant;
import java.time.ZoneId;

public final class AlarmScheduler {
    public enum Result {
        SCHEDULED,
        DISABLED,
        EXACT_ALARM_PERMISSION_REQUIRED,
        FAILED
    }

    public record ScheduleResult(Result result, Instant triggerAt, String detail) {}

    public static final String EXTRA_ALARM_ID = "alarm_id";
    public static final String EXTRA_SCHEDULED_AT = "scheduled_at";

    private final Context context;
    private final AlarmManager alarmManager;

    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public boolean canScheduleExact() {
        return alarmManager.canScheduleExactAlarms();
    }

    public ScheduleResult schedule(Alarm alarm) {
        cancel(alarm.id());
        if (!alarm.enabled()) {
            return new ScheduleResult(Result.DISABLED, null, "闹钟已停用");
        }
        if (!canScheduleExact()) {
            return new ScheduleResult(
                    Result.EXACT_ALARM_PERMISSION_REQUIRED, null, "尚未允许精确闹钟");
        }
        Instant next;
        try {
            next = AlarmTimeCalculator.next(alarm, Instant.now(), ZoneId.systemDefault());
        } catch (IllegalStateException elapsed) {
            return new ScheduleResult(Result.DISABLED, null, "一次性闹钟时间已过，请重新启用");
        }
        PendingIntent trigger =
                triggerIntent(
                        alarm.id(),
                        next.toEpochMilli(),
                        alarm.isSnoozed(),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra(EXTRA_ALARM_ID, alarm.id());
        PendingIntent show =
                PendingIntent.getActivity(
                        context,
                        requestCode(alarm.id(), 1),
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            alarmManager.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(next.toEpochMilli(), show), trigger);
            return new ScheduleResult(Result.SCHEDULED, next, "已交给 Android 系统精确调度");
        } catch (SecurityException error) {
            return new ScheduleResult(Result.EXACT_ALARM_PERMISSION_REQUIRED, null, "精确闹钟能力被撤销");
        } catch (RuntimeException error) {
            return new ScheduleResult(Result.FAILED, null, error.getClass().getSimpleName());
        }
    }

    public void cancel(long alarmId) {
        cancelNormal(alarmId);
        cancelSnooze(alarmId);
    }

    private void cancelNormal(long alarmId) {
        PendingIntent existing =
                triggerIntent(alarmId, 0L, false, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            alarmManager.cancel(existing);
            existing.cancel();
        }
    }

    private void cancelSnooze(long alarmId) {
        PendingIntent existing =
                triggerIntent(alarmId, 0L, true, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            alarmManager.cancel(existing);
            existing.cancel();
        }
    }

    public void rescheduleAll() {
        AlarmRepository alarms = new AlarmRepository(context);
        AppPreferences preferences = new AppPreferences(context);
        String issue = "";
        for (Alarm alarm : alarms.listAll()) {
            if (alarm.enabled()) {
                ScheduleResult result = schedule(alarm);
                if (result.result() == Result.DISABLED) {
                    alarms.save(alarm.withEnabled(false, System.currentTimeMillis()));
                }
                if (result.result() != Result.SCHEDULED) {
                    issue = result.detail();
                }
            } else {
                cancel(alarm.id());
            }
        }
        preferences.setLastScheduleIssue(issue);
    }

    /** Repairs normal alarm registrations without discarding an in-flight snooze. */
    public void reconcileOnAppOpen() {
        AppPreferences preferences = new AppPreferences(context);
        String issue = "";
        for (Alarm alarm : new AlarmRepository(context).listAll()) {
            if (!alarm.enabled()) {
                cancel(alarm.id());
                continue;
            }
            ScheduleResult result = schedule(alarm);
            if (result.result() == Result.DISABLED) {
                new AlarmRepository(context)
                        .save(alarm.withEnabled(false, System.currentTimeMillis()));
            }
            if (result.result() != Result.SCHEDULED) issue = result.detail();
        }
        preferences.setLastScheduleIssue(issue);
    }

    public void cancelAll() {
        for (Alarm alarm : new AlarmRepository(context).listAll()) {
            cancel(alarm.id());
        }
    }

    private PendingIntent triggerIntent(
            long alarmId, long scheduledAt, boolean snooze, int mutableFlag) {
        Intent intent = new Intent(context, AlarmTriggerReceiver.class);
        intent.setAction(AlarmTriggerReceiver.ACTION_TRIGGER);
        intent.putExtra(EXTRA_ALARM_ID, alarmId);
        intent.putExtra(EXTRA_SCHEDULED_AT, scheduledAt);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(
                context,
                requestCode(alarmId, snooze ? 2 : 0),
                intent,
                mutableFlag | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(long alarmId, int discriminator) {
        return (int) (alarmId ^ (alarmId >>> 32)) * 31 + discriminator;
    }
}
