package com.hushwake.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.domain.Alarm;

public final class AlarmTriggerReceiver extends BroadcastReceiver {
    public static final String ACTION_TRIGGER = "com.hushwake.app.action.ALARM_TRIGGER";
    public static final String EXTRA_SNOOZE = "is_snooze";
    private static final long MISSED_WINDOW_MS = 5L * 60L * 1_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_TRIGGER.equals(intent.getAction())) return;
        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L);
        long scheduledAt = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULED_AT, 0L);
        boolean snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false);
        AlarmRepository repository = new AlarmRepository(context);
        Alarm alarm = repository.find(alarmId);
        if (alarm == null || !alarm.enabled()) return;

        long lateness = Math.max(0L, System.currentTimeMillis() - scheduledAt);
        if (scheduledAt <= 0L || lateness > MISSED_WINDOW_MS) {
            advanceNormalSchedule(context, repository, alarm);
            return;
        }

        advanceNormalSchedule(context, repository, alarm);
        Intent service = new Intent(context, AlarmRingingService.class);
        service.setAction(AlarmRingingService.ACTION_START);
        service.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId);
        service.putExtra(EXTRA_SNOOZE, snooze);
        context.startForegroundService(service);
    }

    private static void advanceNormalSchedule(
            Context context, AlarmRepository repository, Alarm alarm) {
        Alarm advanced = alarm.afterOccurrence(System.currentTimeMillis());
        repository.save(advanced);
        if (advanced.enabled()) new AlarmScheduler(context).schedule(advanced);
    }
}
