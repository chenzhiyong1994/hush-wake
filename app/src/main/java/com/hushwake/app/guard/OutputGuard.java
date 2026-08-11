package com.hushwake.app.guard;

import java.util.List;

/**
 * Pure state machine for the fail-closed playback contract.
 *
 * <p>Android audio APIs are deliberately kept outside this class so the ordering of safety
 * actions can be verified on the JVM.</p>
 */
public final class OutputGuard {
    public enum State {
        IDLE,
        PREPARING_SILENT,
        VERIFYING_ROUTE,
        AUDIBLE,
        BLOCKED,
        STOPPED
    }

    public enum Action {
        MUTE,
        START_SILENT,
        VERIFY_ROUTE,
        FADE_IN,
        STOP_PLAYER,
        RECORD_BLOCKED
    }

    public enum VerificationLevel {
        STRONG,
        COMPATIBLE
    }

    public enum BlockReason {
        ROUTE_LOST,
        NO_COMPATIBLE_OUTPUT,
        MULTIPLE_COMPATIBLE_OUTPUTS,
        PREFERRED_ROUTE_REJECTED,
        ROUTE_TIMEOUT,
        UNSAFE_ROUTE,
        PLAYER_ERROR
    }

    public static final class Event {
        private enum Type {
            BEGIN,
            PLAYER_STARTED,
            ROUTE_VERIFIED,
            ROUTE_LOST,
            ROUTE_REJECTED,
            STOP
        }

        private final Type type;
        private final VerificationLevel verificationLevel;
        private final BlockReason blockReason;

        private Event(Type type, VerificationLevel verificationLevel, BlockReason blockReason) {
            this.type = type;
            this.verificationLevel = verificationLevel;
            this.blockReason = blockReason;
        }

        public static Event begin() {
            return new Event(Type.BEGIN, null, null);
        }

        public static Event playerStarted() {
            return new Event(Type.PLAYER_STARTED, null, null);
        }

        public static Event routeVerified(VerificationLevel verificationLevel) {
            if (verificationLevel == null) {
                throw new IllegalArgumentException("verificationLevel is required");
            }
            return new Event(Type.ROUTE_VERIFIED, verificationLevel, null);
        }

        public static Event routeLost(BlockReason blockReason) {
            if (blockReason == null) {
                throw new IllegalArgumentException("blockReason is required");
            }
            return new Event(Type.ROUTE_LOST, null, blockReason);
        }

        public static Event routeRejected(BlockReason blockReason) {
            if (blockReason == null) {
                throw new IllegalArgumentException("blockReason is required");
            }
            return new Event(Type.ROUTE_REJECTED, null, blockReason);
        }

        public static Event stop() {
            return new Event(Type.STOP, null, null);
        }
    }

    public static final class Decision {
        private final State state;
        private final List<Action> actions;
        private final BlockReason blockReason;

        private Decision(State state, List<Action> actions, BlockReason blockReason) {
            this.state = state;
            this.actions = List.copyOf(actions);
            this.blockReason = blockReason;
        }

        public State state() {
            return state;
        }

        public List<Action> actions() {
            return actions;
        }

        public BlockReason blockReason() {
            return blockReason;
        }
    }

    private State state = State.IDLE;
    private VerificationLevel verificationLevel;

    public Decision accept(Event event) {
        if (state == State.IDLE && event.type == Event.Type.BEGIN) {
            state = State.PREPARING_SILENT;
            return decision(Action.MUTE, Action.START_SILENT);
        }
        if (state == State.PREPARING_SILENT && event.type == Event.Type.PLAYER_STARTED) {
            state = State.VERIFYING_ROUTE;
            return decision(Action.VERIFY_ROUTE);
        }
        if (state == State.VERIFYING_ROUTE && event.type == Event.Type.ROUTE_VERIFIED) {
            verificationLevel = event.verificationLevel;
            state = State.AUDIBLE;
            return decision(Action.FADE_IN);
        }
        if (state == State.AUDIBLE && event.type == Event.Type.ROUTE_LOST) {
            return block(event.blockReason);
        }
        if (state == State.VERIFYING_ROUTE && event.type == Event.Type.ROUTE_REJECTED) {
            return block(event.blockReason);
        }
        if (event.type == Event.Type.STOP
                && (state == State.PREPARING_SILENT
                        || state == State.VERIFYING_ROUTE
                        || state == State.AUDIBLE)) {
            state = State.STOPPED;
            return decision(Action.MUTE, Action.STOP_PLAYER);
        }
        return decision();
    }

    public VerificationLevel verificationLevel() {
        return verificationLevel;
    }

    public State state() {
        return state;
    }

    public BlockReason blockReason() {
        return blockReason;
    }

    private BlockReason blockReason;

    private Decision decision(Action... actions) {
        return new Decision(state, List.of(actions), blockReason);
    }

    private Decision block(BlockReason reason) {
        blockReason = reason;
        state = State.BLOCKED;
        return decision(Action.MUTE, Action.STOP_PLAYER, Action.RECORD_BLOCKED);
    }
}
