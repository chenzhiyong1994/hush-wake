package com.hushwake.app.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects only coarse audio-route facts. Device names, addresses and stable IDs never leave memory. */
public final class AudioRouteInspector {
    public enum RouteStatus {
        PENDING,
        SAFE_STRONG,
        SAFE_COMPATIBLE,
        UNSAFE
    }

    public static final class Snapshot {
        private final int apiLevel;
        private final int mediaVolume;
        private final int maxMediaVolume;
        private final List<String> personalOutputTypes;
        private final AudioDeviceInfo preferredTarget;
        private final String blockingReason;

        private Snapshot(
                int apiLevel,
                int mediaVolume,
                int maxMediaVolume,
                List<String> personalOutputTypes,
                AudioDeviceInfo preferredTarget,
                String blockingReason) {
            this.apiLevel = apiLevel;
            this.mediaVolume = mediaVolume;
            this.maxMediaVolume = maxMediaVolume;
            this.personalOutputTypes = List.copyOf(personalOutputTypes);
            this.preferredTarget = preferredTarget;
            this.blockingReason = blockingReason;
        }

        public int apiLevel() {
            return apiLevel;
        }

        public int mediaVolume() {
            return mediaVolume;
        }

        public int maxMediaVolume() {
            return maxMediaVolume;
        }

        public List<String> personalOutputTypes() {
            return personalOutputTypes;
        }

        public String blockingReason() {
            return blockingReason;
        }

        public String verificationMode() {
            return apiLevel >= 36 ? "强验证 · API 36+" : "兼容验证 · API 31–35";
        }

        public boolean canStart() {
            return blockingReason == null;
        }

        public AudioDeviceInfo preferredTarget() {
            return preferredTarget;
        }
    }

    public static final class RouteEvaluation {
        private final RouteStatus status;
        private final String summary;

        private RouteEvaluation(RouteStatus status, String summary) {
            this.status = status;
            this.summary = summary;
        }

        public RouteStatus status() {
            return status;
        }

        public String summary() {
            return summary;
        }
    }

    public Snapshot snapshot(AudioManager audioManager) {
        List<AudioDeviceInfo> candidates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (isAllowedPersonalOutput(device)) {
                candidates.add(device);
                labels.add(typeLabel(device.getType()));
            }
        }

        int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        String reason = null;
        AudioDeviceInfo target = null;
        if (volume <= 0) {
            reason = "系统媒体音量为 0；应用不会自动调高";
        } else if (candidates.isEmpty()) {
            reason = "未检测到受支持的个人音频输出";
        } else if (candidates.size() > 1) {
            reason = "检测到多个候选输出；请只保留一个耳机连接";
        } else {
            target = candidates.get(0);
        }

        return new Snapshot(
                Build.VERSION.SDK_INT,
                volume,
                maxVolume,
                Collections.unmodifiableList(labels),
                target,
                reason);
    }

    public RouteEvaluation evaluate(AudioTrack track, AudioDeviceInfo target) {
        if (track == null || target == null) {
            return new RouteEvaluation(RouteStatus.UNSAFE, "播放器或目标输出不存在");
        }
        if (Build.VERSION.SDK_INT >= 36) {
            List<AudioDeviceInfo> routed = track.getRoutedDevices();
            if (routed.isEmpty()) {
                return new RouteEvaluation(RouteStatus.PENDING, "等待实际路由建立");
            }
            boolean containsTarget = false;
            List<String> labels = new ArrayList<>();
            for (AudioDeviceInfo device : routed) {
                labels.add(typeLabel(device.getType()));
                if (!isAllowedPersonalOutput(device)) {
                    return new RouteEvaluation(
                            RouteStatus.UNSAFE,
                            "实际路由包含非个人输出：" + joinLabels(labels));
                }
                if (device.getId() == target.getId()) {
                    containsTarget = true;
                }
            }
            if (!containsTarget) {
                return new RouteEvaluation(RouteStatus.UNSAFE, "实际路由未包含目标耳机");
            }
            return new RouteEvaluation(RouteStatus.SAFE_STRONG, joinLabels(labels));
        }

        AudioDeviceInfo routed = track.getRoutedDevice();
        if (routed == null) {
            return new RouteEvaluation(RouteStatus.PENDING, "等待单路由证据");
        }
        if (!isAllowedPersonalOutput(routed) || routed.getId() != target.getId()) {
            return new RouteEvaluation(
                    RouteStatus.UNSAFE, "单路由证据不匹配目标耳机：" + typeLabel(routed.getType()));
        }
        return new RouteEvaluation(RouteStatus.SAFE_COMPATIBLE, typeLabel(routed.getType()));
    }

    public RouteEvaluation evaluatePhoneSpeaker(AudioTrack track) {
        if (track == null) {
            return new RouteEvaluation(RouteStatus.UNSAFE, "播放器不存在");
        }
        if (Build.VERSION.SDK_INT >= 36) {
            List<AudioDeviceInfo> routed = track.getRoutedDevices();
            if (routed.isEmpty()) {
                return new RouteEvaluation(RouteStatus.PENDING, "等待系统媒体路由建立");
            }
            for (AudioDeviceInfo device : routed) {
                if (device.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return new RouteEvaluation(
                            RouteStatus.UNSAFE,
                            "智能外放实际路由不是手机扬声器：" + typeLabel(device.getType()));
                }
            }
            return new RouteEvaluation(RouteStatus.SAFE_STRONG, "手机扬声器");
        }
        AudioDeviceInfo routed = track.getRoutedDevice();
        if (routed == null) {
            return new RouteEvaluation(RouteStatus.PENDING, "等待系统媒体路由建立");
        }
        if (routed.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return new RouteEvaluation(
                    RouteStatus.UNSAFE,
                    "智能外放实际路由不是手机扬声器：" + typeLabel(routed.getType()));
        }
        return new RouteEvaluation(RouteStatus.SAFE_COMPATIBLE, "手机扬声器");
    }

    public static boolean isAllowedPersonalOutput(AudioDeviceInfo device) {
        int type = device.getType();
        return type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_USB_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    public static String typeLabel(int type) {
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
            return "有线耳机";
        }
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
            return "有线耳麦";
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
            return "蓝牙 A2DP 设备";
        }
        if (type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return "USB 耳机";
        }
        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            return "蓝牙 LE 耳机";
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return "手机扬声器";
        }
        if (type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            return "手机听筒";
        }
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return "蓝牙通话设备";
        }
        return "其他输出（类型 " + type + "）";
    }

    private static String joinLabels(List<String> labels) {
        return labels.isEmpty() ? "无路由" : String.join(" + ", labels);
    }
}
