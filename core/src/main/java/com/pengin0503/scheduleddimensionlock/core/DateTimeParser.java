package com.pengin0503.scheduleddimensionlock.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats absolute timestamps in {@code yyyy-MM-dd HH:mm} using a configured {@link ZoneId}.
 */
public final class DateTimeParser {
    private static final DateTimeFormatter STRICT_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"([^\"]+)\"|(\\S+)");

    private DateTimeParser() {
    }

    /**
     * Resolves a configured timezone string, falling back to {@link ZoneId#systemDefault()} if blank or invalid.
     */
    public static ZoneId resolveZoneId(String configuredTimezone, Consumer<String> warningSink) {
        if (configuredTimezone == null || configuredTimezone.isBlank()) {
            return ZoneId.systemDefault();
        }
        String trimmed = configuredTimezone.trim();
        try {
            return ZoneId.of(trimmed);
        } catch (DateTimeException e) {
            if (warningSink != null) {
                warningSink.accept("Invalid timezone '" + configuredTimezone + "', falling back to system default (" + ZoneId.systemDefault() + "): " + e.getMessage());
            }
            return ZoneId.systemDefault();
        }
    }

    /**
     * Parses a date-time string in {@code yyyy-MM-dd HH:mm} format using {@code zone}.
     * Optional surrounding double quotes and repeated spaces between date and time are tolerated.
     */
    public static Instant parse(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Date-time must not be empty (expected yyyy-MM-dd HH:mm)");
        }
        if (zone == null) {
            throw new IllegalArgumentException("ZoneId must not be null");
        }
        String cleaned = stripQuotes(raw.trim()).replaceAll("\\s+", " ");
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(cleaned, STRICT_FORMATTER);
            return localDateTime.atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date-time '" + raw + "' (expected yyyy-MM-dd HH:mm)", e);
        }
    }

    /**
     * Parses a pair of date-times representing an open window ({@code unlockAt < lockAt}).
     * Accepts either {@code yyyy-MM-dd HH:mm yyyy-MM-dd HH:mm} or {@code "yyyy-MM-dd HH:mm" "yyyy-MM-dd HH:mm"}.
     */
    public static Window parseWindow(String raw, ZoneId zone) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Window must specify unlock and lock date-times");
        }
        List<String> tokens = tokenize(raw.trim());
        String unlockRaw;
        String lockRaw;
        if (tokens.size() == 2) {
            unlockRaw = tokens.get(0);
            lockRaw = tokens.get(1);
        } else if (tokens.size() == 4) {
            unlockRaw = tokens.get(0) + " " + tokens.get(1);
            lockRaw = tokens.get(2) + " " + tokens.get(3);
        } else {
            throw new IllegalArgumentException(
                    "Invalid window format '" + raw + "' (expected <yyyy-MM-dd> <HH:mm> <yyyy-MM-dd> <HH:mm>)"
            );
        }

        Instant unlockAt = parse(unlockRaw, zone);
        Instant lockAt = parse(lockRaw, zone);
        validateWindow(unlockAt, lockAt);
        return new Window(unlockAt, lockAt);
    }

    /**
     * Validates that if both {@code unlockAt} and {@code lockAt} are present, {@code unlockAt < lockAt}.
     */
    public static void validateWindow(Instant unlockAt, Instant lockAt) {
        if (unlockAt != null && lockAt != null && !unlockAt.isBefore(lockAt)) {
            throw new IllegalArgumentException("Unlock time must be strictly before lock time");
        }
    }

    /**
     * Formats an {@link Instant} as {@code yyyy-MM-dd HH:mm} in {@code zone}.
     */
    public static String format(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "-";
        }
        ZoneId effectiveZone = zone != null ? zone : ZoneId.systemDefault();
        return STRICT_FORMATTER.format(instant.atZone(effectiveZone));
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            String quoted = matcher.group(1);
            String plain = matcher.group(2);
            tokens.add(quoted != null ? quoted : plain);
        }
        return tokens;
    }

    public record Window(Instant unlockAt, Instant lockAt) {
        public Window {
            if (unlockAt == null || lockAt == null) {
                throw new IllegalArgumentException("Window unlockAt and lockAt must not be null");
            }
            validateWindow(unlockAt, lockAt);
        }
    }
}
