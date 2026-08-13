package com.hushwake.app.noise;

import com.hushwake.app.R;

/** Offline field recordings with source and license recorded in docs/audio-credits.md. */
public final class SleepSoundCatalog {
    private SleepSoundCatalog() {}

    public static int resourceId(String id) {
        if ("stream".equals(id)) return R.raw.gentle_stream;
        if ("fireplace".equals(id)) return R.raw.soft_fireplace;
        return R.raw.sleep_rain;
    }

    public static String label(String id) {
        if ("stream".equals(id)) return "林间溪流";
        if ("fireplace".equals(id)) return "轻柔壁炉";
        return "绵密夜雨";
    }
}
