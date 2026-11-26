package com.pengin0503.scheduleddimensionlock;

import com.pengin0503.scheduleddimensionlock.core.DateTimeParser;
import com.pengin0503.scheduleddimensionlock.core.DefaultScheduleParser;
import com.pengin0503.scheduleddimensionlock.core.DurationParser;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

public final class SdlConfig {
    private static final List<String> DEFAULT_THRESHOLDS = List.of("7d", "1d", "12h", "1h", "10m", "5m", "1m");

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> TIMEZONE;
    public static final ModConfigSpec.BooleanValue OP_BYPASS;
    public static final ModConfigSpec.BooleanValue EVACUATE_ON_LOCK;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_COUNTDOWN;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COUNTDOWN_THRESHOLDS;
    public static final ModConfigSpec.IntValue BLOCKED_MESSAGE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_SCHEDULES;

    private static volatile ZoneId cachedZoneId = ZoneId.systemDefault();
    private static volatile List<DurationParser.CountdownThreshold> cachedThresholds =
            DurationParser.parseThresholds(DEFAULT_THRESHOLDS, null);
    private static volatile List<DefaultScheduleParser.DefaultSchedule> cachedDefaultSchedules = List.of();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        TIMEZONE = builder
                .comment("Timezone ID used for parsing and displaying schedule date-times (e.g. 'Asia/Tokyo', 'UTC'). Blank uses the server system default.")
                .define("timezone", "");

        OP_BYPASS = builder
                .comment("If true, players with permission level 2 or higher bypass dimension locks. Never applies to non-player entities.")
                .define("opBypass", true);

        EVACUATE_ON_LOCK = builder
                .comment("If true, non-bypass players inside a dimension are evacuated to Overworld spawn when it locks.")
                .define("evacuateOnLock", true);

        ANNOUNCE_COUNTDOWN = builder
                .comment("If true, server-wide countdown announcements are broadcast before scheduled unlocks and locks.")
                .define("announceCountdown", true);

        COUNTDOWN_THRESHOLDS = builder
                .comment("Countdown thresholds before unlock or lock (e.g. 7d, 1d, 12h, 1h, 10m, 5m, 1m).")
                .defineListAllowEmpty("countdownThresholds", () -> DEFAULT_THRESHOLDS, () -> "", o -> o instanceof String);

        BLOCKED_MESSAGE_COOLDOWN_SECONDS = builder
                .comment("Minimum seconds between blocked-entry messages sent to the same player.")
                .defineInRange("blockedMessageCooldownSeconds", 5, 0, 3600);

        DEFAULT_SCHEDULES = builder
                .comment("Initial schedules imported only on first world load. Format: \"minecraft:the_nether;unlock=2027-01-01 10:00;lock=2027-01-03 22:00\" (unlock/lock each optional).")
                .defineListAllowEmpty("defaultSchedules", List::of, () -> "", o -> o instanceof String);

        SPEC = builder.build();
    }

    private SdlConfig() {
    }

    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshCache();
        }
    }

    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshCache();
        }
    }

    public static void refreshCache() {
        ZoneId zone = DateTimeParser.resolveZoneId(TIMEZONE.get(), ScheduledDimensionLock.LOGGER::warn);
        cachedZoneId = zone;

        List<String> rawThresholds = COUNTDOWN_THRESHOLDS.get().stream()
                .map(Object::toString)
                .toList();
        cachedThresholds = DurationParser.parseThresholds(rawThresholds, ScheduledDimensionLock.LOGGER::warn);

        List<String> rawDefaults = DEFAULT_SCHEDULES.get().stream()
                .map(Object::toString)
                .toList();
        cachedDefaultSchedules = DefaultScheduleParser.parseAll(rawDefaults, zone, ScheduledDimensionLock.LOGGER::warn);
    }

    public static ZoneId getZoneId() {
        return cachedZoneId;
    }

    public static boolean isOpBypass() {
        return OP_BYPASS.get();
    }

    public static boolean isEvacuateOnLock() {
        return EVACUATE_ON_LOCK.get();
    }

    public static boolean isAnnounceCountdown() {
        return ANNOUNCE_COUNTDOWN.get();
    }

    public static List<DurationParser.CountdownThreshold> getCountdownThresholds() {
        return cachedThresholds;
    }

    public static Duration getBlockedMessageCooldown() {
        return Duration.ofSeconds(BLOCKED_MESSAGE_COOLDOWN_SECONDS.get());
    }

    public static List<DefaultScheduleParser.DefaultSchedule> getDefaultSchedules() {
        return cachedDefaultSchedules;
    }
}
