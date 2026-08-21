package com.hushwake.app.alarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hushwake.app.domain.Alarm;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.Test;

public final class AlarmHomeLayoutPolicyTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    public void activeOneTimeAlarmRemainsTheFocusAfterItsScheduleIsConsumed() {
        Instant now = ZonedDateTime.of(2026, 8, 21, 23, 50, 30, 0, ZONE).toInstant();
        Alarm ringing = oneTime(7L, 2026, 8, 21, 23, 50, false);
        Alarm tomorrow = oneTime(8L, 2026, 8, 22, 6, 30, true);

        AlarmHomeLayoutPolicy.Layout layout =
                AlarmHomeLayoutPolicy.arrange(
                        List.of(ringing, tomorrow), 7L, "AUDIBLE", now, ZONE);

        assertEquals(7L, layout.focus().id());
        assertTrue(layout.focusRinging());
        assertEquals(
                ZonedDateTime.of(2026, 8, 21, 23, 50, 0, 0, ZONE).toInstant(),
                layout.focusAt());
        assertEquals(List.of(tomorrow), layout.others());
    }

    @Test
    public void earliestEnabledAlarmIsExcludedAndTheNextOneIsPromotedWhenDisabled() {
        Instant now = ZonedDateTime.of(2026, 8, 21, 20, 0, 0, 0, ZONE).toInstant();
        Alarm later = oneTime(11L, 2026, 8, 21, 22, 0, true);
        Alarm earliest = oneTime(12L, 2026, 8, 21, 21, 0, true);
        Alarm alreadyOff = oneTime(13L, 2026, 8, 21, 20, 30, false);

        AlarmHomeLayoutPolicy.Layout initial =
                AlarmHomeLayoutPolicy.arrange(
                        List.of(later, earliest, alreadyOff), 0L, "IDLE", now, ZONE);

        assertEquals(12L, initial.focus().id());
        assertEquals(List.of(later, alreadyOff), initial.others());

        Alarm disabled = earliest.withEnabled(false, now.toEpochMilli());
        AlarmHomeLayoutPolicy.Layout promoted =
                AlarmHomeLayoutPolicy.arrange(
                        List.of(later, disabled, alreadyOff), 0L, "IDLE", now, ZONE);

        assertEquals(11L, promoted.focus().id());
        assertEquals(List.of(disabled, alreadyOff), promoted.others());
    }

    @Test
    public void noEnabledAlarmLeavesTheFocusEmptyAndKeepsEveryAlarmInOthers() {
        Instant now = ZonedDateTime.of(2026, 8, 21, 20, 0, 0, 0, ZONE).toInstant();
        Alarm first = oneTime(21L, 2026, 8, 21, 21, 0, false);
        Alarm second = oneTime(22L, 2026, 8, 21, 22, 0, false);

        AlarmHomeLayoutPolicy.Layout layout =
                AlarmHomeLayoutPolicy.arrange(
                        List.of(first, second), 0L, "IDLE", now, ZONE);

        assertNull(layout.focus());
        assertNull(layout.focusAt());
        assertEquals(List.of(first, second), layout.others());
    }

    private static Alarm oneTime(
            long id, int year, int month, int day, int hour, int minute, boolean enabled) {
        ZonedDateTime target = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE);
        return new Alarm(
                id,
                hour,
                minute,
                0,
                "",
                "soft_chime",
                50,
                15,
                true,
                5,
                120,
                enabled,
                target.toLocalDate().toEpochDay(),
                1L,
                1L);
    }
}
