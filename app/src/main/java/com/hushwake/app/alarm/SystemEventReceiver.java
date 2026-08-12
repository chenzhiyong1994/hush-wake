package com.hushwake.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.domain.Alarm;

/** Rebuilds system alarms after events that invalidate AlarmManager state or wall-clock time. */
public final class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            if (Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                AlarmRepository alarms = new AlarmRepository(context);
                long now = System.currentTimeMillis();
                for (Alarm alarm : alarms.listAll()) {
                    if (!alarm.isRepeating()
                            && alarm.enabled()
                            && Alarm.nextOneTimeInstant(
                                            alarm.oneTimeEpochDay(),
                                            alarm.hour(),
                                            alarm.minute(),
                                            java.time.ZoneId.systemDefault())
                                    .toEpochMilli()
                                    <= now) {
                        alarms.save(alarm.withEnabled(false, now));
                    }
                }
            }
            new AlarmScheduler(context).rescheduleAll();
        }
    }
}
