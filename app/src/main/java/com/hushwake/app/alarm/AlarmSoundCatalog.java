package com.hushwake.app.alarm;

import com.hushwake.app.R;
import java.util.List;

/** Curated recorded alarm sounds; source and redistribution licenses live in docs/audio-credits.md. */
public final class AlarmSoundCatalog {
    public record Item(String id, String label, String note, String symbol, int resourceId) {}

    private static final List<Item> ITEMS =
            List.of(
                    new Item("soft_chime", "晨光和弦", "柔和 · 持续", "晨", R.raw.alarm_soft_bell),
                    new Item("bright_chime", "清醒节拍", "清晰 · 有律动", "醒", R.raw.alarm_clear_bell),
                    new Item("horizon", "柔光旋律", "舒展 · 渐进", "光", R.raw.alarm_wind_chimes),
                    new Item("deep_bell", "深稳脉冲", "沉稳 · 易辨识", "稳", R.raw.alarm_deep_bell),
                    new Item("wind_chimes", "霓虹晨铃", "丰富 · 持续", "霓", R.raw.alarm_garden_chimes),
                    new Item("morning_birds", "清脆回响", "明亮 · 轻快", "清", R.raw.alarm_morning_birds));

    private AlarmSoundCatalog() {}

    public static List<Item> all() {
        return ITEMS;
    }

    public static String normalizeId(String id) {
        for (Item item : ITEMS) {
            if (item.id().equals(id)) return id;
        }
        return "soft_chime";
    }

    public static Item find(String id) {
        String safeId = normalizeId(id);
        for (Item item : ITEMS) {
            if (item.id().equals(safeId)) return item;
        }
        return ITEMS.get(0);
    }

    public static int resourceId(String id) {
        return find(id).resourceId();
    }
}
