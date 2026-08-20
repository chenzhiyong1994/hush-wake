package com.hushwake.app.alarm;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.hushwake.app.data.AlarmRepository;
import com.hushwake.app.domain.Alarm;
import com.hushwake.app.ui.AmbientWaveView;
import com.hushwake.app.ui.Ui;
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
                    if ("STOPPED".equals(state)) {
                        String detail = intent.getStringExtra(AlarmRingingService.EXTRA_DETAIL);
                        if (detail != null && detail.startsWith("闹钟已改为")) {
                            Toast.makeText(RingingActivity.this, detail, Toast.LENGTH_LONG).show();
                        }
                        finishAndRemoveTask();
                    }
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
        getWindow().setStatusBarColor(Ui.INK);
        getWindow().setNavigationBarColor(Ui.INK);
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 52), Ui.dp(this, 24), Ui.dp(this, 32));
        root.setBackground(Ui.pageBackground(this));

        TextView mark = Ui.text(this, "HUSHWAKE  /  ALARM", 11, Ui.ACID, Typeface.MONOSPACE);
        mark.setLetterSpacing(.12f);
        root.addView(mark);
        TextView badge = Ui.pill(this, "●  正在响铃", true);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.topMargin = Ui.dp(this, 24);
        root.addView(badge, badgeParams);
        time =
                Ui.text(
                        this,
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        72,
                        Ui.PAPER,
                        Ui.display());
        time.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 4));
        root.addView(time);
        TextView name =
                Ui.text(
                        this,
                        alarm == null || alarm.label().isBlank() ? "悄醒" : alarm.label(),
                        20,
                        Ui.PAPER,
                        Ui.bold());
        root.addView(name);

        LinearLayout stateCard = Ui.card(this, Ui.GLASS);
        stateCard.setGravity(Gravity.CENTER_HORIZONTAL);
        stateCard.setBackground(Ui.round(this, Ui.GLASS, 24, Ui.LINE));
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = Ui.dp(this, 28);
        root.addView(stateCard, stateParams);
        AmbientWaveView wave = new AmbientWaveView(this, true);
        stateCard.addView(wave, new LinearLayout.LayoutParams(-1, Ui.dp(this, 42)));
        status = Ui.text(this, "正在建立安全播放会话", 14, Ui.MUTED, Typeface.DEFAULT);
        status.setGravity(Gravity.CENTER);
        status.setPadding(
                Ui.dp(this, 10), Ui.dp(this, 9), Ui.dp(this, 10), Ui.dp(this, 3));
        stateCard.addView(status);

        android.widget.Space flexible = new android.widget.Space(this);
        root.addView(flexible, new LinearLayout.LayoutParams(1, 0, 1));

        Button stop = Ui.button(this, "停止", true);
        stop.setOnClickListener(v -> send(ACTION(AlarmRingingService.ACTION_STOP)));
        root.addView(stop, new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
        snooze = Ui.button(this, "稍后提醒", false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        params.topMargin = Ui.dp(this, 10);
        snooze.setLayoutParams(params);
        snooze.setVisibility(alarm != null ? android.view.View.VISIBLE : android.view.View.GONE);
        snooze.setText("稍后 " + UnifiedAlarmPolicy.SNOOZE_MINUTES + " 分钟");
        snooze.setOnClickListener(
                v -> {
                    snooze.setEnabled(false);
                    snooze.setText("正在改为 5 分钟后…");
                    status.setText("正在保存并重新调度这个闹钟");
                    send(ACTION(AlarmRingingService.ACTION_SNOOZE));
                });
        root.addView(snooze);
        TextView privacy =
                Ui.text(
                        this,
                        "智能输出 · 无耳机时正常外放，检测到耳机时只走已验证路径",
                        11,
                        Ui.MUTED,
                        Typeface.DEFAULT);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(
                Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 8), 0);
        root.addView(privacy);
        return root;
    }

    private Intent ACTION(String action) {
        return new Intent(this, AlarmRingingService.class).setAction(action);
    }

    private void send(Intent intent) { startService(intent); }

}
