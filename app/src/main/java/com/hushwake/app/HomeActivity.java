package com.hushwake.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmRingingService;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.audio.AudioRouteInspector;
import com.hushwake.app.audio.DiagnosticsStore;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.data.DeviceVerificationRepository;
import com.hushwake.app.data.PlaybackEventRepository;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import com.hushwake.app.noise.NoiseSessionStore;
import com.hushwake.app.noise.WhiteNoiseService;
import com.hushwake.app.reliability.ReadinessChecker;
import com.hushwake.app.ui.Ui;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

public final class HomeActivity extends Activity {
    public static final String EXTRA_SCREEN = "screen";
    public static final String SCREEN_ALARMS = "alarms";
    public static final String SCREEN_NOISE = "noise";
    public static final String SCREEN_RELIABILITY = "reliability";
    public static final String SCREEN_HISTORY = "history";
    public static final String SCREEN_SETTINGS = "settings";

    private static final int REQUEST_NOTIFICATIONS = 1001;
    private static final int REQUEST_BLUETOOTH = 1002;
    private static final String INTERNAL_STATE_PERMISSION =
            "com.hushwake.app.permission.INTERNAL_STATE";

    private AlarmRepository alarmRepository;
    private AppPreferences preferences;
    private FrameLayout content;
    private LinearLayout navigation;
    private String currentScreen = SCREEN_ALARMS;
    private boolean noiseReceiverRegistered;

