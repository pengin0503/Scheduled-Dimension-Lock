package com.pengin0503.scheduleddimensionlock;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ScheduledDimensionLock.MOD_ID)
public final class ScheduledDimensionLock {
    public static final String MOD_ID = "scheduleddimensionlock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ScheduledDimensionLock(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(resolveServerConfigType(), SdlConfig.SPEC, MOD_ID + "-server.toml");
        modEventBus.addListener(SdlConfig::onConfigLoad);
        modEventBus.addListener(SdlConfig::onConfigReload);

        NeoForge.EVENT_BUS.register(SdlEventHandler.class);
        NeoForge.EVENT_BUS.addListener(SdlCommands::onRegisterCommands);
    }

    /**
     * Resolves the per-world server config type across FML 11 (26.1.2/26.2: SERVER)
     * and FML 12 (26.3: renamed to SYNCED) so a single 26.x JAR runs on all 26.x releases.
     */
    private static ModConfig.Type resolveServerConfigType() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if ("SERVER".equals(type.name())) {
                return type;
            }
        }
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if ("SYNCED".equals(type.name())) {
                return type;
            }
        }
        throw new IllegalStateException("Neither SERVER nor SYNCED ModConfig.Type is available");
    }
}
