package com.hushwake.app.alarm;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.domain.Alarm;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class RingingActivity extends Activity {
    private TextView status;
    private TextView time;
    private Button snooze;

    private final BroadcastReceiver updates =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String state = intent.getStringExtra(AlarmRingingService.EXTRA_STATE);
                    status.setText(intent.getStringExtra(AlarmRingingService.EXTRA_DETAIL));
                    snooze.setVisibility(
                            intent.getBooleanExtra(AlarmRingingService.EXTRA_CAN_SNOOZE, false)
                                    ? android.view.View.VISIBLE
                                    : android.view.View.GONE);
                    if ("STOPPED".equals(state)) finishAndRemoveTask();
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow()
                .addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().setStatusBarColor(Color.rgb(5, 12, 10));
        getWindow().setNavigationBarColor(Color.rgb(5, 12, 10));
        long alarmId = getIntent().getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L);
        Alarm alarm = new AlarmRepository(this).find(alarmId);
        setContentView(build(alarm));
        AlarmSessionStore.Snapshot snapshot = new AlarmSessionStore(this).load();
        if (!"IDLE".equals(snapshot.state())) {
            status.setText(snapshot.detail());
            snooze.setVisibility(snapshot.canSnooze() ? View.VISIBLE : View.GONE);
            if ("STOPPED".equals(snapshot.state())) {
                finishAndRemoveTask();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStateReceiver();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStateReceiver() {
        IntentFilter filter = new IntentFilter(AlarmRingingService.ACTION_STATE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    updates,
                    filter,
                    AlarmRingingService.INTERNAL_STATE_PERMISSION,
                    null,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            // API 31–32 has no receiver flag overload; the signature permission is the boundary.
            registerReceiver(
                    updates, filter, AlarmRingingService.INTERNAL_STATE_PERMISSION, null);
        }
    }

    @Override
    protected void onStop() {
        try { unregisterReceiver(updates); } catch (IllegalArgumentException ignored) {}
        super.onStop();
    }

    private android.view.View build(Alarm alarm) {
        int ink = Color.rgb(5, 12, 10);
        int paper = Color.rgb(241, 246, 236);
        int acid = Color.rgb(233, 255, 112);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(58), dp(28), dp(36));
        root.setBackgroundColor(ink);

        TextView mark = label("HUSHWAKE  /  PRIVATE ALARM", 12, acid, Typeface.MONOSPACE);
        mark.setLetterSpacing(.12f);
        root.addView(mark);
        time = label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), 72, paper, Typeface.create("serif", Typeface.BOLD));
        time.setPadding(0, dp(34), 0, dp(8));
        root.addView(time);
        TextView name = label(alarm == null || alarm.label().isBlank() ? "悄醒" : alarm.label(), 20, paper, Typeface.DEFAULT_BOLD);
        root.addView(name);
        status = label("正在建立安全播放会话", 15, Color.rgb(166, 183, 173), Typeface.DEFAULT);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(10), dp(22), dp(10), dp(44));
        root.addView(status);

        Button stop = button("停止", acid, ink);
        stop.setOnClickListener(v -> send(ACTION(AlarmRingingService.ACTION_STOP)));
        root.addView(stop);
        snooze = button("稍后提醒", Color.rgb(22, 36, 31), paper);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.topMargin = dp(12);
        snooze.setLayoutParams(params);
        snooze.setVisibility(alarm != null && alarm.snoozeMinutes() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        snooze.setText(alarm == null ? "稍后提醒" : "稍后 " + alarm.snoozeMinutes() + " 分钟");
        snooze.setOnClickListener(v -> send(ACTION(AlarmRingingService.ACTION_SNOOZE)));
        root.addView(snooze);
        TextView privacy = label("无法验证耳机输出时，声音会保持静音。振动只按你的设置兜底。", 12, Color.rgb(126, 145, 136), Typeface.DEFAULT);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(dp(8), dp(34), dp(8), 0);
        root.addView(privacy);
        return root;
    }

    private Intent ACTION(String action) {
        return new Intent(this, AlarmRingingService.class).setAction(action);
    }

    private void send(Intent intent) { startService(intent); }

    private Button button(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(17);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(18));
        button.setBackground(shape);
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(64)));
        return button;
    }

    private TextView label(String value, int size, int color, Typeface face) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(face);
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
