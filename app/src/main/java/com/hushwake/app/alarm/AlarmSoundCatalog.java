package com.hushwake.app.alarm;

import com.hushwake.app.R;
import java.util.List;

/** Curated recorded alarm sounds; source and redistribution licenses live in docs/audio-credits.md. */
public final class AlarmSoundCatalog {
    public record Item(String id, String label, String note, String symbol, int resourceId) {}

    private static final List<Item> ITEMS =
            List.of(
                    new Item("soft_chime", "柔和钟铃", "圆润 · 不突兀", "铃", R.raw.alarm_soft_bell),
                    new Item("bright_chime", "清亮钟铃", "通透 · 易辨识", "清", R.raw.alarm_clear_bell),
                    new Item("horizon", "晨光风铃", "舒展 · 渐醒", "风", R.raw.alarm_wind_chimes),
                    new Item("deep_bell", "低音钟铃", "沉稳 · 温和", "钟", R.raw.alarm_deep_bell),
                    new Item("wind_chimes", "庭院风铃", "自然 · 空灵", "庭", R.raw.alarm_garden_chimes),
                    new Item("morning_birds", "清晨鸟鸣", "自然 · 轻唤", "晨", R.raw.alarm_morning_birds));

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
