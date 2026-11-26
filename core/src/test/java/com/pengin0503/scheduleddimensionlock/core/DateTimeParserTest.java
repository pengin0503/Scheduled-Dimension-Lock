package com.pengin0503.scheduleddimensionlock.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DateTimeParserTest {

    @Test
    void parsesDateTimeWithConfiguredTimezone() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        ZoneId utc = ZoneId.of("UTC");

        Instant tokyoInstant = DateTimeParser.parse("2027-01-01 10:00", tokyo);
        Instant utcInstant = DateTimeParser.parse("2027-01-01 01:00", utc);
        assertEquals(utcInstant, tokyoInstant);
        assertEquals("2027-01-01 10:00", DateTimeParser.format(tokyoInstant, tokyo));
        assertEquals("2027-01-01 01:00", DateTimeParser.format(tokyoInstant, utc));
    }

    @Test
    void toleratesQuotesAndMultipleSpaces() {
        ZoneId utc = ZoneId.of("UTC");
        Instant expected = Instant.parse("2027-05-15T14:30:00Z");
        assertEquals(expected, DateTimeParser.parse("\"2027-05-15 14:30\"", utc));
        assertEquals(expected, DateTimeParser.parse("2027-05-15   14:30", utc));
    }

    @Test
    void rejectsMalformedOrInvalidDates() {
        ZoneId utc = ZoneId.of("UTC");
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("", utc));
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("2027-02-30 10:00", utc));
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("2027-01-01 10:00:00", utc));
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("2027-01-01", utc));
        assertThrows(IllegalArgumentException.class, () -> DateTimeParser.parse("2027/01/01 10:00", utc));
    }

    @Test
    void parsesValidWindowsAndRejectsInvalidOrdering() {
        ZoneId utc = ZoneId.of("UTC");

        DateTimeParser.Window unquoted = DateTimeParser.parseWindow("2027-01-01 10:00 2027-01-03 22:00", utc);
        assertEquals(Instant.parse("2027-01-01T10:00:00Z"), unquoted.unlockAt());
        assertEquals(Instant.parse("2027-01-03T22:00:00Z"), unquoted.lockAt());

        DateTimeParser.Window quoted = DateTimeParser.parseWindow("\"2027-01-01 10:00\" \"2027-01-03 22:00\"", utc);
        assertEquals(unquoted, quoted);

        // Equal unlock and lock must fail
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeParser.parseWindow("2027-01-01 10:00 2027-01-01 10:00", utc));
        // Unlock after lock must fail
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeParser.parseWindow("2027-01-03 22:00 2027-01-01 10:00", utc));
        // Wrong token count
        assertThrows(IllegalArgumentException.class,
                () -> DateTimeParser.parseWindow("2027-01-01 10:00 2027-01-03", utc));
    }

    @Test
    void resolvesTimezoneWithFallbackOnInvalidValue() {
        List<String> warnings = new ArrayList<>();
        assertEquals(ZoneId.systemDefault(), DateTimeParser.resolveZoneId("", warnings::add));
        assertEquals(ZoneId.of("Asia/Tokyo"), DateTimeParser.resolveZoneId("Asia/Tokyo", warnings::add));
        assertTrue(warnings.isEmpty());

        ZoneId fallback = DateTimeParser.resolveZoneId("Mars/Olympus_Mons", warnings::add);
        assertEquals(ZoneId.systemDefault(), fallback);
        assertEquals(1, warnings.size());
    }
}
