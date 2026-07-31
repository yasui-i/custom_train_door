package cn.autoforged.custom_train_door.tarindoor;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts registry IDs written by the original dynamic tarindoor registry to
 * the stable slots used by server pack synchronization.
 *
 * <p>The conversion happens while save data is being decoded. Once the chunk,
 * contraption, item stack or block entity is saved again, Minecraft writes the
 * stable slot ID and the migration becomes permanent.</p>
 */
public final class LegacyTarindoorIdMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/TarindoorMigration");
    private static final Set<String> LOGGED_MAPPINGS = ConcurrentHashMap.newKeySet();
    private static final String BLOCK_STATE_NAME = "Name";
    private static final String REGISTRY_ID = "id";

    private LegacyTarindoorIdMigration() {
    }

    /** Migrate one serialized block state, including Create contraption palettes. */
    public static boolean migrateBlockState(CompoundTag tag) {
        return replaceDoorId(tag, BLOCK_STATE_NAME, false);
    }

    /** Migrate one serialized item stack. */
    public static boolean migrateItemStack(Tag tag) {
        return tag instanceof CompoundTag compound && replaceDoorId(compound, REGISTRY_ID, false);
    }

    /** Migrate one serialized tarindoor block entity type. */
    public static boolean migrateBlockEntity(CompoundTag tag) {
        return replaceDoorId(tag, REGISTRY_ID, true);
    }

    /**
     * Migrate the block-state palettes stored directly in a 1.21 chunk.
     * Structure and Create palettes are handled by {@link #migrateBlockState}.
     */
    public static int migrateChunkPalettes(CompoundTag chunk) {
        int migrated = migrateSectionList(chunk, "sections");
        // Kept for worlds that passed through an older serializer or conversion tool.
        migrated += migrateSectionList(chunk, "Sections");
        return migrated;
    }

    private static int migrateSectionList(CompoundTag chunk, String key) {
        if (!chunk.contains(key, Tag.TAG_LIST)) return 0;
        ListTag sections = chunk.getList(key, Tag.TAG_COMPOUND);
        int migrated = 0;
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            CompoundTag blockStates = section.getCompound("block_states");
            if (!blockStates.contains("palette", Tag.TAG_LIST)) continue;
            ListTag palette = blockStates.getList("palette", Tag.TAG_COMPOUND);
            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                if (migrateBlockState(palette.getCompound(paletteIndex))) migrated++;
            }
        }
        return migrated;
    }

    private static boolean replaceDoorId(CompoundTag tag, String key, boolean blockEntity) {
        if (!tag.contains(key, Tag.TAG_STRING)) return false;
        String oldId = tag.getString(key);
        String newId = blockEntity ? remapBlockEntityId(oldId) : remapDoorId(oldId);
        if (newId == null || newId.equals(oldId)) return false;
        tag.putString(key, newId);
        if (LOGGED_MAPPINGS.add(oldId + "->" + newId)) {
            LOGGER.info("Migrating legacy tarindoor ID {} -> {}", oldId, newId);
        }
        return true;
    }

    static String remapDoorId(String rawId) {
        return remap(rawId, "_door", "_door");
    }

    static String remapBlockEntityId(String rawId) {
        return remap(rawId, "_door_be", "_door_be");
    }

    private static String remap(String rawId, String oldSuffix, String newSuffix) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || !CustomTrainDoorMod.MODID.equals(id.getNamespace())
                || !id.getPath().endsWith(oldSuffix)) {
            return null;
        }

        String packId = id.getPath().substring(0, id.getPath().length() - oldSuffix.length());
        int slot = TarindoorRegistry.getSlot(packId);
        if (slot < 0) return null;
        return ResourceLocation.fromNamespaceAndPath(
                CustomTrainDoorMod.MODID,
                TarindoorRegistry.slotName(slot) + newSuffix
        ).toString();
    }
}
