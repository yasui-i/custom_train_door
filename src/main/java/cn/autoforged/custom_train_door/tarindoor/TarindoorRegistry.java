package cn.autoforged.custom_train_door.tarindoor;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlockEntity;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorPhasedBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Single-block registry for tarindoor doors. Door identity is stored in
 * BlockEntity NBT rather than pre-registered block slots. This allows an
 * unlimited number of door types without bloating the block registry.
 */
public final class TarindoorRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");

    private static volatile List<TarindoorDefinition> definitions = List.of();
    private static volatile Map<String, TarindoorDefinition> definitionsById = Map.of();
    private static final Map<String, Path> ZIP_PATHS = new LinkedHashMap<>();

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CustomTrainDoorMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CustomTrainDoorMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CustomTrainDoorMod.MODID);

    // Single door block and BlockEntity type
    public static final DeferredBlock<TarindoorBlock> TARINDOOR_DOOR;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TarindoorBlockEntity>> TARINDOOR_BE;
    private static final BlockSetType TARINDOOR_SET_TYPE;

    // Default sounds — doors can override via SoundEvent references
    private static final Supplier<SoundEvent> DEFAULT_OPEN_SOUND;
    private static final Supplier<SoundEvent> DEFAULT_CLOSE_SOUND;

    static {
        TARINDOOR_SET_TYPE = BlockSetType.register(new BlockSetType(
                CustomTrainDoorMod.MODID + ":tarindoor_door",
                true, true, true, BlockSetType.PressurePlateSensitivity.EVERYTHING,
                SoundType.NETHERITE_BLOCK,
                SoundType.NETHERITE_BLOCK.getStepSound(), SoundType.NETHERITE_BLOCK.getFallSound(),
                SoundType.NETHERITE_BLOCK.getPlaceSound(), SoundType.NETHERITE_BLOCK.getHitSound(),
                SoundType.NETHERITE_BLOCK.getBreakSound(), SoundType.NETHERITE_BLOCK.getStepSound(),
                SoundType.NETHERITE_BLOCK.getStepSound(), SoundType.NETHERITE_BLOCK.getFallSound()));

        DEFAULT_OPEN_SOUND = SOUND_EVENTS.register("tarindoor_door_open",
                () -> SoundEvent.createVariableRangeEvent(regId("tarindoor_door_open")));
        DEFAULT_CLOSE_SOUND = SOUND_EVENTS.register("tarindoor_door_close",
                () -> SoundEvent.createVariableRangeEvent(regId("tarindoor_door_close")));

        TARINDOOR_DOOR = BLOCKS.register("tarindoor_door", () -> new TarindoorBlock(
                BlockBehaviour.Properties.of()
                        .strength(5.0f, 6.0f).sound(SoundType.NETHERITE_BLOCK)
                        .noOcclusion().pushReaction(PushReaction.DESTROY),
                TARINDOOR_SET_TYPE));

        TARINDOOR_BE = BLOCK_ENTITY_TYPES.register("tarindoor_door_be",
                () -> BlockEntityType.Builder.of((pos, state) -> {
                    // Determine animation type from definition at creation time
                    // Default to lerped if no definition loaded yet
                    return new TarindoorBlockEntity(pos, state);
                }, TARINDOOR_DOOR.get()).build(null));
    }

    private TarindoorRegistry() {}

    // --- Definition loading ---

    public static synchronized void loadAll() {
        clearRuntimeDefinitions();
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions();
        applyDefinitions(loaded);
    }

    public static synchronized void loadSynced(Path directory, Map<String, Integer> ignoredSlots) {
        // Try loading first, only clear+apply if we got definitions
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions(directory, false, true);
        if (loaded.isEmpty()) {
            LOGGER.warn("loadSynced({}): no doors found", directory.getFileName());
            return; // keep current definitions
        }
        clearRuntimeDefinitions();
        applyDefinitions(loaded);
    }

    static synchronized void storeZipPath(String id, Path zipPath) { ZIP_PATHS.put(id, zipPath); }

    private static void clearRuntimeDefinitions() {
        definitions = List.of();
        definitionsById = Map.of();
    }

    private static void applyDefinitions(List<TarindoorDefinition> loaded) {
        Map<String, TarindoorDefinition> byId = new LinkedHashMap<>();
        for (TarindoorDefinition def : loaded) {
            if (byId.containsKey(def.id())) {
                LOGGER.warn("Ignoring duplicate door '{}'", def.id());
                continue;
            }
            byId.put(def.id(), def);
        }
        definitions = List.copyOf(byId.values());
        definitionsById = Map.copyOf(byId);
        LOGGER.info("Activated {} tarindoor door(s)", definitions.size());
    }

    // --- Registration ---

    public static void registerSounds(IEventBus bus) {
        SOUND_EVENTS.register(bus);
    }

    public static void registerBlocks(IEventBus bus) {
        BLOCKS.register(bus);
    }

    public static void registerBlockEntities(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }

    // --- Lookup ---

    @Nullable public static TarindoorDefinition getDefinition(String id) { return definitionsById.get(id); }
    @Nullable public static TarindoorDefinition getDefinition(Block block) {
        return null; // Definition is now retrieved from BlockEntity, not block type
    }

    public static Collection<TarindoorDefinition> getDefinitions() { return definitions; }
    public static BlockSetType getBlockSetType() { return TARINDOOR_SET_TYPE; }

    /** Deterministic variant index (0..N) for item model overrides & block state. */
    public static int getVariantIndex(String doorId) {
        int index = 0;
        for (TarindoorDefinition def : definitions) {
            if (def.id().equals(doorId)) return index;
            index++;
        }
        return 0; // fallback
    }

    @Nullable public static Supplier<SoundEvent> getOpenSound(String id) {
        TarindoorDefinition def = getDefinition(id); if (def == null) return DEFAULT_OPEN_SOUND;
        if (def.block().soundEventOpen() != null) {
            ResourceLocation rl = ResourceLocation.tryParse(def.block().soundEventOpen());
            if (rl != null) {
                SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(rl);
                if (se != null) return () -> se;
            }
        }
        return DEFAULT_OPEN_SOUND;
    }

    @Nullable public static Supplier<SoundEvent> getCloseSound(String id) {
        TarindoorDefinition def = getDefinition(id); if (def == null) return DEFAULT_CLOSE_SOUND;
        if (def.block().soundEventClose() != null) {
            ResourceLocation rl = ResourceLocation.tryParse(def.block().soundEventClose());
            if (rl != null) {
                SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(rl);
                if (se != null) return () -> se;
            }
        }
        return DEFAULT_CLOSE_SOUND;
    }

    @Nullable public static Path getZipPath(TarindoorDefinition def) { return ZIP_PATHS.get(def.id()); }
    public static DeferredBlock<TarindoorBlock> getDoorBlock() { return TARINDOOR_DOOR; }
    public static DeferredHolder<BlockEntityType<?>, ?> getDoorBlockEntity() { return TARINDOOR_BE; }

    private static ResourceLocation regId(String path) {
        return ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, path);
    }
}
