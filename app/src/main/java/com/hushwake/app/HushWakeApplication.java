package com.hushwake.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;

public final class HushWakeApplication extends Application {
    public static final String ALARM_CHANNEL = "private_alarm";
    public static final String NOISE_CHANNEL = "white_noise";

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        NotificationChannel alarm =
                new NotificationChannel(
                        ALARM_CHANNEL, "私密闹钟", NotificationManager.IMPORTANCE_HIGH);
        alarm.setDescription("到点处置、停止与稍后提醒；系统通知本身永不播放声音");
        alarm.setSound(null, (AudioAttributes) null);
        alarm.enableVibration(false);
        alarm.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        alarm.setBypassDnd(false);
        notifications.createNotificationChannel(alarm);

        NotificationChannel noise =
                new NotificationChannel(
                        NOISE_CHANNEL, "耳机白噪音", NotificationManager.IMPORTANCE_LOW);
        noise.setDescription("受耳机隐私守卫保护的后台环境音控制");
        noise.setSound(null, (AudioAttributes) null);
        noise.enableVibration(false);
        notifications.createNotificationChannel(noise);
    }
}
