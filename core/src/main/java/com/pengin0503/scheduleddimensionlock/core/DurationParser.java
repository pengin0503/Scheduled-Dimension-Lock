package com.pengin0503.scheduleddimensionlock.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats compact duration strings such as {@code 2d12h30m}, {@code 7d}, or {@code 45s}.
 */
public final class DurationParser {
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(?:(?<days>\\d+)d)?(?:(?<hours>\\d+)h)?(?:(?<minutes>\\d+)m)?(?:(?<seconds>\\d+)s)?$"
    );
    private static final long MAX_DURATION_SECONDS = 36_500L * 86_400L;

    private DurationParser() {
    }

    /**
     * Parses a positive duration matching {@code (\d+d)?(\d+h)?(\d+m)?(\d+s)?}.
     *
     * @param raw raw duration string
     * @return parsed positive {@link Duration}
     * @throws IllegalArgumentException if the input is blank, malformed, overflows, is too long (over 36,500 days), or totals zero
     */
    public static Duration parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Duration must not be null");
        }
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Duration must not be empty");
        }
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format: '" + raw + "' (expected e.g. 2d12h30m, 7d, 45m)");
        }

        String daysStr = matcher.group("days");
        String hoursStr = matcher.group("hours");
        String minutesStr = matcher.group("minutes");
        String secondsStr = matcher.group("seconds");

        if (daysStr == null && hoursStr == null && minutesStr == null && secondsStr == null) {
            throw new IllegalArgumentException("Duration must contain at least one unit: '" + raw + "'");
        }

        try {
            long totalSeconds = 0L;
            if (daysStr != null) {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(Long.parseLong(daysStr), 86_400L));
            }
            if (hoursStr != null) {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(Long.parseLong(hoursStr), 3_600L));
            }
            if (minutesStr != null) {
                totalSeconds = Math.addExact(totalSeconds, Math.multiplyExact(Long.parseLong(minutesStr), 60L));
            }
            if (secondsStr != null) {
                totalSeconds = Math.addExact(totalSeconds, Long.parseLong(secondsStr));
            }
            if (totalSeconds > MAX_DURATION_SECONDS) {
                throw new IllegalArgumentException("Duration too long");
            }
            if (totalSeconds <= 0L) {
                throw new IllegalArgumentException("Duration must be positive: '" + raw + "'");
            }
            return Duration.ofSeconds(totalSeconds);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Duration value out of range: '" + raw + "'", e);
        }
    }

    /**
     * Formats a duration into a compact human-readable form (e.g. {@code 2d 12h 30m} or {@code 45s}).
     */
    public static String format(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "0s";
        }
        long totalSeconds = duration.getSeconds();
        if (totalSeconds == 0L && duration.getNano() > 0) {
            totalSeconds = 1L;
        }
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append('d');
        }
        if (hours > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(hours).append('h');
        }
        if (minutes > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(minutes).append('m');
        }
        if (seconds > 0 || sb.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(seconds).append('s');
        }
        return sb.toString();
    }

    /**
     * Parses a list of countdown threshold strings into sorted unique thresholds (ascending by duration).
     * Invalid entries are skipped and reported to {@code warningSink}.
     */
    public static List<CountdownThreshold> parseThresholds(List<String> rawThresholds, Consumer<String> warningSink) {
        if (rawThresholds == null || rawThresholds.isEmpty()) {
            return List.of();
        }
        Map<Duration, CountdownThreshold> unique = new LinkedHashMap<>();
        for (String raw : rawThresholds) {
            if (raw == null || raw.isBlank()) {
                if (warningSink != null) {
                    warningSink.accept("Ignoring blank countdown threshold entry");
                }
                continue;
            }
            try {
                Duration parsed = parse(raw);
                String key = raw.trim().toLowerCase();
                unique.putIfAbsent(parsed, new CountdownThreshold(key, parsed));
            } catch (IllegalArgumentException e) {
                if (warningSink != null) {
                    warningSink.accept("Ignoring invalid countdown threshold '" + raw + "': " + e.getMessage());
                }
            }
        }
        List<CountdownThreshold> result = new ArrayList<>(unique.values());
        result.sort(Comparator.comparing(CountdownThreshold::duration));
        return List.copyOf(result);
    }

    public record CountdownThreshold(String key, Duration duration) {
    }
}
