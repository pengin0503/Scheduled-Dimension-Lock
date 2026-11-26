package com.pengin0503.scheduleddimensionlock.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTrackerTest {

    @Test
    void suppressesRepeatedNotificationsWithinCooldown() {
        CooldownTracker tracker = new CooldownTracker();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        Duration cooldown = Duration.ofSeconds(5);
        Instant t0 = Instant.parse("2027-01-01T10:00:00Z");

        assertTrue(tracker.shouldNotify(playerA, t0, cooldown));
        assertFalse(tracker.shouldNotify(playerA, t0.plusSeconds(4), cooldown));
        assertTrue(tracker.shouldNotify(playerB, t0.plusSeconds(2), cooldown));
        assertTrue(tracker.shouldNotify(playerA, t0.plusSeconds(5), cooldown));
        assertFalse(tracker.shouldNotify(playerA, t0.plusSeconds(9), cooldown));
    }

    @Test
    void zeroCooldownAlwaysNotifies() {
        CooldownTracker tracker = new CooldownTracker();
        UUID player = UUID.randomUUID();
        Instant t0 = Instant.parse("2027-01-01T10:00:00Z");

        assertTrue(tracker.shouldNotify(player, t0, Duration.ZERO));
        assertTrue(tracker.shouldNotify(player, t0, Duration.ZERO));
    }
}
