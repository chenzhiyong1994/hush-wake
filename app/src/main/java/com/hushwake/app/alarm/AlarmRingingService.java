package com.hushwake.app.alarm;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import com.hushwake.app.HushWakeApplication;
import com.hushwake.app.R;
import com.hushwake.app.audio.PrivatePlaybackEngine;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.noise.WhiteNoiseService;
import com.hushwake.app.noise.NoiseSessionStore;
import java.time.Instant;

public final class AlarmRingingService extends Service
        implements PrivatePlaybackEngine.Listener {
    public static final String ACTION_START = "com.hushwake.app.action.START_ALARM";
    public static final String ACTION_STOP = "com.hushwake.app.action.STOP_ALARM";
    public static final String ACTION_SNOOZE = "com.hushwake.app.action.SNOOZE_ALARM";
    public static final String ACTION_STATE = "com.hushwake.app.action.ALARM_STATE";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_DETAIL = "detail";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_CAN_SNOOZE = "can_snooze";
    public static final String EXTRA_STOP_ALARM_ID = "stop_alarm_id";
    public static final String INTERNAL_STATE_PERMISSION =
            "com.hushwake.app.permission.INTERNAL_STATE";

    private static final int NOTIFICATION_ID = 4101;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Alarm alarm;
    private PrivatePlaybackEngine engine;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private boolean snoozeOccurrence;
    private boolean finished;
    private boolean audibleReached;
    private boolean safetyFailureReached;
    private String currentDetail = "正在建立安全播放会话";
    private String currentVerification = "未验证";
    private long currentMuteLatency = -1L;

    @Override
    public void onCreate() {
        super.onCreate();
        vibrator = getSystemService(VibratorManager.class).getDefaultVibrator();
        wakeLock =
                getSystemService(PowerManager.class)
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HushWake:private-alarm");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            long requestedAlarmId = intent.getLongExtra(EXTRA_STOP_ALARM_ID, 0L);
            if (requestedAlarmId > 0L
                    && alarm != null
                    && requestedAlarmId != alarm.id()) {
                return START_NOT_STICKY;
            }
            complete("stopped", "用户已停止");
            return START_NOT_STICKY;
        }
        if (ACTION_SNOOZE.equals(action)) {
            snooze();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        long alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L);
        resetActiveOccurrence();
        alarm = new AlarmRepository(this).find(alarmId);
        snoozeOccurrence = intent.getBooleanExtra(AlarmTriggerReceiver.EXTRA_SNOOZE, false);
        if (alarm == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!wakeLock.isHeld()) {
            wakeLock.acquire((UnifiedAlarmPolicy.MAX_RING_SECONDS + 15L) * 1_000L);
        }
        startForeground(NOTIFICATION_ID, notification("正在选择智能输出"));
        broadcastState("STARTING", "正在选择智能输出");
        NoiseSessionStore.Snapshot noise = new NoiseSessionStore(this).load();
        if (!"stopped".equals(noise.state())) {
            startService(
                    new Intent(this, WhiteNoiseService.class)
                            .setAction(WhiteNoiseService.ACTION_STOP));
        }
        handler.postDelayed(this::startPlaybackAfterNoiseStops, 250L);
        handler.postDelayed(
                () -> complete("timeout", "达到最长响铃时间，已自动结束"),
                UnifiedAlarmPolicy.MAX_RING_SECONDS * 1_000L);
        return START_NOT_STICKY;
    }

    private void startPlaybackAfterNoiseStops() {
        if (finished || alarm == null || engine != null) return;
        engine =
                new PrivatePlaybackEngine(
                        this,
                        new PrivatePlaybackEngine.Config(
                                PrivatePlaybackEngine.Purpose.ALARM,
                                alarm.soundId(),
                                UnifiedAlarmPolicy.APP_GAIN_PERCENT,
                                UnifiedAlarmPolicy.FADE_IN_SECONDS),
                        this);
        engine.start();
    }

    @Override
    public void onState(
            PrivatePlaybackEngine.State state,
            String detail,
            String verificationLevel,
            long muteLatencyMs) {
        if (finished && state == PrivatePlaybackEngine.State.STOPPED) return;
        currentDetail = detail;
        currentVerification = verificationLevel;
        currentMuteLatency = muteLatencyMs;
        if (state == PrivatePlaybackEngine.State.AUDIBLE) audibleReached = true;
        if (state == PrivatePlaybackEngine.State.BLOCKED && alarm != null) {
            safetyFailureReached = true;
            if (UnifiedAlarmPolicy.VIBRATE_WHEN_BLOCKED) startFallbackVibration();
        }
        android.app.NotificationManager notifications =
                getSystemService(android.app.NotificationManager.class);
        notifications.notify(NOTIFICATION_ID, notification(detail));
        broadcastState(state.name(), detail);
    }

    private void snooze() {
        if (alarm == null || snoozeOccurrence) return;
        try {
            Instant next = Instant.now().plusSeconds(UnifiedAlarmPolicy.SNOOZE_MINUTES * 60L);
            new AlarmScheduler(this).scheduleSnooze(alarm.id(), next);
            complete("snoozed", "已稍后提醒 " + UnifiedAlarmPolicy.SNOOZE_MINUTES + " 分钟");
        } catch (RuntimeException error) {
            currentDetail = "无法精确安排稍后提醒";
            broadcastState("BLOCKED", currentDetail);
        }
    }

    private void complete(String eventType, String detail) {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (engine != null) {
            engine.release();
            engine = null;
        }
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (alarm != null) {
            new AppPreferences(this)
                    .completeTestAlarm(alarm.id(), audibleReached && !safetyFailureReached);
        }
        broadcastState("STOPPED", detail);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification notification(String detail) {
        Intent screen = new Intent(this, RingingActivity.class);
        screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        screen.putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm == null ? 0L : alarm.id());
        PendingIntent fullScreen =
                PendingIntent.getActivity(
                        this,
                        5101,
                        screen,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stop =
                new Notification.Action.Builder(
                                null,
                                "停止",
                                serviceAction(ACTION_STOP, 5102))
                        .build();
        Notification.Builder builder =
                new Notification.Builder(this, HushWakeApplication.ALARM_CHANNEL)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(
                                alarm == null || alarm.label().isBlank() ? "悄醒" : alarm.label())
                        .setContentText(detail)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setFullScreenIntent(fullScreen, true)
                        .setContentIntent(fullScreen)
                        .addAction(stop);
        if (alarm != null && !snoozeOccurrence) {
            builder.addAction(
                    new Notification.Action.Builder(
                                    null,
                                    "稍后 " + UnifiedAlarmPolicy.SNOOZE_MINUTES + " 分钟",
                                    serviceAction(ACTION_SNOOZE, 5103))
                            .build());
        }
        return builder.build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, AlarmRingingService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void startFallbackVibration() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(
                VibrationEffect.createWaveform(new long[] {0L, 500L, 500L, 500L, 900L}, 1));
    }

    private void broadcastState(String state, String detail) {
        boolean canSnooze =
                alarm != null && !snoozeOccurrence;
        new AlarmSessionStore(this)
                .save(
                        new AlarmSessionStore.Snapshot(
                                alarm == null ? 0L : alarm.id(),
                                state,
                                detail,
                                alarm == null ? "" : alarm.label(),
                                canSnooze));
        Intent update = new Intent(ACTION_STATE).setPackage(getPackageName());
        update.putExtra(EXTRA_STATE, state);
        update.putExtra(EXTRA_DETAIL, detail);
        update.putExtra(EXTRA_LABEL, alarm == null ? "" : alarm.label());
        update.putExtra(
                EXTRA_CAN_SNOOZE,
                canSnooze);
        sendBroadcast(update, INTERNAL_STATE_PERMISSION);
    }

    private void resetActiveOccurrence() {
        handler.removeCallbacksAndMessages(null);
        boolean hadActiveOccurrence = alarm != null && !finished;
        finished = true;
        if (engine != null) {
            PrivatePlaybackEngine previous = engine;
            engine = null;
            previous.release();
        }
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (hadActiveOccurrence) {
            new AppPreferences(this).completeTestAlarm(alarm.id(), false);
        }
        finished = false;
        audibleReached = false;
        safetyFailureReached = false;
        currentDetail = "正在建立安全播放会话";
        currentVerification = "未验证";
        currentMuteLatency = -1L;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        boolean unexpected = !finished && alarm != null;
        PrivatePlaybackEngine current = engine;
        engine = null;
        finished = true;
        if (current != null) current.release();
        if (vibrator != null) vibrator.cancel();
        if (unexpected) {
            broadcastState("STOPPED", "系统结束了响铃服务");
            new AppPreferences(this).completeTestAlarm(alarm.id(), false);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
