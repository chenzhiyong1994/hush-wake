package com.hushwake.app.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.hushwake.app.data.DeviceVerificationRepository;
import com.hushwake.app.domain.DeviceVerification;
import com.hushwake.app.domain.DeviceVerificationPolicy;
import com.hushwake.app.platform.PlatformVersion;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fail-closed audio session shared by alarms and white noise. The writer emits zero samples and the
 * AudioTrack gain remains zero until the current device verification and actual route both pass.
 */
public final class PrivatePlaybackEngine {
    public static final int AUDIO_ENGINE_VERSION = 2;

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
    private AudioFocusRequest focusRequest;
    private volatile boolean writerRunning;
    private long routeDeadlineMs;
    private long muteLatencyMs = -1L;
    private String verificationLevel = "未验证";

    private final AudioRouting.OnRoutingChangedListener routingListener =
            router -> {
                latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
                if (state == State.AUDIBLE) {
                    block("ROUTE_CHANGED", "有声期间实际输出发生变化，声音已立即阻断");
                } else {
                    checkRoute();
                }
            };

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
                    if (state != State.VERIFYING_ROUTE && state != State.AUDIBLE) return;
                    latencyTracker.onRouteSignal(SystemClock.elapsedRealtimeNanos());
                    if (state == State.AUDIBLE) {
                        block("OUTPUT_SET_CHANGED", "有声期间输出设备集合发生变化，声音已立即阻断");
                        return;
                    }
                    AudioRouteInspector.Snapshot snapshot = inspector.snapshot(audioManager);
                    if (snapshot.personalOutputTypes().size() != 1) {
                        block("AMBIGUOUS_OUTPUT", "检测到多个候选输出，声音已阻断");
                    } else {
                        checkRoute();
                    }
                }
            };

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
        AudioRouteInspector.Snapshot snapshot = inspector.snapshot(audioManager);
        if (!snapshot.canStart()) {
            block("OUTPUT_UNAVAILABLE", snapshot.blockingReason());
            return;
        }
        targetDevice = snapshot.preferredTarget();
        DeviceIdentity identity = DeviceFingerprint.create(context, targetDevice);
        if (identity == null) {
            block("DEVICE_IDENTITY_UNAVAILABLE", "无法安全识别当前耳机；请重新连接或更换耳机");
            return;
        }
        DeviceVerification record = verifications.find(identity.hash());
        boolean verificationValid =
                DeviceVerificationPolicy.isValid(
                        record,
                        identity.hash(),
                        PlatformVersion.androidMajor(),
                        AUDIO_ENGINE_VERSION,
                        Instant.now());
        if (!verificationValid) {
            block("PRIVACY_TEST_REQUIRED", "当前耳机尚无有效隐私测试记录");
            return;
        }
        state = State.PREPARING_SILENT;
        notifyState("播放器以双层静音准备");
        if (!requestAudioFocus()) {
            block("AUDIO_FOCUS_UNAVAILABLE", "当前无法获得音频焦点");
            return;
        }
        notifyState("播放器以双层静音启动");
        createSilentTrack();
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
        if (state == State.AUDIBLE && audioTrack != null) {
            audioTrack.setVolume(percent / 100f);
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
            if (!audioTrack.setPreferredDevice(targetDevice)) {
                block("PREFERRED_ROUTE_REJECTED", "Android 拒绝目标耳机路由请求");
                return;
            }
            audioTrack.addOnRoutingChangedListener(routingListener, mainHandler);
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
            writerRunning = true;
            AudioTrack writerTrack = audioTrack;
            writerExecutor.execute(() -> writeSamples(writerTrack));
            audioTrack.play();
            state = State.VERIFYING_ROUTE;
            routeDeadlineMs = SystemClock.elapsedRealtime() + ROUTE_TIMEOUT_MS;
            notifyState("正在读取播放器实际路由");
            mainHandler.post(this::checkRoute);
        } catch (RuntimeException error) {
            block("PLAYER_ERROR", "播放器创建失败：" + error.getClass().getSimpleName());
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
        if (audioTrack == null || (state != State.VERIFYING_ROUTE && state != State.AUDIBLE)) {
            return;
        }
        AudioRouteInspector.RouteEvaluation evaluation = inspector.evaluate(audioTrack, targetDevice);
        if (evaluation.status() == AudioRouteInspector.RouteStatus.PENDING) {
            if (state == State.VERIFYING_ROUTE && SystemClock.elapsedRealtime() < routeDeadlineMs) {
                mainHandler.postDelayed(this::checkRoute, 50L);
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
        final long started = SystemClock.elapsedRealtime();
        final long durationMs = config.fadeInSeconds() * 1_000L;
        final float target = config.volumePercent() / 100f;
        Runnable fade =
                new Runnable() {
                    @Override
                    public void run() {
                        if (audioTrack == null || state != State.AUDIBLE) return;
                        float progress =
                                durationMs == 0L
                                        ? 1f
                                        : Math.min(
                                                1f,
                                                (SystemClock.elapsedRealtime() - started)
                                                        / (float) durationMs);
                        audioTrack.setVolume(target * progress);
                        if (progress < 1f) mainHandler.postDelayed(this, 50L);
                    }
                };
        mainHandler.post(fade);
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
        samplesAudible.set(false);
        AudioTrack current = audioTrack;
        if (current != null) {
            try {
                current.setVolume(0f);
            } catch (RuntimeException ignored) {
                // A concurrently failed/released track cannot emit app audio.
            }
        }
        long measured = latencyTracker.consumeOnMute(SystemClock.elapsedRealtimeNanos());
        if (measured >= 0L) muteLatencyMs = measured;
    }

    private void releaseTrack() {
        mainHandler.removeCallbacksAndMessages(null);
        writerRunning = false;
        AudioTrack current = audioTrack;
        audioTrack = null;
        if (current == null) return;
        try { current.removeOnRoutingChangedListener(routingListener); } catch (RuntimeException ignored) {}
        try { audioManager.unregisterAudioDeviceCallback(deviceCallback); } catch (RuntimeException ignored) {}
        try {
            current.pause();
            current.flush();
            current.stop();
        } catch (RuntimeException ignored) {
            // Release is the terminal action.
        }
        current.release();
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
        Synth synth = new Synth(config.soundId(), config.purpose());
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

    private static final class Synth {
        private final String soundId;
        private final Purpose purpose;
        private long frame;
        private long random = 0x6a09e667f3bcc909L;
        private double low;
        private double mid;

        Synth(String soundId, Purpose purpose) {
            this.soundId = soundId;
            this.purpose = purpose;
        }

        void fill(short[] target) {
            for (int i = 0; i < target.length; i++, frame++) {
                double sample = purpose == Purpose.ALARM ? alarmSample() : noiseSample();
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

        private double noiseSample() {
            random ^= random << 13;
            random ^= random >>> 7;
            random ^= random << 17;
            double white = ((random >>> 11) / (double) (1L << 53)) * 2d - 1d;
            low += 0.015d * (white - low);
            mid += 0.08d * (white - mid);
            if ("pink".equals(soundId)) return low * 0.8d + mid * 0.2d;
            if ("ocean".equals(soundId)) {
                double swell = 0.42d + 0.35d * Math.sin(2d * Math.PI * frame / SAMPLE_RATE / 7d);
                return (low * 0.85d + mid * 0.15d) * swell;
            }
            if ("campfire".equals(soundId)) {
                double crackle = Math.abs(white) > 0.985d ? white * 0.8d : 0d;
                return low * 0.65d + crackle;
            }
            return low * 0.7d + mid * 0.3d;
        }
    }
}
