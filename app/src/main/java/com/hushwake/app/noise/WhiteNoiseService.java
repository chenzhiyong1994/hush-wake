package com.hushwake.app.noise;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.hushwake.app.HomeActivity;
import com.hushwake.app.HushWakeApplication;
import com.hushwake.app.R;
import com.hushwake.app.audio.PrivatePlaybackEngine;

/** Foreground sleep-sound playback; timer continues while paused and never exceeds eight hours. */
public final class WhiteNoiseService extends Service
        implements PrivatePlaybackEngine.Listener {
    public static final String ACTION_START = "com.hushwake.app.action.NOISE_START";
    public static final String ACTION_PAUSE = "com.hushwake.app.action.NOISE_PAUSE";
    public static final String ACTION_RESUME = "com.hushwake.app.action.NOISE_RESUME";
    public static final String ACTION_SWITCH_SOUND = "com.hushwake.app.action.NOISE_SWITCH_SOUND";
    public static final String ACTION_STOP = "com.hushwake.app.action.NOISE_STOP";
    public static final String ACTION_STATE = "com.hushwake.app.action.NOISE_STATE";
    public static final String EXTRA_SOUND_ID = "sound_id";
    public static final String EXTRA_TIMER_MINUTES = "timer_minutes";
    public static final String EXTRA_FADE_SECONDS = "fade_seconds";

    private static final int NOTIFICATION_ID = 4201;
    private static final long MAX_SESSION_MS = 8L * 60L * 60L * 1_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PrivatePlaybackEngine engine;
    private NoiseSessionStore store;
    private String soundId = "rain";
    private int fadeSeconds = 15;
    private long endsAtEpochMs;
    private boolean paused;
    private boolean stopping;
    private boolean completed;
    private String detail = "正在建立安全播放会话";

    @Override
    public void onCreate() {
        super.onCreate();
        store = new NoiseSessionStore(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSession("用户停止");
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pause();
            return START_NOT_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            resume();
            return START_NOT_STICKY;
        }
        if (ACTION_SWITCH_SOUND.equals(action)) {
            switchSound(intent.getStringExtra(EXTRA_SOUND_ID));
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;

        soundId = SleepSoundCatalog.normalizeId(intent.getStringExtra(EXTRA_SOUND_ID));
        fadeSeconds = clamp(intent.getIntExtra(EXTRA_FADE_SECONDS, 15), 0, 30);
        int timerMinutes = clamp(intent.getIntExtra(EXTRA_TIMER_MINUTES, 30), 0, 480);
        long requested = timerMinutes == 0 ? MAX_SESSION_MS : timerMinutes * 60_000L;
        endsAtEpochMs = System.currentTimeMillis() + Math.min(requested, MAX_SESSION_MS);
        stopping = false;
        completed = false;
        paused = false;
        startForeground(NOTIFICATION_ID, notification("正在选择智能输出"));
        scheduleEnd();
        startEngine();
        return START_NOT_STICKY;
    }

    @Override
    public void onState(
            PrivatePlaybackEngine.State state,
            String newDetail,
            String verificationLevel,
            long muteLatencyMs) {
        detail = newDetail;
        if (state == PrivatePlaybackEngine.State.BLOCKED) {
            store.save(snapshot("blocked", newDetail));
            getSystemService(android.app.NotificationManager.class)
                    .notify(NOTIFICATION_ID, notification(newDetail));
            broadcast();
            handler.postDelayed(() -> stopSession("声音已阻断"), 2_000L);
            return;
        }
        if (state == PrivatePlaybackEngine.State.AUDIBLE) {
            store.save(snapshot("playing", newDetail));
        }
        if (!stopping) {
            getSystemService(android.app.NotificationManager.class)
                    .notify(NOTIFICATION_ID, notification(newDetail));
            broadcast();
        }
    }

    private void startEngine() {
        if (System.currentTimeMillis() >= endsAtEpochMs) {
            stopSession("定时结束");
            return;
        }
        if (engine != null) engine.release();
        engine =
                new PrivatePlaybackEngine(
                        this,
                        new PrivatePlaybackEngine.Config(
                                PrivatePlaybackEngine.Purpose.WHITE_NOISE,
                                soundId,
                                100,
                                2),
                        this);
        engine.start();
    }

    private void pause() {
        if (paused || stopping) return;
        paused = true;
        if (engine != null) {
            engine.release();
            engine = null;
        }
        detail = "已暂停；定时器继续计时";
        store.save(snapshot("paused", detail));
        getSystemService(android.app.NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(detail));
        broadcast();
    }

    private void resume() {
        if (!paused || stopping) return;
        paused = false;
        detail = "正在重新选择智能输出";
        scheduleEnd();
        startEngine();
    }

    private void switchSound(String requestedSoundId) {
        if (stopping || endsAtEpochMs <= 0L) return;
        SleepSoundSwitchPolicy.Decision decision =
                SleepSoundSwitchPolicy.decide(
                        soundId, requestedSoundId, paused, endsAtEpochMs, fadeSeconds);
        if (!decision.changed()) return;
        soundId = decision.soundId();
        detail = paused ? "已切换为" + soundLabel(soundId) + "；继续后播放" : "正在切换为" + soundLabel(soundId);
        store.save(snapshot(paused ? "paused" : "switching", detail));
        getSystemService(android.app.NotificationManager.class)
                .notify(NOTIFICATION_ID, notification(detail));
        broadcast();
        if (decision.restartPlaybackNow()) startEngine();
    }

    private void scheduleEnd() {
        handler.removeCallbacksAndMessages(null);
        long remaining = Math.max(0L, endsAtEpochMs - System.currentTimeMillis());
        if (remaining == 0L) {
            stopSession("定时结束");
            return;
        }
        long fadeAt = Math.max(0L, remaining - fadeSeconds * 1_000L);
        if (fadeSeconds > 0 && fadeAt < remaining) {
            handler.postDelayed(this::startFadeOut, fadeAt);
        }
        handler.postDelayed(() -> stopSession("定时结束"), remaining);
    }

    private void startFadeOut() {
        if (paused || engine == null || stopping) return;
        final long started = System.currentTimeMillis();
        Runnable step =
                new Runnable() {
                    @Override
                    public void run() {
                        if (engine == null || stopping || paused) return;
                        float progress =
                                Math.min(
                                        1f,
                                        (System.currentTimeMillis() - started)
                                                / (float) Math.max(1, fadeSeconds * 1_000));
                        engine.setVolumePercent(Math.round(100f * (1f - progress)));
                        detail = "定时渐隐中";
                        store.save(snapshot("fading", detail));
                        broadcast();
                        if (progress < 1f) handler.postDelayed(this, 100L);
                    }
                };
        handler.post(step);
    }

    private void stopSession(String reason) {
        if (stopping) return;
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        if (engine != null) {
            engine.release();
            engine = null;
        }
        detail = reason;
        store.save(snapshot("stopped", detail));
        completed = true;
        broadcast();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, HomeActivity.class);
        open.putExtra(HomeActivity.EXTRA_SCREEN, HomeActivity.SCREEN_NOISE);
        PendingIntent content =
                PendingIntent.getActivity(
                        this,
                        5201,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder =
                new Notification.Builder(this, HushWakeApplication.NOISE_CHANNEL)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("悄醒 · " + soundLabel(soundId))
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_TRANSPORT)
                        .setOngoing(true)
                        .setContentIntent(content)
                        .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (paused) {
            builder.addAction(action("继续", ACTION_RESUME, 5202));
        } else {
            builder.addAction(action("暂停", ACTION_PAUSE, 5202));
        }
        builder.addAction(action("停止", ACTION_STOP, 5203));
        return builder.build();
    }

    private Notification.Action action(String title, String action, int requestCode) {
        Intent intent = new Intent(this, WhiteNoiseService.class).setAction(action);
        PendingIntent pending =
                PendingIntent.getService(
                        this,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(null, title, pending).build();
    }

    private NoiseSessionStore.Snapshot snapshot(String state, String text) {
        return new NoiseSessionStore.Snapshot(
                state, soundId, endsAtEpochMs, fadeSeconds, text);
    }

    private void broadcast() {
        sendBroadcast(
                new Intent(ACTION_STATE).setPackage(getPackageName()),
                com.hushwake.app.alarm.AlarmRingingService.INTERNAL_STATE_PERMISSION);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static String soundLabel(String id) {
        return SleepSoundCatalog.label(id);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopping = true;
        PrivatePlaybackEngine current = engine;
        engine = null;
        if (current != null) current.release();
        if (!completed && store != null) {
            detail = "系统结束了助眠声服务";
            store.save(snapshot("stopped", detail));
            broadcast();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
