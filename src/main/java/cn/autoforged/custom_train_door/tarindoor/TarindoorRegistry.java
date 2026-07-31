package cn.autoforged.custom_train_door.tarindoor;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlockEntity;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorPhasedBlockEntity;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Supplier;

/**
 * Fixed registry slots backed by runtime-loaded tarindoor definitions.
 *
 * <p>Blocks cannot be registered after a client starts connecting. Registering a
 * stable set of slots on every installation keeps the NeoForge registries
 * identical, while definitions and ZIP resources can safely be synchronized
 * during the connection configuration phase.</p>
 */
public final class TarindoorRegistry {
    public static final int MAX_SLOTS = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");
    private static final Gson GSON = new Gson();
    private static final String SLOT_FILE = "slots.json";

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

    private TarindoorRegistry() {
    }

    /** Load local packs and retain stable slots in tarindoor/slots.json. */
    public static synchronized void loadAll() {
        clearRuntimeDefinitions();
        Path dir = TarindoorZipLoader.getTarindoorDir();
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions();
        applyDefinitions(loaded, loadOrAssignSlots(dir, loaded));
    }

    /**
     * Replace local definitions with a server-provided set. The supplied slot
     * map is authoritative and is never merged with the client's local map.
     */
    public static synchronized void loadSynced(Path directory, Map<String, Integer> authoritativeSlots) {
        clearRuntimeDefinitions();
        List<TarindoorDefinition> loaded = TarindoorZipLoader.loadDefinitions(directory, false);
        applyDefinitions(loaded, authoritativeSlots);
    }

    static synchronized void storeZipPath(String id, Path zipPath) {
        ZIP_PATHS.put(id, zipPath);
    }

    private static void clearRuntimeDefinitions() {
        ZIP_PATHS.clear();
        definitions = List.of();
        definitionsById = Map.of();
        definitionsBySlot = Map.of();
        slotsById = Map.of();
    }

    private static void applyDefinitions(List<TarindoorDefinition> loaded, Map<String, Integer> assignedSlots) {
        Map<String, TarindoorDefinition> byId = new LinkedHashMap<>();
        Map<Integer, TarindoorDefinition> bySlot = new TreeMap<>();
        Map<String, Integer> validSlots = new LinkedHashMap<>();

        loaded.stream().sorted(Comparator.comparing(TarindoorDefinition::id)).forEach(def -> {
            Integer slot = assignedSlots.get(def.id());
            if (slot == null || slot < 0 || slot >= MAX_SLOTS || bySlot.containsKey(slot)) {
                LOGGER.warn("Ignoring door '{}' because its synchronized slot is invalid or occupied: {}",
                        def.id(), slot);
                return;
            }
            byId.put(def.id(), def);
            bySlot.put(slot, def);
            validSlots.put(def.id(), slot);
        });

        definitions = List.copyOf(byId.values());
        definitionsById = Map.copyOf(byId);
        definitionsBySlot = Map.copyOf(bySlot);
        slotsById = Map.copyOf(validSlots);
        LOGGER.info("Activated {} tarindoor door(s) in {} fixed registry slots",
                definitions.size(), MAX_SLOTS);
    }

    private static Map<String, Integer> loadOrAssignSlots(Path directory, List<TarindoorDefinition> loaded) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Path slotFile = directory.resolve(SLOT_FILE);
        if (Files.isRegularFile(slotFile)) {
            try (Reader reader = Files.newBufferedReader(slotFile)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    json.entrySet().forEach(entry -> {
                        try {
                            int slot = entry.getValue().getAsInt();
                            if (slot >= 0 && slot < MAX_SLOTS && !result.containsValue(slot)) {
                                result.put(entry.getKey(), slot);
                            }
                        } catch (RuntimeException ignored) {
                        }
                    });
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read {}, assigning slots again: {}", slotFile, e.getMessage());
            }
        }

        Set<Integer> used = new HashSet<>(result.values());
        for (TarindoorDefinition def : loaded.stream()
                .sorted(Comparator.comparing(TarindoorDefinition::id)).toList()) {
            if (result.containsKey(def.id())) continue;
            int slot = firstFreeSlot(used);
            if (slot < 0) {
                LOGGER.warn("At most {} tarindoor packs are supported; skipping '{}'", MAX_SLOTS, def.id());
                continue;
            }
            result.put(def.id(), slot);
            used.add(slot);
        }

        try {
            Files.createDirectories(directory);
            JsonObject json = new JsonObject();
            result.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
            Path temporary = directory.resolve(SLOT_FILE + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, slotFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, slotFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save stable tarindoor slots: {}", e.getMessage());
        }
        return result;
    }

