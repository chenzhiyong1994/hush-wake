package com.hushwake.app.guard;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public final class OutputGuardTest {
    @Test
    public void startingATestMutesBeforeStartingThePlayer() {
        OutputGuard guard = new OutputGuard();

        OutputGuard.Decision decision = guard.accept(OutputGuard.Event.begin());

        assertEquals(OutputGuard.State.PREPARING_SILENT, decision.state());
        assertEquals(
                List.of(OutputGuard.Action.MUTE, OutputGuard.Action.START_SILENT),
                decision.actions());
    }

    @Test
    public void routeEvidenceIsRequiredBeforeTheGuardCanFadeIn() {
        OutputGuard guard = new OutputGuard();
        guard.accept(OutputGuard.Event.begin());

        OutputGuard.Decision checking = guard.accept(OutputGuard.Event.playerStarted());
        assertEquals(OutputGuard.State.VERIFYING_ROUTE, checking.state());
        assertEquals(List.of(OutputGuard.Action.VERIFY_ROUTE), checking.actions());

        OutputGuard.Decision audible =
                guard.accept(OutputGuard.Event.routeVerified(OutputGuard.VerificationLevel.STRONG));
        assertEquals(OutputGuard.State.AUDIBLE, audible.state());
        assertEquals(List.of(OutputGuard.Action.FADE_IN), audible.actions());
    }

    @Test
    public void losingTheRouteMutesBeforeStoppingAndRecordingTheBlock() {
        OutputGuard guard = audibleGuard();

        OutputGuard.Decision blocked =
                guard.accept(OutputGuard.Event.routeLost(OutputGuard.BlockReason.ROUTE_LOST));

        assertEquals(OutputGuard.State.BLOCKED, blocked.state());
        assertEquals(OutputGuard.BlockReason.ROUTE_LOST, blocked.blockReason());
        assertEquals(
                List.of(
                        OutputGuard.Action.MUTE,
                        OutputGuard.Action.STOP_PLAYER,
                        OutputGuard.Action.RECORD_BLOCKED),
                blocked.actions());
    }

    @Test
    public void rejectedRouteNeverBecomesAudible() {
        OutputGuard guard = new OutputGuard();
        guard.accept(OutputGuard.Event.begin());
        guard.accept(OutputGuard.Event.playerStarted());

        OutputGuard.Decision blocked =
                guard.accept(OutputGuard.Event.routeRejected(OutputGuard.BlockReason.UNSAFE_ROUTE));

        assertEquals(OutputGuard.State.BLOCKED, blocked.state());
        assertEquals(OutputGuard.BlockReason.UNSAFE_ROUTE, blocked.blockReason());
        assertEquals(
                List.of(
                        OutputGuard.Action.MUTE,
                        OutputGuard.Action.STOP_PLAYER,
                        OutputGuard.Action.RECORD_BLOCKED),
                blocked.actions());
    }

    @Test
    public void stoppingAnAudibleTestMutesBeforeStoppingThePlayer() {
        OutputGuard guard = audibleGuard();

        OutputGuard.Decision stopped = guard.accept(OutputGuard.Event.stop());

        assertEquals(OutputGuard.State.STOPPED, stopped.state());
        assertEquals(
                List.of(OutputGuard.Action.MUTE, OutputGuard.Action.STOP_PLAYER),
                stopped.actions());
    }

    private static OutputGuard audibleGuard() {
        OutputGuard guard = new OutputGuard();
        guard.accept(OutputGuard.Event.begin());
        guard.accept(OutputGuard.Event.playerStarted());
        guard.accept(OutputGuard.Event.routeVerified(OutputGuard.VerificationLevel.STRONG));
        return guard;
    }
}
