package com.hushwake.app.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.hushwake.app.data.DeviceVerificationRepository;
import com.hushwake.app.domain.DeviceVerification;
import com.hushwake.app.domain.DeviceVerificationPolicy;
import com.hushwake.app.platform.PlatformVersion;
import com.hushwake.app.noise.SleepSoundCatalog;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Smart-output session shared by alarms and white noise. Speaker playback is allowed only when no
 * headset is present; headset sessions remain silent until device verification and actual routing
 * both pass.
 */
public final class PrivatePlaybackEngine {
    public static final int AUDIO_ENGINE_VERSION = 3;

    public enum Purpose { ALARM, WHITE_NOISE }
    public enum State { IDLE, PREPARING_SILENT, VERIFYING_ROUTE, AUDIBLE, BLOCKED, STOPPED }

    public record Config(
            Purpose purpose, String soundId, int volumePercent, int fadeInSeconds) {
        public Config {
            if (purpose == null || soundId == null || soundId.isBlank()) {
                throw new IllegalArgumentException("Playback purpose and sound are required");
            }
            if (volumePercent < 0 || volumePercent > 100 || fadeInSeconds < 0) {
                throw new IllegalArgumentException("Invalid volume or fade duration");
            }
        }
    }

    public interface Listener {
        void onState(State state, String detail, String verificationLevel, long muteLatencyMs);
    }

    private static final int SAMPLE_RATE = 48_000;
    private static final long ROUTE_TIMEOUT_MS = 1_000L;
    private static final long ROUTE_SETTLE_MS = 50L;

    private final Context context;
    private final AudioManager audioManager;
    private final AudioRouteInspector inspector = new AudioRouteInspector();
    private final DeviceVerificationRepository verifications;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean samplesAudible = new AtomicBoolean(false);
    private final MuteLatencyTracker latencyTracker = new MuteLatencyTracker();
    private final Config config;
    private final Listener listener;

    private State state = State.IDLE;
    private AudioDeviceInfo targetDevice;
    private AudioTrack audioTrack;
    private MediaPlayer mediaPlayer;
    private AudioFocusRequest focusRequest;
    private volatile boolean writerRunning;
    private long routeDeadlineMs;
    private long muteLatencyMs = -1L;
    private long fadeGeneration;
    private SmartOutputPolicy.Mode outputMode = SmartOutputPolicy.Mode.BLOCKED;
    private String verificationLevel = "未验证";

    private final AudioRouting.OnRoutingChangedListener routingListener =
            router -> handleRoutingSignal();

