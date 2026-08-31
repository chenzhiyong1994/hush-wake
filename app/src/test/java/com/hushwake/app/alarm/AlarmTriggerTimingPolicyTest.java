package com.hushwake.app.alarm;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public final class AlarmTriggerTimingPolicyTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    public void alarmBroadcastThreeMinutesEarlyIsRescheduledInsteadOfRinging() {
        long scheduledAt =
                ZonedDateTime.of(2026, 9, 1, 13, 3, 0, 0, ZONE).toInstant().toEpochMilli();
        long receivedAt =
                ZonedDateTime.of(2026, 9, 1, 13, 0, 0, 0, ZONE).toInstant().toEpochMilli();

        AlarmTriggerTimingPolicy.Decision decision =
                AlarmTriggerTimingPolicy.evaluate(scheduledAt, receivedAt);

        assertEquals(AlarmTriggerTimingPolicy.Decision.RESCHEDULE_EARLY, decision);
    }

    @Test
    public void alarmBroadcastAtItsScheduledMinuteRings() {
        long scheduledAt =
                ZonedDateTime.of(2026, 9, 1, 13, 3, 0, 0, ZONE).toInstant().toEpochMilli();

        assertEquals(
                AlarmTriggerTimingPolicy.Decision.RING,
                AlarmTriggerTimingPolicy.evaluate(scheduledAt, scheduledAt));
    }

    @Test
    public void alarmBroadcastMoreThanFiveMinutesLateIsNotReplayed() {
        long scheduledAt =
                ZonedDateTime.of(2026, 9, 1, 13, 3, 0, 0, ZONE).toInstant().toEpochMilli();
        long receivedAt = scheduledAt + 5L * 60L * 1_000L + 1L;

        assertEquals(
                AlarmTriggerTimingPolicy.Decision.MISSED,
                AlarmTriggerTimingPolicy.evaluate(scheduledAt, receivedAt));
    }
}
