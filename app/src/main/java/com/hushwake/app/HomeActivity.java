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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmHomeRefreshPolicy;
import com.hushwake.app.alarm.AlarmRingingService;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.alarm.AlarmSessionStore;
import com.hushwake.app.alarm.AlarmStopPolicy;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.domain.AlarmTimeCalculator;
import com.hushwake.app.noise.NoiseSessionStore;
import com.hushwake.app.noise.NoiseTimerPresentation;
import com.hushwake.app.noise.SleepSoundCatalog;
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
    private boolean alarmReceiverRegistered;
    private boolean noiseReceiverRegistered;
    private final Handler noiseTimerHandler = new Handler(Looper.getMainLooper());

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
        if (!alarmReceiverRegistered) {
            registerAlarmReceiver();
            alarmReceiverRegistered = true;
        }
        if (!noiseReceiverRegistered) {
            registerNoiseReceiver();
            noiseReceiverRegistered = true;
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
        showScreen(requested, true);
    }

    private void showScreen(String requested, boolean animate) {
        noiseTimerHandler.removeCallbacksAndMessages(null);
        String screen = sanitizeScreen(requested);
        currentScreen = screen;
        content.removeAllViews();
        View page = SCREEN_NOISE.equals(screen) ? noisePage() : alarmsPage();
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (animate) {
            page.setAlpha(0f);
            page.setTranslationY(Ui.dp(this, 8));
            page.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        }
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
        root.addView(Ui.eyebrow(this, "闹钟"));
        root.addView(hero("几点叫醒你？", 36));
        TextView promise =
                Ui.text(
                        this,
                        "不同于普通闹钟：悄醒只用媒体音播放，跟随手机媒体音量；连接耳机后只走已验证耳机，断连也不会转到扬声器。",
                        13,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        promise.setPadding(0, 0, 0, Ui.dp(this, 20));
        root.addView(promise);
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
            TextView title =
                    Ui.text(
                            this,
                            "还没有闹钟",
                            20,
                            Ui.PAPER,
                            Ui.medium());
            title.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 5));
            empty.addView(title);
            empty.addView(Ui.text(this, "先设一个时间，之后随时可以调整。", 13, Ui.MUTED, Typeface.DEFAULT));
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
                        42,
                        alarm.enabled() ? Ui.PAPER : Ui.MUTED,
                        Ui.display());
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
        summary.setPadding(0, Ui.dp(this, 1), 0, 0);
        card.addView(summary);
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
                            Ui.WARM,
                            Ui.medium());
            nextView.setPadding(0, Ui.dp(this, 9), 0, 0);
            card.addView(nextView);
            if (new AlarmScheduler(this).canScheduleExact()) {
                TextView backgroundReady =
                        Ui.text(
                                this,
                                "后台唤醒已交给 Android · 点此检查电池/自启动",
                                12,
                                Ui.MUTED,
                                Typeface.DEFAULT);
                backgroundReady.setPadding(0, Ui.dp(this, 4), 0, 0);
                backgroundReady.setOnClickListener(v -> requestBackgroundSettings());
                card.addView(backgroundReady);
            }
        } else {
            TextView off = Ui.text(this, "已关闭", 12, Ui.MUTED, Ui.medium());
            off.setPadding(0, Ui.dp(this, 9), 0, 0);
            card.addView(off);
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
        root.addView(Ui.eyebrow(this, "助眠声"));
        root.addView(hero("想听什么入睡？", 36));
        ReadinessChecker.Status readiness = ReadinessChecker.inspect(this);
        root.addView(outputBanner(readiness));
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
                Ui.text(this, "声音库", 16, Ui.PAPER, Ui.medium()),
                new LinearLayout.LayoutParams(0, -2, 1));
        libraryTitle.addView(
                Ui.text(this, "左右滑动 · 6 种真实录音", 11, Ui.MUTED, Typeface.DEFAULT));
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
            LinearLayout choice = soundChoice(item, selected);
            choice.setOnClickListener(v -> selectSleepSound(soundId, session, active));
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(Ui.dp(this, 116), Ui.dp(this, 140));
            if (i > 0) params.leftMargin = Ui.dp(this, 10);
            soundChoices.addView(choice, params);
        }
        soundStrip.addView(soundChoices, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(soundStrip, new LinearLayout.LayoutParams(-1, Ui.dp(this, 140)));
        root.addView(Ui.space(this, 14));

        LinearLayout status = Ui.card(this, active ? Ui.RAISED : Ui.PANEL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.eyebrow(this, active ? "正在播放" : "准备播放"));
        TextView soundName =
                Ui.text(this, WhiteNoiseService.soundLabel(selectedSoundId), 21, Ui.PAPER, Ui.medium());
        soundName.setPadding(0, Ui.dp(this, 5), 0, 0);
        copy.addView(soundName);
        status.addView(copy);
        TextView state = Ui.text(this, session.detail(), 12, Ui.MUTED, Typeface.DEFAULT);
        state.setPadding(0, Ui.dp(this, 9), 0, 0);
        status.addView(state);
        TextView remaining =
                Ui.text(
                        this,
                        active
                                ? timerPresentation.remainingLabel()
                                        + " · "
                                        + timerPresentation.fadeLabel()
                                : "选择声音与时长后开始播放",
                        12,
                        active ? Ui.WARM : Ui.MUTED,
                        Ui.medium());
        remaining.setPadding(0, Ui.dp(this, 4), 0, 0);
        status.addView(remaining);

        LinearLayout playbackActions = new LinearLayout(this);
        playbackActions.setOrientation(LinearLayout.HORIZONTAL);
        playbackActions.setPadding(0, Ui.dp(this, 14), 0, 0);
        Button primary =
                Ui.button(
                        this,
                        active
                                ? ("paused".equals(session.state()) ? "继续播放" : "暂停")
                                : "播放",
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

        Spinner timer =
                Ui.spinner(
                        this,
                        new String[] {"持续播放（最长 8 小时）", "15 分钟", "30 分钟", "45 分钟", "60 分钟"});
        timer.setSelection(
                indexOf(new int[] {0, 15, 30, 45, 60}, preferences.noiseTimerMinutes()));
        Spinner fade = Ui.spinner(this, new String[] {"直接结束", "5 秒渐隐", "15 秒渐隐", "30 秒渐隐"});
        fade.setSelection(indexOf(new int[] {0, 5, 15, 30}, preferences.noiseFadeSeconds()));
        if (timerPresentation.showNextSessionSettings()) {
            root.addView(Ui.space(this, 14));
            LinearLayout controls = Ui.card(this, Ui.PANEL);
            controls.addView(Ui.text(this, "本次播放", 18, Ui.PAPER, Ui.medium()));
            addField(controls, "播放时长", timer);
            addField(controls, "结束方式", fade);
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

    private LinearLayout soundChoice(SleepSoundCatalog.Item item, boolean selected) {
        LinearLayout choice = new LinearLayout(this);
        choice.setOrientation(LinearLayout.VERTICAL);
        choice.setGravity(Gravity.CENTER);
        choice.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        choice.setBackground(Ui.round(this, selected ? Ui.ACID : Ui.PANEL, 24, selected ? Ui.ACID : Ui.LINE));
        choice.addView(Ui.text(this, item.symbol(), 27, selected ? Ui.INK : Ui.WARM, Ui.display()));
        TextView label = Ui.text(this, item.shortLabel(), 14, selected ? Ui.INK : Ui.PAPER, Ui.medium());
        label.setPadding(0, Ui.dp(this, 8), 0, 0);
        choice.addView(label);
        TextView note = Ui.text(this, item.note(), 10, selected ? Ui.INK : Ui.MUTED, Typeface.DEFAULT);
        note.setPadding(0, Ui.dp(this, 2), 0, 0);
        choice.addView(note);
        return choice;
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
            title = "耳机播放";
            detail = "已连接耳机 · 断连会立即静音";
            accent = Ui.ACID;
        } else {
            title = "扬声器播放";
            detail = "当前无耳机 · 跟随媒体音量";
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
        if (!status.backgroundAllowed()) {
            return infoBanner(
                    "后台运行已受限",
                    "系统可能拦截到点启动。请在应用详情中将电池用量设为“不受限制”，并按手机系统允许自启动；强行停止后仍无法唤醒。",
                    Ui.WARM,
                    "去解除",
                    this::requestBackgroundSettings);
        }
        return null;
    }

    private View infoBanner(
            String title, String detail, int accent, String actionLabel, Runnable action) {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = Ui.text(this, "●", 14, accent, Typeface.DEFAULT_BOLD);
        row.addView(mark, new LinearLayout.LayoutParams(Ui.dp(this, 28), -2));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 14, Ui.PAPER, Ui.medium()));
        TextView description = Ui.text(this, detail, 12, Ui.MUTED, Typeface.DEFAULT);
        description.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(description);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            TextView button = Ui.text(this, actionLabel, 13, Ui.ACID, Typeface.DEFAULT_BOLD);
            button.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, Ui.dp(this, 8));
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

    private void requestBackgroundSettings() {
        startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:" + getPackageName())));
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
                Ui.text(this, value, size, Ui.PAPER, Ui.display());
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
