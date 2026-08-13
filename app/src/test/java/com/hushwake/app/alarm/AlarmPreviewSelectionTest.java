package com.hushwake.app.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AlarmPreviewSelectionTest {
    @Test
    public void bindingSavedSoundDoesNotStartAnUnexpectedPreview() {
        AlarmPreviewSelection.Decision decision =
                AlarmPreviewSelection.select("soft_chime", "horizon", false);

        assertEquals("horizon", decision.soundId());
        assertFalse(decision.previewNow());
    }

    @Test
    public void tappingAnySoundSelectsAndPreviewsItImmediately() {
        AlarmPreviewSelection.Decision decision =
                AlarmPreviewSelection.select("soft_chime", "bright_chime", true);

        assertEquals("bright_chime", decision.soundId());
        assertTrue(decision.previewNow());
    }

    @Test
    public void tappingSelectedSoundReplaysItsPreview() {
        AlarmPreviewSelection.Decision decision =
                AlarmPreviewSelection.select("horizon", "horizon", true);

        assertEquals("horizon", decision.soundId());
        assertTrue(decision.previewNow());
    }

    @Test
    public void newlyAddedRecordedSoundCanBeSelectedAndPreviewed() {
        AlarmPreviewSelection.Decision decision =
                AlarmPreviewSelection.select("soft_chime", "wind_chimes", true);

        assertEquals("wind_chimes", decision.soundId());
        assertTrue(decision.previewNow());
    }
}