    private final AudioDeviceCallback deviceCallback =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    if (targetDevice == null) return;
                    for (AudioDeviceInfo removed : removedDevices) {
                        if (removed.getId() == targetDevice.getId()) {
                            latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
                            block("HEADSET_DISCONNECTED", "耳机已断开，声音已阻断");
                            return;
                        }
                    }
                }

                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    if (state != State.VERIFYING_ROUTE && state != State.AUDIBLE) {
                        return;
                    }
                    AudioRouteInspector.Snapshot snapshot = inspector.snapshot(audioManager);
                    if (outputMode == SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
                        beginPublicRouteRecheck();
                        return;
                    }
                    revalidateAfterRouteSignal();
                    if (snapshot.personalOutputTypes().size() != 1) {
                        block("AMBIGUOUS_OUTPUT", "检测到多个候选输出，声音已阻断");
                    }
                }
            };

    private final Runnable routePoller = this::checkRoute;
    private final Runnable smartOutputPoller = this::settlePublicRouteSignal;

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            change -> {
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
                    block("AUDIO_FOCUS_LOST", "其他应用占用音频，声音已阻断");
                }
            };

    public PrivatePlaybackEngine(Context context, Config config, Listener listener) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.verifications = new DeviceVerificationRepository(context);
        this.config = config;
        this.listener = listener;
    }

    public void start() {
        if (state != State.IDLE) return;
        latencyTracker.onRouteVerifiedSafe();
        muteLatencyMs = -1L;
        verificationLevel = "未验证";
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            block("BLUETOOTH_PERMISSION_REQUIRED", "需要蓝牙连接权限以安全选择智能输出");
            return;
        }
        AudioRouteInspector.Snapshot snapshot = inspector.snapshot(audioManager);
        outputMode =
                SmartOutputPolicy.choose(
                        snapshot.mediaVolume(), snapshot.personalOutputTypes().size());
        if (outputMode == SmartOutputPolicy.Mode.BLOCKED) {
            String reason =
                    snapshot.mediaVolume() <= 0
                            ? "系统媒体音量为 0；应用不会自动调高"
                            : "检测到多个候选耳机；请只保留一个连接";
            block("OUTPUT_UNAVAILABLE", reason);
            return;
        }
        if (outputMode == SmartOutputPolicy.Mode.PRIVATE_HEADSET) {
            targetDevice = snapshot.preferredTarget();
            if (!hasValidHeadsetVerification(targetDevice)) {
                return;
            }
        } else {
            targetDevice = null;
            verificationLevel = "智能外放";
        }
        state = State.PREPARING_SILENT;
        notifyState(
                outputMode == SmartOutputPolicy.Mode.PRIVATE_HEADSET
                        ? "耳机模式以双层静音准备"
                        : "智能输出正在建立系统媒体播放");
        if (!requestAudioFocus()) {
            block("AUDIO_FOCUS_UNAVAILABLE", "当前无法获得音频焦点");
            return;
        }
        notifyState("播放器以双层静音启动");
        if (config.purpose() == Purpose.WHITE_NOISE) {
            createSilentRecordingPlayer();
        } else {
            createSilentTrack();
        }
    }

    public void stop() {
        if (state == State.STOPPED) return;
        muteNow();
        releaseTrack();
        abandonAudioFocus();
        state = State.STOPPED;
        notifyState("播放已停止");
    }

    public void setVolumePercent(int percent) {
        if (percent < 0 || percent > 100) return;
        if (state == State.AUDIBLE && activeRouter() != null) {
            setPlaybackVolume(percent / 100f);
        }
    }

    public State state() { return state; }

    public void release() {
        stop();
        mainHandler.removeCallbacksAndMessages(null);
        writerExecutor.shutdownNow();
    }

    private boolean requestAudioFocus() {
        AudioAttributes attributes = audioAttributes();
        int focusGain =
                config.purpose() == Purpose.ALARM
                        ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                        : AudioManager.AUDIOFOCUS_GAIN;
        focusRequest =
                new AudioFocusRequest.Builder(focusGain)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(focusListener, mainHandler)
                        .setWillPauseWhenDucked(true)
                        .build();
        return audioManager.requestAudioFocus(focusRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void createSilentTrack() {
        try {
            int minBuffer =
                    AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(minBuffer, SAMPLE_RATE / 5 * 2);
            audioTrack =
                    new AudioTrack.Builder()
                            .setAudioAttributes(audioAttributes())
                            .setAudioFormat(
                                    new AudioFormat.Builder()
                                            .setSampleRate(SAMPLE_RATE)
                                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                            .build())
                            .setBufferSizeInBytes(bufferBytes)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();
            samplesAudible.set(false);
            audioTrack.setVolume(0f);
            if (outputMode == SmartOutputPolicy.Mode.PRIVATE_HEADSET
                    && !audioTrack.setPreferredDevice(targetDevice)) {
                block("PREFERRED_ROUTE_REJECTED", "Android 拒绝目标耳机路由请求");
                return;
            }
            audioTrack.addOnRoutingChangedListener(routingListener, mainHandler);
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
            writerRunning = true;
            AudioTrack writerTrack = audioTrack;
            writerExecutor.execute(() -> writeSamples(writerTrack));
            audioTrack.play();
            if (outputMode == SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
                state = State.VERIFYING_ROUTE;
                routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
                notifyState("正在确认系统媒体外放路由");
                mainHandler.postDelayed(smartOutputPoller, ROUTE_SETTLE_MS);
            } else {
                state = State.VERIFYING_ROUTE;
                routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
                notifyState("正在读取播放器实际耳机路由");
                mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
            }
        } catch (RuntimeException error) {
            block("PLAYER_ERROR", "播放器创建失败：" + error.getClass().getSimpleName());
        }
    }

    private void createSilentRecordingPlayer() {
        try (AssetFileDescriptor source =
                context.getResources()
                        .openRawResourceFd(SleepSoundCatalog.resourceId(config.soundId()))) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(audioAttributes());
            mediaPlayer.setDataSource(
                    source.getFileDescriptor(), source.getStartOffset(), source.getLength());
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnErrorListener(
                    (player, what, extra) -> {
                        block("PLAYER_ERROR", "助眠录音播放失败");
                        return true;
                    });
            if (outputMode == SmartOutputPolicy.Mode.PRIVATE_HEADSET
                    && !mediaPlayer.setPreferredDevice(targetDevice)) {
                block("PREFERRED_ROUTE_REJECTED", "Android 拒绝目标耳机路由请求");
                return;
            }
            mediaPlayer.addOnRoutingChangedListener(routingListener, mainHandler);
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
            mediaPlayer.prepare();
            mediaPlayer.start();
            beginRouteVerification();
        } catch (IOException | RuntimeException error) {
            block("PLAYER_ERROR", "助眠录音播放失败：" + error.getClass().getSimpleName());
        }
    }

    private void beginRouteVerification() {
        state = State.VERIFYING_ROUTE;
        routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
        if (outputMode == SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
            notifyState("正在确认系统媒体外放路由");
            mainHandler.postDelayed(smartOutputPoller, ROUTE_SETTLE_MS);
        } else {
            notifyState("正在读取播放器实际耳机路由");
            mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
        }
    }

    private AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(
                        config.purpose() == Purpose.ALARM
                                ? AudioAttributes.CONTENT_TYPE_SONIFICATION
                                : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
    }

    private void checkRoute() {
        if (outputMode != SmartOutputPolicy.Mode.PRIVATE_HEADSET
                || activeRouter() == null
                || (state != State.VERIFYING_ROUTE && state != State.AUDIBLE)) {
            return;
        }
        AudioRouteInspector.RouteEvaluation evaluation = inspector.evaluate(activeRouter(), targetDevice);
        if (evaluation.status() == AudioRouteInspector.RouteStatus.PENDING) {
            if (state == State.VERIFYING_ROUTE && SystemClock.elapsedRealtime() < routeDeadlineMs) {
                mainHandler.postDelayed(routePoller, 50L);
            } else {
                block("ROUTE_TIMEOUT", "无法在安全窗口内验证实际输出");
            }
            return;
        }
        if (evaluation.status() == AudioRouteInspector.RouteStatus.UNSAFE) {
            block("UNSAFE_ROUTE", evaluation.summary());
            return;
        }
        latencyTracker.onRouteVerifiedSafe();
        if (state == State.VERIFYING_ROUTE) {
            verificationLevel =
                    evaluation.status() == AudioRouteInspector.RouteStatus.SAFE_STRONG
                            ? "强验证"
                            : "兼容验证";
            state = State.AUDIBLE;
            samplesAudible.set(true);
            notifyState("仅耳机播放中 · " + evaluation.summary());
            startFadeIn();
        }
    }

    private void startFadeIn() {
        final long generation = ++fadeGeneration;
        final long started = SystemClock.elapsedRealtime();
        final long durationMs = config.fadeInSeconds() * 1_000L;
        final float target = config.volumePercent() / 100f;
        Runnable fade =
                new Runnable() {
                    @Override
                    public void run() {
                        if (activeRouter() == null
                                || state != State.AUDIBLE
                                || generation != fadeGeneration) return;
                        float progress =
                                durationMs == 0L
                                        ? 1f
                                        : Math.min(
                                                1f,
                                                (SystemClock.elapsedRealtime() - started)
                                                        / (float) durationMs);
                        setPlaybackVolume(target * progress);
                        if (progress < 1f) mainHandler.postDelayed(this, 50L);
                    }
                };
        mainHandler.post(fade);
    }

    private void revalidateAfterRouteSignal() {
        if (outputMode != SmartOutputPolicy.Mode.PRIVATE_HEADSET) {
            return;
        }
        latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
        if (state == State.AUDIBLE) {
            muteNow();
            state = State.VERIFYING_ROUTE;
            routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
            notifyState("路由信号到达，已静音并重新验证实际输出");
            mainHandler.removeCallbacks(routePoller);
            mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
        } else if (state == State.VERIFYING_ROUTE) {
            mainHandler.removeCallbacks(routePoller);
            mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
        }
    }

    private void handleRoutingSignal() {
        if (outputMode == SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
            beginPublicRouteRecheck();
        } else {
            revalidateAfterRouteSignal();
        }
    }

    private void beginPublicRouteRecheck() {
        if (outputMode != SmartOutputPolicy.Mode.PUBLIC_MEDIA
                || (state != State.AUDIBLE && state != State.VERIFYING_ROUTE)) {
            return;
        }
        latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
        if (state == State.AUDIBLE) {
            muteNow();
            state = State.VERIFYING_ROUTE;
            routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
            notifyState("输出发生变化，已静音并重新选择智能输出");
        }
        mainHandler.removeCallbacks(smartOutputPoller);
        mainHandler.postDelayed(smartOutputPoller, ROUTE_SETTLE_MS);
    }

    private void settlePublicRouteSignal() {
        if (outputMode != SmartOutputPolicy.Mode.PUBLIC_MEDIA
                || state != State.VERIFYING_ROUTE) {
            return;
        }
        AudioRouteInspector.Snapshot snapshot = inspector.snapshot(audioManager);
        SmartOutputPolicy.Mode next =
                SmartOutputPolicy.reselectAfterDeviceChange(
                        outputMode,
                        snapshot.mediaVolume(),
                        snapshot.personalOutputTypes().size());
        if (next == SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
            AudioRouteInspector.RouteEvaluation evaluation =
                    inspector.evaluatePhoneSpeaker(activeRouter());
            if (evaluation.status() == AudioRouteInspector.RouteStatus.PENDING) {
                if (SystemClock.elapsedRealtime() < routeDeadlineMs) {
                    mainHandler.postDelayed(smartOutputPoller, 50L);
                } else {
                    block("PUBLIC_ROUTE_TIMEOUT", "无法在安全窗口内确认系统媒体外放");
                }
                return;
            }
            boolean phoneSpeaker =
                    evaluation.status() == AudioRouteInspector.RouteStatus.SAFE_STRONG
                            || evaluation.status() == AudioRouteInspector.RouteStatus.SAFE_COMPATIBLE;
            if (SmartOutputPolicy.confirmPublicRoute(
                            snapshot.mediaVolume(),
                            snapshot.personalOutputTypes().size(),
                            phoneSpeaker)
                    != SmartOutputPolicy.Mode.PUBLIC_MEDIA) {
                block("UNSUPPORTED_PUBLIC_ROUTE", evaluation.summary());
                return;
            }
            latencyTracker.onRouteVerifiedSafe();
            verificationLevel = "智能外放";
            state = State.AUDIBLE;
            samplesAudible.set(true);
            notifyState("智能输出 · 当前使用系统媒体外放");
            startFadeIn();
            return;
        }
        if (next != SmartOutputPolicy.Mode.PRIVATE_HEADSET) {
            block("AMBIGUOUS_OUTPUT", "耳机接入后目标不唯一，声音已阻断");
            return;
        }
        targetDevice = snapshot.preferredTarget();
        if (!hasValidHeadsetVerification(targetDevice)) {
            return;
        }
        AudioRouting router = activeRouter();
        if (router == null || !router.setPreferredDevice(targetDevice)) {
            block("PREFERRED_ROUTE_REJECTED", "耳机接入后 Android 拒绝目标路由请求");
            return;
        }
        outputMode = SmartOutputPolicy.Mode.PRIVATE_HEADSET;
        verificationLevel = "未验证";
        state = State.VERIFYING_ROUTE;
        routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
        notifyState("检测到耳机，已停止外放并验证耳机实际路由");
        mainHandler.removeCallbacks(routePoller);
        mainHandler.postDelayed(routePoller, ROUTE_SETTLE_MS);
    }

    private boolean hasValidHeadsetVerification(AudioDeviceInfo device) {
        DeviceIdentity identity = DeviceFingerprint.create(context, device);
        if (identity == null) {
            block("DEVICE_IDENTITY_UNAVAILABLE", "无法安全识别当前耳机；请重新连接或更换耳机");
            return false;
        }
        DeviceVerification record = verifications.find(identity.hash());
        boolean valid =
                DeviceVerificationPolicy.isValid(
                        record,
                        identity.hash(),
                        PlatformVersion.androidMajor(),
                        AUDIO_ENGINE_VERSION,
                        Instant.now());
        if (!valid) {
            block("PRIVACY_TEST_REQUIRED", "当前耳机尚无有效隐私测试记录");
            return false;
        }
        return true;
    }

    private void block(String reasonCode, String detail) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> block(reasonCode, detail));
            return;
        }
        if (state == State.BLOCKED || state == State.STOPPED) return;
        muteNow();
        releaseTrack();
        abandonAudioFocus();
        state = State.BLOCKED;
        listener.onState(state, reasonCode + " · " + detail, verificationLevel, muteLatencyMs);
    }

    private void muteNow() {
        fadeGeneration++;
        samplesAudible.set(false);
        AudioTrack current = audioTrack;
        if (current != null) {
            try {
                current.setVolume(0f);
            } catch (RuntimeException ignored) {
                // A concurrently failed/released track cannot emit app audio.
            }
        }
        MediaPlayer recording = mediaPlayer;
        if (recording != null) {
            try {
                recording.setVolume(0f, 0f);
            } catch (RuntimeException ignored) {
                // A concurrently failed/released player cannot emit app audio.
            }
        }
        long measured = latencyTracker.consumeOnMute(SystemClock.elapsedRealtimeNanos());
        if (measured >= 0L) muteLatencyMs = measured;
    }

    private void releaseTrack() {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacks(routePoller);
        mainHandler.removeCallbacks(smartOutputPoller);
        writerRunning = false;
        AudioTrack current = audioTrack;
        audioTrack = null;
        MediaPlayer recording = mediaPlayer;
        mediaPlayer = null;
        if (current == null && recording == null) return;
        try { audioManager.unregisterAudioDeviceCallback(deviceCallback); } catch (RuntimeException ignored) {}
        if (current != null) {
            try { current.removeOnRoutingChangedListener(routingListener); } catch (RuntimeException ignored) {}
            try {
                current.pause();
                current.flush();
                current.stop();
            } catch (RuntimeException ignored) {
                // Release is the terminal action.
            }
            current.release();
        }
        if (recording != null) {
            try { recording.removeOnRoutingChangedListener(routingListener); } catch (RuntimeException ignored) {}
            try { recording.stop(); } catch (RuntimeException ignored) {}
            recording.release();
        }
    }

    private AudioRouting activeRouter() {
        return mediaPlayer != null ? mediaPlayer : audioTrack;
    }

    private void setPlaybackVolume(float volume) {
        if (audioTrack != null) audioTrack.setVolume(volume);
        if (mediaPlayer != null) mediaPlayer.setVolume(volume, volume);
    }

    private void abandonAudioFocus() {
        if (focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    private void notifyState(String detail) {
        listener.onState(state, detail, verificationLevel, muteLatencyMs);
    }

    private void writeSamples(AudioTrack writerTrack) {
        short[] buffer = new short[960];
        AlarmSynth synth = new AlarmSynth(config.soundId());
        while (writerRunning && !Thread.currentThread().isInterrupted()) {
            if (samplesAudible.get()) {
                synth.fill(buffer);
            } else {
                java.util.Arrays.fill(buffer, (short) 0);
            }
            int written = writerTrack.write(buffer, 0, buffer.length, AudioTrack.WRITE_BLOCKING);
            if (written < 0 && writerRunning) {
                mainHandler.post(() -> block("PLAYER_WRITE_ERROR", "播放器写入失败"));
                return;
            }
        }
    }

    private static final class AlarmSynth {
        private final String soundId;
        private long frame;

        AlarmSynth(String soundId) {
            this.soundId = soundId;
        }

        void fill(short[] target) {
            for (int i = 0; i < target.length; i++, frame++) {
                double sample = alarmSample();
                target[i] = (short) (Math.max(-1d, Math.min(1d, sample)) * Short.MAX_VALUE * 0.35d);
            }
        }

        private double alarmSample() {
            double seconds = frame / (double) SAMPLE_RATE;
            double period = seconds % 3.0;
            if (period > 1.75) return 0d;
            double attack = Math.min(1d, period / 0.08d);
            double release = Math.min(1d, (1.75d - period) / 0.35d);
            double envelope = Math.max(0d, Math.min(attack, release));
            double base = "bright_chime".equals(soundId) ? 659.25d : 440d;
            if ("horizon".equals(soundId)) base = 392d;
            return envelope
                    * (Math.sin(2d * Math.PI * base * seconds)
                            + 0.38d * Math.sin(2d * Math.PI * base * 1.5d * seconds))
                    / 1.38d;
        }

    }
}
