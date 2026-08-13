package com.hushwake.app.noise;

/** Produces one unambiguous timer summary for the active or next sleep-sound session. */
public final class NoiseTimerPresentation {
    private NoiseTimerPresentation() {}

    public record ViewState(
            boolean active,
            String remainingLabel,
            String savedTimerLabel,
            String fadeLabel,
            boolean showNextSessionSettings) {}

    public static ViewState resolve(
            String sessionState,
            long endsAtEpochMs,
            long nowEpochMs,
            int savedTimerMinutes,
            int fadeSeconds) {
        boolean active = !"stopped".equals(sessionState) && !"blocked".equals(sessionState);
        String remaining = "尚未开始";
        if (active && endsAtEpochMs > nowEpochMs) {
            long minutes = Math.max(1L, (endsAtEpochMs - nowEpochMs + 59_999L) / 60_000L);
            remaining = "约 " + minutes + " 分钟后结束";
        } else if (active) {
            remaining = "即将结束";
        }
        String savedTimer =
                savedTimerMinutes == 0
                        ? "持续播放（最长 8 小时）"
                        : savedTimerMinutes + " 分钟";
        String fade = fadeSeconds == 0 ? "直接结束" : fadeSeconds + " 秒渐隐";
        return new ViewState(active, remaining, savedTimer, fade, !active);
    }
}
