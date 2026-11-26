package com.pengin0503.scheduleddimensionlock;

import com.mojang.serialization.Codec;
import com.pengin0503.scheduleddimensionlock.core.DimensionState;
import com.pengin0503.scheduleddimensionlock.core.ScheduleManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SdlSavedData extends SavedData {
    private static final String TAG_DEFAULTS_IMPORTED = "defaultsImported";
    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_LOCKED = "locked";
    private static final String TAG_UNLOCK_AT = "unlockAt";
    private static final String TAG_LOCK_AT = "lockAt";
    private static final String TAG_ANNOUNCED = "announcedThresholds";

    public static final Codec<SdlSavedData> CODEC = CompoundTag.CODEC.xmap(
            SdlSavedData::fromTag,
            SdlSavedData::toTag
    );

    public static final SavedDataType<SdlSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ScheduledDimensionLock.MOD_ID, ScheduledDimensionLock.MOD_ID),
            SdlSavedData::new,
            CODEC
    );

    private final ScheduleManager manager = new ScheduleManager();
    private boolean defaultsImported = false;

    public SdlSavedData() {
    }

    public static SdlSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public ScheduleManager getManager() {
        return manager;
    }

    public boolean isDefaultsImported() {
        return defaultsImported;
    }

    public void setDefaultsImported(boolean defaultsImported) {
        this.defaultsImported = defaultsImported;
        setDirty();
    }

    public static SdlSavedData fromTag(CompoundTag root) {
        SdlSavedData data = new SdlSavedData();
        data.defaultsImported = root.getBooleanOr(TAG_DEFAULTS_IMPORTED, false);

        Optional<CompoundTag> dimensionsOpt = root.getCompound(TAG_DIMENSIONS);
        if (dimensionsOpt.isEmpty()) {
            return data;
        }
        CompoundTag dimensionsTag = dimensionsOpt.get();
        for (String dimId : dimensionsTag.keySet()) {
            Optional<CompoundTag> entryOpt = dimensionsTag.getCompound(dimId);
            if (entryOpt.isEmpty()) {
                ScheduledDimensionLock.LOGGER.warn("Skipping malformed SavedData entry for dimension '{}': not a compound tag", dimId);
                continue;
            }
            CompoundTag entry = entryOpt.get();
            try {
                boolean locked = entry.getBooleanOr(TAG_LOCKED, false);
                Instant unlockAt = readTimestamp(entry, TAG_UNLOCK_AT, dimId);
                Instant lockAt = readTimestamp(entry, TAG_LOCK_AT, dimId);

                Set<String> announced = new LinkedHashSet<>();
                Optional<ListTag> listOpt = entry.getList(TAG_ANNOUNCED);
                if (listOpt.isPresent()) {
                    ListTag list = listOpt.get();
                    for (int i = 0; i < list.size(); i++) {
                        list.getString(i).filter(s -> !s.isBlank()).ifPresent(announced::add);
                    }
                }

                DimensionState state = new DimensionState(locked, unlockAt, lockAt, announced);
                data.manager.putLoadedState(dimId, state);
            } catch (Exception e) {
                ScheduledDimensionLock.LOGGER.warn("Discarding invalid SavedData entry for dimension '{}': {}", dimId, e.getMessage());
            }
        }
        return data;
    }

    private static Instant readTimestamp(CompoundTag entry, String key, String dimId) {
        Optional<Long> epochOpt = entry.getLong(key);
        if (epochOpt.isEmpty()) {
            return null;
        }
        long epochSec = epochOpt.get();
        if (epochSec == -1L) {
            return null;
        }
        if (epochSec < 0L) {
            throw new IllegalArgumentException("Invalid negative timestamp " + epochSec + " for " + key + " on " + dimId);
        }
        return Instant.ofEpochSecond(epochSec);
    }

    public CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        root.putBoolean(TAG_DEFAULTS_IMPORTED, defaultsImported);

        CompoundTag dimensionsTag = new CompoundTag();
        for (Map.Entry<String, DimensionState> entry : manager.getAllStates().entrySet()) {
            DimensionState state = entry.getValue();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(TAG_LOCKED, state.isLocked());
            tag.putLong(TAG_UNLOCK_AT, state.getUnlockAt() != null ? state.getUnlockAt().getEpochSecond() : -1L);
            tag.putLong(TAG_LOCK_AT, state.getLockAt() != null ? state.getLockAt().getEpochSecond() : -1L);

            ListTag announcedList = new ListTag();
            for (String thresholdKey : state.getAnnouncedThresholds()) {
                announcedList.add(StringTag.valueOf(thresholdKey));
            }
            tag.put(TAG_ANNOUNCED, announcedList);
            dimensionsTag.put(entry.getKey(), tag);
        }
        root.put(TAG_DIMENSIONS, dimensionsTag);
        return root;
    }
}
