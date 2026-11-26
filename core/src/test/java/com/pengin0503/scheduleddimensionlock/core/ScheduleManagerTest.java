package com.pengin0503.scheduleddimensionlock.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleManagerTest {

    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final List<DurationParser.CountdownThreshold> DEFAULT_THRESHOLDS =
            DurationParser.parseThresholds(List.of("7d", "1d", "12h", "1h", "10m", "5m", "1m"), null);

    @Test
    void overworldIsAlwaysRejected() {
        ScheduleManager manager = new ScheduleManager();
        Instant t1 = Instant.parse("2027-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2027-01-02T10:00:00Z");

        assertFalse(manager.isLocked(ScheduleManager.OVERWORLD_ID));
        assertThrows(IllegalArgumentException.class, () -> manager.lock(ScheduleManager.OVERWORLD_ID));
        assertThrows(IllegalArgumentException.class, () -> manager.unlock(ScheduleManager.OVERWORLD_ID));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleUnlock(ScheduleManager.OVERWORLD_ID, t1));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleLock(ScheduleManager.OVERWORLD_ID, t2));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleWindow(ScheduleManager.OVERWORLD_ID, t1, t2));
        assertThrows(IllegalArgumentException.class, () -> manager.clearSchedule(ScheduleManager.OVERWORLD_ID));
    }

    @Test
    void enforcesUnlockBeforeLockInvariant() {
        ScheduleManager manager = new ScheduleManager();
        Instant t1 = Instant.parse("2027-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2027-01-02T10:00:00Z");

        manager.scheduleUnlock(NETHER, t2);
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleLock(NETHER, t1));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleLock(NETHER, t2));

        manager.clearSchedule(NETHER);
        manager.scheduleLock(NETHER, t1);
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleUnlock(NETHER, t2));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleUnlock(NETHER, t1));

        assertThrows(IllegalArgumentException.class, () -> manager.scheduleWindow(END, t2, t1));
        assertThrows(IllegalArgumentException.class, () -> manager.scheduleWindow(END, t1, t1));
    }

    @Test
    void manualUnlockDoesNotClearSchedules() {
        ScheduleManager manager = new ScheduleManager();
        Instant t1 = Instant.parse("2027-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2027-01-02T10:00:00Z");

        manager.scheduleWindow(NETHER, t1, t2);
        assertTrue(manager.isLocked(NETHER));

        manager.unlock(NETHER);
        assertFalse(manager.isLocked(NETHER));
        DimensionState state = manager.getState(NETHER).orElseThrow();
        assertEquals(t1, state.getUnlockAt());
        assertEquals(t2, state.getLockAt());
    }

    @Test
    void executesUnlockAndRelockTransitionsAndClearsExecutedTimestamps() {
        ScheduleManager manager = new ScheduleManager();
        Instant unlockAt = Instant.parse("2027-01-01T10:00:00Z");
        Instant lockAt = Instant.parse("2027-01-01T12:00:00Z");
        manager.scheduleWindow(NETHER, unlockAt, lockAt);

        // Before unlock
        ScheduleManager.TickResult before = manager.evaluate(Instant.parse("2027-01-01T09:00:00Z"), false, DEFAULT_THRESHOLDS);
        assertTrue(before.transitions().isEmpty());
        assertTrue(manager.isLocked(NETHER));

        // Exact unlock time
        ScheduleManager.TickResult onUnlock = manager.evaluate(unlockAt, false, DEFAULT_THRESHOLDS);
        assertEquals(1, onUnlock.transitions().size());
        assertEquals(ScheduleManager.TransitionType.UNLOCKED, onUnlock.transitions().get(0).type());
        assertFalse(manager.isLocked(NETHER));
        assertNull(manager.getState(NETHER).orElseThrow().getUnlockAt());
        assertEquals(lockAt, manager.getState(NETHER).orElseThrow().getLockAt());

        // Exact re-lock time
        ScheduleManager.TickResult onLock = manager.evaluate(lockAt, false, DEFAULT_THRESHOLDS);
        assertEquals(1, onLock.transitions().size());
        assertEquals(ScheduleManager.TransitionType.LOCKED, onLock.transitions().get(0).type());
        assertTrue(manager.isLocked(NETHER));
        assertNull(manager.getState(NETHER).orElseThrow().getLockAt());
    }

    @Test
    void catchUpExecutesPassedWindowWhileServerWasOffline() {
        ScheduleManager manager = new ScheduleManager();
        Instant unlockAt = Instant.parse("2027-01-01T10:00:00Z");
        Instant lockAt = Instant.parse("2027-01-01T12:00:00Z");
        manager.scheduleWindow(NETHER, unlockAt, lockAt);

        // Server restarts at 13:00 (past both unlockAt and lockAt)
        ScheduleManager.TickResult result = manager.evaluate(Instant.parse("2027-01-01T13:00:00Z"), true, DEFAULT_THRESHOLDS);
        assertEquals(2, result.transitions().size());
        assertEquals(ScheduleManager.TransitionType.UNLOCKED, result.transitions().get(0).type());
        assertEquals(ScheduleManager.TransitionType.LOCKED, result.transitions().get(1).type());
        assertTrue(result.countdowns().isEmpty());
        assertTrue(manager.isLocked(NETHER));
        assertNull(manager.getState(NETHER).orElseThrow().getUnlockAt());
        assertNull(manager.getState(NETHER).orElseThrow().getLockAt());
    }

    @Test
    void countdownNotifiesOncePerThresholdAndResetsOnReschedule() {
        ScheduleManager manager = new ScheduleManager();
        Instant unlockAt = Instant.parse("2027-01-01T10:00:00Z");
        manager.scheduleUnlock(NETHER, unlockAt);

        // 2 hours before -> crosses 7d, 1d, 12h; emits only one event (12h) and marks all three announced
        ScheduleManager.TickResult r1 = manager.evaluate(Instant.parse("2027-01-01T08:00:00Z"), true, DEFAULT_THRESHOLDS);
        assertEquals(1, r1.countdowns().size());
        assertEquals("12h", r1.countdowns().get(0).thresholdKey());
        assertEquals(ScheduleManager.TransitionType.UNLOCKED, r1.countdowns().get(0).targetTransition());

        // 1h 30m before -> no new threshold crossed
        ScheduleManager.TickResult r2 = manager.evaluate(Instant.parse("2027-01-01T08:30:00Z"), true, DEFAULT_THRESHOLDS);
        assertTrue(r2.countdowns().isEmpty());
        assertFalse(r2.dirty());

        // 59m before -> crosses 1h
        ScheduleManager.TickResult r3 = manager.evaluate(Instant.parse("2027-01-01T09:01:00Z"), true, DEFAULT_THRESHOLDS);
        assertEquals(1, r3.countdowns().size());
        assertEquals("1h", r3.countdowns().get(0).thresholdKey());

        // Reschedule resets announced thresholds
        Instant newUnlock = Instant.parse("2027-01-01T11:00:00Z");
        manager.scheduleUnlock(NETHER, newUnlock);
        assertTrue(manager.getState(NETHER).orElseThrow().getAnnouncedThresholds().isEmpty());

        ScheduleManager.TickResult r4 = manager.evaluate(Instant.parse("2027-01-01T10:00:00Z"), true, DEFAULT_THRESHOLDS);
        assertEquals(1, r4.countdowns().size());
        assertEquals("1h", r4.countdowns().get(0).thresholdKey());
    }

    @Test
    void countdownWorksForRelockAndRespectsDisabledFlag() {
        ScheduleManager manager = new ScheduleManager();
        Instant lockAt = Instant.parse("2027-01-01T12:00:00Z");
        manager.scheduleLock(END, lockAt);

        // Disabled -> no countdowns
        ScheduleManager.TickResult disabled = manager.evaluate(Instant.parse("2027-01-01T11:55:00Z"), false, DEFAULT_THRESHOLDS);
        assertTrue(disabled.countdowns().isEmpty());

        // Enabled -> fires 5m countdown for LOCKED
        ScheduleManager.TickResult enabled = manager.evaluate(Instant.parse("2027-01-01T11:55:00Z"), true, DEFAULT_THRESHOLDS);
        assertEquals(1, enabled.countdowns().size());
        assertEquals(ScheduleManager.TransitionType.LOCKED, enabled.countdowns().get(0).targetTransition());
        assertEquals("5m", enabled.countdowns().get(0).thresholdKey());
    }

    @Test
    void clearSchedulePreservesLockState() {
        ScheduleManager manager = new ScheduleManager();
        Instant unlockAt = Instant.parse("2027-01-01T10:00:00Z");
        manager.scheduleUnlock(NETHER, unlockAt);

        assertTrue(manager.clearSchedule(NETHER));
        assertTrue(manager.isLocked(NETHER));
        assertNull(manager.getState(NETHER).orElseThrow().getUnlockAt());
        assertFalse(manager.clearSchedule(NETHER));
    }
}
