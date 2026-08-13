package com.hushwake.app.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.hushwake.app.guard.OutputGuard;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the real-device privacy test. Both the PCM generator and AudioTrack gain stay at zero until
 * the pure guard has accepted current route evidence.
 */
public final class AudioSafetyEngine {
    public interface Listener {
        void onSnapshot(AudioRouteInspector.Snapshot snapshot);

        void onGuardState(OutputGuard.State state, String title, String detail);

        void onCountdown(int secondsRemaining);

        void onLog(String message);

        void onBlocked(TestReport report);

        void onConfirmationRequested(TestReport report);
    }

    private static final int SAMPLE_RATE = 48_000;
    private static final long ROUTE_TIMEOUT_MS = 1_000L;
    private static final long ROUTE_SETTLE_MS = 50L;
    private static final long TEST_DURATION_MS = 10_000L;
    private static final float TEST_GAIN = 0.12f;

    private final AudioManager audioManager;
    private final Context context;
    private final AudioRouteInspector inspector = new AudioRouteInspector();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pcmAudible = new AtomicBoolean(false);
    private final MuteLatencyTracker muteLatencyTracker = new MuteLatencyTracker();
    private final Listener listener;

    private OutputGuard guard = new OutputGuard();
    private AudioRouteInspector.Snapshot snapshot;
    private AudioDeviceInfo targetDevice;
    private AudioTrack audioTrack;
    private volatile boolean writerRunning;
    private long routeDeadlineMs;
    private long muteLatencyMs = -1L;
    private long testEndsAtMs;
    private long fadeGeneration;
    private String targetType = "无";
    private String routeSummary = "尚未建立";
    private String verificationLabel = "未通过";

    private final AudioRouting.OnRoutingChangedListener routingListener =
            router -> revalidateAfterRouteSignal();

