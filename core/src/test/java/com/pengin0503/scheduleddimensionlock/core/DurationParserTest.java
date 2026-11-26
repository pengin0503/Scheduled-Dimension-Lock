package com.pengin0503.scheduleddimensionlock.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {

    @Test
    void parsesSingleAndCombinedUnits() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d"));
        assertEquals(Duration.ofHours(12), DurationParser.parse("12h"));
        assertEquals(Duration.ofMinutes(45), DurationParser.parse("45m"));
        assertEquals(Duration.ofSeconds(30), DurationParser.parse("30s"));
        assertEquals(
                Duration.ofDays(2).plusHours(12).plusMinutes(30),
                DurationParser.parse("2d12h30m")
        );
        assertEquals(
                Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4),
                DurationParser.parse(" 1D2H3M4S ")
        );
    }

    @Test
    void rejectsInvalidOrNonPositiveDurations() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0s"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("0d0h0m0s"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("-5m"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("30m2h"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("5w"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("999999999999999999999d"));
    }

    @Test
    void rejectsDurationsLongerThanLimit() {
        IllegalArgumentException tooManyDays = assertThrows(
                IllegalArgumentException.class, () -> DurationParser.parse("36501d"));
        assertEquals("Duration too long", tooManyDays.getMessage());
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("36500d1s"));
    }

    @Test
    void acceptsDurationsWithinLimit() {
        assertEquals(Duration.ofDays(36_500), DurationParser.parse("36500d"));
        assertEquals(Duration.ofMinutes(90), DurationParser.parse("1h30m"));
    }

    @Test
    void formatsDurationsHumanReadably() {
        assertEquals("0s", DurationParser.format(Duration.ZERO));
        assertEquals("0s", DurationParser.format(Duration.ofSeconds(-5)));
        assertEquals("45s", DurationParser.format(Duration.ofSeconds(45)));
        assertEquals("1h", DurationParser.format(Duration.ofHours(1)));
        assertEquals("2d 12h 30m", DurationParser.format(Duration.ofDays(2).plusHours(12).plusMinutes(30)));
        assertEquals("1m 5s", DurationParser.format(Duration.ofSeconds(65)));
    }

    @Test
    void parsesAndSortsThresholdsWhileSkippingInvalidEntries() {
        List<String> warnings = new ArrayList<>();
        List<DurationParser.CountdownThreshold> thresholds = DurationParser.parseThresholds(
                List.of("1d", "invalid", "5m", "1h", "5m", "0s", ""),
                warnings::add
        );

        assertEquals(3, thresholds.size());
        assertEquals("5m", thresholds.get(0).key());
        assertEquals("1h", thresholds.get(1).key());
        assertEquals("1d", thresholds.get(2).key());
        assertEquals(3, warnings.size());
    }
}
