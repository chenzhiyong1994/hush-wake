package com.hushwake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.audio.AudioRouteInspector;
import com.hushwake.app.audio.AudioSafetyEngine;
import com.hushwake.app.audio.DiagnosticsStore;
import com.hushwake.app.audio.DeviceIdentity;
import com.hushwake.app.audio.PrivatePlaybackEngine;
import com.hushwake.app.audio.TestReport;
import com.hushwake.app.data.DeviceVerificationRepository;
import com.hushwake.app.domain.DeviceVerification;
import com.hushwake.app.guard.OutputGuard;
import com.hushwake.app.platform.PlatformVersion;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public final class MainActivity extends Activity implements AudioSafetyEngine.Listener {
    private static final int INK = Color.rgb(9, 17, 15);
    private static final int PANEL = Color.rgb(16, 27, 24);
    private static final int PANEL_RAISED = Color.rgb(22, 36, 31);
    private static final int LINE = Color.rgb(49, 69, 61);
    private static final int PAPER = Color.rgb(239, 246, 236);
    private static final int MUTED = Color.rgb(157, 176, 166);
    private static final int ACID = Color.rgb(233, 255, 112);
    private static final int WARM = Color.rgb(255, 184, 107);
    private static final int DANGER = Color.rgb(255, 126, 112);

    private final Deque<String> logLines = new ArrayDeque<>();

    private AudioSafetyEngine engine;
    private DiagnosticsStore diagnosticsStore;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView modeValue;
    private TextView volumeValue;
    private TextView outputValue;
    private TextView readinessValue;
    private TextView logText;
    private TextView reportText;
    private CheckBox deviceConfirmation;
    private Button startButton;
    private Button stopButton;
    private AudioRouteInspector.Snapshot latestSnapshot;
    private String snapshotSignature = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        diagnosticsStore = new DiagnosticsStore(this);
        setContentView(buildScreen());
        configureWindow();
        engine = new AudioSafetyEngine(this, this);
        reportText.setText(diagnosticsStore.load());
        engine.refreshSnapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (engine != null) {
            engine.refreshSnapshot();
        }
    }

    @Override
    protected void onStop() {
        if (engine != null) {
            engine.stopByUser();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (engine != null) {
            engine.release();
        }
        super.onDestroy();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setNavigationBarContrastEnforced(false);
        WindowInsetsController controller = window.getDecorView().getWindowInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(INK);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(44));
        scroll.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(WindowInsets.Type.systemBars());
                        view.setPadding(0, bars.top, 0, bars.bottom);
                        return insets;
                    });
        }

        TextView eyebrow = text("HUSHWAKE  /  LAB 01", 12, ACID, Typeface.MONOSPACE);
        eyebrow.setLetterSpacing(0.16f);
        content.addView(eyebrow);

        TextView title = text("只在耳边\n响起。", 42, PAPER, Typeface.create("serif", Typeface.BOLD));
        title.setLineSpacing(0f, 0.92f);
        title.setPadding(0, dp(14), 0, dp(10));
        content.addView(title);

        TextView intro =
                text(
                        "这是首个实体机技术验证版。它不设置正式闹钟，只验证最重要的一件事：输出不确定时，应用必须保持安静。",
                        15,
                        MUTED,
                        Typeface.DEFAULT);
        intro.setLineSpacing(dp(4), 1f);
        content.addView(intro);
        content.addView(space(26));

        LinearLayout hero = verticalPanel(PANEL_RAISED, LINE, 22);
        TextView heroLabel = text("当前守卫", 11, MUTED, Typeface.MONOSPACE);
        heroLabel.setLetterSpacing(0.12f);
        hero.addView(heroLabel);
        statusTitle = text("安全待机", 29, ACID, Typeface.create("serif", Typeface.BOLD));
        statusTitle.setPadding(0, dp(10), 0, dp(5));
        hero.addView(statusTitle);
        statusDetail = text("输出不确定时保持安静", 14, PAPER, Typeface.DEFAULT);
        hero.addView(statusDetail);
        content.addView(hero);
        content.addView(space(14));

        LinearLayout evidence = verticalPanel(PANEL, LINE, 18);
        evidence.addView(sectionLabel("硬件证据 / LIVE"));
        modeValue = addEvidenceRow(evidence, "验证能力", "检查中");
        volumeValue = addEvidenceRow(evidence, "媒体音量", "检查中");
        outputValue = addEvidenceRow(evidence, "候选输出", "检查中");
        readinessValue = addEvidenceRow(evidence, "起播门禁", "检查中");
        content.addView(evidence);
        content.addView(space(14));

        LinearLayout actionPanel = verticalPanel(PANEL, LINE, 18);
        actionPanel.addView(sectionLabel("隐私测试 / 10 SEC"));
        TextView testNote =
                text(
                        "应用先播放零采样并将增益锁为 0；实际路由通过后，才以 12% 应用增益播放短促测试音。系统媒体音量不会被修改。",
                        14,
                        PAPER,
                        Typeface.DEFAULT);
        testNote.setLineSpacing(dp(3), 1f);
        testNote.setPadding(0, dp(12), 0, dp(12));
        actionPanel.addView(testNote);

        deviceConfirmation = new CheckBox(this);
        deviceConfirmation.setText("我确认当前唯一候选设备是仅本人佩戴的耳机");
        deviceConfirmation.setTextColor(PAPER);
        deviceConfirmation.setTextSize(14);
        deviceConfirmation.setButtonTintList(
                new ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {ACID, MUTED}));
        deviceConfirmation.setPadding(0, dp(2), 0, dp(12));
        deviceConfirmation.setOnCheckedChangeListener((button, checked) -> updateStartEnabled());
        actionPanel.addView(deviceConfirmation);

        startButton = button("开始 10 秒隐私测试", ACID, INK, true);
        startButton.setOnClickListener(
                view -> {
                    logLines.clear();
                    updateLog();
                    engine.startTest();
                });
        actionPanel.addView(startButton);

        stopButton = button("立即静音并停止", Color.TRANSPARENT, PAPER, false);
        stopButton.setVisibility(View.GONE);
        stopButton.setOnClickListener(view -> engine.stopByUser());
        LinearLayout.LayoutParams stopParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stopParams.topMargin = dp(10);
        stopButton.setLayoutParams(stopParams);
        actionPanel.addView(stopButton);
        content.addView(actionPanel);
        content.addView(space(14));

        LinearLayout trace = verticalPanel(PANEL, LINE, 18);
        trace.addView(sectionLabel("安全链路 / TRACE"));
        logText = text("等待测试", 13, MUTED, Typeface.MONOSPACE);
        logText.setLineSpacing(dp(6), 1f);
        logText.setPadding(0, dp(14), 0, 0);
        trace.addView(logText);
        content.addView(trace);
        content.addView(space(14));

        LinearLayout report = verticalPanel(PANEL, LINE, 18);
        report.addView(sectionLabel("本地诊断 / LAST RESULT"));
        reportText = text("尚无测试记录", 13, MUTED, Typeface.MONOSPACE);
        reportText.setLineSpacing(dp(4), 1f);
        reportText.setPadding(0, dp(14), 0, dp(12));
        report.addView(reportText);
        Button copy = button("复制诊断摘要", Color.TRANSPARENT, ACID, false);
        copy.setOnClickListener(view -> copyReport());
        report.addView(copy);
        content.addView(report);
        content.addView(space(22));

        TextView footer =
                text(
                        "边界说明  ·  API 31–35 只能获得单路由证据，仍需人工确认；API 36+ 才能枚举播放轨的全部实际输出。任何外放或不确定结果都应视为失败。",
                        12,
                        MUTED,
                        Typeface.DEFAULT);
        footer.setLineSpacing(dp(3), 1f);
        content.addView(footer);
        return scroll;
    }

    @Override
    public void onSnapshot(AudioRouteInspector.Snapshot snapshot) {
        latestSnapshot = snapshot;
        String signature = String.join("|", snapshot.personalOutputTypes());
        if (!signature.equals(snapshotSignature)) {
            snapshotSignature = signature;
            deviceConfirmation.setChecked(false);
        }
        modeValue.setText(snapshot.verificationMode());
        volumeValue.setText(
                getString(R.string.volume_value, snapshot.mediaVolume(), snapshot.maxMediaVolume()));
        outputValue.setText(
                snapshot.personalOutputTypes().isEmpty()
                        ? "未检测到"
                        : String.join(" + ", snapshot.personalOutputTypes()));
        if (snapshot.canStart()) {
            readinessValue.setText("等待人工确认耳机类型");
            readinessValue.setTextColor(WARM);
        } else {
            readinessValue.setText(snapshot.blockingReason());
            readinessValue.setTextColor(DANGER);
        }
        updateStartEnabled();
    }

    @Override
    public void onGuardState(OutputGuard.State state, String title, String detail) {
        statusTitle.setText(title);
        statusDetail.setText(detail);
        boolean active =
                state == OutputGuard.State.PREPARING_SILENT
                        || state == OutputGuard.State.VERIFYING_ROUTE
                        || state == OutputGuard.State.AUDIBLE;
        startButton.setVisibility(active ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(active ? View.VISIBLE : View.GONE);
        if (state == OutputGuard.State.BLOCKED) {
            statusTitle.setTextColor(DANGER);
        } else if (state == OutputGuard.State.AUDIBLE) {
            statusTitle.setTextColor(WARM);
        } else {
            statusTitle.setTextColor(ACID);
        }
    }

    @Override
    public void onCountdown(int secondsRemaining) {
        stopButton.setText(getString(R.string.stop_countdown, secondsRemaining));
    }

    @Override
    public void onLog(String message) {
        if (logLines.size() == 8) {
            logLines.removeFirst();
        }
        logLines.addLast(message);
        updateLog();
    }

    @Override
    public void onBlocked(TestReport report) {
        diagnosticsStore.save(report);
        reportText.setText(report.format());
        deviceConfirmation.setChecked(false);
        Toast.makeText(this, "为避免外放，本次声音已阻断", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onConfirmationRequested(TestReport report) {
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("刚才的声音只在耳机里吗？")
                        .setMessage("请按真实听感确认。只要听到外放，或无法确定，就选择“外放或不确定”。")
                        .setPositiveButton(
                                "是，只在耳机",
                                (ignored, which) ->
                                        saveManualResult(
                                                report.withResult(
                                                        "人工确认通过", "用户确认声音只在耳机中"),
                                                true))
                        .setNegativeButton(
                                "外放或不确定",
                                (ignored, which) ->
                                        saveManualResult(
                                                report.withResult(
                                                        "人工确认失败",
                                                        "听到外放或无法确认；该组合不得用于正式闹钟"),
                                                false))
                        .create();
        dialog.setOnCancelListener(
                ignored ->
                        saveManualResult(
                                report.withResult("人工确认未完成", "未确认结果；按失败处理"), false));
        dialog.show();
    }

    private void saveManualResult(TestReport report, boolean passed) {
        diagnosticsStore.save(report);
        DeviceIdentity identity = engine.currentTargetIdentity();
        if (passed && identity == null) {
            passed = false;
            report =
                    report.withResult(
                            "人工确认失败",
                            "无法为当前耳机生成本机身份；正式闹钟与白噪音保持有声阻断");
            diagnosticsStore.save(report);
        }
        if (identity != null) {
            new DeviceVerificationRepository(this)
                    .save(
                            new DeviceVerification(
                                    identity.hash(),
                                    identity.typeLabel(),
                                    PlatformVersion.androidMajor(),
                                    PrivatePlaybackEngine.AUDIO_ENGINE_VERSION,
                                    Instant.now(),
                                    passed));
        }
        reportText.setText(report.format());
        statusTitle.setText(passed ? "本次测试通过" : "本次测试失败");
        statusTitle.setTextColor(passed ? ACID : DANGER);
        statusDetail.setText(
                passed
                        ? "已绑定当前耳机，有效期 90 天；系统或音频引擎升级后需重测"
                        : "保持静音；该耳机组合不得用于私密闹钟");
        deviceConfirmation.setChecked(false);
    }

    private void updateStartEnabled() {
        boolean enabled =
                latestSnapshot != null
                        && latestSnapshot.canStart()
                        && deviceConfirmation.isChecked();
        startButton.setEnabled(enabled);
        startButton.setAlpha(enabled ? 1f : 0.38f);
        if (enabled) {
            readinessValue.setText("可以开始静音起播测试");
            readinessValue.setTextColor(ACID);
        }
    }

    private void updateLog() {
        logText.setText(logLines.isEmpty() ? "等待测试" : String.join("\n", logLines));
    }

    private void copyReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("HushWake 诊断摘要", diagnosticsStore.load()));
        Toast.makeText(this, "诊断摘要已复制", Toast.LENGTH_SHORT).show();
    }

    private TextView addEvidenceRow(LinearLayout parent, String label, String initialValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(13), 0, dp(10));
        TextView key = text(label, 11, MUTED, Typeface.MONOSPACE);
        key.setLetterSpacing(0.08f);
        TextView value = text(initialValue, 15, PAPER, Typeface.DEFAULT_BOLD);
        value.setPadding(0, dp(5), 0, 0);
        row.addView(key);
        row.addView(value);
        parent.addView(
                row,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
    }

    private TextView sectionLabel(String value) {
        TextView label = text(value, 11, WARM, Typeface.MONOSPACE);
        label.setLetterSpacing(0.12f);
        return label;
    }

    private LinearLayout verticalPanel(int color, int strokeColor, int padding) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        panel.setBackground(rounded(color, 20, strokeColor));
        panel.setElevation(dp(1));
        panel.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    private Button button(String label, int backgroundColor, int textColor, boolean filled) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(54));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable shape = rounded(backgroundColor, 16, filled ? backgroundColor : LINE);
        button.setBackground(
                new RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), shape, null));
        button.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private TextView text(String value, int sizeSp, int color, Typeface typeface) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(typeface);
        return text;
    }

    private Space space(int heightDp) {
        Space space = new Space(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return space;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
