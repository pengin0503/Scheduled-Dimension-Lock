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
        modContainer.registerConfig(ModConfig.Type.SERVER, SdlConfig.SPEC);
        modEventBus.addListener(SdlConfig::onConfigLoad);
        modEventBus.addListener(SdlConfig::onConfigReload);

        NeoForge.EVENT_BUS.register(SdlEventHandler.class);
        NeoForge.EVENT_BUS.addListener(SdlCommands::onRegisterCommands);
    }
}
