package com.hushwake.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
import com.hushwake.app.alarm.UnifiedAlarmPolicy;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.data.AppPreferences;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.ui.Ui;
import java.time.LocalTime;

public final class EditAlarmActivity extends Activity {
    public static final String EXTRA_ALARM_ID = "edit_alarm_id";

    private AlarmRepository repository;
    private Alarm existing;
    private int hour;
    private int minute;
    private Button timeButton;
    private EditText label;
    private final CheckBox[] weekdayChecks = new CheckBox[7];
    private Spinner sound;
    private Switch enabled;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new AlarmRepository(this);
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
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 48));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView back = Ui.text(this, "←  返回闹钟", 14, Ui.ACID, Typeface.DEFAULT_BOLD);
        back.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 20));
        back.setOnClickListener(v -> finish());
        root.addView(back);
        root.addView(Ui.eyebrow(this, existing == null ? "NEW ALARM" : "EDIT ALARM"));
        TextView title =
                Ui.text(
                        this,
                        existing == null ? "几点叫醒你？" : "调整这次提醒",
                        34,
                        Ui.PAPER,
                        Typeface.create("serif", Typeface.BOLD));
        title.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        root.addView(title);

        timeButton = Ui.button(this, "00:00", false);
        timeButton.setTextSize(42);
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

        LinearLayout schedule = Ui.card(this, Ui.PANEL);
        schedule.addView(Ui.eyebrow(this, "SCHEDULE  /  时间"));
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
        TextView repeatLabel = Ui.text(this, "重复", 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        repeatLabel.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 7));
        schedule.addView(repeatLabel);
        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < labels.length; i++) {
            CheckBox check = new CheckBox(this);
            check.setText(labels[i]);
            check.setTextColor(Ui.PAPER);
            check.setGravity(Gravity.CENTER);
            check.setButtonTintList(
                    new android.content.res.ColorStateList(
                            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                            new int[] {Ui.ACID, Ui.MUTED}));
            days.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
            weekdayChecks[i] = check;
        }
        schedule.addView(days);
        enabled = new Switch(this);
        enabled.setText("启用这个闹钟");
        enabled.setTextColor(Ui.PAPER);
        enabled.setTextSize(14);
        enabled.setPadding(0, Ui.dp(this, 14), 0, 0);
        enabled.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACID));
        schedule.addView(enabled);
        root.addView(schedule);
        root.addView(Ui.space(this, 14));

        LinearLayout soundCard = Ui.card(this, Ui.RAISED);
        soundCard.addView(Ui.eyebrow(this, "SOUND  /  铃声"));
        sound = Ui.spinner(this, new String[] {"柔和钟声", "清亮钟声", "地平线"});
        addField(soundCard, "选择铃声", sound);
        TextView media =
                Ui.text(
                        this,
                        "响铃音量跟随手机媒体音量。无耳机时从扬声器播放；连接耳机时只通过耳机播放。",
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

    private void bind(Alarm alarm) {
        updateTime();
        Alarm source = alarm == null ? Alarm.newDefault(hour, minute, System.currentTimeMillis()) : alarm;
        label.setText(source.label());
        for (int i = 0; i < 7; i++) weekdayChecks[i].setChecked(source.repeatsOn(i + 1));
        sound.setSelection(soundIndex(source.soundId()));
        enabled.setChecked(source.enabled());
    }

    private void save() {
        String alarmLabel = label.getText().toString().trim();
        if (alarmLabel.codePointCount(0, alarmLabel.length()) > 30) {
            label.setError("标签最多 30 个字符");
            return;
        }
        int repeatMask = 0;
        for (int i = 0; i < 7; i++) {
            if (weekdayChecks[i].isChecked()) repeatMask |= Alarm.weekdayBit(i + 1);
        }
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
                        alarmLabel,
                        new String[] {"soft_chime", "bright_chime", "horizon"}[
                                sound.getSelectedItemPosition()],
                        UnifiedAlarmPolicy.APP_GAIN_PERCENT,
                        UnifiedAlarmPolicy.FADE_IN_SECONDS,
                        UnifiedAlarmPolicy.VIBRATE_WHEN_BLOCKED,
                        UnifiedAlarmPolicy.SNOOZE_MINUTES,
                        UnifiedAlarmPolicy.MAX_RING_SECONDS,
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
        if (!saved.enabled()) stopIfRinging(saved.id());
        Toast.makeText(this, result.detail(), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
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

    private void addField(LinearLayout parent, String title, View input) {
        TextView fieldLabel = Ui.text(this, title, 12, Ui.MUTED, Typeface.DEFAULT_BOLD);
        fieldLabel.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        parent.addView(fieldLabel);
        parent.addView(input, new LinearLayout.LayoutParams(-1, -2));
    }

    private void updateTime() {
        timeButton.setText(String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute));
    }

    private int soundIndex(String id) {
        return "bright_chime".equals(id) ? 1 : "horizon".equals(id) ? 2 : 0;
    }

    private void configureWindow() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
        getWindow().setNavigationBarContrastEnforced(false);
    }
}
