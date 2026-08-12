package com.hushwake.app.audio;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the most recent coarse report on-device. There is no network path in this build. */
public final class DiagnosticsStore {
    private static final String PREFS = "hushwake_local_diagnostics";
    private static final String LAST_REPORT = "last_report";

    private final SharedPreferences preferences;

    public DiagnosticsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(TestReport report) {
        preferences.edit().putString(LAST_REPORT, report.format()).apply();
    }

    public String load() {
        return preferences.getString(LAST_REPORT, "尚无测试记录");
    }

    public void clear() {
        preferences.edit().clear().commit();
    }
}
