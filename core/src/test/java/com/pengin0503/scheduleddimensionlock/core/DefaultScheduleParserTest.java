package com.pengin0503.scheduleddimensionlock.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultScheduleParserTest {

    @Test
    void parsesValidDefaultScheduleEntries() {
        ZoneId utc = ZoneId.of("UTC");
        List<String> warnings = new ArrayList<>();
        List<DefaultScheduleParser.DefaultSchedule> parsed = DefaultScheduleParser.parseAll(List.of(
                "minecraft:the_nether;unlock=2027-01-01 10:00;lock=2027-01-03 22:00",
                "minecraft:the_end;unlock=2027-01-05 18:00",
                "modid:custom;lock=2027-01-06 20:00"
        ), utc, warnings::add);

        assertTrue(warnings.isEmpty());
        assertEquals(3, parsed.size());

        assertEquals("minecraft:the_nether", parsed.get(0).dimensionId());
        assertTrue(parsed.get(0).locked());
        assertEquals(Instant.parse("2027-01-01T10:00:00Z"), parsed.get(0).unlockAt());
        assertEquals(Instant.parse("2027-01-03T22:00:00Z"), parsed.get(0).lockAt());

        assertEquals("minecraft:the_end", parsed.get(1).dimensionId());
        assertTrue(parsed.get(1).locked());
        assertNull(parsed.get(1).lockAt());

        assertEquals("modid:custom", parsed.get(2).dimensionId());
        assertFalse(parsed.get(2).locked());
        assertNull(parsed.get(2).unlockAt());
    }

    @Test
    void skipsInvalidEntriesWithWarnings() {
        ZoneId utc = ZoneId.of("UTC");
        List<String> warnings = new ArrayList<>();
        List<DefaultScheduleParser.DefaultSchedule> parsed = DefaultScheduleParser.parseAll(List.of(
                "minecraft:overworld;unlock=2027-01-01 10:00",
                "minecraft:the_nether;unlock=2027-01-05 10:00;lock=2027-01-01 10:00",
                "minecraft:the_end;badkey=123",
                "minecraft:the_end"
        ), utc, warnings::add);

        assertTrue(parsed.isEmpty());
        assertEquals(4, warnings.size());
    }
}
