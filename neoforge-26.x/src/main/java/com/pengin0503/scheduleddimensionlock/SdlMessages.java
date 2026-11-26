package com.pengin0503.scheduleddimensionlock;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

/**
 * Builds translatable components with English fallbacks so vanilla clients without the mod
 * still see readable text while clients with the mod use en_us / ja_jp lang files.
 */
public final class SdlMessages {
    private static final Map<String, String> FALLBACKS = Map.ofEntries(
            Map.entry("message.scheduleddimensionlock.blocked", "%s is currently locked."),
            Map.entry("message.scheduleddimensionlock.blocked_until", "%s is currently locked. Unlocks in %s."),
            Map.entry("message.scheduleddimensionlock.evacuated", "%s is locked. You have been returned to the Overworld spawn."),
            Map.entry("message.scheduleddimensionlock.announce.unlocked", "[Scheduled Dimension Lock] %s is now unlocked!"),
            Map.entry("message.scheduleddimensionlock.announce.locked", "[Scheduled Dimension Lock] %s has been locked."),
            Map.entry("message.scheduleddimensionlock.announce.countdown_unlock", "[Scheduled Dimension Lock] %s will unlock in %s."),
            Map.entry("message.scheduleddimensionlock.announce.countdown_lock", "[Scheduled Dimension Lock] %s will lock in %s."),

            Map.entry("command.scheduleddimensionlock.status.header", "=== Dimension Lock Status (TZ: %s) ==="),
            Map.entry("command.scheduleddimensionlock.status.empty", "No dimensions are currently locked or scheduled."),
            Map.entry("command.scheduleddimensionlock.status.state.locked", "LOCKED"),
            Map.entry("command.scheduleddimensionlock.status.state.unlocked", "UNLOCKED"),
            Map.entry("command.scheduleddimensionlock.status.line", "%s: %s"),
            Map.entry("command.scheduleddimensionlock.status.line_unlock", "%s: %s (unlocks at %s, in %s)"),
            Map.entry("command.scheduleddimensionlock.status.line_lock", "%s: %s (locks at %s, in %s)"),
            Map.entry("command.scheduleddimensionlock.status.detail.header", "=== Status: %s (TZ: %s) ==="),
            Map.entry("command.scheduleddimensionlock.status.detail.state", "State: %s"),
            Map.entry("command.scheduleddimensionlock.status.detail.unlock_at", "Scheduled Unlock: %s (in %s)"),
            Map.entry("command.scheduleddimensionlock.status.detail.lock_at", "Scheduled Lock: %s (in %s)"),
            Map.entry("command.scheduleddimensionlock.status.detail.none", "No scheduled transitions."),

            Map.entry("command.scheduleddimensionlock.lock.success", "Locked %s (evacuated %s player(s))."),
            Map.entry("command.scheduleddimensionlock.unlock.success", "Unlocked %s."),
            Map.entry("command.scheduleddimensionlock.unlock.schedule_retained", "Note: existing schedule was not cleared. Use /sdl schedule %s clear to remove it."),
            Map.entry("command.scheduleddimensionlock.schedule.unlock_set", "Scheduled %s to unlock at %s (in %s)."),
            Map.entry("command.scheduleddimensionlock.schedule.lock_set", "Scheduled %s to lock at %s (in %s)."),
            Map.entry("command.scheduleddimensionlock.schedule.window_set", "Scheduled open window for %s: %s -> %s."),
            Map.entry("command.scheduleddimensionlock.schedule.cleared", "Cleared all schedules for %s."),
            Map.entry("command.scheduleddimensionlock.schedule.nothing_to_clear", "No schedules were set for %s."),
            Map.entry("command.scheduleddimensionlock.evacuate.success", "Evacuated %s player(s) from %s to the Overworld spawn."),
            Map.entry("command.scheduleddimensionlock.reload.success", "Reloaded Scheduled Dimension Lock configuration (TZ: %s)."),

            Map.entry("command.scheduleddimensionlock.error.overworld", "The Overworld (minecraft:overworld) cannot be locked or scheduled."),
            Map.entry("command.scheduleddimensionlock.error.invalid_argument", "Invalid argument: %s")
    );

    private SdlMessages() {
    }

    public static MutableComponent text(String key, Object... args) {
        String fallback = FALLBACKS.getOrDefault(key, key);
        return Component.translatableWithFallback(key, fallback, args);
    }
}
