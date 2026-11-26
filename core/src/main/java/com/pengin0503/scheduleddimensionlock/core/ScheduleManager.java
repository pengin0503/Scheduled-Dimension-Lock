package com.pengin0503.scheduleddimensionlock.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pure Java state machine for dimension lock states, scheduled transitions, catch-up, and countdowns.
 */
public final class ScheduleManager {
    public static final String OVERWORLD_ID = "minecraft:overworld";

    private final Map<String, DimensionState> states = new TreeMap<>();

    public enum TransitionType {
        UNLOCKED,
        LOCKED
    }

    public record TransitionEvent(String dimensionId, TransitionType type) {
    }

    public record CountdownEvent(String dimensionId, TransitionType targetTransition, String thresholdKey, Duration remaining) {
    }

    public record TickResult(List<TransitionEvent> transitions, List<CountdownEvent> countdowns, boolean dirty) {
    }

    public static void validateDimensionId(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("Dimension ID must not be empty");
        }
        if (OVERWORLD_ID.equals(dimensionId.trim())) {
            throw new IllegalArgumentException("The Overworld (" + OVERWORLD_ID + ") cannot be locked or scheduled");
        }
    }

    public boolean isLocked(String dimensionId) {
        if (dimensionId == null || OVERWORLD_ID.equals(dimensionId)) {
            return false;
        }
        DimensionState state = states.get(dimensionId);
        return state != null && state.isLocked();
    }

    public Optional<DimensionState> getState(String dimensionId) {
        if (dimensionId == null) {
            return Optional.empty();
        }
        DimensionState state = states.get(dimensionId);
        return state == null ? Optional.empty() : Optional.of(state.copy());
    }

    public Map<String, DimensionState> getAllStates() {
        Map<String, DimensionState> snapshot = new TreeMap<>();
        for (Map.Entry<String, DimensionState> entry : states.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Loads a validated state directly (used when deserializing SavedData).
     */
    public void putLoadedState(String dimensionId, DimensionState state) {
        validateDimensionId(dimensionId);
        if (state == null) {
            throw new IllegalArgumentException("DimensionState must not be null");
        }
        states.put(dimensionId.trim(), state.copy());
    }

    public void lock(String dimensionId) {
        validateDimensionId(dimensionId);
        String id = dimensionId.trim();
        DimensionState state = states.computeIfAbsent(id, k -> new DimensionState(false, null, null, null));
        if (!state.isLocked()) {
            state.setLocked(true);
            state.clearAnnouncedThresholds();
        }
    }

    /**
     * Immediately unlocks the dimension without clearing scheduled {@code unlockAt} or {@code lockAt}.
     */
    public void unlock(String dimensionId) {
        validateDimensionId(dimensionId);
        String id = dimensionId.trim();
        DimensionState state = states.computeIfAbsent(id, k -> new DimensionState(false, null, null, null));
        if (state.isLocked()) {
            state.setLocked(false);
            state.clearAnnouncedThresholds();
        }
    }

    public void scheduleUnlock(String dimensionId, Instant unlockAt) {
        validateDimensionId(dimensionId);
        if (unlockAt == null) {
            throw new IllegalArgumentException("unlockAt must not be null");
        }
        String id = dimensionId.trim();
        DimensionState existing = states.get(id);
        if (existing != null) {
            DateTimeParser.validateWindow(unlockAt, existing.getLockAt());
            existing.setUnlockAt(unlockAt);
            existing.setLocked(true);
            existing.clearAnnouncedThresholds();
        } else {
            states.put(id, new DimensionState(true, unlockAt, null, null));
        }
    }

    public void scheduleLock(String dimensionId, Instant lockAt) {
        validateDimensionId(dimensionId);
        if (lockAt == null) {
            throw new IllegalArgumentException("lockAt must not be null");
        }
        String id = dimensionId.trim();
        DimensionState existing = states.get(id);
        if (existing != null) {
            DateTimeParser.validateWindow(existing.getUnlockAt(), lockAt);
            existing.setLockAt(lockAt);
            existing.clearAnnouncedThresholds();
        } else {
            states.put(id, new DimensionState(false, null, lockAt, null));
        }
    }

    public void scheduleWindow(String dimensionId, Instant unlockAt, Instant lockAt) {
        validateDimensionId(dimensionId);
        if (unlockAt == null || lockAt == null) {
            throw new IllegalArgumentException("Window unlockAt and lockAt must not be null");
        }
        DateTimeParser.validateWindow(unlockAt, lockAt);
        String id = dimensionId.trim();
        DimensionState existing = states.get(id);
        if (existing != null) {
            existing.setWindow(unlockAt, lockAt);
            existing.setLocked(true);
            existing.clearAnnouncedThresholds();
        } else {
            states.put(id, new DimensionState(true, unlockAt, lockAt, null));
        }
    }

    /**
     * Clears {@code unlockAt}, {@code lockAt}, and announced thresholds for the dimension without changing {@code locked}.
     */
    public boolean clearSchedule(String dimensionId) {
        validateDimensionId(dimensionId);
        String id = dimensionId.trim();
        DimensionState existing = states.get(id);
        if (existing == null) {
            return false;
        }
        boolean hadSchedule = existing.hasSchedule() || !existing.getAnnouncedThresholds().isEmpty();
        existing.setWindow(null, null);
        existing.clearAnnouncedThresholds();
        return hadSchedule;
    }

    /**
     * Imports default schedules on initial world load.
     */
    public boolean importDefaultSchedules(List<DefaultScheduleParser.DefaultSchedule> defaults) {
        if (defaults == null || defaults.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (DefaultScheduleParser.DefaultSchedule entry : defaults) {
            if (entry == null || OVERWORLD_ID.equals(entry.dimensionId())) {
                continue;
            }
            states.put(entry.dimensionId(), new DimensionState(entry.locked(), entry.unlockAt(), entry.lockAt(), null));
            changed = true;
        }
        return changed;
    }

    /**
     * Evaluates all dimension schedules against {@code now}, executing due transitions (including catch-up)
     * and emitting countdown notifications when remaining time drops to or below configured thresholds.
     */
    public TickResult evaluate(Instant now, boolean announceCountdown, List<DurationParser.CountdownThreshold> thresholds) {
        List<TransitionEvent> transitions = new ArrayList<>();
        List<CountdownEvent> countdowns = new ArrayList<>();
        boolean dirty = false;

        for (Map.Entry<String, DimensionState> entry : states.entrySet()) {
            String dimId = entry.getKey();
            DimensionState state = entry.getValue();

            // 1. Check unlock transition (or clear stale unlockAt if already unlocked)
            if (state.getUnlockAt() != null && !now.isBefore(state.getUnlockAt())) {
                if (state.isLocked()) {
                    state.setLocked(false);
                    state.setUnlockAt(null);
                    state.clearAnnouncedThresholds();
                    transitions.add(new TransitionEvent(dimId, TransitionType.UNLOCKED));
                    dirty = true;
                } else {
                    state.setUnlockAt(null);
                    state.clearAnnouncedThresholds();
                    dirty = true;
                }
            }

            // 2. Check lock transition (also handles catch-up when both unlockAt and lockAt passed while offline)
            if (state.getLockAt() != null && !now.isBefore(state.getLockAt())) {
                if (!state.isLocked()) {
                    state.setLocked(true);
                    state.setLockAt(null);
                    state.clearAnnouncedThresholds();
                    transitions.add(new TransitionEvent(dimId, TransitionType.LOCKED));
                    dirty = true;
                } else {
                    state.setLockAt(null);
                    state.clearAnnouncedThresholds();
                    dirty = true;
                }
            }

            // 3. Check countdown for the next pending transition
            if (announceCountdown && thresholds != null && !thresholds.isEmpty()) {
                Instant nextTarget = null;
                TransitionType targetType = null;
                if (state.isLocked() && state.getUnlockAt() != null) {
                    nextTarget = state.getUnlockAt();
                    targetType = TransitionType.UNLOCKED;
                } else if (!state.isLocked() && state.getLockAt() != null) {
                    nextTarget = state.getLockAt();
                    targetType = TransitionType.LOCKED;
                }

                if (nextTarget != null) {
                    Duration remaining = Duration.between(now, nextTarget);
                    if (remaining.isPositive()) {
                        DurationParser.CountdownThreshold smallestCrossed = null;
                        for (DurationParser.CountdownThreshold threshold : thresholds) {
                            if (remaining.compareTo(threshold.duration()) <= 0
                                    && !state.isThresholdAnnounced(threshold.key())) {
                                state.markThresholdAnnounced(threshold.key());
                                dirty = true;
                                if (smallestCrossed == null
                                        || threshold.duration().compareTo(smallestCrossed.duration()) < 0) {
                                    smallestCrossed = threshold;
                                }
                            }
                        }
                        if (smallestCrossed != null) {
                            countdowns.add(new CountdownEvent(dimId, targetType, smallestCrossed.key(), remaining));
                        }
                    }
                }
            }
        }

        return new TickResult(List.copyOf(transitions), List.copyOf(countdowns), dirty);
    }

    /**
     * Returns the next scheduled transition time for the given dimension, if any.
     */
    public Optional<NextTransition> getNextTransition(String dimensionId) {
        DimensionState state = states.get(dimensionId);
        if (state == null) {
            return Optional.empty();
        }
        if (state.isLocked() && state.getUnlockAt() != null) {
            return Optional.of(new NextTransition(TransitionType.UNLOCKED, state.getUnlockAt()));
        }
        if (!state.isLocked() && state.getLockAt() != null) {
            return Optional.of(new NextTransition(TransitionType.LOCKED, state.getLockAt()));
        }
        return Optional.empty();
    }

    public record NextTransition(TransitionType type, Instant time) {
    }
}
