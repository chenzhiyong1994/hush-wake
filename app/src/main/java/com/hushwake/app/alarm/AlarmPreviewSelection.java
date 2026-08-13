package com.hushwake.app.alarm;

/** Selection behavior for the alarm editor's tap-to-preview sound choices. */
public final class AlarmPreviewSelection {
    private AlarmPreviewSelection() {}

    public record Decision(String soundId, boolean previewNow) {}

    public static Decision select(
            String currentSoundId, String requestedSoundId, boolean userInitiated) {
        String safeId = normalize(requestedSoundId);
        return new Decision(safeId, userInitiated);
    }

    public static String normalize(String soundId) {
        return AlarmSoundCatalog.normalizeId(soundId);
    }
}
