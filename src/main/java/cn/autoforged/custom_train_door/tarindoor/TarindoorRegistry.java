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
 * Fixed registry slots pre-allocated on every installation to keep
 * NeoForge registry identical across client and server. A large slot
 * count ensures virtually unlimited custom door packs.
 */
public final class TarindoorRegistry {
    public static final int MAX_SLOTS = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");

    private static volatile List<TarindoorDefinition> definitions = List.of();
    private static volatile Map<String, TarindoorDefinition> definitionsById = Map.of();
    private static volatile Map<Integer, TarindoorDefinition> definitionsBySlot = Map.of();
    private static volatile Map<String, Integer> slotsById = Map.of();
    private static final Map<String, Path> ZIP_PATHS = new LinkedHashMap<>();

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CustomTrainDoorMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CustomTrainDoorMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CustomTrainDoorMod.MODID);

    private static final List<DeferredBlock<TarindoorBlock>> ORDERED_BLOCKS = new ArrayList<>();
    private static final List<Supplier<SoundEvent>> OPEN_SOUNDS = new ArrayList<>();
    private static final List<Supplier<SoundEvent>> CLOSE_SOUNDS = new ArrayList<>();
    private static final List<BlockSetType> BLOCK_SET_TYPES = new ArrayList<>();
    private static final List<DeferredHolder<BlockEntityType<?>, ?>> BLOCK_ENTITY_HOLDERS = new ArrayList<>();

    private TarindoorRegistry() {}

    public static synchronized void loadAll() {
        clearRuntimeDefinitions();
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions();
        applyDefinitions(loaded, assignSlots(loaded));
    }

    public static synchronized void loadSynced(Path directory, Map<String, Integer> authoritativeSlots) {
        clearRuntimeDefinitions();
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions(directory, false);
        applyDefinitions(loaded, authoritativeSlots);
    }

    static synchronized void storeZipPath(String id, Path zipPath) { ZIP_PATHS.put(id, zipPath); }

    private static void clearRuntimeDefinitions() {
        definitions = List.of();
        definitionsById = Map.of();
        definitionsBySlot = Map.of();
        slotsById = Map.of();
    }

    private static void applyDefinitions(List<TarindoorDefinition> loaded, Map<String, Integer> assignedSlots) {
        Map<String, TarindoorDefinition> byId = new LinkedHashMap<>();
        Map<Integer, TarindoorDefinition> bySlot = new TreeMap<>();
        Map<String, Integer> validSlots = new LinkedHashMap<>();

        for (TarindoorDefinition def : loaded) {
            Integer slot = assignedSlots.get(def.id());
            if (slot == null || slot < 0 || slot >= MAX_SLOTS || bySlot.containsKey(slot)) {
                LOGGER.warn("Ignoring door '{}' — invalid or occupied slot: {}", def.id(), slot);
                continue;
            }
            byId.put(def.id(), def);
            bySlot.put(slot, def);
            validSlots.put(def.id(), slot);
        }

        definitions = List.copyOf(byId.values());
        definitionsById = Map.copyOf(byId);
        definitionsBySlot = Map.copyOf(bySlot);
        slotsById = Map.copyOf(validSlots);
        LOGGER.info("Activated {} tarindoor door(s) in {} registry slots", definitions.size(), MAX_SLOTS);
    }

    private static Map<String, Integer> assignSlots(List<TarindoorDefinition> loaded) {
        Map<String, Integer> result = new LinkedHashMap<>();
        // Sort by ID for deterministic slot assignment across server and client
        loaded.stream()
                .sorted(Comparator.comparing(TarindoorDefinition::id))
                .forEach(def -> result.put(def.id(), freeSlot(result.values())));
        return result;
    }

    private static int freeSlot(Collection<Integer> used) {
        for (int s = 0; s < MAX_SLOTS; s++) if (!used.contains(s)) return s;
        return -1;
    }

    // --- Registration ---

    public static void registerSounds(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            String name = slotName(slot);
            TarindoorDefinition def = getDefinition(slot);
            boolean sharedOpen = def != null && def.block().soundEventOpen() != null;
            boolean sharedClose = def != null && def.block().soundEventClose() != null;
            OPEN_SOUNDS.add(SOUND_EVENTS.register(name + "_door_open", () -> SoundEvent.createVariableRangeEvent(
                    sharedOpen ? ResourceLocation.parse(def.block().soundEventOpen()) : regId(name + "_door_open"))));
            CLOSE_SOUNDS.add(SOUND_EVENTS.register(name + "_door_close", () -> SoundEvent.createVariableRangeEvent(
                    sharedClose ? ResourceLocation.parse(def.block().soundEventClose()) : regId(name + "_door_close"))));
        }
        SOUND_EVENTS.register(bus);
    }

    @SuppressWarnings("unchecked")
    public static void registerBlockEntities(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            final int s = slot;
            BLOCK_ENTITY_HOLDERS.add(BLOCK_ENTITY_TYPES.register(slotName(slot) + "_door_be",
                    () -> BlockEntityType.Builder.of((pos, state) -> {
                        TarindoorDefinition def = getDefinition(s);
                        if (def != null && def.animation().type() == TarindoorDefinition.AnimationType.PHASED)
                            return new TarindoorPhasedBlockEntity(pos, state);
                        return new TarindoorBlockEntity(pos, state);
                    }, ORDERED_BLOCKS.get(s).get()).build(null)));
        }
        BLOCK_ENTITY_TYPES.register(bus);
    }

    public static void registerBlocks(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            final int s = slot;
            DeferredBlock<TarindoorBlock> block = BLOCKS.register(slotName(slot) + "_door", () -> {
                BlockSetType bst = BlockSetType.register(new BlockSetType(
                        CustomTrainDoorMod.MODID + ":" + slotName(s) + "_door",
                        true, true, true, BlockSetType.PressurePlateSensitivity.EVERYTHING,
                        SoundType.NETHERITE_BLOCK,
                        CLOSE_SOUNDS.get(s).get(), OPEN_SOUNDS.get(s).get(),
                        SoundType.NETHERITE_BLOCK.getStepSound(), SoundType.NETHERITE_BLOCK.getFallSound(),
                        SoundType.NETHERITE_BLOCK.getPlaceSound(), SoundType.NETHERITE_BLOCK.getHitSound(),
                        SoundType.NETHERITE_BLOCK.getBreakSound(), SoundType.NETHERITE_BLOCK.getStepSound()));
                BLOCK_SET_TYPES.add(bst);
                return new TarindoorBlock(
                        BlockBehaviour.Properties.of()
                                .strength(5.0f, 6.0f).sound(SoundType.NETHERITE_BLOCK)
                                .noOcclusion().pushReaction(PushReaction.DESTROY), bst, s);
            });
            ORDERED_BLOCKS.add(block);
        }
        BLOCKS.register(bus);
    }

    // --- Lookup ---

    @Nullable public static TarindoorDefinition getDefinition(String id) { return definitionsById.get(id); }
    @Nullable public static TarindoorDefinition getDefinition(int slot) { return definitionsBySlot.get(slot); }
    @Nullable public static TarindoorDefinition getDefinition(Block block) {
        return block instanceof TarindoorBlock t ? getDefinition(t.getSlot()) : null;
    }

    public static Collection<TarindoorDefinition> getDefinitions() { return definitions; }
    public static Map<String, Integer> getSlotAssignments() { return slotsById; }
    public static int getSlot(String id) { return slotsById.getOrDefault(id, -1); }
    public static String slotName(int slot) { return String.format(Locale.ROOT, "tarindoor_%02d", slot); }

    @Nullable public static BlockSetType getBlockSetType(String id) {
        int s = getSlot(id); return s >= 0 ? BLOCK_SET_TYPES.get(s) : null;
    }

    @Nullable public static Supplier<SoundEvent> getOpenSound(String id) {
        TarindoorDefinition def = getDefinition(id); if (def == null) return null;
        if (def.block().soundEventOpen() != null)
            return () -> Optional.ofNullable(BuiltInRegistries.SOUND_EVENT
                    .get(ResourceLocation.parse(def.block().soundEventOpen())))
                    .orElse(OPEN_SOUNDS.get(getSlot(id)).get());
        return OPEN_SOUNDS.get(getSlot(id));
    }

    @Nullable public static Supplier<SoundEvent> getCloseSound(String id) {
        TarindoorDefinition def = getDefinition(id); if (def == null) return null;
        if (def.block().soundEventClose() != null)
            return () -> Optional.ofNullable(BuiltInRegistries.SOUND_EVENT
                    .get(ResourceLocation.parse(def.block().soundEventClose())))
                    .orElse(CLOSE_SOUNDS.get(getSlot(id)).get());
        return CLOSE_SOUNDS.get(getSlot(id));
    }

    @Nullable public static BlockEntityType<?> getBlockEntityType(int slot) {
        return slot >= 0 && slot < BLOCK_ENTITY_HOLDERS.size() ? BLOCK_ENTITY_HOLDERS.get(slot).get() : null;
    }
    @Nullable public static BlockEntityType<?> getBlockEntityType(String id) { return getBlockEntityType(getSlot(id)); }

    public static List<DeferredBlock<TarindoorBlock>> getOrderedBlocks() { return ORDERED_BLOCKS; }
    public static List<DeferredBlock<TarindoorBlock>> getActiveBlocks() {
        return definitionsBySlot.keySet().stream().sorted().map(ORDERED_BLOCKS::get).toList();
    }
    public static Collection<DeferredHolder<BlockEntityType<?>, ?>> getBlockEntityHolders() { return BLOCK_ENTITY_HOLDERS; }
    @Nullable public static Path getZipPath(TarindoorDefinition def) { return ZIP_PATHS.get(def.id()); }

    private static ResourceLocation regId(String path) {
        return ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, path);
    }
}
