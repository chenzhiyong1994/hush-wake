package com.hushwake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.alarm.AlarmScheduler;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.ui.Ui;
import java.time.LocalTime;

public final class EditAlarmActivity extends Activity {
    public static final String EXTRA_ALARM_ID = "edit_alarm_id";

    private AlarmRepository repository;
    private AppPreferences preferences;
    private Alarm existing;
    private int hour;
    private int minute;
    private Button timeButton;
    private EditText label;
    private final CheckBox[] weekdayChecks = new CheckBox[7];
    private Spinner sound;
    private SeekBar volume;
    private TextView volumeLabel;
    private Spinner fade;
    private Switch vibration;
    private Spinner snooze;
    private Spinner maxRing;
    private Switch enabled;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new AlarmRepository(this);
        preferences = new AppPreferences(this);
        long id = getIntent().getLongExtra(EXTRA_ALARM_ID, 0L);
        existing = id == 0L ? null : repository.find(id);
        LocalTime suggested = LocalTime.now().plusHours(1).withSecond(0).withNano(0);
        hour = existing == null ? suggested.getHour() : existing.hour();
        minute = existing == null ? suggested.getMinute() : existing.minute();
        configureWindow();
        setContentView(buildScreen());
        bind(existing);
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.INK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 46));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView back = Ui.text(this, "←  返回", 14, Ui.ACID, Typeface.DEFAULT_BOLD);
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.eyebrow(this, existing == null ? "NEW PRIVATE ALARM" : "EDIT PRIVATE ALARM"));
        TextView title =
                Ui.text(
                        this,
                        existing == null ? "新建悄醒" : "编辑悄醒",
                        34,
                        Ui.PAPER,
                        Typeface.create("serif", Typeface.BOLD));
        title.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 24));
        root.addView(title);

        timeButton = Ui.button(this, "00:00", false);
        timeButton.setTextSize(38);
        timeButton.setTypeface(Typeface.create("serif", Typeface.BOLD));
        timeButton.setOnClickListener(
                v ->
                        new TimePickerDialog(
                                        this,
                                        (picker, h, m) -> {
                                            hour = h;
                                            minute = m;
                                            updateTime();
                                        },
                                        hour,
                                        minute,
                                        true)
                                .show());
        root.addView(timeButton);
        root.addView(Ui.space(this, 14));

        LinearLayout basics = Ui.card(this, Ui.PANEL);
        basics.addView(Ui.eyebrow(this, "BASICS  /  时间与重复"));
        label = new EditText(this);
        label.setHint("标签（可选，最多 30 字）");
        label.setHintTextColor(Ui.MUTED);
        label.setTextColor(Ui.PAPER);
        label.setTextSize(16);
        label.setSingleLine(true);
        label.setMaxLines(1);
        label.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        label.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 10));
        basics.addView(label);
        TextView repeatLabel = Ui.text(this, "重复", 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        repeatLabel.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        basics.addView(repeatLabel);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < labels.length; i++) {
            CheckBox check = new CheckBox(this);
            check.setText(labels[i]);
            check.setTextColor(Ui.PAPER);
            check.setGravity(android.view.Gravity.CENTER);
            check.setButtonTintList(
                    new android.content.res.ColorStateList(
                            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                            new int[] {Ui.ACID, Ui.MUTED}));
            days.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
            weekdayChecks[i] = check;
        }
        basics.addView(days);
        root.addView(basics);
        root.addView(Ui.space(this, 14));

        LinearLayout audio = Ui.card(this, Ui.PANEL);
        audio.addView(Ui.eyebrow(this, "PRIVATE AUDIO  /  仅耳机"));
        sound = spinner(new String[] {"柔和钟声", "清亮钟声", "地平线"});
        addField(audio, "铃声", sound);
        volumeLabel = Ui.text(this, "应用内增益 · 50%", 13, Ui.PAPER, Typeface.DEFAULT_BOLD);
        volumeLabel.setPadding(0, Ui.dp(this, 14), 0, 0);
        audio.addView(volumeLabel);
        volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        volume.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        volume.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                        volumeLabel.setText("应用内增益 · " + value + "%");
                    }
                    @Override public void onStartTrackingTouch(SeekBar bar) {}
                    @Override public void onStopTrackingTouch(SeekBar bar) {}
                });
        audio.addView(volume);
        fade = spinner(new String[] {"不渐强", "15 秒", "30 秒", "60 秒"});
        addField(audio, "渐强", fade);
        TextView guard =
                Ui.text(
                        this,
                        "保存的音量不会绕过输出守卫，也不会修改系统媒体音量。当前耳机未验证时，本次仍保持静音。",
                        12,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        guard.setPadding(0, Ui.dp(this, 12), 0, 0);
        audio.addView(guard);
        root.addView(audio);
        root.addView(Ui.space(this, 14));

        LinearLayout fallback = Ui.card(this, Ui.PANEL);
        fallback.addView(Ui.eyebrow(this, "FALLBACK  /  处置"));
        vibration = toggle("声音被阻断时振动兜底");
        fallback.addView(vibration);
        snooze = spinner(new String[] {"关闭", "3 分钟", "5 分钟", "10 分钟"});
        addField(fallback, "一次稍后提醒", snooze);
        maxRing = spinner(new String[] {"30 秒", "1 分钟", "2 分钟", "5 分钟"});
        addField(fallback, "最长响铃", maxRing);
        enabled = toggle("保存后启用");
        fallback.addView(enabled);
        root.addView(fallback);
        root.addView(Ui.space(this, 18));

        Button save = Ui.button(this, "保存并重新调度", true);
        save.setOnClickListener(v -> save());
        root.addView(save);
        Button test = Ui.button(this, "打开隐私测试", false);
        Ui.marginTop(test, 10);
        test.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(test);
        if (existing != null) {
            Button delete = Ui.button(this, "删除闹钟", false);
            delete.setTextColor(Ui.DANGER);
            Ui.marginTop(delete, 10);
            delete.setOnClickListener(v -> confirmDelete());
            root.addView(delete);
        }
        return scroll;
    }

    private void bind(Alarm alarm) {
        updateTime();
        Alarm source = alarm;
        if (source == null) {
            long now = System.currentTimeMillis();
            source =
                    new Alarm(
                            0,
                            hour,
                            minute,
                            0,
                            "",
                            "soft_chime",
                            preferences.defaultAlarmVolume(),
                            preferences.defaultAlarmFadeSeconds(),
                            preferences.defaultVibration(),
                            preferences.defaultSnoozeMinutes(),
                            preferences.defaultMaxRingSeconds(),
                            true,
                            Alarm.nextOneTimeEpochDay(hour, minute, java.time.Instant.now()),
                            now,
                            now);
        }
        label.setText(source.label());
        for (int i = 0; i < 7; i++) weekdayChecks[i].setChecked(source.repeatsOn(i + 1));
        sound.setSelection(soundIndex(source.soundId()));
        volume.setProgress(source.volumePercent());
        fade.setSelection(indexOf(new int[] {0, 15, 30, 60}, source.fadeInSeconds()));
        vibration.setChecked(source.vibrationEnabled());
        snooze.setSelection(indexOf(new int[] {0, 3, 5, 10}, source.snoozeMinutes()));
        maxRing.setSelection(indexOf(new int[] {30, 60, 120, 300}, source.maxRingSeconds()));
        enabled.setChecked(source.enabled());
    }

    private void save() {
        String alarmLabel = label.getText().toString().trim();
        if (alarmLabel.codePointCount(0, alarmLabel.length()) > 30) {
            label.setError("标签最多 30 个字符");
            return;
        }
        if (vibration.isChecked() && !preferences.vibrationWarningAcknowledged()) {
            new AlertDialog.Builder(this)
                    .setTitle("振动也可能打扰附近的人")
                    .setMessage("当声音因耳机不可用而被阻断时，手机会按此设置振动。你可以现在关闭，也可以确认继续使用。")
                    .setPositiveButton(
                            "确认开启",
                            (d, w) -> {
                                preferences.setVibrationWarningAcknowledged(true);
                                persist();
                            })
                    .setNegativeButton(
                            "关闭振动",
                            (d, w) -> {
                                vibration.setChecked(false);
                                persist();
                            })
                    .show();
            return;
        }
        persist();
    }

    private void persist() {
        int repeatMask = 0;
        for (int i = 0; i < 7; i++) if (weekdayChecks[i].isChecked()) repeatMask |= Alarm.weekdayBit(i + 1);
        long now = System.currentTimeMillis();
        long id = existing == null ? 0L : existing.id();
        long created = existing == null ? now : existing.createdAtEpochMs();
        long oneTimeDate =
                repeatMask == 0
                        ? Alarm.nextOneTimeEpochDay(hour, minute, java.time.Instant.ofEpochMilli(now))
                        : Long.MIN_VALUE;
        Alarm alarm =
                new Alarm(
                        id,
                        hour,
                        minute,
                        repeatMask,
                        label.getText().toString(),
                        new String[] {"soft_chime", "bright_chime", "horizon"}[sound.getSelectedItemPosition()],
                        volume.getProgress(),
                        new int[] {0, 15, 30, 60}[fade.getSelectedItemPosition()],
                        vibration.isChecked(),
                        new int[] {0, 3, 5, 10}[snooze.getSelectedItemPosition()],
                        new int[] {30, 60, 120, 300}[maxRing.getSelectedItemPosition()],
                        enabled.isChecked(),
                        oneTimeDate,
                        created,
                        now);
        Alarm saved = repository.save(alarm);
        AlarmScheduler scheduler = new AlarmScheduler(this);
        scheduler.cancel(saved.id());
        AlarmScheduler.ScheduleResult result = scheduler.schedule(saved);
        new AppPreferences(this)
                .setLastScheduleIssue(
                        result.result() == AlarmScheduler.Result.SCHEDULED
                                        || result.result() == AlarmScheduler.Result.DISABLED
                                ? ""
                                : result.detail());
        Toast.makeText(this, result.detail(), Toast.LENGTH_LONG).show();
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除这个闹钟？")
                .setMessage("未来系统调度也会同时取消。此操作无法撤销。")
                .setPositiveButton(
                        "删除",
                        (d, w) -> {
                            new AlarmScheduler(this).cancel(existing.id());
                            repository.delete(existing.id());
                            setResult(RESULT_OK);
                            finish();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private Spinner spinner(String[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(Ui.round(this, Ui.RAISED, 8, Ui.LINE));
        spinner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        return spinner;
    }

    private Switch toggle(String text) {
        Switch toggle = new Switch(this);
        toggle.setText(text);
        toggle.setTextColor(Ui.PAPER);
        toggle.setTextSize(14);
        toggle.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        return toggle;
    }

    private void addField(LinearLayout parent, String title, View input) {
        TextView label = Ui.text(this, title, 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        label.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        parent.addView(label);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateTime() { timeButton.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)); }
    private int soundIndex(String id) { return "bright_chime".equals(id) ? 1 : "horizon".equals(id) ? 2 : 0; }
    private static int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return 0;
    }
    private void configureWindow() {
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
        getWindow().setNavigationBarContrastEnforced(false);
    }
}