    private final AudioDeviceCallback deviceCallback =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    if (targetDevice != null) {
                        for (AudioDeviceInfo removed : removedDevices) {
                            if (removed.getId() == targetDevice.getId()) {
                                muteLatencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
                                failForRouteChange(OutputGuard.BlockReason.ROUTE_LOST);
                                return;
                            }
                        }
                    }
                    refreshSnapshot();
                }

                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if (guard.state() == OutputGuard.State.AUDIBLE
                            || guard.state() == OutputGuard.State.VERIFYING_ROUTE) {
                        revalidateAfterRouteSignal();
                    }
                    snapshot = inspector.snapshot(audioManager);
                    listener.onSnapshot(snapshot);
                    if (guard.state() == OutputGuard.State.VERIFYING_ROUTE) {
                        if (snapshot.personalOutputTypes().size() != 1) {
                            failForRouteChange(OutputGuard.BlockReason.MULTIPLE_COMPATIBLE_OUTPUTS);
                        }
                    }
                }
            };

    private final Runnable routePoller = this::checkRouteNow;
    private final Runnable testFinisher = this::finishForConfirmation;
    private final Runnable countdownTicker =
            new Runnable() {
                @Override
                public void run() {
                    if (guard.state() != OutputGuard.State.AUDIBLE) {
                        return;
                    }
                    long remaining = Math.max(0L, testEndsAtMs - SystemClock.elapsedRealtime());
                    listener.onCountdown((int) Math.ceil(remaining / 1_000.0));
                    if (remaining > 0L) {
                        mainHandler.postDelayed(this, 250L);
                    }
                }
            };

    public AudioSafetyEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.listener = listener;
    }

    /** Returns only the irreversible local identity for the currently tested target. */
    public DeviceIdentity currentTargetIdentity() {
        return DeviceFingerprint.create(context, targetDevice);
    }

    public void refreshSnapshot() {
        snapshot = inspector.snapshot(audioManager);
        listener.onSnapshot(snapshot);
    }

    public void startTest() {
        snapshot = inspector.snapshot(audioManager);
        listener.onSnapshot(snapshot);
        if (!snapshot.canStart()) {
            TestReport report =
                    new TestReport(
                            "已阻断",
                            apiBucket(),
                            "未验证",
                            joinTypes(snapshot),
                            "未建立",
                            snapshot.blockingReason(),
                            -1L);
            listener.onBlocked(report);
            return;
        }

        muteImmediately();
        stopPlayer();
        guard = new OutputGuard();
        targetDevice = snapshot.preferredTarget();
        targetType = AudioRouteInspector.typeLabel(targetDevice.getType());
        routeSummary = "尚未建立";
        verificationLabel = "未通过";
        muteLatencyTracker.onRouteVerifiedSafe();
        muteLatencyMs = -1L;
        listener.onLog("01  应用增益归零");
        dispatch(OutputGuard.Event.begin());
    }

    public void stopByUser() {
        if (guard.state() == OutputGuard.State.PREPARING_SILENT
                || guard.state() == OutputGuard.State.VERIFYING_ROUTE
                || guard.state() == OutputGuard.State.AUDIBLE) {
            dispatch(OutputGuard.Event.stop());
            listener.onGuardState(OutputGuard.State.STOPPED, "测试已停止", "播放器已先静音再释放");
        } else {
            muteImmediately();
            stopPlayer();
        }
    }

    public void release() {
        stopByUser();
        mainHandler.removeCallbacksAndMessages(null);
        audioExecutor.shutdownNow();
    }

    private void dispatch(OutputGuard.Event event) {
        OutputGuard.Decision decision = guard.accept(event);
        for (OutputGuard.Action action : decision.actions()) {
            execute(action);
        }
        publishGuardState(decision);
    }

    private void execute(OutputGuard.Action action) {
        switch (action) {
            case MUTE:
                muteImmediately();
                break;
            case START_SILENT:
                startSilentPlayer();
                break;
            case VERIFY_ROUTE:
                listener.onLog("03  读取当前实际路由");
                routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
                mainHandler.removeCallbacks(routePoller);
                mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
                break;
            case FADE_IN:
                listener.onLog("04  路由通过，低增益渐强");
                startControlledFadeIn();
                break;
            case STOP_PLAYER:
                stopPlayer();
                break;
            case RECORD_BLOCKED:
                TestReport report = blockedReport(guard.blockReason());
                listener.onBlocked(report);
                break;
        }
    }

    private void startSilentPlayer() {
        try {
            int minBuffer =
                    AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(minBuffer, SAMPLE_RATE / 5 * 2);
            AudioAttributes attributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
            AudioFormat format =
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build();
            audioTrack =
                    new AudioTrack.Builder()
                            .setAudioAttributes(attributes)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferBytes)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();
            pcmAudible.set(false);
            audioTrack.setVolume(0f);
            listener.onLog("02  播放器以双层静音启动");

            if (!audioTrack.setPreferredDevice(targetDevice)) {
                enterVerifyingThenReject(OutputGuard.BlockReason.PREFERRED_ROUTE_REJECTED);
                return;
            }

            audioTrack.addOnRoutingChangedListener(routingListener, mainHandler);
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
            writerRunning = true;
            audioTrack.play();
            AudioTrack writerTrack = audioTrack;
            audioExecutor.execute(() -> writePcm(writerTrack));
            dispatch(OutputGuard.Event.playerStarted());
        } catch (RuntimeException error) {
            listener.onLog("播放器创建失败：" + error.getClass().getSimpleName());
            enterVerifyingThenReject(OutputGuard.BlockReason.PLAYER_ERROR);
        }
    }

    private void enterVerifyingThenReject(OutputGuard.BlockReason reason) {
        if (guard.state() == OutputGuard.State.PREPARING_SILENT) {
            dispatch(OutputGuard.Event.playerStarted());
        }
        if (guard.state() == OutputGuard.State.VERIFYING_ROUTE) {
            dispatch(OutputGuard.Event.routeRejected(reason));
        }
    }

    private void writePcm(AudioTrack writerTrack) {
        short[] samples = new short[960];
        double phase = 0.0;
        double phaseStep = 2.0 * Math.PI * 523.25 / SAMPLE_RATE;
        while (writerRunning && !Thread.currentThread().isInterrupted()) {
            boolean makeTone = pcmAudible.get();
            for (int i = 0; i < samples.length; i++) {
                samples[i] = makeTone ? (short) (Math.sin(phase) * Short.MAX_VALUE * 0.35) : 0;
                phase += phaseStep;
                if (phase >= Math.PI * 2.0) {
                    phase -= Math.PI * 2.0;
                }
            }
            int written = writerTrack.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
            if (written < 0 && writerRunning) {
                mainHandler.post(this::handlePlayerError);
                return;
            }
        }
    }

    private void checkRouteNow() {
        if (audioTrack == null) {
            return;
        }
        OutputGuard.State state = guard.state();
        if (state != OutputGuard.State.VERIFYING_ROUTE && state != OutputGuard.State.AUDIBLE) {
            return;
        }

        AudioRouteInspector.RouteEvaluation evaluation = inspector.evaluate(audioTrack, targetDevice);
        routeSummary = evaluation.summary();
        if (evaluation.status() == AudioRouteInspector.RouteStatus.PENDING) {
            if (state == OutputGuard.State.VERIFYING_ROUTE
                    && SystemClock.elapsedRealtime() < routeDeadlineMs) {
                mainHandler.postDelayed(routePoller, 50L);
            } else if (state == OutputGuard.State.VERIFYING_ROUTE) {
                dispatch(OutputGuard.Event.routeRejected(OutputGuard.BlockReason.ROUTE_TIMEOUT));
            } else {
                failForRouteChange(OutputGuard.BlockReason.ROUTE_LOST);
            }
            return;
        }

        if (evaluation.status() == AudioRouteInspector.RouteStatus.UNSAFE) {
            failForRouteChange(OutputGuard.BlockReason.UNSAFE_ROUTE);
            return;
        }

        muteLatencyTracker.onRouteVerifiedSafe();

        if (state == OutputGuard.State.VERIFYING_ROUTE) {
            OutputGuard.VerificationLevel level =
                    evaluation.status() == AudioRouteInspector.RouteStatus.SAFE_STRONG
                            ? OutputGuard.VerificationLevel.STRONG
                            : OutputGuard.VerificationLevel.COMPATIBLE;
            verificationLabel = level == OutputGuard.VerificationLevel.STRONG ? "强验证" : "兼容验证";
            dispatch(OutputGuard.Event.routeVerified(level));
        }
    }

    private void failForRouteChange(OutputGuard.BlockReason reason) {
        if (guard.state() == OutputGuard.State.AUDIBLE) {
            dispatch(OutputGuard.Event.routeLost(reason));
        } else if (guard.state() == OutputGuard.State.VERIFYING_ROUTE) {
            dispatch(OutputGuard.Event.routeRejected(reason));
        }
    }

    private void revalidateAfterRouteSignal() {
        muteLatencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
        if (guard.state() == OutputGuard.State.AUDIBLE) {
            listener.onLog("路由信号到达，先静音并重新验证实际输出");
            dispatch(OutputGuard.Event.routeSignal());
        } else if (guard.state() == OutputGuard.State.VERIFYING_ROUTE) {
            mainHandler.removeCallbacks(routePoller);
            mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
        }
    }

    private void handlePlayerError() {
        listener.onLog("播放器写入失败，执行失败静音");
        if (guard.state() == OutputGuard.State.AUDIBLE) {
            muteLatencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
            dispatch(OutputGuard.Event.routeLost(OutputGuard.BlockReason.PLAYER_ERROR));
        } else {
            enterVerifyingThenReject(OutputGuard.BlockReason.PLAYER_ERROR);
        }
    }

    private void startControlledFadeIn() {
        pcmAudible.set(true);
        final long generation = ++fadeGeneration;
        final long started = SystemClock.elapsedRealtime();
        Runnable fade =
                new Runnable() {
                    @Override
                    public void run() {
                        if (audioTrack == null
                                || guard.state() != OutputGuard.State.AUDIBLE
                                || generation != fadeGeneration) {
                            return;
                        }
                        float progress =
                                Math.min(1f, (SystemClock.elapsedRealtime() - started) / 700f);
                        audioTrack.setVolume(TEST_GAIN * progress);
                        if (progress < 1f) {
                            mainHandler.postDelayed(this, 50L);
                        }
                    }
                };
        mainHandler.post(fade);
        testEndsAtMs = SystemClock.elapsedRealtime() + TEST_DURATION_MS;
        mainHandler.post(countdownTicker);
        mainHandler.postDelayed(testFinisher, TEST_DURATION_MS);
    }

    private void finishForConfirmation() {
        if (guard.state() != OutputGuard.State.AUDIBLE) {
            return;
        }
        dispatch(OutputGuard.Event.stop());
        TestReport report =
                new TestReport(
                        "待人工确认",
                        apiBucket(),
                        verificationLabel,
                        targetType,
                        routeSummary,
                        "10 秒低音量测试音已完成",
                        muteLatencyMs);
        listener.onConfirmationRequested(report);
    }

    private void muteImmediately() {
        fadeGeneration++;
        pcmAudible.set(false);
        AudioTrack current = audioTrack;
        if (current != null) {
            try {
                current.setVolume(0f);
            } catch (IllegalStateException ignored) {
                // A concurrently released track is already unable to output app audio.
            }
        }
        long measuredLatency = muteLatencyTracker.consumeOnMute(SystemClock.elapsedRealtimeNanos());
        if (measuredLatency >= 0L) {
            muteLatencyMs = measuredLatency;
        }
        mainHandler.removeCallbacks(testFinisher);
        mainHandler.removeCallbacks(countdownTicker);
    }

    private void stopPlayer() {
        mainHandler.removeCallbacks(routePoller);
        pcmAudible.set(false);
        AudioTrack current = audioTrack;
        audioTrack = null;
        if (current != null) {
            try {
                current.setVolume(0f);
            } catch (IllegalStateException ignored) {
                // A released track cannot emit application audio.
            }
        }
        writerRunning = false;
        if (current != null) {
            try {
                current.removeOnRoutingChangedListener(routingListener);
            } catch (RuntimeException ignored) {
                // Listener may not have been registered if setup failed.
            }
            try {
                audioManager.unregisterAudioDeviceCallback(deviceCallback);
            } catch (RuntimeException ignored) {
                // Callback may not have been registered if setup failed.
            }
            try {
                current.pause();
                current.flush();
                current.stop();
            } catch (IllegalStateException ignored) {
                // Release below is the terminal safety action.
            }
            current.release();
        }
    }

    private void publishGuardState(OutputGuard.Decision decision) {
        switch (decision.state()) {
            case PREPARING_SILENT:
                listener.onGuardState(decision.state(), "静音准备", "播放器增益已锁定为 0");
                break;
            case VERIFYING_ROUTE:
                listener.onGuardState(decision.state(), "验证实际路由", "未通过前不会产生测试音");
                break;
            case AUDIBLE:
                listener.onGuardState(
                        decision.state(), "仅耳机测试中", verificationLabel + " · " + routeSummary);
                break;
            case BLOCKED:
                listener.onGuardState(decision.state(), "声音已阻断", reasonLabel(decision.blockReason()));
                break;
            case STOPPED:
                listener.onGuardState(decision.state(), "测试音已静音", "等待你的听感确认");
                break;
            default:
                listener.onGuardState(decision.state(), "安全待机", "输出不确定时保持安静");
                break;
        }
    }

    private TestReport blockedReport(OutputGuard.BlockReason reason) {
        return new TestReport(
                "已阻断",
                apiBucket(),
                verificationLabel,
                targetType,
                routeSummary,
                reasonLabel(reason),
                muteLatencyMs);
    }

    private String apiBucket() {
        return android.os.Build.VERSION.SDK_INT >= 36 ? "API 36+" : "API 31–35";
    }

    private static String joinTypes(AudioRouteInspector.Snapshot snapshot) {
        return snapshot.personalOutputTypes().isEmpty()
                ? "无"
                : String.join(" + ", snapshot.personalOutputTypes());
    }

    private static String reasonLabel(OutputGuard.BlockReason reason) {
        if (reason == null) {
            return "未知原因（保持静音）";
        }
        switch (reason) {
            case ROUTE_LOST:
                return "目标耳机已断开或路由已丢失";
            case NO_COMPATIBLE_OUTPUT:
                return "没有兼容的个人音频输出";
            case MULTIPLE_COMPATIBLE_OUTPUTS:
                return "多个候选输出导致路由不确定";
            case PREFERRED_ROUTE_REJECTED:
                return "系统拒绝设置目标输出";
            case ROUTE_TIMEOUT:
                return "1 秒内未建立可验证路由";
            case UNSAFE_ROUTE:
                return "实际路由包含扬声器或与目标不匹配";
            case PLAYER_ERROR:
                return "播放器初始化或写入失败";
            default:
                return "未知原因（保持静音）";
        }
    }
}
