package com.hushwake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmPreviewSelection;
import com.hushwake.app.alarm.AlarmRingingService;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.alarm.AlarmSessionStore;
import com.hushwake.app.alarm.AlarmSoundCatalog;
import com.hushwake.app.alarm.AlarmStopPolicy;
import com.hushwake.app.alarm.AlarmTimeSelection;
import com.hushwake.app.audio.PrivatePlaybackEngine;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.reliability.OemAutostartNavigator;
import com.hushwake.app.ui.Ui;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class EditAlarmActivity extends Activity {
    public static final String EXTRA_ALARM_ID = "edit_alarm_id";

    private AlarmRepository repository;
    private AppPreferences preferences;
    private Alarm existing;
    private int hour;
    private int minute;
    private TextView timeValue;
    private EditText label;
    private final TextView[] weekdayChoices = new TextView[7];
    private int repeatMask;
    private final List<TextView> soundChoices = new ArrayList<>();
    private String selectedSoundId = "soft_chime";
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private PrivatePlaybackEngine previewEngine;
    private View oemAutostartWarning;
    private boolean oemConfirmationDialogVisible;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new AlarmRepository(this);
        preferences = new AppPreferences(this);
        long id = getIntent().getLongExtra(EXTRA_ALARM_ID, 0L);
        existing = id == 0L ? null : repository.find(id);
        AlarmTimeSelection suggested = AlarmTimeSelection.suggested(LocalTime.now());
        hour = existing == null ? suggested.hour() : existing.hour();
        minute = existing == null ? suggested.minute() : existing.minute();
        configureWindow();
        setContentView(buildScreen());
        bind(existing);
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.INK);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 48));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView back = Ui.text(this, "←  返回闹钟", 14, Ui.ACID, Typeface.DEFAULT_BOLD);
        back.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.eyebrow(this, existing == null ? "新闹钟" : "编辑闹钟"));
        TextView title =
                Ui.text(
                        this,
                        existing == null ? "几点叫醒你？" : "调整这次提醒",
                        34,
                        Ui.PAPER,
                        Ui.display());
        title.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        root.addView(title);

        if (!preferences.oemAutostartConfirmed(Build.MANUFACTURER)) {
            oemAutostartWarning = buildOemAutostartWarning();
            root.addView(oemAutostartWarning);
            root.addView(Ui.space(this, 14));
        }

        LinearLayout timeCard = Ui.card(this, Ui.RAISED);
        timeCard.setPadding(
                Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16));
        TextView timeLabel = Ui.text(this, "唤醒时间", 12, Ui.ACID, Ui.medium());
        timeLabel.setLetterSpacing(0.08f);
        timeCard.addView(timeLabel);
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeValue = Ui.text(this, "00:00", 50, Ui.PAPER, Ui.display());
        timeRow.addView(timeValue, new LinearLayout.LayoutParams(0, -2, 1));
        TextView change = Ui.text(this, "调整  ›", 14, Ui.ACID, Typeface.DEFAULT_BOLD);
        timeRow.addView(change);
        timeCard.addView(timeRow);
        TextView timeHint =
                Ui.text(this, "上下滚动小时与分钟，也可以快速设为稍后", 12, Ui.MUTED, Typeface.DEFAULT);
        timeCard.addView(timeHint);
        timeCard.setOnClickListener(v -> showTimePicker());
        root.addView(timeCard);
        root.addView(Ui.space(this, 14));

        LinearLayout schedule = Ui.card(this, Ui.PANEL);
        schedule.addView(sectionTitle("时间与重复", "选择日期规则，留空就是仅响一次。"));
        label = new EditText(this);
        label.setHint("标签（可选）");
        label.setHintTextColor(Ui.MUTED);
        label.setTextColor(Ui.PAPER);
        label.setTextSize(16);
        label.setSingleLine(true);
        label.setMaxLines(1);
        label.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        label.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 10));
        schedule.addView(label);
        TextView repeatLabel = Ui.text(this, "重复", 12, Ui.MUTED, Ui.medium());
        repeatLabel.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 7));
        schedule.addView(repeatLabel);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < labels.length; i++) {
            TextView choice = Ui.choice(this, labels[i], false);
            int dayIndex = i;
            choice.setOnClickListener(v -> toggleWeekday(dayIndex));
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) dayParams.leftMargin = Ui.dp(this, 5);
            days.addView(choice, dayParams);
            weekdayChoices[i] = choice;
        }
        schedule.addView(days);
        root.addView(schedule);
        root.addView(Ui.space(this, 14));

        LinearLayout soundCard = Ui.card(this, Ui.PANEL);
        soundCard.addView(sectionTitle("铃声库", "左右滑动浏览真实录音；点选试听，再点一次重播。"));
        HorizontalScrollView soundStrip = new HorizontalScrollView(this);
        soundStrip.setHorizontalScrollBarEnabled(false);
        soundStrip.setClipToPadding(false);
        LinearLayout sounds = new LinearLayout(this);
        sounds.setOrientation(LinearLayout.HORIZONTAL);
        List<AlarmSoundCatalog.Item> alarmSounds = AlarmSoundCatalog.all();
        for (int i = 0; i < alarmSounds.size(); i++) {
            AlarmSoundCatalog.Item item = alarmSounds.get(i);
            TextView choice = alarmSoundChoice(item, false);
            String soundId = item.id();
            choice.setOnClickListener(v -> selectSound(soundId, true));
            LinearLayout.LayoutParams choiceParams =
                    new LinearLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 104));
            if (i > 0) choiceParams.leftMargin = Ui.dp(this, 8);
            sounds.addView(choice, choiceParams);
            soundChoices.add(choice);
        }
        sounds.setPadding(0, Ui.dp(this, 14), 0, 0);
        soundStrip.addView(sounds, new HorizontalScrollView.LayoutParams(-2, -1));
        soundCard.addView(soundStrip, new LinearLayout.LayoutParams(-1, Ui.dp(this, 118)));
        TextView media =
                Ui.text(
                        this,
                        "试听和正式响铃都跟随手机媒体音量。",
                        13,
                        Ui.PAPER,
                        Typeface.DEFAULT);
        media.setPadding(0, Ui.dp(this, 14), 0, 0);
        soundCard.addView(media);
        Button openVolume = Ui.button(this, "调整手机媒体音量", false);
        Ui.marginTop(openVolume, 14);
        openVolume.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)));
        soundCard.addView(openVolume);
        root.addView(soundCard);
        root.addView(Ui.space(this, 18));

        Button save = Ui.button(this, "保存闹钟", true);
        save.setOnClickListener(v -> save());
        root.addView(save);
        if (existing != null) {
            Button delete = Ui.button(this, "删除闹钟", false);
            delete.setTextColor(Ui.DANGER);
            Ui.marginTop(delete, 10);
            delete.setOnClickListener(v -> confirmDelete());
            root.addView(delete);
        }
        return scroll;
    }

    private View buildOemAutostartWarning() {
        LinearLayout card = Ui.card(this, Ui.PANEL);
        card.setPadding(
                Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15), Ui.dp(this, 13));
        card.setBackground(Ui.round(this, Ui.PANEL, 20, Ui.ACID));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, "●  自启动尚未确认", 14, Ui.ACID, Ui.medium());
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = Ui.text(this, "去设置  ›", 13, Ui.ACID, Typeface.DEFAULT_BOLD);
        heading.addView(action);
        card.addView(heading);
        TextView detail =
                Ui.text(
                        this,
                        "未开启时，收起或退出应用后闹铃可能无法唤起。",
                        12,
                        Ui.PAPER,
                        Typeface.DEFAULT);
        detail.setPadding(0, Ui.dp(this, 5), 0, 0);
        card.addView(detail);
        card.setOnClickListener(v -> requestOemAutostart());
        return card;
    }

    private void bind(Alarm alarm) {
        updateTime();
        Alarm source = alarm == null ? Alarm.newDefault(hour, minute, System.currentTimeMillis()) : alarm;
        label.setText(source.label());
        repeatMask = source.repeatMask();
        updateWeekdayChoices();
        selectSound(source.soundId(), false);
    }

    private void save() {
        String alarmLabel = label.getText().toString().trim();
        if (alarmLabel.codePointCount(0, alarmLabel.length()) > 30) {
            label.setError("标签最多 30 个字符");
            return;
        }
        long now = System.currentTimeMillis();
        Alarm alarm =
                Alarm.savedFromEditor(
                        existing,
                        hour,
                        minute,
                        repeatMask,
                        alarmLabel,
                        selectedSoundId,
                        now);
        Alarm saved = repository.save(alarm);
        AlarmScheduler scheduler = new AlarmScheduler(this);
        scheduler.cancel(saved.id());
        AlarmScheduler.ScheduleResult result = scheduler.schedule(saved);
        preferences.setLastScheduleIssue(
                result.result() == AlarmScheduler.Result.SCHEDULED
                                || result.result() == AlarmScheduler.Result.DISABLED
                        ? ""
                        : result.detail());
        Toast.makeText(this, result.detail(), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showPendingOemAutostartConfirmation();
    }

    private void requestOemAutostart() {
        preferences.beginOemAutostartConfirmation(Build.MANUFACTURER);
        if (OemAutostartNavigator.open(this)) return;
        preferences.finishOemAutostartConfirmation(Build.MANUFACTURER, false);
        Toast.makeText(this, "系统没有提供可打开的自启动或电池设置入口。", Toast.LENGTH_LONG).show();
    }

    private void showPendingOemAutostartConfirmation() {
        if (oemConfirmationDialogVisible
                || preferences == null
                || !preferences.oemAutostartConfirmationPending(Build.MANUFACTURER)) {
            return;
        }
        oemConfirmationDialogVisible = true;
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
                                    if (oemAutostartWarning != null) {
                                        oemAutostartWarning.setVisibility(View.GONE);
                                    }
                                })
                        .setNegativeButton(
                                "还没开启",
                                (ignoredDialog, which) ->
                                        preferences.finishOemAutostartConfirmation(
                                                Build.MANUFACTURER, false))
                        .create();
        dialog.setOnDismissListener(ignored -> oemConfirmationDialogVisible = false);
        dialog.show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除这个闹钟？")
                .setMessage("未来调度会取消；如果它正在响，也会立即停止。")
                .setPositiveButton(
                        "删除",
                        (d, w) -> {
                            new AlarmScheduler(this).cancel(existing.id());
                            stopIfRinging(existing.id());
                            repository.delete(existing.id());
                            setResult(RESULT_OK);
                            finish();
                        })
                .setNegativeButton("取消", null)
                .show();
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

    private View sectionTitle(String title, String detail) {
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(Ui.text(this, title, 19, Ui.PAPER, Ui.medium()));
        TextView description = Ui.text(this, detail, 12, Ui.MUTED, Typeface.DEFAULT);
        description.setPadding(0, Ui.dp(this, 3), 0, 0);
        copy.addView(description);
        return copy;
    }

    private void updateTime() {
        timeValue.setText(AlarmTimeSelection.of(hour, minute).display());
    }

    private void showTimePicker() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                Ui.dp(this, 22), Ui.dp(this, 4), Ui.dp(this, 22), Ui.dp(this, 10));

        TextView guidance =
                Ui.text(this, "上下滑动数字，或选择一个常用时段", 13, Ui.MUTED, Typeface.DEFAULT);
        guidance.setPadding(0, 0, 0, Ui.dp(this, 12));
        content.addView(guidance);

        TextView preview =
                Ui.text(
                        this,
                        AlarmTimeSelection.of(hour, minute).display(),
                        38,
                        Ui.ACID,
                        Ui.display());
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, 0, 0, Ui.dp(this, 8));
        content.addView(preview);

        NumberPicker hourPicker = timeWheel(0, 23, hour);
        NumberPicker minutePicker = timeWheel(0, 59, minute);
        NumberPicker.OnValueChangeListener refresh =
                (picker, oldValue, newValue) ->
                        preview.setText(
                                AlarmTimeSelection.of(
                                                hourPicker.getValue(), minutePicker.getValue())
                                        .display());
        hourPicker.setOnValueChangedListener(refresh);
        minutePicker.setOnValueChangedListener(refresh);

        LinearLayout wheels = new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.addView(timeWheelColumn("小时", hourPicker), new LinearLayout.LayoutParams(0, -2, 1));
        TextView colon = Ui.text(this, ":", 30, Ui.MUTED, Ui.display());
        colon.setGravity(Gravity.CENTER);
        wheels.addView(colon, new LinearLayout.LayoutParams(Ui.dp(this, 28), -1));
        wheels.addView(
                timeWheelColumn("分钟", minutePicker), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(wheels);

        TextView quickLabel = Ui.text(this, "快速设置", 12, Ui.MUTED, Ui.medium());
        quickLabel.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 7));
        content.addView(quickLabel);
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        int[] offsets = {15, 30, 60};
        String[] labels = {"15 分钟后", "30 分钟后", "1 小时后"};
        for (int i = 0; i < offsets.length; i++) {
            TextView choice = Ui.choice(this, labels[i], false);
            int offset = offsets[i];
            choice.setOnClickListener(
                    v -> {
                        AlarmTimeSelection quick =
                                AlarmTimeSelection.after(LocalTime.now(), offset);
                        hourPicker.setValue(quick.hour());
                        minutePicker.setValue(quick.minute());
                        preview.setText(quick.display());
                    });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) params.leftMargin = Ui.dp(this, 6);
            quickRow.addView(choice, params);
        }
        content.addView(quickRow);

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("设置唤醒时间")
                        .setView(content)
                        .setNegativeButton("取消", null)
                        .setPositiveButton(
                                "完成",
                                (ignored, which) -> {
                                    hour = hourPicker.getValue();
                                    minute = minutePicker.getValue();
                                    updateTime();
                                })
                        .create();
        dialog.setOnShowListener(
                ignored -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow()
                                .setBackgroundDrawable(Ui.round(this, Ui.PANEL, 24, Ui.LINE));
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.ACID);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Ui.MUTED);
                });
        dialog.show();
    }

    private NumberPicker timeWheel(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setTextColor(Ui.PAPER);
        String[] displayed = new String[max - min + 1];
        for (int i = 0; i < displayed.length; i++) {
            displayed[i] = String.format(java.util.Locale.ROOT, "%02d", min + i);
        }
        picker.setDisplayedValues(displayed);
        picker.setBackground(Ui.round(this, Ui.RAISED, 18, Ui.LINE));
        return picker;
    }

    private LinearLayout timeWheelColumn(String label, NumberPicker picker) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView heading = Ui.text(this, label, 12, Ui.MUTED, Ui.medium());
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, 0, 0, Ui.dp(this, 5));
        column.addView(heading);
        column.addView(picker, new LinearLayout.LayoutParams(-1, Ui.dp(this, 132)));
        return column;
    }

    private void selectSound(String soundId, boolean userInitiated) {
        AlarmPreviewSelection.Decision decision =
                AlarmPreviewSelection.select(selectedSoundId, soundId, userInitiated);
        selectedSoundId = decision.soundId();
        List<AlarmSoundCatalog.Item> items = AlarmSoundCatalog.all();
        for (int i = 0; i < items.size(); i++) {
            setAlarmSoundChoiceSelected(
                    soundChoices.get(i), items.get(i), items.get(i).id().equals(selectedSoundId));
        }
        if (decision.previewNow()) startPreview();
    }

    private TextView alarmSoundChoice(AlarmSoundCatalog.Item item, boolean selected) {
        TextView choice =
                Ui.text(
                        this,
                        item.symbol() + "\n" + item.label() + "\n" + item.note(),
                        13,
                        selected ? Ui.INK : Ui.PAPER,
                        Ui.medium());
        choice.setGravity(Gravity.CENTER);
        choice.setLineSpacing(Ui.dp(this, 2), 1f);
        choice.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        choice.setBackground(
                Ui.round(
                        this,
                        selected ? Ui.ACID : Ui.RAISED,
                        20,
                        selected ? Ui.ACID : Ui.LINE));
        return choice;
    }

    private void setAlarmSoundChoiceSelected(
            TextView choice, AlarmSoundCatalog.Item item, boolean selected) {
        choice.setText(item.symbol() + "\n" + item.label() + "\n" + item.note());
        choice.setTextColor(selected ? Ui.INK : Ui.PAPER);
        choice.setBackground(
                Ui.round(
                        this,
                        selected ? Ui.ACID : Ui.RAISED,
                        20,
                        selected ? Ui.ACID : Ui.LINE));
    }

    private void toggleWeekday(int index) {
        repeatMask ^= Alarm.weekdayBit(index + 1);
        updateWeekdayChoices();
    }

    private void updateWeekdayChoices() {
        for (int i = 0; i < weekdayChoices.length; i++) {
            Ui.setChoiceSelected(
                    weekdayChoices[i], (repeatMask & Alarm.weekdayBit(i + 1)) != 0);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPreview();
    }

    private void startPreview() {
        stopPreview();
        previewEngine =
                new PrivatePlaybackEngine(
                        this,
                        new PrivatePlaybackEngine.Config(
                                PrivatePlaybackEngine.Purpose.ALARM,
                                selectedSoundId,
                                35,
                                0),
                        (state, detail, verification, muteLatencyMs) -> {
                            if (state == PrivatePlaybackEngine.State.BLOCKED) {
                                Toast.makeText(this, detail, Toast.LENGTH_SHORT).show();
                            }
                        });
        previewEngine.start();
        previewHandler.postDelayed(this::stopPreview, 5_000L);
    }

    private void stopPreview() {
        previewHandler.removeCallbacksAndMessages(null);
        PrivatePlaybackEngine current = previewEngine;
        previewEngine = null;
        if (current != null) current.release();
    }

    private void configureWindow() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
        getWindow().setNavigationBarContrastEnforced(false);
    }
}
