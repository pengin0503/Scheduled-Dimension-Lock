package com.pengin0503.scheduleddimensionlock.core;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses {@code defaultSchedules} config entries such as
 * {@code "minecraft:the_nether;unlock=2027-01-01 10:00;lock=2027-01-03 22:00"}.
 */
public final class DefaultScheduleParser {
    private DefaultScheduleParser() {
    }

    public record DefaultSchedule(String dimensionId, boolean locked, Instant unlockAt, Instant lockAt) {
    }

    public static List<DefaultSchedule> parseAll(List<String> rawEntries, ZoneId zone, Consumer<String> warningSink) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return List.of();
        }
        List<DefaultSchedule> results = new ArrayList<>();
        for (String raw : rawEntries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                results.add(parseEntry(raw, zone));
            } catch (IllegalArgumentException e) {
                if (warningSink != null) {
                    warningSink.accept("Ignoring invalid defaultSchedule entry '" + raw + "': " + e.getMessage());
                }
            }
        }
        return List.copyOf(results);
    }

    public static DefaultSchedule parseEntry(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Entry must not be blank");
        }
        String[] parts = raw.split(";");
        String dimensionId = parts[0].trim();
        if (dimensionId.isEmpty()) {
            throw new IllegalArgumentException("Missing dimension ID");
        }
        if (ScheduleManager.OVERWORLD_ID.equals(dimensionId)) {
            throw new IllegalArgumentException("Overworld cannot be locked or scheduled");
        }

        Instant unlockAt = null;
        Instant lockAt = null;
        Boolean explicitLocked = null;

        for (int i = 1; i < parts.length; i++) {
            String segment = parts[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            int eq = segment.indexOf('=');
            if (eq <= 0 || eq == segment.length() - 1) {
                throw new IllegalArgumentException("Invalid key=value segment: '" + segment + "'");
            }
            String key = segment.substring(0, eq).trim().toLowerCase();
            String value = segment.substring(eq + 1).trim();
            switch (key) {
                case "unlock" -> unlockAt = DateTimeParser.parse(value, zone);
                case "lock" -> lockAt = DateTimeParser.parse(value, zone);
                case "locked" -> {
                    if ("true".equalsIgnoreCase(value)) {
                        explicitLocked = Boolean.TRUE;
                    } else if ("false".equalsIgnoreCase(value)) {
                        explicitLocked = Boolean.FALSE;
                    } else {
                        throw new IllegalArgumentException("Invalid boolean for locked: '" + value + "'");
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option '" + key + "'");
            }
        }

        if (unlockAt == null && lockAt == null && explicitLocked == null) {
            throw new IllegalArgumentException("Entry must specify at least unlock, lock, or locked");
        }

        DateTimeParser.validateWindow(unlockAt, lockAt);

        boolean initialLocked;
        if (explicitLocked != null) {
            initialLocked = explicitLocked;
        } else {
            initialLocked = unlockAt != null;
        }
        return new DefaultSchedule(dimensionId, initialLocked, unlockAt, lockAt);
    }
}
