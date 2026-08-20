package com.hushwake.app.noise;

import com.hushwake.app.R;
import java.util.List;

/** Offline field recordings with source and license recorded in docs/audio-credits.md. */
public final class SleepSoundCatalog {
    public record Item(
            String id,
            String label,
            String shortLabel,
            String note,
            String symbol,
            int resourceId,
            int textureResourceId) {}

    private static final List<Item> ITEMS =
            List.of(
                    new Item("rain", "绵密夜雨", "夜雨", "均匀雨幕", "雨", R.raw.sleep_rain, R.drawable.sleep_texture_rain),
                    new Item("stream", "林间溪流", "溪流", "清澈水流", "水", R.raw.gentle_stream, R.drawable.sleep_texture_stream),
                    new Item("fireplace", "轻柔壁炉", "壁炉", "低缓柴火", "火", R.raw.soft_fireplace, R.drawable.sleep_texture_fireplace),
                    new Item("morning", "清晨林鸟", "晨林", "鸟鸣与微风", "晨", R.raw.morning_forest, R.drawable.sleep_texture_morning),
                    new Item("crickets", "静夜虫鸣", "虫鸣", "平稳夜色", "夜", R.raw.night_crickets, R.drawable.sleep_texture_crickets),
                    new Item("wind", "旷野微风", "微风", "舒缓自然风", "风", R.raw.gentle_wind, R.drawable.sleep_texture_wind),
                    new Item("ocean", "深夜海浪", "海浪", "低频潮汐", "海", R.raw.ocean_waves, R.drawable.sleep_texture_ocean),
                    new Item("thunder", "远方雷雨", "远雷", "低沉雨雷", "雷", R.raw.distant_thunder, R.drawable.sleep_texture_thunder));

    private SleepSoundCatalog() {}

    public static List<Item> all() {
        return ITEMS;
    }

    public static String normalizeId(String id) {
        for (Item item : ITEMS) {
            if (item.id().equals(id)) return id;
        }
        return "rain";
    }

    public static int resourceId(String id) {
        return find(id).resourceId();
    }

    public static String label(String id) {
        return find(id).label();
    }

    public static Item find(String id) {
        String safeId = normalizeId(id);
        for (Item item : ITEMS) {
            if (item.id().equals(safeId)) return item;
        }
        return ITEMS.get(0);
    }
}