    private final BroadcastReceiver noiseUpdates =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (SCREEN_NOISE.equals(currentScreen)) showScreen(SCREEN_NOISE);
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        alarmRepository = new AlarmRepository(this);
        preferences = new AppPreferences(this);
        new AlarmScheduler(this).reconcileOnAppOpen();
        configureWindow();
        setContentView(buildShell());
        if (!preferences.outputPolicyAcknowledged()) {
            showOnboarding();
        } else {
            String requested = getIntent().getStringExtra(EXTRA_SCREEN);
            showScreen(requested == null ? SCREEN_ALARMS : requested);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences.outputPolicyAcknowledged()) showScreen(currentScreen);
        if (!noiseReceiverRegistered) {
            registerNoiseReceiver();
            noiseReceiverRegistered = true;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerNoiseReceiver() {
        IntentFilter filter = new IntentFilter(WhiteNoiseService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    noiseUpdates,
                    filter,
                    INTERNAL_STATE_PERMISSION,
                    null,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            // API 31–32 has no receiver flag overload; the signature permission is the boundary.
            registerReceiver(noiseUpdates, filter, INTERNAL_STATE_PERMISSION, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (noiseReceiverRegistered) {
            try { unregisterReceiver(noiseUpdates); } catch (IllegalArgumentException ignored) {}
        }
        super.onDestroy();
    }

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.INK);
        content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 7));
        navigation.setBackground(Ui.round(this, Ui.PANEL, 0, Ui.LINE));
        addNav("闹钟", SCREEN_ALARMS);
        addNav("白噪音", SCREEN_NOISE);
        addNav("可靠性", SCREEN_RELIABILITY);
        addNav("记录", SCREEN_HISTORY);
        addNav("设置", SCREEN_SETTINGS);
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, Ui.dp(this, 64)));
        return shell;
    }

    private void addNav(String label, String screen) {
        TextView item = Ui.text(this, label, 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        item.setGravity(Gravity.CENTER);
        item.setTag(screen);
        item.setOnClickListener(v -> showScreen(screen));
        navigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showScreen(String screen) {
        if (screen == null) screen = SCREEN_ALARMS;
        currentScreen = screen;
        content.removeAllViews();
        View page;
        if (SCREEN_NOISE.equals(screen)) page = noisePage();
        else if (SCREEN_RELIABILITY.equals(screen)) page = reliabilityPage();
        else if (SCREEN_HISTORY.equals(screen)) page = historyPage();
        else if (SCREEN_SETTINGS.equals(screen)) page = settingsPage();
        else page = alarmsPage();
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < navigation.getChildCount(); i++) {
            TextView item = (TextView) navigation.getChildAt(i);
            boolean selected = screen.equals(item.getTag());
            item.setTextColor(selected ? Ui.ACID : Ui.MUTED);
            item.setBackground(selected ? Ui.round(this, Ui.RAISED, 14, Ui.LINE) : null);
        }
    }

    private void showOnboarding() {
        currentScreen = SCREEN_RELIABILITY;
        content.removeAllViews();
        navigation.setVisibility(View.GONE);
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "HUSHWAKE  /  悄醒"));
        TextView title = hero("声音，去往\n正确的地方。", 43);
        title.setLineSpacing(0, .92f);
        root.addView(title);
        TextView intro =
                Ui.text(
                        this,
                        "悄醒默认使用智能输出：没有耳机时正常使用手机媒体外放；检测到耳机时，只在通过验证的耳机路径播放。",
                        16,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        intro.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 26));
        root.addView(intro);
        root.addView(principleCard("01", "无耳机，正常外放", "闹钟和白噪音都可以使用手机的系统媒体输出。"));
        root.addView(Ui.space(this, 12));
        root.addView(principleCard("02", "有耳机，严格守卫", "先静音验证实际耳机路由；耳机播放中断连不会突然切回扬声器。"));
        root.addView(Ui.space(this, 12));
        root.addView(principleCard("03", "所有数据留在本机", "没有账号、云同步或诊断上传；耳机只保存不可逆的本机哈希。"));
        root.addView(Ui.space(this, 22));
        CheckBox acknowledge = new CheckBox(this);
        acknowledge.setText("我理解：无耳机时会正常外放；检测到耳机时只允许耳机播放");
        acknowledge.setTextColor(Ui.PAPER);
        acknowledge.setTextSize(14);
        acknowledge.setButtonTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Ui.ACID, Ui.MUTED}));
        root.addView(acknowledge);
        Button continueButton = Ui.button(this, "进入可靠性中心", true);
        continueButton.setEnabled(false);
        continueButton.setAlpha(.38f);
        acknowledge.setOnCheckedChangeListener(
                (button, checked) -> {
                    continueButton.setEnabled(checked);
                    continueButton.setAlpha(checked ? 1f : .38f);
                });
        continueButton.setOnClickListener(
                v -> {
                    preferences.acknowledgeOutputPolicy();
                    navigation.setVisibility(View.VISIBLE);
                    showScreen(SCREEN_RELIABILITY);
                });
        Ui.marginTop(continueButton, 14);
        root.addView(continueButton);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private View alarmsPage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "HUSHWAKE  /  PRIVATE ALARMS"));
        root.addView(hero("今天，安静地醒来。", 34));
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        LinearLayout ready = Ui.card(this, readiness.fullyReady() ? Ui.RAISED : Ui.PANEL);
        TextView readyTitle =
                Ui.text(
                        this,
                        readiness.fullyReady() ? "有声条件已就绪" : "还有可靠性项目待处理",
                        18,
                        readiness.fullyReady() ? Ui.ACID : Ui.WARM,
                        Typeface.create("serif", Typeface.BOLD));
        ready.addView(readyTitle);
        TextView detail =
                Ui.text(
                        this,
                        readiness.fullyReady()
                                ? readiness.output() + " · 每次到点仍会重新验证实际路由"
                                : readinessSummary(readiness),
                        12,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        detail.setPadding(0, Ui.dp(this, 6), 0, 0);
        ready.addView(detail);
        ready.setOnClickListener(v -> showScreen(SCREEN_RELIABILITY));
        root.addView(ready);
        root.addView(Ui.space(this, 16));

        List<Alarm> items = alarmRepository.listAll();
        if (items.isEmpty()) {
            LinearLayout empty = Ui.card(this, Ui.PANEL);
            empty.addView(Ui.eyebrow(this, "NO ALARMS YET"));
            TextView emptyTitle =
                    Ui.text(this, "留一个只属于耳边的提醒。", 23, Ui.PAPER, Typeface.create("serif", Typeface.BOLD));
            emptyTitle.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
            empty.addView(emptyTitle);
            empty.addView(
                    Ui.text(
                            this,
                            "可先保存为有风险状态；无耳机时使用智能外放，有耳机时需通过当前耳机隐私测试。",
                            13,
                            Ui.MUTED,
                            Typeface.DEFAULT));
            root.addView(empty);
        } else {
            for (Alarm alarm : items) {
                root.addView(alarmCard(alarm, readiness));
                root.addView(Ui.space(this, 12));
            }
        }
        Button add = Ui.button(this, "+  新建私密闹钟", true);
        Ui.marginTop(add, 8);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditAlarmActivity.class)));
        root.addView(add);
        return scroll;
    }

    private View alarmCard(Alarm alarm, ReadinessChecker.Status readiness) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time =
                Ui.text(
                        this,
                        String.format(java.util.Locale.ROOT, "%02d:%02d", alarm.hour(), alarm.minute()),
                        37,
                        alarm.enabled() ? Ui.PAPER : Ui.MUTED,
                        Typeface.create("serif", Typeface.BOLD));
        top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setChecked(alarm.enabled());
        enabled.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        enabled.setOnCheckedChangeListener(
                (button, checked) -> {
                    Alarm changed = alarm.withEnabled(checked, System.currentTimeMillis());
                    alarmRepository.save(changed);
                    AlarmScheduler scheduler = new AlarmScheduler(this);
                    scheduler.cancel(changed.id());
                    AlarmScheduler.ScheduleResult result = scheduler.schedule(changed);
                    preferences.setLastScheduleIssue(
                            result.result() == AlarmScheduler.Result.SCHEDULED
                                            || result.result() == AlarmScheduler.Result.DISABLED
                                    ? ""
                                    : result.detail());
                    Toast.makeText(this, result.detail(), Toast.LENGTH_SHORT).show();
                    showScreen(SCREEN_ALARMS);
                });
        top.addView(enabled);
        card.addView(top);
        String meta = repeatSummary(alarm.repeatMask());
        if (!alarm.label().isBlank()) meta += "  ·  " + alarm.label();
        TextView summary = Ui.text(this, meta, 13, Ui.MUTED, Typeface.DEFAULT);
        summary.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 10));
        card.addView(summary);
        boolean scheduled = alarm.enabled() && new AlarmScheduler(this).canScheduleExact();
        String state;
        int color;
        if (!alarm.enabled()) { state = "已停用"; color = Ui.MUTED; }
        else if (!scheduled) { state = "有风险 · 未精确调度"; color = Ui.WARM; }
        else if (!readiness.readyForSound()) { state = "有风险 · 输出条件待处理"; color = Ui.WARM; }
        else if (!readiness.fullScreen()) { state = "有风险 · 锁屏入口受限"; color = Ui.WARM; }
        else { state = readiness.headsetConnected() ? "已就绪 · 当前耳机验证有效" : "已就绪 · 智能外放"; color = Ui.ACID; }
        card.addView(Ui.text(this, state, 12, color, Typeface.DEFAULT_BOLD));
        if (alarm.enabled()) {
            Instant next = AlarmTimeCalculator.next(alarm, Instant.now(), ZoneId.systemDefault());
            TextView nextView =
                    Ui.text(
                            this,
                            "下一次 · "
                                    + DateTimeFormatter.ofPattern("M月d日 E HH:mm")
                                            .format(next.atZone(ZoneId.systemDefault())),
                            12,
                            Ui.MUTED,
                            Typeface.DEFAULT);
            nextView.setPadding(0, Ui.dp(this, 5), 0, 0);
            card.addView(nextView);
        }
        card.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, EditAlarmActivity.class)
                                        .putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarm.id())));
        return card;
    }

    private View noisePage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "EAR-SAFE AMBIENCE  /  离线"));
        root.addView(hero("让夜晚慢一点。", 34));
        NoiseSessionStore.Snapshot session = new NoiseSessionStore(this).load();
        LinearLayout status = Ui.card(this, Ui.RAISED);
        status.addView(
                Ui.text(
                        this,
                        WhiteNoiseService.soundLabel(session.soundId()),
                        25,
                        Ui.PAPER,
                        Typeface.create("serif", Typeface.BOLD)));
        String stateText = session.detail();
        if (session.endsAtEpochMs() > System.currentTimeMillis()
                && !"stopped".equals(session.state())) {
            long minutes = Math.max(0L, (session.endsAtEpochMs() - System.currentTimeMillis() + 59_999L) / 60_000L);
            stateText += " · 约 " + minutes + " 分钟后结束";
        }
        TextView stateView = Ui.text(this, stateText, 13, Ui.ACID, Typeface.DEFAULT_BOLD);
        stateView.setPadding(0, Ui.dp(this, 7), 0, 0);
        status.addView(stateView);
        root.addView(status);
        root.addView(Ui.space(this, 14));

        LinearLayout controls = Ui.card(this, Ui.PANEL);
        controls.addView(Ui.eyebrow(this, "SESSION  /  智能输出"));
        Spinner sound = spinner(new String[] {"细雨", "粉红噪音", "远海", "篝火"});
        sound.setSelection(indexOf(new String[] {"rain", "pink", "ocean", "campfire"}, preferences.noiseSoundId()));
        addField(controls, "声音", sound);
        TextView volumeText = Ui.text(this, "应用内增益 · " + preferences.noiseVolume() + "%", 13, Ui.PAPER, Typeface.DEFAULT_BOLD);
        volumeText.setPadding(0, Ui.dp(this, 14), 0, 0);
        controls.addView(volumeText);
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(preferences.noiseVolume());
        volume.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        volume.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        volume.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { volumeText.setText("应用内增益 · " + value + "%"); }
                    @Override public void onStartTrackingTouch(SeekBar bar) {}
                    @Override public void onStopTrackingTouch(SeekBar bar) {}
                });
        controls.addView(volume);
        Spinner timer = spinner(new String[] {"关闭（最长 8 小时）", "15 分钟", "30 分钟", "45 分钟", "60 分钟"});
        timer.setSelection(indexOf(new int[] {0, 15, 30, 45, 60}, preferences.noiseTimerMinutes()));
        addField(controls, "定时", timer);
        Spinner fade = spinner(new String[] {"不渐隐", "5 秒", "15 秒", "30 秒"});
        fade.setSelection(indexOf(new int[] {0, 5, 15, 30}, preferences.noiseFadeSeconds()));
        addField(controls, "结束渐隐", fade);
        TextView privacy =
                Ui.text(
                        this,
                        "无耳机时使用手机媒体外放；检测到耳机时切换到耳机守卫。耳机播放中断连会先静音并停止，不会突然改为外放。",
                        12,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        privacy.setPadding(0, Ui.dp(this, 14), 0, 0);
        controls.addView(privacy);
        root.addView(controls);

        boolean active = !"stopped".equals(session.state()) && !"blocked".equals(session.state());
        Button primary = Ui.button(this, active ? ("paused".equals(session.state()) ? "继续" : "暂停") : "开始播放", true);
        Ui.marginTop(primary, 16);
        primary.setOnClickListener(
                v -> {
                    if (active) {
                        startService(
                                new Intent(this, WhiteNoiseService.class)
                                        .setAction(
                                                "paused".equals(session.state())
                                                        ? WhiteNoiseService.ACTION_RESUME
                                                        : WhiteNoiseService.ACTION_PAUSE));
                    } else {
                        String soundId = new String[] {"rain", "pink", "ocean", "campfire"}[sound.getSelectedItemPosition()];
                        int timerMinutes = new int[] {0, 15, 30, 45, 60}[timer.getSelectedItemPosition()];
                        int fadeSeconds = new int[] {0, 5, 15, 30}[fade.getSelectedItemPosition()];
                        preferences.saveNoiseDefaults(volume.getProgress(), timerMinutes, fadeSeconds, soundId);
                        Intent play =
                                new Intent(this, WhiteNoiseService.class)
                                        .setAction(WhiteNoiseService.ACTION_START)
                                        .putExtra(WhiteNoiseService.EXTRA_SOUND_ID, soundId)
                                        .putExtra(WhiteNoiseService.EXTRA_VOLUME, volume.getProgress())
                                        .putExtra(WhiteNoiseService.EXTRA_TIMER_MINUTES, timerMinutes)
                                        .putExtra(WhiteNoiseService.EXTRA_FADE_SECONDS, fadeSeconds);
                        startForegroundService(play);
                    }
                });
        root.addView(primary);
        if (active) {
            Button stop = Ui.button(this, "停止", false);
            Ui.marginTop(stop, 10);
            stop.setOnClickListener(
                    v -> startService(new Intent(this, WhiteNoiseService.class).setAction(WhiteNoiseService.ACTION_STOP)));
            root.addView(stop);
        }
        return scroll;
    }

    private View reliabilityPage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "RELIABILITY CENTER  /  分项检查"));
        root.addView(hero("能否准时，\n为何安静。", 34));
        ReadinessChecker.Status status = ReadinessChecker.inspect(this);
        root.addView(statusCard("Android 输出验证", Build.VERSION.SDK_INT >= 36, Build.VERSION.SDK_INT >= 36 ? "强验证 · 可枚举播放轨全部路由" : "兼容验证 · API 31–35 单路由证据", null));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("精确闹钟", status.exactAlarm(), status.exactAlarm() ? "已允许" : "未允许；闹钟会保存但不假装已调度", this::requestExactAlarm));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("通知", status.notifications(), status.notifications() ? "已允许" : "未允许；锁屏处置入口会受限", this::requestNotifications));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("全屏闹钟入口", status.fullScreen(), status.fullScreen() ? "已允许" : "未允许；将退化为高优先级通知", this::requestFullScreen));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("蓝牙连接能力", status.bluetoothPermission(), status.bluetoothPermission() ? "已允许" : "未允许；无法绑定当前蓝牙耳机", this::requestBluetooth));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("媒体音量", status.mediaVolume(), status.mediaVolume() ? "可用；应用不会自动调高" : "当前为 0，本次有声不可用", () -> startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS))));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("当前输出", status.outputSelectable(), status.output(), null));
        root.addView(Ui.space(this, 10));
        root.addView(
                statusCard(
                        "当前耳机隐私测试",
                        !status.headsetConnected() || status.deviceVerified(),
                        !status.headsetConnected()
                                ? "当前无耳机 · 智能外放无需耳机测试"
                                : status.deviceVerified()
                                        ? "已通过 · 90 天内且环境未变化"
                                        : "未通过、已过期或无法识别当前耳机",
                        status.headsetConnected()
                                ? () -> startActivity(new Intent(this, MainActivity.class))
                                : null));
        root.addView(Ui.space(this, 10));
        root.addView(statusCard("1 分钟测试闹钟", status.testAlarmPassed(), status.testAlarmPassed() ? "已至少完成一次实际系统触发" : "尚未完成", this::createTestAlarm));
        if (!status.scheduleIssue().isBlank()) {
            root.addView(Ui.space(this, 10));
            root.addView(statusCard("最近调度问题", false, status.scheduleIssue(), null));
        }
        Button refresh = Ui.button(this, "重新检查", true);
        Ui.marginTop(refresh, 18);
        refresh.setOnClickListener(v -> showScreen(SCREEN_RELIABILITY));
        root.addView(refresh);
        return scroll;
    }

    private View historyPage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "LOCAL HISTORY  /  最近 30 次或 30 天"));
        root.addView(hero("发生过什么，\n只留在这里。", 34));
        List<PlaybackEventRepository.Event> events = new PlaybackEventRepository(this).recent();
        if (events.isEmpty()) {
            LinearLayout empty = Ui.card(this, Ui.PANEL);
            empty.addView(Ui.text(this, "尚无触发与播放记录", 18, Ui.PAPER, Typeface.create("serif", Typeface.BOLD)));
            TextView note = Ui.text(this, "隐私测试的可复制摘要仍保留在可靠性实验室中。", 13, Ui.MUTED, Typeface.DEFAULT);
            note.setPadding(0, Ui.dp(this, 7), 0, 0);
            empty.addView(note);
            root.addView(empty);
        }
        for (PlaybackEventRepository.Event event : events) {
            LinearLayout card = Ui.card(this, Ui.PANEL);
            card.addView(Ui.eyebrow(this, event.eventType().toUpperCase(java.util.Locale.ROOT)));
            TextView result = Ui.text(this, event.result(), 19, Ui.PAPER, Typeface.create("serif", Typeface.BOLD));
            result.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
            card.addView(result);
            String meta = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .format(event.createdAt().atZone(ZoneId.systemDefault()));
            if (!event.verificationLevel().isBlank()) meta += " · " + event.verificationLevel();
            if (event.latencyMs() >= 0) meta += " · 静音 " + event.latencyMs() + " ms";
            card.addView(Ui.text(this, meta, 12, Ui.MUTED, Typeface.DEFAULT));
            if (!event.reasonCode().isBlank()) {
                TextView reason = Ui.text(this, event.reasonCode(), 12, Ui.WARM, Typeface.MONOSPACE);
                reason.setPadding(0, Ui.dp(this, 6), 0, 0);
                card.addView(reason);
            }
            root.addView(card);
            root.addView(Ui.space(this, 10));
        }
        return scroll;
    }

    private View settingsPage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "SETTINGS & PRIVACY  /  本地"));
        root.addView(hero("设置清楚，\n边界也清楚。", 34));

        LinearLayout defaults = Ui.card(this, Ui.PANEL);
        defaults.addView(Ui.eyebrow(this, "DEFAULTS  /  新建闹钟"));
        defaults.addView(settingRow("应用内增益", preferences.defaultAlarmVolume() + "%"));
        defaults.addView(settingRow("渐强", preferences.defaultAlarmFadeSeconds() + " 秒"));
        defaults.addView(settingRow("振动兜底", preferences.defaultVibration() ? "开启" : "关闭"));
        defaults.addView(settingRow("稍后提醒", preferences.defaultSnoozeMinutes() + " 分钟"));
        defaults.addView(settingRow("最长响铃", preferences.defaultMaxRingSeconds() + " 秒"));
        Button editDefaults = Ui.button(this, "调整新建闹钟默认值", false);
        Ui.marginTop(editDefaults, 12);
        editDefaults.setOnClickListener(v -> alarmDefaultsDialog());
        defaults.addView(editDefaults);
        root.addView(defaults);
        root.addView(Ui.space(this, 14));

        LinearLayout boundaries = Ui.card(this, Ui.PANEL);
        boundaries.addView(Ui.eyebrow(this, "BOUNDARIES  /  无法保证"));
        boundaries.addView(
                Ui.text(
                        this,
                        "关机、无电、卸载、应用被强制停止或系统/厂商后台策略阻止时，悄醒无法保证触发。耳机播放中的断连不会自动降级为扬声器外放。",
                        13,
                        Ui.MUTED,
                        Typeface.DEFAULT));
        root.addView(boundaries);
        root.addView(Ui.space(this, 14));

        LinearLayout data = Ui.card(this, Ui.PANEL);
        data.addView(Ui.eyebrow(this, "LOCAL DATA  /  无账号无上传"));
        TextView dataNote = Ui.text(this, "精确闹钟时间、标签和耳机标识只存在应用沙箱；耳机原始名称与地址不写入数据库。", 13, Ui.MUTED, Typeface.DEFAULT);
        dataNote.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        data.addView(dataNote);
        Button clearHistory = Ui.button(this, "清除播放记录", false);
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        data.addView(clearHistory);
        Button clearAll = Ui.button(this, "清除全部本地数据", false);
        clearAll.setTextColor(Ui.DANGER);
        Ui.marginTop(clearAll, 10);
        clearAll.setOnClickListener(v -> confirmClearAll());
        data.addView(clearAll);
        root.addView(data);
        root.addView(Ui.space(this, 18));
        TextView version = Ui.text(this, "HushWake 0.2.1-beta · Android API 31+", 12, Ui.MUTED, Typeface.MONOSPACE);
        version.setGravity(Gravity.CENTER);
        root.addView(version);
        return scroll;
    }

    private LinearLayout statusCard(String title, boolean passed, String detail, Runnable action) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, passed ? "✓" : "!", 18, passed ? Ui.ACID : Ui.WARM, Typeface.DEFAULT_BOLD);
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 30), -2));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 15, Ui.PAPER, Typeface.DEFAULT_BOLD));
        TextView detailView = Ui.text(this, detail, 12, Ui.MUTED, Typeface.DEFAULT);
        detailView.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(detailView);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView fix = Ui.text(this, passed ? "查看" : "修复", 13, Ui.ACID, Typeface.DEFAULT_BOLD);
            fix.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), 0, Ui.dp(this, 10));
            row.addView(fix);
            card.setOnClickListener(v -> action.run());
        }
        card.addView(row);
        return card;
    }

    private View principleCard(String number, String title, String detail) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        card.addView(Ui.eyebrow(this, number));
        TextView heading = Ui.text(this, title, 20, Ui.PAPER, Typeface.create("serif", Typeface.BOLD));
        heading.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 5));
        card.addView(heading);
        card.addView(Ui.text(this, detail, 13, Ui.MUTED, Typeface.DEFAULT));
        return card;
    }

    private TextView settingRow(String key, String value) {
        TextView row = Ui.text(this, key + "    " + value, 14, Ui.PAPER, Typeface.DEFAULT);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        return row;
    }

    private void alarmDefaultsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        form.setPadding(p, 0, p, 0);
        Spinner volume = spinner(new String[] {"25%", "35%", "50%", "65%", "80%"});
        volume.setSelection(indexOf(new int[] {25, 35, 50, 65, 80}, preferences.defaultAlarmVolume()));
        addField(form, "应用内增益", volume);
        Spinner fade = spinner(new String[] {"不渐强", "15 秒", "30 秒", "60 秒"});
        fade.setSelection(indexOf(new int[] {0, 15, 30, 60}, preferences.defaultAlarmFadeSeconds()));
        addField(form, "渐强", fade);
        Switch vibration = new Switch(this);
        vibration.setText("振动兜底");
        vibration.setChecked(preferences.defaultVibration());
        form.addView(vibration);
        Spinner snooze = spinner(new String[] {"关闭", "3 分钟", "5 分钟", "10 分钟"});
        snooze.setSelection(indexOf(new int[] {0, 3, 5, 10}, preferences.defaultSnoozeMinutes()));
        addField(form, "稍后提醒", snooze);
        Spinner max = spinner(new String[] {"30 秒", "1 分钟", "2 分钟", "5 分钟"});
        max.setSelection(indexOf(new int[] {30, 60, 120, 300}, preferences.defaultMaxRingSeconds()));
        addField(form, "最长响铃", max);
        new AlertDialog.Builder(this)
                .setTitle("新建闹钟默认值")
                .setView(form)
                .setPositiveButton(
                        "保存",
                        (d, w) -> {
                            preferences.saveAlarmDefaults(
                                    new int[] {25, 35, 50, 65, 80}[volume.getSelectedItemPosition()],
                                    new int[] {0, 15, 30, 60}[fade.getSelectedItemPosition()],
                                    vibration.isChecked(),
                                    new int[] {0, 3, 5, 10}[snooze.getSelectedItemPosition()],
                                    new int[] {30, 60, 120, 300}[max.getSelectedItemPosition()]);
                            showScreen(SCREEN_SETTINGS);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestExactAlarm() {
        startActivity(
                new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:" + getPackageName())));
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        } else {
            startActivity(
                    new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }
    }

    private void requestFullScreen() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                    new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestBluetooth() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
    }

    private void createTestAlarm() {
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        if (!readiness.exactAlarm()) {
            Toast.makeText(this, "请先允许精确闹钟", Toast.LENGTH_LONG).show();
            requestExactAlarm();
            return;
        }
        LocalTime next = LocalTime.now().plusMinutes(1);
        long now = System.currentTimeMillis();
        Alarm test =
                new Alarm(
                        0,
                        next.getHour(),
                        next.getMinute(),
                        0,
                        "1 分钟测试",
                        "soft_chime",
                        20,
                        0,
                        true,
                        0,
                        30,
                        true,
                        Alarm.nextOneTimeEpochDay(
                                next.getHour(), next.getMinute(), Instant.ofEpochMilli(now)),
                        now,
                        now);
        Alarm saved = alarmRepository.save(test);
        AlarmScheduler.ScheduleResult result = new AlarmScheduler(this).schedule(saved);
        if (result.result() == AlarmScheduler.Result.SCHEDULED) {
            preferences.setPendingTestAlarm(saved.id());
        }
        Toast.makeText(this, result.detail() + " · " + String.format(java.util.Locale.ROOT, "%02d:%02d", next.getHour(), next.getMinute()), Toast.LENGTH_LONG).show();
        showScreen(SCREEN_RELIABILITY);
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("清除播放记录？")
                .setMessage("闹钟配置和耳机验证记录会保留。")
                .setPositiveButton(
                        "清除",
                        (d, w) -> {
                            new PlaybackEventRepository(this).clear();
                            showScreen(SCREEN_SETTINGS);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("清除全部本地数据？")
                .setMessage("所有未来调度、闹钟、验证记录、播放记录与设置都会删除。此操作无法撤销。")
                .setPositiveButton(
                        "全部清除",
                        (d, w) -> {
                            startService(
                                    new Intent(this, WhiteNoiseService.class)
                                            .setAction(WhiteNoiseService.ACTION_STOP)
                                            .putExtra(WhiteNoiseService.EXTRA_DISCARD, true));
                            startService(
                                    new Intent(this, AlarmRingingService.class)
                                            .setAction(AlarmRingingService.ACTION_STOP)
                                            .putExtra(AlarmRingingService.EXTRA_DISCARD, true));
                            new AlarmScheduler(this).cancelAll();
                            com.hushwake.app.data.HushWakeDatabase.get(this).clearAllUserData();
                            new DiagnosticsStore(this).clear();
                            new com.hushwake.app.alarm.AlarmSessionStore(this).clear();
                            new NoiseSessionStore(this).clear();
                            preferences.clearAll();
                            navigation.setVisibility(View.GONE);
                            showOnboarding();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.INK);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        return scroll;
    }

    private LinearLayout pageRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 46));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        return root;
    }

    private TextView hero(String value, int size) {
        TextView hero = Ui.text(this, value, size, Ui.PAPER, Typeface.create("serif", Typeface.BOLD));
        hero.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        return hero;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        return spinner;
    }

    private void addField(LinearLayout parent, String title, View input) {
        TextView label = Ui.text(this, title, 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        label.setPadding(0, Ui.dp(this, 13), 0, Ui.dp(this, 3));
        parent.addView(label);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
    }

    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return 0;
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(target)) return i;
        return 0;
    }

    private static String repeatSummary(int mask) {
        if (mask == 0) return "仅一次";
        if (mask == 31) return "工作日";
        if (mask == 96) return "周末";
        if (mask == 127) return "每天";
        StringBuilder text = new StringBuilder("周");
        String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) text.append(days[i]);
        return text.toString();
    }

    private static String readinessSummary(ReadinessChecker.Status status) {
        if (!status.exactAlarm()) return "未允许精确闹钟";
        if (!status.notifications()) return "通知未允许";
        if (!status.fullScreen()) return "全屏闹钟入口未允许";
        if (!status.bluetoothPermission()) return "蓝牙连接能力未允许";
        if (!status.mediaVolume()) return "系统媒体音量为 0";
        if (!status.outputSelectable()) return "检测到多个耳机，无法唯一选择输出";
        if (status.headsetConnected() && !status.deviceVerified()) return "当前耳机需要隐私测试";
        return "请打开可靠性中心检查";
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
        getWindow().setNavigationBarContrastEnforced(false);
    }
}
