package com.pengin0503.scheduleddimensionlock;

import com.pengin0503.scheduleddimensionlock.core.DimensionState;
import com.pengin0503.scheduleddimensionlock.core.ScheduleManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class SdlSavedData extends SavedData {
    public static final String DATA_NAME = ScheduledDimensionLock.MOD_ID;

    private static final String TAG_DEFAULTS_IMPORTED = "defaultsImported";
    private static final String TAG_DIMENSIONS = "dimensions";
    private static final String TAG_LOCKED = "locked";
    private static final String TAG_UNLOCK_AT = "unlockAt";
    private static final String TAG_LOCK_AT = "lockAt";
    private static final String TAG_ANNOUNCED = "announcedThresholds";

    private static final SavedData.Factory<SdlSavedData> FACTORY =
            new SavedData.Factory<>(SdlSavedData::new, SdlSavedData::load);

    private final ScheduleManager manager = new ScheduleManager();
    private boolean defaultsImported = false;

    public SdlSavedData() {
    }

    public static SdlSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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

    public static SdlSavedData load(CompoundTag root, HolderLookup.Provider registries) {
        SdlSavedData data = new SdlSavedData();
        data.defaultsImported = root.getBoolean(TAG_DEFAULTS_IMPORTED);

        if (!root.contains(TAG_DIMENSIONS, Tag.TAG_COMPOUND)) {
            return data;
        }
        CompoundTag dimensionsTag = root.getCompound(TAG_DIMENSIONS);
        for (String dimId : dimensionsTag.getAllKeys()) {
            if (!dimensionsTag.contains(dimId, Tag.TAG_COMPOUND)) {
                ScheduledDimensionLock.LOGGER.warn("Skipping malformed SavedData entry for dimension '{}': not a compound tag", dimId);
                continue;
            }
            CompoundTag entry = dimensionsTag.getCompound(dimId);
            try {
                boolean locked = entry.getBoolean(TAG_LOCKED);
                Instant unlockAt = readTimestamp(entry, TAG_UNLOCK_AT, dimId);
                Instant lockAt = readTimestamp(entry, TAG_LOCK_AT, dimId);

                Set<String> announced = new LinkedHashSet<>();
                if (entry.contains(TAG_ANNOUNCED, Tag.TAG_LIST)) {
                    ListTag list = entry.getList(TAG_ANNOUNCED, Tag.TAG_STRING);
                    for (int i = 0; i < list.size(); i++) {
                        String key = list.getString(i);
                        if (!key.isBlank()) {
                            announced.add(key);
                        }
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
        if (!entry.contains(key, Tag.TAG_LONG)) {
            return null;
        }
        long epochSec = entry.getLong(key);
        if (epochSec == -1L) {
            return null;
        }
        if (epochSec < 0L) {
            throw new IllegalArgumentException("Invalid negative timestamp " + epochSec + " for " + key + " on " + dimId);
        }
        return Instant.ofEpochSecond(epochSec);
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
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
