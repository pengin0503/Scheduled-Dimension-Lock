package com.pengin0503.scheduleddimensionlock.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the lock and schedule state of a single dimension.
 */
public final class DimensionState {
    private boolean locked;
    private Instant unlockAt;
    private Instant lockAt;
    private final Set<String> announcedThresholds;

    public DimensionState(boolean locked, Instant unlockAt, Instant lockAt, Set<String> announcedThresholds) {
        DateTimeParser.validateWindow(unlockAt, lockAt);
        this.locked = locked;
        this.unlockAt = unlockAt;
        this.lockAt = lockAt;
        this.announcedThresholds = new LinkedHashSet<>();
        if (announcedThresholds != null) {
            for (String threshold : announcedThresholds) {
                if (threshold != null && !threshold.isBlank()) {
                    this.announcedThresholds.add(threshold.trim().toLowerCase());
                }
            }
        }
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getUnlockAt() {
        return unlockAt;
    }

    public void setUnlockAt(Instant unlockAt) {
        DateTimeParser.validateWindow(unlockAt, this.lockAt);
        this.unlockAt = unlockAt;
    }

    public Instant getLockAt() {
        return lockAt;
    }

    public void setLockAt(Instant lockAt) {
        DateTimeParser.validateWindow(this.unlockAt, lockAt);
        this.lockAt = lockAt;
    }

    public void setWindow(Instant unlockAt, Instant lockAt) {
        DateTimeParser.validateWindow(unlockAt, lockAt);
        this.unlockAt = unlockAt;
        this.lockAt = lockAt;
    }

    public Set<String> getAnnouncedThresholds() {
        return Collections.unmodifiableSet(announcedThresholds);
    }

    public boolean markThresholdAnnounced(String thresholdKey) {
        return announcedThresholds.add(thresholdKey.trim().toLowerCase());
    }

    public boolean isThresholdAnnounced(String thresholdKey) {
        return announcedThresholds.contains(thresholdKey.trim().toLowerCase());
    }

    public void clearAnnouncedThresholds() {
        announcedThresholds.clear();
    }

    public boolean hasSchedule() {
        return unlockAt != null || lockAt != null;
    }

    public DimensionState copy() {
        return new DimensionState(locked, unlockAt, lockAt, announcedThresholds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DimensionState that)) return false;
        return locked == that.locked
                && Objects.equals(unlockAt, that.unlockAt)
                && Objects.equals(lockAt, that.lockAt)
                && Objects.equals(announcedThresholds, that.announcedThresholds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locked, unlockAt, lockAt, announcedThresholds);
    }
}
