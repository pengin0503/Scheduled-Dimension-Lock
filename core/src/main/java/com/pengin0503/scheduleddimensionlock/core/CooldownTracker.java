package com.pengin0503.scheduleddimensionlock.core;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppresses repeated blocked-entry messages to the same player within a cooldown window.
 */
public final class CooldownTracker {
    private final Map<UUID, Instant> lastSent = new ConcurrentHashMap<>();

    public boolean shouldNotify(UUID playerId, Instant now, Duration cooldown) {
        if (playerId == null || now == null) {
            return false;
        }
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            lastSent.put(playerId, now);
            return true;
        }
        Instant previous = lastSent.get(playerId);
        if (previous == null || !now.isBefore(previous.plus(cooldown))) {
            lastSent.put(playerId, now);
            return true;
        }
        return false;
    }

    public void remove(UUID playerId) {
        if (playerId != null) {
            lastSent.remove(playerId);
        }
    }

    public void clear() {
        lastSent.clear();
    }
}
