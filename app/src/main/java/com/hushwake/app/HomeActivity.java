package com.hushwake.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmRingingService;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.alarm.AlarmSessionStore;
import com.hushwake.app.alarm.AlarmStopPolicy;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import com.hushwake.app.noise.NoiseSessionStore;
import com.hushwake.app.noise.WhiteNoiseService;
import com.hushwake.app.reliability.ReadinessChecker;
import com.hushwake.app.ui.Ui;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class HomeActivity extends Activity {
    public static final String EXTRA_SCREEN = "screen";
    public static final String SCREEN_ALARMS = "alarms";
    public static final String SCREEN_NOISE = "noise";

    private static final int REQUEST_NOTIFICATIONS = 1001;
    private static final int REQUEST_BLUETOOTH = 1002;
    private static final String INTERNAL_STATE_PERMISSION =
            "com.hushwake.app.permission.INTERNAL_STATE";

    private AlarmRepository alarms;
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
        alarms = new AlarmRepository(this);
        preferences = new AppPreferences(this);
        new AlarmScheduler(this).reconcileOnAppOpen();
        configureWindow();
        setContentView(buildShell());
        if (!preferences.outputPolicyAcknowledged()) {
            showOnboarding();
        } else {
            showScreen(sanitizeScreen(getIntent().getStringExtra(EXTRA_SCREEN)));
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
            registerReceiver(noiseUpdates, filter, INTERNAL_STATE_PERMISSION, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (noiseReceiverRegistered) {
            try {
                unregisterReceiver(noiseUpdates);
            } catch (IllegalArgumentException ignored) {
                // Receiver already gone.
            }
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
        navigation.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 8));
        navigation.setBackground(Ui.round(this, Ui.PANEL, 0, Ui.LINE));
        addNav("闹钟", SCREEN_ALARMS);
        addNav("助眠声", SCREEN_NOISE);
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, Ui.dp(this, 66)));
        return shell;
    }

    private void addNav(String label, String screen) {
        TextView item = Ui.text(this, label, 14, Ui.MUTED, Typeface.DEFAULT_BOLD);
        item.setGravity(Gravity.CENTER);
        item.setTag(screen);
        item.setOnClickListener(v -> showScreen(screen));
        navigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showScreen(String requested) {
        String screen = sanitizeScreen(requested);
        currentScreen = screen;
        content.removeAllViews();
        View page = SCREEN_NOISE.equals(screen) ? noisePage() : alarmsPage();
        page.setAlpha(0f);
        page.setTranslationY(Ui.dp(this, 8));
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        for (int i = 0; i < navigation.getChildCount(); i++) {
            TextView item = (TextView) navigation.getChildAt(i);
            boolean selected = screen.equals(item.getTag());
            item.setTextColor(selected ? Ui.ACID : Ui.MUTED);
            item.setBackground(selected ? Ui.round(this, Ui.RAISED, 16, Ui.LINE) : null);
        }
    }

    private static String sanitizeScreen(String screen) {
        return SCREEN_NOISE.equals(screen) ? SCREEN_NOISE : SCREEN_ALARMS;
    }

    private void showOnboarding() {
        currentScreen = SCREEN_ALARMS;
        content.removeAllViews();
        navigation.setVisibility(View.GONE);
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "HUSHWAKE  /  悄醒"));
        root.addView(hero("声音，去对的地方。", 40));
        root.addView(
                principleCard(
                        "没有耳机",
                        "闹钟和助眠声会使用手机扬声器，并跟随系统媒体音量。",
                        Ui.WARM));
        root.addView(Ui.space(this, 12));
        root.addView(
                principleCard(
                        "连接耳机",
                        "声音只允许通过已确认的耳机路径；断连会先静音再停止。",
                        Ui.ACID));
        root.addView(Ui.space(this, 20));
        CheckBox acknowledge = new CheckBox(this);
        acknowledge.setText("我了解当前输出会随耳机连接状态自动选择");
        acknowledge.setTextColor(Ui.PAPER);
        acknowledge.setTextSize(14);
        acknowledge.setButtonTintList(
                new android.content.res.ColorStateList(
                        new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                        new int[] {Ui.ACID, Ui.MUTED}));
        root.addView(acknowledge);
        Button start = Ui.button(this, "开始使用", true);
        start.setEnabled(false);
        start.setAlpha(.38f);
        acknowledge.setOnCheckedChangeListener(
                (button, checked) -> {
                    start.setEnabled(checked);
                    start.setAlpha(checked ? 1f : .38f);
                });
        start.setOnClickListener(
                v -> {
                    preferences.acknowledgeOutputPolicy();
                    navigation.setVisibility(View.VISIBLE);
                    showScreen(SCREEN_ALARMS);
                });
        Ui.marginTop(start, 14);
        root.addView(start);
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private View alarmsPage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "HUSHWAKE  /  ALARMS"));
        root.addView(hero("今晚，放心睡。", 34));
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        root.addView(outputBanner(readiness));
        View permission = alarmPermissionBanner(readiness);
        if (permission != null) {
            root.addView(Ui.space(this, 10));
            root.addView(permission);
        }
        root.addView(Ui.space(this, 18));

        List<Alarm> items = alarms.listAll();
        if (items.isEmpty()) {
            LinearLayout empty = Ui.card(this, Ui.PANEL);
            empty.addView(Ui.eyebrow(this, "NO ALARMS"));
            TextView title =
                    Ui.text(
                            this,
                            "还没有闹钟",
                            23,
                            Ui.PAPER,
                            Typeface.create("serif", Typeface.BOLD));
            title.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 5));
            empty.addView(title);
            empty.addView(Ui.text(this, "选一个时间，其余交给悄醒。", 13, Ui.MUTED, Typeface.DEFAULT));
            root.addView(empty);
        } else {
            AlarmSessionStore.Snapshot active = new AlarmSessionStore(this).load();
            for (Alarm alarm : items) {
                root.addView(alarmCard(alarm, active));
                root.addView(Ui.space(this, 12));
            }
        }
        Button add = Ui.button(this, "+  新建闹钟", true);
        Ui.marginTop(add, 8);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditAlarmActivity.class)));
        root.addView(add);
        return scroll;
    }

    private View alarmCard(Alarm alarm, AlarmSessionStore.Snapshot active) {
        LinearLayout card = Ui.card(this, alarm.enabled() ? Ui.RAISED : Ui.PANEL);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView time =
                Ui.text(
                        this,
                        String.format(java.util.Locale.ROOT, "%02d:%02d", alarm.hour(), alarm.minute()),
                        39,
                        alarm.enabled() ? Ui.PAPER : Ui.MUTED,
                        Typeface.create("serif", Typeface.BOLD));
        top.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setChecked(alarm.enabled());
        enabled.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        enabled.setOnCheckedChangeListener(
                (button, checked) -> setAlarmEnabled(alarm, checked));
        top.addView(enabled);
        card.addView(top);
        String meta = repeatSummary(alarm.repeatMask());
        if (!alarm.label().isBlank()) meta += "  ·  " + alarm.label();
        TextView summary = Ui.text(this, meta, 13, Ui.MUTED, Typeface.DEFAULT);
        summary.setPadding(0, Ui.dp(this, 1), 0, Ui.dp(this, 9));
        card.addView(summary);
        card.addView(
                Ui.text(
                        this,
                        alarm.enabled() ? "已开启" : "已关闭",
                        12,
                        alarm.enabled() ? Ui.ACID : Ui.MUTED,
                        Typeface.DEFAULT_BOLD));
        if (isActiveOccurrence(alarm.id(), active)) {
            Button stop = Ui.button(this, "正在响铃 · 立即停止", false);
            Ui.marginTop(stop, 12);
            stop.setOnClickListener(
                    v -> {
                        stopIfRinging(alarm.id());
                        Toast.makeText(this, "已停止这个闹钟", Toast.LENGTH_SHORT).show();
                    });
            card.addView(stop);
        }
        if (alarm.enabled()) {
            Instant next = AlarmTimeCalculator.next(alarm, Instant.now(), ZoneId.systemDefault());
            TextView nextView =
                    Ui.text(
                            this,
                            "下一次 · "
                                    + DateTimeFormatter.ofPattern(
                                                    "M月d日 E HH:mm",
                                                    java.util.Locale.SIMPLIFIED_CHINESE)
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

    private static boolean isActiveOccurrence(
            long alarmId, AlarmSessionStore.Snapshot active) {
        return alarmId > 0L
                && alarmId == active.alarmId()
                && !"IDLE".equals(active.state())
                && !"STOPPED".equals(active.state());
    }

    private void setAlarmEnabled(Alarm alarm, boolean checked) {
        Alarm changed = alarm.withEnabled(checked, System.currentTimeMillis());
        alarms.save(changed);
        AlarmScheduler scheduler = new AlarmScheduler(this);
        scheduler.cancel(changed.id());
        AlarmScheduler.ScheduleResult result = scheduler.schedule(changed);
        preferences.setLastScheduleIssue(
                result.result() == AlarmScheduler.Result.SCHEDULED
                                || result.result() == AlarmScheduler.Result.DISABLED
                        ? ""
                        : result.detail());
        if (!checked) stopIfRinging(changed.id());
        Toast.makeText(
                        this,
                        checked ? result.detail() : "闹钟已关闭；正在响铃也会立即停止",
                        Toast.LENGTH_SHORT)
                .show();
        showScreen(SCREEN_ALARMS);
    }

    private void stopIfRinging(long alarmId) {
        AlarmSessionStore.Snapshot active = new AlarmSessionStore(this).load();
        if (!AlarmStopPolicy.shouldStop(
                alarmId, false, active.alarmId(), active.state())) return;
        startService(
                new Intent(this, AlarmRingingService.class)
                        .setAction(AlarmRingingService.ACTION_STOP)
                        .putExtra(AlarmRingingService.EXTRA_STOP_ALARM_ID, alarmId));
    }

    private View noisePage() {
        ScrollView scroll = scroll();
        LinearLayout root = pageRoot(scroll);
        root.addView(Ui.eyebrow(this, "SLEEP SOUNDS  /  真实录音"));
        root.addView(hero("把房间，调安静一点。", 34));
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        root.addView(outputBanner(readiness));
        root.addView(Ui.space(this, 14));

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
            long minutes =
                    Math.max(
                            0L,
                            (session.endsAtEpochMs() - System.currentTimeMillis() + 59_999L)
                                    / 60_000L);
            stateText += " · 约 " + minutes + " 分钟后结束";
        }
        TextView state = Ui.text(this, stateText, 13, Ui.ACID, Typeface.DEFAULT_BOLD);
        state.setPadding(0, Ui.dp(this, 7), 0, 0);
        status.addView(state);
        root.addView(status);
        root.addView(Ui.space(this, 14));

        LinearLayout controls = Ui.card(this, Ui.PANEL);
        controls.addView(Ui.eyebrow(this, "AMBIENCE  /  离线循环"));
        Spinner sound = Ui.spinner(this, new String[] {"绵密夜雨", "林间溪流", "轻柔壁炉"});
        sound.setSelection(
                indexOf(
                        new String[] {"rain", "stream", "fireplace"},
                        preferences.noiseSoundId()));
        addField(controls, "声音", sound);
        Spinner timer =
                Ui.spinner(
                        this,
                        new String[] {"持续播放（最长 8 小时）", "15 分钟", "30 分钟", "45 分钟", "60 分钟"});
        timer.setSelection(
                indexOf(new int[] {0, 15, 30, 45, 60}, preferences.noiseTimerMinutes()));
        addField(controls, "定时", timer);
        Spinner fade = Ui.spinner(this, new String[] {"直接结束", "5 秒渐隐", "15 秒渐隐", "30 秒渐隐"});
        fade.setSelection(indexOf(new int[] {0, 5, 15, 30}, preferences.noiseFadeSeconds()));
        addField(controls, "结束方式", fade);
        TextView volume =
                Ui.text(
                        this,
                        "音量完全跟随手机媒体音量；实体音量键可以随时调整。",
                        13,
                        Ui.PAPER,
                        Typeface.DEFAULT);
        volume.setPadding(0, Ui.dp(this, 14), 0, 0);
        controls.addView(volume);
        root.addView(controls);

        boolean active =
                !"stopped".equals(session.state()) && !"blocked".equals(session.state());
        Button primary =
                Ui.button(
                        this,
                        active ? ("paused".equals(session.state()) ? "继续播放" : "暂停") : "开始播放",
                        true);
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
                        return;
                    }
                    String soundId =
                            new String[] {"rain", "stream", "fireplace"}[
                                    sound.getSelectedItemPosition()];
                    int timerMinutes =
                            new int[] {0, 15, 30, 45, 60}[timer.getSelectedItemPosition()];
                    int fadeSeconds =
                            new int[] {0, 5, 15, 30}[fade.getSelectedItemPosition()];
                    preferences.saveNoiseDefaults(timerMinutes, fadeSeconds, soundId);
                    Intent play =
                            new Intent(this, WhiteNoiseService.class)
                                    .setAction(WhiteNoiseService.ACTION_START)
                                    .putExtra(WhiteNoiseService.EXTRA_SOUND_ID, soundId)
                                    .putExtra(WhiteNoiseService.EXTRA_TIMER_MINUTES, timerMinutes)
                                    .putExtra(WhiteNoiseService.EXTRA_FADE_SECONDS, fadeSeconds);
                    startForegroundService(play);
                });
        root.addView(primary);
        if (active) {
            Button stop = Ui.button(this, "停止播放", false);
            Ui.marginTop(stop, 10);
            stop.setOnClickListener(
                    v ->
                            startService(
                                    new Intent(this, WhiteNoiseService.class)
                                            .setAction(WhiteNoiseService.ACTION_STOP)));
            root.addView(stop);
        }
        TextView credit =
                Ui.text(
                        this,
                        "真实环境录音 · 离线保存 · 来源与许可见项目音频授权说明",
                        11,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(credit);
        return scroll;
    }

    private View outputBanner(ReadinessChecker.Status status) {
        String title;
        String detail;
        int accent;
        Runnable action = null;
        String actionLabel = null;
        if (!status.bluetoothPermission()) {
            title = "允许蓝牙权限后自动选择输出";
            detail = "用于判断耳机是否连接，不读取或保存耳机名称。";
            accent = Ui.WARM;
            action = this::requestBluetooth;
            actionLabel = "允许";
        } else if (!status.mediaVolume()) {
            title = "手机媒体音量当前为 0";
            detail = "悄醒不会替你调高音量。";
            accent = Ui.WARM;
            action = () -> startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
            actionLabel = "调整";
        } else if (!status.outputSelectable()) {
            title = "检测到多个耳机输出";
            detail = "请暂时只保留一个耳机连接。";
            accent = Ui.DANGER;
        } else if (status.headsetConnected() && !status.deviceVerified()) {
            title = "耳机已连接 · 播放前需要确认";
            detail = "做一次低音量测试，确认声音不会漏到扬声器。";
            accent = Ui.WARM;
            action = () -> startActivity(new Intent(this, MainActivity.class));
            actionLabel = "确认耳机";
        } else if (status.headsetConnected()) {
            title = "耳机已连接 · 将只通过耳机播放";
            detail = "播放中断连会立即静音并停止。";
            accent = Ui.ACID;
        } else {
            title = "当前无耳机 · 将通过扬声器播放";
            detail = "音量跟随手机媒体音量。";
            accent = Ui.WARM;
            action = () -> startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS));
            actionLabel = "调整音量";
        }
        return infoBanner(title, detail, accent, actionLabel, action);
    }

    private View alarmPermissionBanner(ReadinessChecker.Status status) {
        if (!status.exactAlarm()) {
            return infoBanner(
                    "还需允许精确闹钟",
                    "否则系统可能不会按设定时间触发。",
                    Ui.WARM,
                    "去允许",
                    this::requestExactAlarm);
        }
        if (!status.notifications()) {
            return infoBanner(
                    "还需允许闹钟通知",
                    "用于显示正在响铃和停止入口。",
                    Ui.WARM,
                    "去允许",
                    this::requestNotifications);
        }
        if (!status.fullScreen()) {
            return infoBanner(
                    "锁屏响铃入口未开启",
                    "开启后闹钟响起时更容易直接看到停止按钮。",
                    Ui.WARM,
                    "去开启",
                    this::requestFullScreen);
        }
        return null;
    }

    private View infoBanner(
            String title, String detail, int accent, String actionLabel, Runnable action) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, "●", 14, accent, Typeface.DEFAULT_BOLD);
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 28), -2));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 16, Ui.PAPER, Typeface.DEFAULT_BOLD));
        TextView description = Ui.text(this, detail, 12, Ui.MUTED, Typeface.DEFAULT);
        description.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(description);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView button = Ui.text(this, actionLabel, 13, Ui.ACID, Typeface.DEFAULT_BOLD);
            button.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), 0, Ui.dp(this, 11));
            row.addView(button);
            card.setOnClickListener(v -> action.run());
        }
        card.addView(row);
        return card;
    }

    private View principleCard(String title, String detail, int accent) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        TextView heading =
                Ui.text(this, title, 21, accent, Typeface.create("serif", Typeface.BOLD));
        heading.setPadding(0, 0, 0, Ui.dp(this, 6));
        card.addView(heading);
        card.addView(Ui.text(this, detail, 14, Ui.PAPER, Typeface.DEFAULT));
        return card;
    }

    private void requestExactAlarm() {
        startActivity(
                new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(android.net.Uri.parse("package:" + getPackageName())));
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
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
                            .setData(android.net.Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestBluetooth() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        }
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
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 48));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        return root;
    }

    private TextView hero(String value, int size) {
        TextView hero =
                Ui.text(this, value, size, Ui.PAPER, Typeface.create("serif", Typeface.BOLD));
        hero.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        return hero;
    }

    private void addField(LinearLayout parent, String title, View input) {
        TextView label = Ui.text(this, title, 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        label.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
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

    private void configureWindow() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
        getWindow().setNavigationBarContrastEnforced(false);
    }
}
