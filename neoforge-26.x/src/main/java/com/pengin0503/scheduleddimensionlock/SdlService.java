package com.pengin0503.scheduleddimensionlock;

import com.pengin0503.scheduleddimensionlock.core.CooldownTracker;
import com.pengin0503.scheduleddimensionlock.core.DurationParser;
import com.pengin0503.scheduleddimensionlock.core.ScheduleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelData;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SdlService {
    private static final CooldownTracker BLOCKED_COOLDOWN = new CooldownTracker();

    private SdlService() {
    }

    public static CooldownTracker getBlockedCooldown() {
        return BLOCKED_COOLDOWN;
    }

    public static boolean isBypassPlayer(ServerPlayer player) {
        return SdlConfig.isOpBypass() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void handleServerStarted(MinecraftServer server) {
        SdlConfig.refreshCache();
        SdlSavedData data = SdlSavedData.get(server);
        if (!data.isDefaultsImported()) {
            data.getManager().importDefaultSchedules(SdlConfig.getDefaultSchedules());
            data.setDefaultsImported(true);
        }
        // Immediate catch-up evaluation on startup
        runSchedulerStep(server, Instant.now());
    }

    public static void runPeriodicCheck(MinecraftServer server) {
        Instant now = Instant.now();
        runSchedulerStep(server, now);
        enforceSecondaryDefense(server);
    }

    public static void runSchedulerStep(MinecraftServer server, Instant now) {
        SdlSavedData data = SdlSavedData.get(server);
        ScheduleManager.TickResult result = data.getManager().evaluate(
                now,
                SdlConfig.isAnnounceCountdown(),
                SdlConfig.getCountdownThresholds()
        );

        if (result.dirty()) {
            data.setDirty();
        }

        for (ScheduleManager.TransitionEvent event : result.transitions()) {
            if (event.type() == ScheduleManager.TransitionType.UNLOCKED) {
                server.getPlayerList().broadcastSystemMessage(
                        SdlMessages.text("message.scheduleddimensionlock.announce.unlocked", event.dimensionId())
                                .withStyle(ChatFormatting.GREEN),
                        false
                );
            } else {
                server.getPlayerList().broadcastSystemMessage(
                        SdlMessages.text("message.scheduleddimensionlock.announce.locked", event.dimensionId())
                                .withStyle(ChatFormatting.RED),
                        false
                );
                if (SdlConfig.isEvacuateOnLock()) {
                    evacuateDimension(server, event.dimensionId());
                }
            }
        }

        for (ScheduleManager.CountdownEvent countdown : result.countdowns()) {
            String key = countdown.targetTransition() == ScheduleManager.TransitionType.UNLOCKED
                    ? "message.scheduleddimensionlock.announce.countdown_unlock"
                    : "message.scheduleddimensionlock.announce.countdown_lock";
            ChatFormatting color = countdown.targetTransition() == ScheduleManager.TransitionType.UNLOCKED
                    ? ChatFormatting.YELLOW
                    : ChatFormatting.GOLD;
            server.getPlayerList().broadcastSystemMessage(
                    SdlMessages.text(key, countdown.dimensionId(), DurationParser.format(countdown.remaining()))
                            .withStyle(color),
                    false
            );
        }
    }

    public static void enforceSecondaryDefense(MinecraftServer server) {
        SdlSavedData data = SdlSavedData.get(server);
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            checkAndEvacuatePlayer(server, player, data.getManager());
        }
    }

    public static void checkAndEvacuatePlayer(MinecraftServer server, ServerPlayer player, ScheduleManager manager) {
        // Login, respawn and the periodic sweep all find players already inside a dimension.
        // With evacuation disabled they may stay; entry is still blocked by the travel handler.
        if (!SdlConfig.isEvacuateOnLock()) {
            return;
        }
        String currentDim = player.level().dimension().identifier().toString();
        if (manager.isLocked(currentDim) && !isBypassPlayer(player)) {
            evacuatePlayer(server, player, currentDim);
        }
    }

    public static int evacuateDimension(MinecraftServer server, String dimensionId) {
        if (ScheduleManager.OVERWORLD_ID.equals(dimensionId)) {
            return 0;
        }
        int count = 0;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer player : players) {
            String currentDim = player.level().dimension().identifier().toString();
            if (dimensionId.equals(currentDim) && !isBypassPlayer(player)) {
                evacuatePlayer(server, player, dimensionId);
                count++;
            }
        }
        return count;
    }

    public static void evacuatePlayer(MinecraftServer server, ServerPlayer player, String fromDimensionId) {
        if (player.isDeadOrDying()) {
            return;
        }
        ServerLevel overworld = server.overworld();
        LevelData.RespawnData respawnData = overworld.getRespawnData();
        BlockPos spawnPos = findSafeSpawn(overworld, respawnData.pos());
        player.teleportTo(
                overworld,
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.1,
                spawnPos.getZ() + 0.5,
                Set.of(),
                respawnData.yaw(),
                respawnData.pitch(),
                true
        );
        player.sendSystemMessage(
                SdlMessages.text("message.scheduleddimensionlock.evacuated", fromDimensionId)
                        .withStyle(ChatFormatting.RED)
        );
    }

    private static BlockPos findSafeSpawn(ServerLevel overworld, BlockPos sharedSpawn) {
        for (int offset = 0; offset <= 16; offset++) {
            BlockPos candidate = sharedSpawn.above(offset);
            if (!overworld.getBlockState(candidate).isSolid()
                    && !overworld.getBlockState(candidate.above()).isSolid()
                    && overworld.getBlockState(candidate.below()).isSolid()) {
                return candidate;
            }
        }
        return sharedSpawn;
    }

    public static void sendBlockedMessage(ServerPlayer player, String dimensionId, Instant unlockAt) {
        Instant now = Instant.now();
        if (!BLOCKED_COOLDOWN.shouldNotify(player.getUUID(), now, SdlConfig.getBlockedMessageCooldown())) {
            return;
        }
        if (unlockAt != null && unlockAt.isAfter(now)) {
            Duration remaining = Duration.between(now, unlockAt);
            player.sendSystemMessage(
                    SdlMessages.text("message.scheduleddimensionlock.blocked_until",
                            dimensionId,
                            DurationParser.format(remaining))
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            player.sendSystemMessage(
                    SdlMessages.text("message.scheduleddimensionlock.blocked", dimensionId)
                            .withStyle(ChatFormatting.RED)
            );
        }
    }
}
