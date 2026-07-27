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

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Central registry for all tarindoor dynamically-loaded doors.
 */
public class TarindoorRegistry {

    private static List<TarindoorDefinition> definitions = List.of();
    private static final Map<String, TarindoorDefinition> DEFINITIONS_BY_ID = new LinkedHashMap<>();
    private static final Map<Block, TarindoorDefinition> DEFINITIONS_BY_BLOCK = new IdentityHashMap<>();
    private static final Map<String, Path> ZIP_PATHS = new LinkedHashMap<>();

    // --- Deferred Registers ---
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CustomTrainDoorMod.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CustomTrainDoorMod.MODID);

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CustomTrainDoorMod.MODID);

    // --- Storage ---
    private static final List<DeferredBlock<?>> orderedBlocks = new ArrayList<>();
    private static final Map<String, Supplier<SoundEvent>> openSounds = new LinkedHashMap<>();
    private static final Map<String, Supplier<SoundEvent>> closeSounds = new LinkedHashMap<>();
    private static final Map<String, BlockSetType> blockSetTypes = new LinkedHashMap<>();
    private static final Map<String, DeferredHolder<BlockEntityType<?>, ?>> blockEntityTypes = new LinkedHashMap<>();

    /**
     * Load all definitions from tarindoor zips.
     * Must be called before any registerXxx() method.
     */
    public static void loadAll() {
        DEFINITIONS_BY_ID.clear();
        DEFINITIONS_BY_BLOCK.clear();
        ZIP_PATHS.clear();
        definitions = List.copyOf(TarindoorZipLoader.loadDefinitions());
        for (TarindoorDefinition def : definitions) {
            DEFINITIONS_BY_ID.put(def.id(), def);
        }
    }

    // --- Registration methods (called in mod constructor) ---

    public static void registerSounds(IEventBus bus) {
        for (TarindoorDefinition def : definitions) {
            String id = def.id();
            String seOpen = def.block().soundEventOpen();
            String seClose = def.block().soundEventClose();

            // If sound_event is specified, reuse the existing SoundEvent
            Supplier<SoundEvent> open;
            Supplier<SoundEvent> close;

            if (seOpen != null) {
                ResourceLocation rl = ResourceLocation.parse(seOpen);
                open = existingSoundOrFallback(rl, BlockSetType.IRON.doorOpen());
            } else {
                open = SOUND_EVENTS.register(
                        id + "_door_open",
                        () -> SoundEvent.createVariableRangeEvent(
                                ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, id + "_door_open"))
                );
            }

            if (seClose != null) {
                ResourceLocation rl = ResourceLocation.parse(seClose);
                close = existingSoundOrFallback(rl, BlockSetType.IRON.doorClose());
            } else {
                close = SOUND_EVENTS.register(
                        id + "_door_close",
                        () -> SoundEvent.createVariableRangeEvent(
                                ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, id + "_door_close"))
                );
            }

            openSounds.put(id, open);
            closeSounds.put(id, close);
        }
        SOUND_EVENTS.register(bus);
    }

    private static Supplier<SoundEvent> existingSoundOrFallback(ResourceLocation id, SoundEvent fallback) {
        return () -> {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
            return sound != null ? sound : fallback;
        };
    }

    @SuppressWarnings("unchecked")
    public static void registerBlockEntities(IEventBus bus) {
        for (TarindoorDefinition def : definitions) {
            String id = def.id();
            boolean isPhased = def.animation().type() == TarindoorDefinition.AnimationType.PHASED;

            DeferredBlock<?> deferredBlock = findDeferredBlock(id);
            if (deferredBlock == null) continue;

            DeferredHolder<BlockEntityType<?>, ?> holder = BLOCK_ENTITY_TYPES.register(
                    id + "_door_be",
                    () -> {
                        BlockEntityType.BlockEntitySupplier<?> supplier;
                        if (isPhased) {
                            supplier = (pos, state) -> new TarindoorPhasedBlockEntity(pos, state);
                        } else {
                            supplier = (pos, state) -> new TarindoorBlockEntity(pos, state);
                        }
                        return BlockEntityType.Builder.of(supplier, deferredBlock.get()).build(null);
                    }
            );
            blockEntityTypes.put(id, holder);
        }
        BLOCK_ENTITY_TYPES.register(bus);
    }

    public static void registerBlocks(IEventBus bus) {
        for (TarindoorDefinition def : definitions) {
            String id = def.id();

            DeferredBlock<TarindoorBlock> deferredBlock = BLOCKS.register(
                    id + "_door",
                    () -> {
                        BlockSetType blockSetType = createBlockSetType(def);
                        TarindoorBlock block = new TarindoorBlock(
                                BlockBehaviour.Properties.of()
                                        .mapColor(def.block().mapColor())
                                        .strength(def.block().hardness(), def.block().resistance())
                                        .sound(def.block().soundType())
                                        .noOcclusion()
                                        .pushReaction(PushReaction.DESTROY),
                                blockSetType,
                                def
                        );
                        DEFINITIONS_BY_BLOCK.put(block, def);
                        return block;
                    }
            );

            orderedBlocks.add(deferredBlock);
        }

        BLOCKS.register(bus);
    }

    private static BlockSetType createBlockSetType(TarindoorDefinition def) {
        String id = def.id();
        Supplier<SoundEvent> openSound = openSounds.get(id);
        Supplier<SoundEvent> closeSound = closeSounds.get(id);
        BlockSetType blockSetType = BlockSetType.register(new BlockSetType(
                CustomTrainDoorMod.MODID + ":" + id + "_door",
                true, true, true,
                BlockSetType.PressurePlateSensitivity.EVERYTHING,
                def.block().soundType(),
                closeSound != null && closeSound.get() != null
                        ? closeSound.get() : BlockSetType.IRON.doorClose(),
                openSound != null && openSound.get() != null
                        ? openSound.get() : BlockSetType.IRON.doorOpen(),
                SoundType.NETHERITE_BLOCK.getStepSound(),
                SoundType.NETHERITE_BLOCK.getFallSound(),
                SoundType.NETHERITE_BLOCK.getPlaceSound(),
                SoundType.NETHERITE_BLOCK.getHitSound(),
                SoundType.NETHERITE_BLOCK.getBreakSound(),
                SoundType.NETHERITE_BLOCK.getStepSound()
        ));
        blockSetTypes.put(id, blockSetType);
        return blockSetType;
    }

    // --- Lookup methods ---

    public static TarindoorDefinition getDefinition(String id) {
        return DEFINITIONS_BY_ID.get(id);
    }

    public static TarindoorDefinition getDefinition(Block block) {
        return DEFINITIONS_BY_BLOCK.get(block);
    }

    public static Collection<TarindoorDefinition> getDefinitions() {
        return definitions;
    }

    public static BlockSetType getBlockSetType(String id) {
        return blockSetTypes.get(id);
    }

    public static Supplier<SoundEvent> getOpenSound(String id) {
        return openSounds.get(id);
    }

    public static Supplier<SoundEvent> getCloseSound(String id) {
        return closeSounds.get(id);
    }

    public static BlockEntityType<?> getBlockEntityType(String id) {
        var holder = blockEntityTypes.get(id);
        return holder != null ? (BlockEntityType<?>) holder.get() : null;
    }

    public static List<DeferredBlock<?>> getOrderedBlocks() {
        return orderedBlocks;
    }

    public static Collection<DeferredHolder<BlockEntityType<?>, ?>> getBlockEntityHolders() {
        return blockEntityTypes.values();
    }

    @Nullable
    public static Path getZipPath(TarindoorDefinition def) {
        return ZIP_PATHS.get(def.id());
    }

    static void storeZipPath(String id, Path zipPath) {
        ZIP_PATHS.put(id, zipPath);
    }

    private static DeferredBlock<?> findDeferredBlock(String id) {
        for (DeferredBlock<?> db : orderedBlocks) {
            if (db.getId().getPath().equals(id + "_door")) {
                return db;
            }
        }
        return null;
    }
}
