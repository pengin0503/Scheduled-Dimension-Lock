package com.pengin0503.scheduleddimensionlock;

import com.pengin0503.scheduleddimensionlock.core.DimensionState;
import com.pengin0503.scheduleddimensionlock.core.ScheduleManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.util.Optional;

public final class SdlEventHandler {
    private static int tickCounter = 0;

    private SdlEventHandler() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        tickCounter = 0;
        SdlService.getBlockedCooldown().clear();
        SdlService.handleServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        tickCounter = 0;
        SdlService.getBlockedCooldown().clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < 20) {
            return;
        }
        tickCounter = 0;
        SdlService.runPeriodicCheck(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        MinecraftServer server = event.getEntity().level().getServer();
        if (server == null) {
            return;
        }
        String targetDim = event.getDimension().identifier().toString();
        ScheduleManager manager = SdlSavedData.get(server).getManager();
        if (!manager.isLocked(targetDim)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (SdlService.isBypassPlayer(player)) {
                return;
            }
            event.setCanceled(true);
            Optional<DimensionState> state = manager.getState(targetDim);
            Instant unlockAt = state.map(DimensionState::getUnlockAt).orElse(null);
            SdlService.sendBlockedMessage(player, targetDim, unlockAt);
        } else {
            // Non-player entities never bypass
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        ScheduleManager manager = SdlSavedData.get(server).getManager();
        SdlService.checkAndEvacuatePlayer(server, player, manager);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SdlService.getBlockedCooldown().remove(event.getEntity().getUUID());
    }
}
