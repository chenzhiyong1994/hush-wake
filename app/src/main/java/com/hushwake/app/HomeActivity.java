package com.hushwake.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmHomeRefreshPolicy;
import com.hushwake.app.alarm.AlarmRingingService;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.alarm.AlarmSessionStore;
import com.hushwake.app.alarm.AlarmSoundCatalog;
import com.hushwake.app.alarm.AlarmStopPolicy;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import com.hushwake.app.noise.NoiseSessionStore;
import com.hushwake.app.noise.NoiseTimerPresentation;
import com.hushwake.app.noise.SleepSoundCatalog;
import com.hushwake.app.noise.WhiteNoiseService;
import com.hushwake.app.reliability.AlarmWakePermissionPolicy;
import com.hushwake.app.reliability.OemAutostartNavigator;
import com.hushwake.app.reliability.ReadinessChecker;
import com.hushwake.app.ui.AmbientWaveView;
import com.hushwake.app.ui.BrandLaunchPolicy;
import com.hushwake.app.ui.BrandLaunchView;
import com.hushwake.app.ui.Ui;
import java.time.Duration;
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
    private FrameLayout appRoot;
    private BrandLaunchView brandLaunch;
    private boolean brandLaunchPlaying;
    private boolean systemSplashExited;
    private FrameLayout content;
    private LinearLayout navigation;
    private String currentScreen = SCREEN_ALARMS;
    private boolean alarmReceiverRegistered;
    private boolean noiseReceiverRegistered;
    private boolean outputDeviceCallbackRegistered;
    private boolean wakePermissionDialogVisible;
    private final Handler noiseTimerHandler = new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback outputDeviceUpdates =
            new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    scheduleOutputRefresh();
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    scheduleOutputRefresh();
                }
            };

    private final BroadcastReceiver noiseUpdates =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (SCREEN_NOISE.equals(currentScreen)) showScreen(SCREEN_NOISE, false);
                }
            };

    private final BroadcastReceiver alarmUpdates =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String state = intent.getStringExtra(AlarmRingingService.EXTRA_STATE);
                    if (AlarmHomeRefreshPolicy.shouldRefresh(currentScreen, state)) {
                        showScreen(SCREEN_ALARMS, false);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getSplashScreen()
                .setOnExitAnimationListener(
                        splash -> {
                            systemSplashExited = true;
                            splash.remove();
                            playBrandLaunch();
                        });
        alarms = new AlarmRepository(this);
        preferences = new AppPreferences(this);
        new AlarmScheduler(this).reconcileOnAppOpen();
        configureWindow();
        attachShell(state);
        if (!preferences.outputPolicyAcknowledged()) {
            showOnboarding();
        } else {
            showScreen(sanitizeScreen(getIntent().getStringExtra(EXTRA_SCREEN)));
        }
        playBrandLaunch();
    }

    private void attachShell(Bundle state) {
        appRoot = new FrameLayout(this);
        appRoot.addView(buildShell(), new FrameLayout.LayoutParams(-1, -1));
        AlarmSessionStore.Snapshot alarmSession = new AlarmSessionStore(this).load();
        Intent intent = getIntent();
        boolean launcherIntent =
                Intent.ACTION_MAIN.equals(intent.getAction())
                        && intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        if (BrandLaunchPolicy.shouldShow(state != null, launcherIntent, alarmSession.state())) {
            brandLaunch = new BrandLaunchView(this);
            appRoot.addView(brandLaunch, new FrameLayout.LayoutParams(-1, -1));
        }
        setContentView(appRoot);
    }

    private void playBrandLaunch() {
        BrandLaunchView launch = brandLaunch;
        if (launch == null || brandLaunchPlaying || !systemSplashExited) return;
        brandLaunchPlaying = true;
        launch.play(
                () ->
                        launch.animate()
                                .alpha(0f)
                                .setDuration(240L)
                                .withEndAction(
                                        () -> {
                                            if (launch.getParent() == appRoot) {
                                                appRoot.removeView(launch);
                                            }
                                            if (brandLaunch == launch) brandLaunch = null;
                                        })
                                .start());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences.outputPolicyAcknowledged()) {
            showScreen(currentScreen);
            content.post(this::showPendingOemAutostartConfirmation);
        }
        if (!alarmReceiverRegistered) {
            registerAlarmReceiver();
            alarmReceiverRegistered = true;
        }
        if (!noiseReceiverRegistered) {
            registerNoiseReceiver();
            noiseReceiverRegistered = true;
        }
        if (!outputDeviceCallbackRegistered) {
            getSystemService(AudioManager.class)
                    .registerAudioDeviceCallback(outputDeviceUpdates, noiseTimerHandler);
            outputDeviceCallbackRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        if (outputDeviceCallbackRegistered) {
            getSystemService(AudioManager.class).unregisterAudioDeviceCallback(outputDeviceUpdates);
            outputDeviceCallbackRegistered = false;
        }
        super.onPause();
    }

    private void scheduleOutputRefresh() {
        noiseTimerHandler.postDelayed(
                () -> {
                    if (!isFinishing()
                            && preferences != null
                            && preferences.outputPolicyAcknowledged()) {
                        showScreen(currentScreen, false);
                    }
                },
                180L);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (preferences.outputPolicyAcknowledged()) {
            showScreen(currentScreen, false);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerAlarmReceiver() {
        IntentFilter filter = new IntentFilter(AlarmRingingService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    alarmUpdates,
                    filter,
                    INTERNAL_STATE_PERMISSION,
                    null,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(alarmUpdates, filter, INTERNAL_STATE_PERMISSION, null);
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
        noiseTimerHandler.removeCallbacksAndMessages(null);
        if (alarmReceiverRegistered) {
            try {
                unregisterReceiver(alarmUpdates);
            } catch (IllegalArgumentException ignored) {
                // Receiver already gone.
            }
        }
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
        navigation.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        navigation.setBackground(Ui.round(this, Ui.PANEL, 18, Ui.LINE));
        addNav("闹钟", SCREEN_ALARMS, R.drawable.ic_nav_alarm);
        addNav("助眠声", SCREEN_NOISE, R.drawable.ic_nav_moon);
        LinearLayout.LayoutParams navParams =
                new LinearLayout.LayoutParams(-1, Ui.dp(this, 58));
        navParams.setMargins(
                Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 10));
        shell.addView(navigation, navParams);
        return shell;
    }

    private void addNav(String label, String screen, int iconResource) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(Ui.MUTED));
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(Ui.dp(this, 19), Ui.dp(this, 19));
        iconParams.rightMargin = Ui.dp(this, 8);
        item.addView(icon, iconParams);
        item.addView(Ui.text(this, label, 13, Ui.MUTED, Ui.bold()));
        item.setTag(screen);
        item.setOnClickListener(v -> showScreen(screen));
        navigation.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void showScreen(String requested) {
        showScreen(requested, true);
    }

    private void showScreen(String requested, boolean animate) {
        noiseTimerHandler.removeCallbacksAndMessages(null);
        String screen = sanitizeScreen(requested);
        currentScreen = screen;
        content.removeAllViews();
        View page = SCREEN_NOISE.equals(screen) ? noisePage() : alarmsPage();
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        page.requestApplyInsets();
        if (animate) {
            page.setAlpha(0f);
            page.setTranslationY(Ui.dp(this, 8));
            page.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        }
        for (int i = 0; i < navigation.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) navigation.getChildAt(i);
            boolean selected = screen.equals(item.getTag());
            item.setBackground(selected ? Ui.round(this, Ui.RAISED, 14, Ui.LINE) : null);
            ((ImageView) item.getChildAt(0))
                    .setImageTintList(
                            ColorStateList.valueOf(selected ? Ui.ACID : Ui.MUTED));
            ((TextView) item.getChildAt(1)).setTextColor(selected ? Ui.ACID : Ui.MUTED);
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
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        root.addView(outputBanner(readiness));
        root.addView(Ui.space(this, 18));

        View header =
                pageHeader(
                        "几点叫醒你？",
                        "精确调度 · 轻柔渐响",
                        null,
                        null);
        root.addView(header);

        View permission = alarmPermissionBanner(readiness);
        if (permission != null) {
            root.addView(Ui.space(this, 14));
            root.addView(permission);
        }
        List<Alarm> items = alarms.listAll();
        root.addView(Ui.space(this, 16));
        root.addView(nextAlarmCard(items));
        root.addView(Ui.space(this, 20));
        root.addView(sectionHeader("所有闹钟", items.size() + " 个设置"));
        root.addView(Ui.space(this, 10));
        if (!items.isEmpty()) {
            AlarmSessionStore.Snapshot active = new AlarmSessionStore(this).load();
            for (Alarm alarm : items) {
                root.addView(alarmCard(alarm, active));
                root.addView(Ui.space(this, 12));
            }
        }
        Button add = Ui.button(this, "⊕  新建智能闹钟", true);
        Ui.marginTop(add, 8);
        add.setOnClickListener(v -> startActivity(new Intent(this, EditAlarmActivity.class)));
        root.addView(add);
        return scroll;
    }

    private View alarmCard(Alarm alarm, AlarmSessionStore.Snapshot active) {
        LinearLayout card = Ui.card(this, alarm.enabled() ? Ui.RAISED : Ui.PANEL);
        card.setPadding(
                Ui.dp(this, 16), Ui.dp(this, 15), Ui.dp(this, 16), Ui.dp(this, 15));
        card.setBackground(
                alarm.enabled()
                        ? Ui.gradient(
                                this,
                                Ui.RAISED,
                                Ui.GLASS,
                                24,
                                android.graphics.Color.rgb(83, 62, 36))
                        : Ui.round(this, Ui.PANEL, 24, Ui.LINE));
        if (!alarm.enabled()) card.setAlpha(.74f);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.BOTTOM);
        TextView time =
                Ui.text(
                        this,
                        String.format(java.util.Locale.ROOT, "%02d:%02d", alarm.hour(), alarm.minute()),
                        34,
                        alarm.enabled() ? Ui.PAPER : Ui.MUTED,
                        Ui.display());
        titleRow.addView(time);
        if (!alarm.label().isBlank()) {
            TextView tag =
                    Ui.text(
                            this,
                            alarm.label(),
                            12,
                            alarm.enabled() ? Ui.ACID : Ui.MUTED,
                            Ui.medium());
            tag.setPadding(Ui.dp(this, 8), 0, 0, Ui.dp(this, 3));
            titleRow.addView(tag);
        }
        copy.addView(titleRow);
        String meta = repeatSummary(alarm.repeatMask());
        meta +=
                alarm.enabled()
                        ? "  ·  铃声：" + AlarmSoundCatalog.find(alarm.soundId()).label()
                        : "  ·  已关闭";
        TextView summary = Ui.text(this, meta, 12, Ui.MUTED, Typeface.DEFAULT);
        summary.setPadding(0, Ui.dp(this, 2), 0, 0);
        copy.addView(summary);
        top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setChecked(alarm.enabled());
        Ui.styleSwitch(this, enabled);
        enabled.setOnCheckedChangeListener(
                (button, checked) -> setAlarmEnabled(alarm, checked));
        top.addView(enabled);
        card.addView(top);
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
        card.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, EditAlarmActivity.class)
                                        .putExtra(EditAlarmActivity.EXTRA_ALARM_ID, alarm.id())));
        return card;
    }

    private View nextAlarmCard(List<Alarm> items) {
        Alarm nextAlarm = null;
        Instant nextAt = null;
        Instant now = Instant.now();
        for (Alarm alarm : items) {
            if (!alarm.enabled()) continue;
            Instant candidate = AlarmTimeCalculator.next(alarm, now, ZoneId.systemDefault());
            if (nextAt == null || candidate.isBefore(nextAt)) {
                nextAt = candidate;
                nextAlarm = alarm;
            }
        }

        LinearLayout card = Ui.card(this, Ui.GLASS);
        card.setPadding(
                Ui.dp(this, 18), Ui.dp(this, 17), Ui.dp(this, 18), Ui.dp(this, 17));
        card.setBackground(
                Ui.gradient(
                        this,
                        Ui.ACID_SOFT,
                        Ui.PANEL,
                        26,
                        android.graphics.Color.rgb(100, 72, 35)));
        if (nextAlarm == null || nextAt == null) {
            card.addView(Ui.pill(this, "✦  下一次响铃", true), new LinearLayout.LayoutParams(-2, -2));
            TextView empty = Ui.text(this, "暂无已开启闹钟", 24, Ui.PAPER, Ui.medium());
            empty.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
            card.addView(empty);
            card.addView(Ui.text(this, "打开任一闹钟后，这里会显示准确时间。", 12, Ui.MUTED, Typeface.DEFAULT));
            return card;
        }

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(Ui.pill(this, "✦  下一次响铃", true));
        TextView date =
                Ui.text(
                        this,
                        DateTimeFormatter.ofPattern(
                                        "M月d日 E", java.util.Locale.SIMPLIFIED_CHINESE)
                                .format(nextAt.atZone(ZoneId.systemDefault())),
                        11,
                        Ui.MUTED,
                        Typeface.MONOSPACE);
        date.setGravity(Gravity.END);
        heading.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(heading);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.BOTTOM);
        TextView time =
                Ui.text(
                        this,
                        DateTimeFormatter.ofPattern("HH:mm")
                                .format(nextAt.atZone(ZoneId.systemDefault())),
                        48,
                        Ui.PAPER,
                        Ui.display());
        time.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 5));
        timeRow.addView(time);
        TextView period =
                Ui.text(
                        this,
                        nextAt.atZone(ZoneId.systemDefault()).getHour() < 12 ? "上午" : "下午",
                        13,
                        Ui.ACID,
                        Ui.medium());
        period.setPadding(Ui.dp(this, 8), 0, 0, Ui.dp(this, 10));
        timeRow.addView(period);
        card.addView(timeRow);
        card.addView(Ui.divider(this));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, Ui.dp(this, 11), 0, 0);
        TextView countdown =
                Ui.text(this, "◷  " + formatCountdown(now, nextAt), 12, Ui.PAPER, Ui.medium());
        footer.addView(countdown, new LinearLayout.LayoutParams(0, -2, 1));
        String note =
                nextAlarm.label().isBlank()
                        ? repeatSummary(nextAlarm.repeatMask())
                        : nextAlarm.label();
        footer.addView(Ui.text(this, note, 11, Ui.MUTED, Typeface.DEFAULT));
        card.addView(footer);

        Alarm target = nextAlarm;
        card.setOnClickListener(
                v ->
                        startActivity(
                                new Intent(this, EditAlarmActivity.class)
                                        .putExtra(EditAlarmActivity.EXTRA_ALARM_ID, target.id())));
        return card;
    }

    private static String formatCountdown(Instant now, Instant target) {
        long minutes = Math.max(1L, Duration.between(now, target).toMinutes());
        long days = minutes / (24L * 60L);
        long hours = (minutes % (24L * 60L)) / 60L;
        long rest = minutes % 60L;
        if (days > 0) return days + "天 " + hours + "小时后";
        if (hours > 0) return hours + "小时 " + rest + "分钟后";
        return rest + "分钟后";
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
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        root.addView(outputBanner(readiness));
        root.addView(Ui.space(this, 18));
        root.addView(pageHeader("想听什么入睡？", "8 种高品质原声 · 本地离线播放", null, null));
        root.addView(Ui.space(this, 18));

        NoiseSessionStore.Snapshot session = new NoiseSessionStore(this).load();
        NoiseTimerPresentation.ViewState timerPresentation =
                NoiseTimerPresentation.resolve(
                        session.state(),
                        session.endsAtEpochMs(),
                        System.currentTimeMillis(),
                        preferences.noiseTimerMinutes(),
                        session.fadeSeconds());
        boolean active = timerPresentation.active();
        String selectedSoundId =
                active
                        ? SleepSoundCatalog.normalizeId(session.soundId())
                        : SleepSoundCatalog.normalizeId(preferences.noiseSoundId());

        LinearLayout libraryTitle = new LinearLayout(this);
        libraryTitle.setGravity(Gravity.CENTER_VERTICAL);
        libraryTitle.addView(
                Ui.text(this, "声音库", 13, Ui.MUTED, Ui.medium()),
                new LinearLayout.LayoutParams(0, -2, 1));
        libraryTitle.addView(
                Ui.text(this, "左右滑动选择 8 种原声", 11, Ui.MUTED, Typeface.DEFAULT));
        root.addView(libraryTitle);
        root.addView(Ui.space(this, 10));
        HorizontalScrollView soundStrip = new HorizontalScrollView(this);
        soundStrip.setHorizontalScrollBarEnabled(false);
        soundStrip.setClipToPadding(false);
        LinearLayout soundChoices = new LinearLayout(this);
        soundChoices.setOrientation(LinearLayout.HORIZONTAL);
        List<SleepSoundCatalog.Item> sounds = SleepSoundCatalog.all();
        for (int i = 0; i < sounds.size(); i++) {
            SleepSoundCatalog.Item item = sounds.get(i);
            String soundId = item.id();
            boolean selected = soundId.equals(selectedSoundId);
            FrameLayout choice = soundChoice(item, selected);
            choice.setOnClickListener(v -> selectSleepSound(soundId, session, active));
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(Ui.dp(this, 124), Ui.dp(this, 154));
            if (i > 0) params.leftMargin = Ui.dp(this, 11);
            soundChoices.addView(choice, params);
        }
        soundStrip.addView(soundChoices, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(soundStrip, new LinearLayout.LayoutParams(-1, Ui.dp(this, 154)));
        root.addView(Ui.space(this, 16));

        LinearLayout status = Ui.card(this, active ? Ui.RAISED : Ui.PANEL);
        status.setPadding(
                Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 18));
        status.setBackground(
                active
                        ? Ui.gradient(
                                this,
                                Ui.RAISED,
                                Ui.GLASS,
                                24,
                                android.graphics.Color.rgb(86, 64, 37))
                        : Ui.round(this, Ui.GLASS, 24, Ui.LINE));
        TextView playing =
                Ui.text(
                        this,
                        active ? "●  正在播放" : "●  准备播放",
                        11,
                        active ? Ui.WARM : Ui.MUTED,
                        Ui.medium());
        status.addView(playing);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView soundName =
                Ui.text(this, WhiteNoiseService.soundLabel(selectedSoundId), 22, Ui.PAPER, Ui.bold());
        soundName.setPadding(0, Ui.dp(this, 7), 0, 0);
        copy.addView(soundName);
        TextView state = Ui.text(this, session.detail(), 12, Ui.MUTED, Typeface.DEFAULT);
        state.setPadding(0, Ui.dp(this, 2), 0, 0);
        copy.addView(state);
        titleRow.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        AmbientWaveView wave = new AmbientWaveView(this, active && !"paused".equals(session.state()));
        titleRow.addView(wave, new LinearLayout.LayoutParams(Ui.dp(this, 94), Ui.dp(this, 48)));
        status.addView(titleRow);
        TextView remaining =
                Ui.text(
                        this,
                        active
                                ? timerPresentation.remainingLabel()
                                        + " · "
                                        + timerPresentation.fadeLabel()
                                : "选择声音与时长后开始播放",
                        12,
                        active ? Ui.ACID : Ui.MUTED,
                        Ui.medium());
        remaining.setPadding(0, Ui.dp(this, 10), 0, 0);
        status.addView(remaining);

        LinearLayout playbackActions = new LinearLayout(this);
        playbackActions.setOrientation(LinearLayout.HORIZONTAL);
        playbackActions.setPadding(0, Ui.dp(this, 14), 0, 0);
        Button primary =
                Ui.button(
                        this,
                        active
                                ? ("paused".equals(session.state()) ? "▶  继续播放" : "Ⅱ  暂停助眠声")
                                : "▶  播放助眠声",
                        true);
        playbackActions.addView(
                primary,
                new LinearLayout.LayoutParams(0, Ui.dp(this, 52), active ? 1.15f : 1f));
        if (active) {
            Button stop = Ui.button(this, "停止", false);
            LinearLayout.LayoutParams stopParams =
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 52), .85f);
            stopParams.leftMargin = Ui.dp(this, 10);
            playbackActions.addView(stop, stopParams);
            stop.setOnClickListener(
                    v ->
                            startService(
                                    new Intent(this, WhiteNoiseService.class)
                                            .setAction(WhiteNoiseService.ACTION_STOP)));
        }
        status.addView(playbackActions);
        root.addView(status);

        TimerSelector timer = timerSelector(preferences.noiseTimerMinutes());
        FadeSelector fade = fadeSelector(preferences.noiseFadeSeconds());
        if (timerPresentation.showNextSessionSettings()) {
            root.addView(Ui.space(this, 16));
            LinearLayout controls = Ui.card(this, Ui.GLASS);
            controls.setPadding(
                    Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 18));
            LinearLayout settingsHeader = new LinearLayout(this);
            settingsHeader.setGravity(Gravity.CENTER_VERTICAL);
            ImageView timerIcon = new ImageView(this);
            timerIcon.setImageResource(R.drawable.ic_playback_timer);
            timerIcon.setImageTintList(ColorStateList.valueOf(Ui.ACID));
            LinearLayout.LayoutParams timerIconParams =
                    new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18));
            timerIconParams.rightMargin = Ui.dp(this, 8);
            settingsHeader.addView(timerIcon, timerIconParams);
            settingsHeader.addView(
                    Ui.text(this, "本次播放设置", 13, Ui.PAPER, Ui.bold()),
                    new LinearLayout.LayoutParams(0, -2, 1));
            controls.addView(settingsHeader);
            controls.addView(Ui.space(this, 16));
            controls.addView(timer.root);
            controls.addView(Ui.space(this, 16));
            controls.addView(Ui.divider(this));
            controls.addView(Ui.space(this, 16));
            controls.addView(fade.root);
            root.addView(controls);
        } else {
            scheduleNoiseTimer(remaining, session);
        }

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
                    String soundId = selectedSoundId;
                    int timerMinutes = timer.minutes;
                    int fadeSeconds = fade.seconds;
                    preferences.saveNoiseDefaults(timerMinutes, fadeSeconds, soundId);
                    Intent play =
                            new Intent(this, WhiteNoiseService.class)
                                    .setAction(WhiteNoiseService.ACTION_START)
                                    .putExtra(WhiteNoiseService.EXTRA_SOUND_ID, soundId)
                                    .putExtra(WhiteNoiseService.EXTRA_TIMER_MINUTES, timerMinutes)
                                    .putExtra(WhiteNoiseService.EXTRA_FADE_SECONDS, fadeSeconds);
                    startForegroundService(play);
                });
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

    private FrameLayout soundChoice(SleepSoundCatalog.Item item, boolean selected) {
        FrameLayout choice = new FrameLayout(this);
        choice.setBackground(Ui.round(this, Color.TRANSPARENT, 24, Color.TRANSPARENT));
        choice.setClipToOutline(true);

        ImageView texture = new ImageView(this);
        texture.setImageResource(item.textureResourceId());
        texture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        texture.setAlpha(selected ? .94f : .72f);
        choice.addView(texture, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        GradientDrawable shadow =
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[] {
                            Color.argb(18, 7, 9, 14),
                            Color.argb(88, 7, 9, 14),
                            Color.argb(242, 7, 9, 14)
                        });
        scrim.setBackground(shadow);
        choice.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout foreground = new LinearLayout(this);
        foreground.setOrientation(LinearLayout.VERTICAL);
        foreground.setGravity(Gravity.BOTTOM);
        foreground.setPadding(
                Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13));
        int accent = soundAccent(item.id());
        foreground.addView(Ui.text(this, item.shortLabel(), 14, Ui.PAPER, Ui.bold()));
        TextView note = Ui.text(this, item.note(), 10, Ui.MUTED, Typeface.DEFAULT);
        note.setPadding(0, Ui.dp(this, 2), 0, 0);
        foreground.addView(note);
        choice.addView(foreground, new FrameLayout.LayoutParams(-1, -1));

        View selectionBorder = new View(this);
        selectionBorder.setBackground(
                Ui.round(this, Color.TRANSPARENT, 24, selected ? accent : Ui.LINE));
        choice.addView(selectionBorder, new FrameLayout.LayoutParams(-1, -1));
        choice.setElevation(Ui.dp(this, selected ? 4 : 1));
        return choice;
    }

    private int soundAccent(String soundId) {
        return switch (soundId) {
            case "rain", "stream", "ocean" -> Ui.BLUE;
            case "fireplace" -> Ui.ACID;
            case "crickets", "thunder" -> Ui.VIOLET;
            default -> Ui.WARM;
        };
    }

    private void scheduleNoiseTimer(
            TextView remaining, NoiseSessionStore.Snapshot session) {
        Runnable ticker =
                new Runnable() {
                    @Override
                    public void run() {
                        if (!SCREEN_NOISE.equals(currentScreen) || !remaining.isAttachedToWindow()) return;
                        NoiseTimerPresentation.ViewState state =
                                NoiseTimerPresentation.resolve(
                                        session.state(),
                                        session.endsAtEpochMs(),
                                        System.currentTimeMillis(),
                                        preferences.noiseTimerMinutes(),
                                        session.fadeSeconds());
                        remaining.setText(state.remainingLabel() + " · " + state.fadeLabel());
                        noiseTimerHandler.postDelayed(this, 15_000L);
                    }
                };
        noiseTimerHandler.postDelayed(ticker, 15_000L);
    }

    private void selectSleepSound(
            String soundId, NoiseSessionStore.Snapshot session, boolean active) {
        String currentSoundId =
                active
                        ? SleepSoundCatalog.normalizeId(session.soundId())
                        : SleepSoundCatalog.normalizeId(preferences.noiseSoundId());
        if (soundId.equals(currentSoundId)) return;
        preferences.saveNoiseDefaults(
                preferences.noiseTimerMinutes(), preferences.noiseFadeSeconds(), soundId);
        if (active) {
            startService(
                    new Intent(this, WhiteNoiseService.class)
                            .setAction(WhiteNoiseService.ACTION_SWITCH_SOUND)
                            .putExtra(WhiteNoiseService.EXTRA_SOUND_ID, soundId));
        } else {
            new NoiseSessionStore(this)
                    .save(
                            new NoiseSessionStore.Snapshot(
                                    "stopped",
                                    SleepSoundCatalog.normalizeId(soundId),
                                    0L,
                                    preferences.noiseFadeSeconds(),
                                    "尚未播放"));
            showScreen(SCREEN_NOISE);
        }
    }

    private View outputBanner(ReadinessChecker.Status status) {
        String title;
        String detail;
        int accent;
        int dotColor;
        int iconResource = R.drawable.ic_output_speaker;
        Runnable action = this::requestOemAutostart;
        String actionLabel =
                status.oemAutostartConfirmed() ? "✓ 自启动已确认" : "! 自启动待确认";
        int actionAccent = status.oemAutostartConfirmed() ? Ui.WARM : Ui.ACID;
        int actionFill =
                status.oemAutostartConfirmed()
                        ? Color.argb(28, Color.red(Ui.WARM), Color.green(Ui.WARM), Color.blue(Ui.WARM))
                        : Ui.ACID_SOFT;
        if (!status.bluetoothPermission()) {
            title = "允许蓝牙权限后自动选择输出";
            detail = "用于判断耳机是否连接，不读取或保存耳机名称。";
            accent = Ui.WARM;
            dotColor = Ui.ACID;
            action = this::requestBluetooth;
            actionLabel = "允许";
            actionAccent = Ui.ACID;
            actionFill = Ui.ACID_SOFT;
        } else if (!status.mediaVolume()) {
            title = "手机媒体音量当前为 0";
            detail = "悄醒不会替你调高音量。";
            accent = Ui.ACID;
            dotColor = Ui.DANGER;
        } else if (!status.outputSelectable()) {
            title = "检测到多个耳机输出";
            detail = "请暂时只保留一个耳机连接。";
            accent = Ui.DANGER;
            dotColor = Ui.DANGER;
            iconResource = R.drawable.ic_output_headphones;
        } else if (status.headsetConnected()) {
            title = "耳机播放";
            detail = "跟随系统媒体输出 · 播放时自动确认路由";
            accent = Ui.WARM;
            dotColor = Ui.WARM;
            iconResource = R.drawable.ic_output_headphones;
        } else {
            title = "扬声器播放";
            detail = "内置音效引擎 · 跟随系统音量";
            accent = Ui.ACID;
            dotColor = Ui.WARM;
        }
        return outputStatusCard(
                title,
                detail,
                accent,
                dotColor,
                iconResource,
                actionLabel,
                actionAccent,
                actionFill,
                action);
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
        if (!status.backgroundAllowed()) {
            return infoBanner(
                    "后台运行已受限",
                    "系统可能拦截到点启动。请在应用详情中将电池用量设为“不受限制”，并按手机系统允许自启动；强行停止后仍无法唤醒。",
                    Ui.WARM,
                    "去解除",
                    this::requestBackgroundSettings);
        }
        if (!status.standbyAllowed()) {
            return infoBanner(
                    "应用处于受限待机状态",
                    "Android 会严格限制后台任务和闹钟，请在应用电池设置中改为“不受限制”。",
                    Ui.WARM,
                    "去解除",
                    this::requestBackgroundSettings);
        }
        if (!status.batteryOptimizationExempt()) {
            return infoBanner(
                    "电池优化可能拦截后台唤醒",
                    "可通过 Android 专用系统确认，将悄醒加入电池优化豁免名单。",
                    Ui.WARM,
                    "去允许",
                    this::requestBatteryOptimization);
        }
        return null;
    }

    private boolean showPendingOemAutostartConfirmation() {
        if (wakePermissionDialogVisible
                || !preferences.oemAutostartConfirmationPending(Build.MANUFACTURER)) {
            return false;
        }
        wakePermissionDialogVisible = true;
        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("确认厂商自启动")
                        .setMessage(
                                "若需关闭应用后也正常唤起闹铃，请开启“自启动”、“允许后台启动”权限")
                        .setPositiveButton(
                                "已经开启",
                                (ignoredDialog, which) -> {
                                    preferences.finishOemAutostartConfirmation(
                                            Build.MANUFACTURER, true);
                                    showScreen(currentScreen, false);
                                })
                        .setNegativeButton(
                                "还没开启",
                                (ignoredDialog, which) -> {
                                        preferences.finishOemAutostartConfirmation(
                                                Build.MANUFACTURER, false);
                                    showScreen(currentScreen, false);
                                })
                        .create();
        dialog.setOnDismissListener(ignored -> wakePermissionDialogVisible = false);
        dialog.show();
        Ui.styleDialog(dialog);
        return true;
    }

    private static AlarmWakePermissionPolicy.Issue firstWakeIssue(
            ReadinessChecker.Status status) {
        return AlarmWakePermissionPolicy.firstIssue(
                status.exactAlarm(),
                status.notifications(),
                status.fullScreen(),
                status.backgroundAllowed(),
                status.standbyAllowed(),
                status.batteryOptimizationExempt(),
                status.oemAutostartConfirmed());
    }

    private void requestWakePermission(AlarmWakePermissionPolicy.Issue issue) {
        switch (issue) {
            case EXACT_ALARM -> requestExactAlarm();
            case NOTIFICATIONS -> requestNotifications();
            case FULL_SCREEN -> requestFullScreen();
            case BACKGROUND_RESTRICTED, STANDBY_RESTRICTED -> requestBackgroundSettings();
            case BATTERY_OPTIMIZATION -> requestBatteryOptimization();
            case OEM_AUTOSTART_UNCONFIRMED -> requestOemAutostart();
            case NONE -> {
                // Nothing to request.
            }
        }
    }

    private View infoBanner(
            String title, String detail, int accent, String actionLabel, Runnable action) {
        LinearLayout card = Ui.card(this, Ui.GLASS);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.round(this, Ui.GLASS, 20, accent));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, "!", 13, accent, Ui.bold());
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(this, Ui.PANEL, 999, accent));
        LinearLayout.LayoutParams markParams =
                new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        markParams.rightMargin = Ui.dp(this, 10);
        row.addView(mark, markParams);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 13, Ui.PAPER, Ui.bold()));
        TextView description = Ui.text(this, detail, 11, Ui.MUTED, Typeface.DEFAULT);
        description.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(description);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView button = Ui.text(this, actionLabel, 12, Ui.ACID, Ui.bold());
            button.setGravity(Gravity.CENTER);
            button.setPadding(
                    Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 7));
            button.setBackground(Ui.round(this, Ui.ACID_SOFT, 14, Ui.ACID_SOFT));
            row.addView(button);
            card.setOnClickListener(v -> action.run());
        }
        card.addView(row);
        return card;
    }

    private View outputStatusCard(
            String title,
            String detail,
            int accent,
            int dotColor,
            int iconResource,
            String actionLabel,
            int actionAccent,
            int actionFill,
            Runnable action) {
        LinearLayout card = Ui.card(this, Ui.GLASS);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        card.setBackground(Ui.round(this, Ui.GLASS, 19, Ui.LINE));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setImageTintList(ColorStateList.valueOf(accent));
        icon.setPadding(
                Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        icon.setBackground(
                Ui.round(
                        this,
                        Color.argb(58, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        999,
                        Color.TRANSPARENT));
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        iconParams.rightMargin = Ui.dp(this, 11);
        row.addView(icon, iconParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.text(this, title, 13, Ui.PAPER, Ui.bold()));
        View dot = new View(this);
        dot.setBackground(Ui.round(this, dotColor, 999, Color.TRANSPARENT));
        LinearLayout.LayoutParams dotParams =
                new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6));
        dotParams.leftMargin = Ui.dp(this, 7);
        titleRow.addView(dot, dotParams);
        copy.addView(titleRow);
        TextView description = Ui.text(this, detail, 10, Ui.MUTED, Typeface.DEFAULT);
        description.setPadding(0, Ui.dp(this, 2), 0, 0);
        copy.addView(description);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView button = Ui.text(this, actionLabel, 12, actionAccent, Ui.bold());
            button.setGravity(Gravity.CENTER);
            button.setPadding(
                    Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 7));
            button.setBackground(
                    new RippleDrawable(
                            ColorStateList.valueOf(
                                    Color.argb(
                                            48,
                                            Color.red(actionAccent),
                                            Color.green(actionAccent),
                                            Color.blue(actionAccent))),
                            Ui.round(this, actionFill, 14, actionAccent),
                            null));
            button.setOnClickListener(v -> action.run());
            row.addView(button);
            card.setOnClickListener(v -> action.run());
        }
        card.addView(row);
        return card;
    }

    private View principleCard(String title, String detail, int accent) {
        LinearLayout card = Ui.card(this, Ui.GLASS);
        card.setBackground(Ui.gradient(this, Ui.GLASS, Ui.PANEL, 24, accent));
        TextView heading =
                Ui.text(this, title, 19, accent, Ui.bold());
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

    private void requestBackgroundSettings() {
        startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + getPackageName())));
    }

    private void requestBatteryOptimization() {
        Intent request =
                new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(android.net.Uri.parse("package:" + getPackageName()));
        try {
            startActivity(request);
        } catch (RuntimeException unavailable) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException missingSettings) {
                Toast.makeText(this, "系统没有提供电池优化设置入口。", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestOemAutostart() {
        preferences.beginOemAutostartConfirmation(Build.MANUFACTURER);
        if (OemAutostartNavigator.open(this)) return;
        preferences.finishOemAutostartConfirmation(Build.MANUFACTURER, false);
        Toast.makeText(this, "系统没有提供可打开的自启动或电池设置入口。", Toast.LENGTH_LONG).show();
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
        scroll.setBackground(Ui.pageBackground(this));
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener(
                    (view, insets) -> {
                        android.graphics.Insets bars =
                                insets.getInsets(WindowInsets.Type.statusBars());
                        view.setPadding(0, bars.top, 0, 0);
                        return insets;
                    });
        }
        return scroll;
    }

    private LinearLayout pageRoot(ScrollView scroll) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 34));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        return root;
    }

    private View pageHeader(
            String title, String subtitle, String actionLabel, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 27, Ui.PAPER, Ui.bold()));
        TextView detail = Ui.text(this, subtitle, 12, Ui.MUTED, Typeface.DEFAULT);
        detail.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null && actionLabel != null) {
            TextView button = Ui.text(this, actionLabel, 24, Ui.PAPER, Ui.display());
            button.setGravity(Gravity.CENTER);
            button.setContentDescription("新建闹钟");
            button.setBackground(Ui.round(this, Ui.RAISED, 999, Ui.LINE));
            button.setOnClickListener(v -> action.run());
            row.addView(
                    button,
                    new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        }
        return row;
    }

    private View sectionHeader(String title, String trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(
                Ui.text(this, title, 12, Ui.MUTED, Ui.medium()),
                new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null && !trailing.isBlank()) {
            row.addView(Ui.text(this, trailing, 11, Ui.MUTED, Typeface.DEFAULT));
        }
        return row;
    }

    private TimerSelector timerSelector(int savedMinutes) {
        TimerSelector selector = new TimerSelector();
        selector.minutes = normalizeTimerMinutes(savedMinutes);
        selector.root = new LinearLayout(this);
        selector.root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(
                Ui.text(this, "定时关闭时长", 12, Ui.MUTED, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView value =
                Ui.text(this, selector.minutes + " 分钟", 12, Ui.PAPER, Ui.bold());
        heading.addView(value);
        selector.root.addView(heading);
        selector.root.addView(Ui.space(this, 7));

        SeekBar slider = new SeekBar(this);
        slider.setMax(23);
        slider.setProgress((selector.minutes - 5) / 5);
        slider.setSplitTrack(false);
        slider.setProgressTintList(ColorStateList.valueOf(Ui.ACID));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        slider.setThumbTintList(ColorStateList.valueOf(Ui.ACID));
        slider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        selector.minutes = 5 + progress * 5;
                        value.setText(selector.minutes + " 分钟");
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                });
        selector.root.addView(slider, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));

        LinearLayout scale = new LinearLayout(this);
        scale.setGravity(Gravity.CENTER_VERTICAL);
        addTimerScaleLabel(scale, "5min", Gravity.START);
        addTimerScaleLabel(scale, "30min", Gravity.CENTER);
        addTimerScaleLabel(scale, "60min", Gravity.CENTER);
        addTimerScaleLabel(scale, "120min", Gravity.END);
        selector.root.addView(scale);
        return selector;
    }

    private void addTimerScaleLabel(LinearLayout row, String label, int gravity) {
        TextView text = Ui.text(this, label, 10, Color.rgb(101, 116, 139), Typeface.MONOSPACE);
        text.setGravity(gravity);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private FadeSelector fadeSelector(int savedSeconds) {
        FadeSelector selector = new FadeSelector();
        selector.seconds = savedSeconds == 0 || savedSeconds == 30 ? savedSeconds : 15;
        selector.root = new LinearLayout(this);
        selector.root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(
                Ui.text(this, "停止方式", 12, Ui.MUTED, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView value =
                Ui.text(this, fadeSummary(selector.seconds), 12, Ui.PAPER, Ui.bold());
        heading.addView(value);
        selector.root.addView(heading);
        selector.root.addView(Ui.space(this, 9));

        int[] seconds = {15, 30, 0};
        String[] labels = {"15s 柔和渐隐", "30s 极缓平滑", "立即停止"};
        TextView[] choices = new TextView[seconds.length];
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < seconds.length; i++) {
            TextView choice = Ui.choice(this, labels[i], selector.seconds == seconds[i]);
            choice.setTextSize(10);
            choice.setSingleLine(true);
            int index = i;
            choice.setOnClickListener(
                    v -> {
                        selector.seconds = seconds[index];
                        value.setText(fadeSummary(selector.seconds));
                        for (int j = 0; j < choices.length; j++) {
                            Ui.setChoiceSelected(choices[j], j == index);
                        }
                    });
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1);
            if (i > 0) params.leftMargin = Ui.dp(this, 7);
            row.addView(choice, params);
            choices[i] = choice;
        }
        selector.root.addView(row);
        return selector;
    }

    private static int normalizeTimerMinutes(int minutes) {
        if (minutes < 5 || minutes > 120) return 30;
        return Math.max(5, Math.min(120, Math.round(minutes / 5f) * 5));
    }

    private static String fadeSummary(int seconds) {
        return seconds == 0 ? "立即停止" : seconds + " 秒渐隐";
    }

    private TextView hero(String value, int size) {
        TextView hero =
                Ui.text(this, value, size, Ui.PAPER, Ui.display());
        hero.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        return hero;
    }

    private static final class TimerSelector {
        private LinearLayout root;
        private int minutes;
    }

    private static final class FadeSelector {
        private LinearLayout root;
        private int seconds;
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
