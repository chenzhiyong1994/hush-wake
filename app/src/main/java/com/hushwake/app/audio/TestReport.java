package com.hushwake.app.audio;

/** Coarse, local-only test report. It intentionally excludes exact times and device identifiers. */
public final class TestReport {
    private final String result;
    private final String apiBucket;
    private final String verificationLevel;
    private final String deviceType;
    private final String routeSummary;
    private final String reason;
    private final long muteLatencyMs;

    public TestReport(
            String result,
            String apiBucket,
            String verificationLevel,
            String deviceType,
            String routeSummary,
            String reason,
            long muteLatencyMs) {
        this.result = safe(result);
        this.apiBucket = safe(apiBucket);
        this.verificationLevel = safe(verificationLevel);
        this.deviceType = safe(deviceType);
        this.routeSummary = safe(routeSummary);
        this.reason = safe(reason);
        this.muteLatencyMs = muteLatencyMs;
    }

    public TestReport withResult(String newResult, String newReason) {
        return new TestReport(
                newResult,
                apiBucket,
                verificationLevel,
                deviceType,
                routeSummary,
                newReason,
                muteLatencyMs);
    }

    public String result() {
        return result;
    }

    public String format() {
        StringBuilder text = new StringBuilder();
        text.append("HushWake 路由实验室\n");
        text.append("结果: ").append(result).append('\n');
        text.append("Android: ").append(apiBucket).append('\n');
        text.append("验证级别: ").append(verificationLevel).append('\n');
        text.append("候选输出: ").append(deviceType).append('\n');
        text.append("实际路由: ").append(routeSummary).append('\n');
        if (!reason.isEmpty()) {
            text.append("说明: ").append(reason).append('\n');
        }
        if (muteLatencyMs >= 0) {
            text.append("检测到静音: ").append(muteLatencyMs).append(" ms\n");
        }
        text.append("隐私: 不含设备名称、地址、稳定标识或精确闹钟时间");
        return text.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