    private static int firstFreeSlot(Set<Integer> used) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            if (!used.contains(slot)) return slot;
        }
        return -1;
    }

    public static void registerSounds(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            String name = slotName(slot);
            OPEN_SOUNDS.add(SOUND_EVENTS.register(name + "_door_open",
                    () -> SoundEvent.createVariableRangeEvent(id(name + "_door_open"))));
            CLOSE_SOUNDS.add(SOUND_EVENTS.register(name + "_door_close",
                    () -> SoundEvent.createVariableRangeEvent(id(name + "_door_close"))));
        }
        SOUND_EVENTS.register(bus);
    }

    public static void registerBlocks(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            final int fixedSlot = slot;
            DeferredBlock<TarindoorBlock> block = BLOCKS.register(slotName(slot) + "_door",
                    () -> new TarindoorBlock(
                            BlockBehaviour.Properties.of()
                                    .strength(5.0f, 6.0f)
                                    .sound(SoundType.NETHERITE_BLOCK)
                                    .noOcclusion()
                                    .pushReaction(PushReaction.DESTROY),
                            createBlockSetType(fixedSlot),
                            fixedSlot));
            ORDERED_BLOCKS.add(block);
        }
        BLOCKS.register(bus);
    }

    @SuppressWarnings("unchecked")
    public static void registerBlockEntities(IEventBus bus) {
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            final int fixedSlot = slot;
            DeferredHolder<BlockEntityType<?>, ?> holder = BLOCK_ENTITY_TYPES.register(
                    slotName(slot) + "_door_be",
                    () -> BlockEntityType.Builder.of((pos, state) -> {
                        TarindoorDefinition def = getDefinition(fixedSlot);
                        if (def != null && def.animation().type() == TarindoorDefinition.AnimationType.PHASED) {
                            return new TarindoorPhasedBlockEntity(pos, state);
                        }
                        return new TarindoorBlockEntity(pos, state);
                    }, ORDERED_BLOCKS.get(fixedSlot).get()).build(null));
            BLOCK_ENTITY_HOLDERS.add(holder);
        }
        BLOCK_ENTITY_TYPES.register(bus);
    }

    private static BlockSetType createBlockSetType(int slot) {
        BlockSetType type = BlockSetType.register(new BlockSetType(
                CustomTrainDoorMod.MODID + ":" + slotName(slot) + "_door",
                true, true, true,
                BlockSetType.PressurePlateSensitivity.EVERYTHING,
                SoundType.NETHERITE_BLOCK,
                CLOSE_SOUNDS.get(slot).get(),
                OPEN_SOUNDS.get(slot).get(),
                SoundType.NETHERITE_BLOCK.getStepSound(),
                SoundType.NETHERITE_BLOCK.getFallSound(),
                SoundType.NETHERITE_BLOCK.getPlaceSound(),
                SoundType.NETHERITE_BLOCK.getHitSound(),
                SoundType.NETHERITE_BLOCK.getBreakSound(),
                SoundType.NETHERITE_BLOCK.getStepSound()));
        BLOCK_SET_TYPES.add(type);
        return type;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, path);
    }

    public static String slotName(int slot) {
        return String.format(Locale.ROOT, "tarindoor_%02d", slot);
    }

    @Nullable
    public static TarindoorDefinition getDefinition(String id) {
        return definitionsById.get(id);
    }

    @Nullable
    public static TarindoorDefinition getDefinition(int slot) {
        return definitionsBySlot.get(slot);
    }

    @Nullable
    public static TarindoorDefinition getDefinition(Block block) {
        return block instanceof TarindoorBlock tarindoor ? getDefinition(tarindoor.getSlot()) : null;
    }

    public static Collection<TarindoorDefinition> getDefinitions() {
        return definitions;
    }

    public static Map<String, Integer> getSlotAssignments() {
        return slotsById;
    }

    public static int getSlot(String id) {
        return slotsById.getOrDefault(id, -1);
    }

    @Nullable
    public static BlockSetType getBlockSetType(String id) {
        int slot = getSlot(id);
        return slot >= 0 ? BLOCK_SET_TYPES.get(slot) : null;
    }

    @Nullable
    public static Supplier<SoundEvent> getOpenSound(String id) {
        TarindoorDefinition def = getDefinition(id);
        if (def == null) return null;
        if (def.block().soundEventOpen() != null) {
            return existingSoundOrFallback(ResourceLocation.parse(def.block().soundEventOpen()),
                    OPEN_SOUNDS.get(getSlot(id)).get());
        }
        return OPEN_SOUNDS.get(getSlot(id));
    }

    @Nullable
    public static Supplier<SoundEvent> getCloseSound(String id) {
        TarindoorDefinition def = getDefinition(id);
        if (def == null) return null;
        if (def.block().soundEventClose() != null) {
            return existingSoundOrFallback(ResourceLocation.parse(def.block().soundEventClose()),
                    CLOSE_SOUNDS.get(getSlot(id)).get());
        }
        return CLOSE_SOUNDS.get(getSlot(id));
    }

    private static Supplier<SoundEvent> existingSoundOrFallback(ResourceLocation sound, SoundEvent fallback) {
        return () -> Optional.ofNullable(BuiltInRegistries.SOUND_EVENT.get(sound)).orElse(fallback);
    }

    @Nullable
    public static BlockEntityType<?> getBlockEntityType(int slot) {
        return slot >= 0 && slot < BLOCK_ENTITY_HOLDERS.size()
                ? BLOCK_ENTITY_HOLDERS.get(slot).get() : null;
    }

    @Nullable
    public static BlockEntityType<?> getBlockEntityType(String id) {
        return getBlockEntityType(getSlot(id));
    }

    public static List<DeferredBlock<TarindoorBlock>> getOrderedBlocks() {
        return Collections.unmodifiableList(ORDERED_BLOCKS);
    }

    public static List<DeferredBlock<TarindoorBlock>> getActiveBlocks() {
        return definitionsBySlot.keySet().stream().sorted()
                .map(ORDERED_BLOCKS::get).toList();
    }

    public static Collection<DeferredHolder<BlockEntityType<?>, ?>> getBlockEntityHolders() {
        return Collections.unmodifiableList(BLOCK_ENTITY_HOLDERS);
    }

    @Nullable
    public static Path getZipPath(TarindoorDefinition def) {
        return ZIP_PATHS.get(def.id());
    }
}
