package cn.autoforged.custom_train_door.tarindoor.resource;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

public class TarindoorResourcePack implements PackResources {

    private static final String NAMESPACE = CustomTrainDoorMod.MODID;
    private static final int MAX_RESOURCE_BYTES = 32 * 1024 * 1024;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    // Minimal 1x1 white pixel PNG (valid PNG binary)
    private static final byte[] PLACEHOLDER_PNG = {
        (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // signature
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1
        0x08, 0x02, 0x00, 0x00, 0x00, (byte)0x90, 0x77, 0x53, (byte)0xDE,
        0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT
        0x08, (byte)0xD7, 0x63, 0x60, 0x60, 0x60, 0x00, 0x00,
        0x00, 0x04, 0x00, 0x01, 0x27, 0x34, 0x07, (byte)0xE7,
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND
        (byte)0xAE, 0x42, 0x60, (byte)0x82
    };

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... elements) { return null; }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (!getNamespaces(packType).contains(location.getNamespace())) return null;

        String path = location.getPath();
        String namespace = location.getNamespace();
        String cacheKey = packType.name() + ":" + namespace + ":" + path;
        byte[] data = cache.computeIfAbsent(cacheKey,
                ignored -> loadResource(packType, namespace, path));
        if (data != null) {
            byte[] d = data;
            return () -> new ByteArrayInputStream(d);
        }
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String prefix, ResourceOutput output) {
        if (packType == PackType.SERVER_DATA) {
            if ("minecraft".equals(namespace)) {
                accept(namespace, prefix, output, "tags/block/doors.json", getDoorTagJson());
                return;
            }
            if (!NAMESPACE.equals(namespace)) return;
            for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
                if (def.recipe() != null) {
                    accept(namespace, prefix, output,
                            "recipe/" + def.id() + "_door.json", getRecipeJson(def));
                }
            }
            return;
        }
        if (!NAMESPACE.equals(namespace)) return;

        // Single blockstate for tarindoor_door with variant-based model selection
        accept(namespace, prefix, output, "blockstates/tarindoor_door.json",
                getSingleBlockstateJson());

        // Fallback item model (items with ITEM_MODEL component use door-specific models)
        accept(namespace, prefix, output, "models/item/tarindoor_door.json",
                getDefaultItemModelJson());

        // Placeholder model for when no doors are loaded (before server sync)
        accept(namespace, prefix, output, "models/block/tarindoor_placeholder_bottom.json",
                getPlaceholderModelJson("bottom"));
        accept(namespace, prefix, output, "models/block/tarindoor_placeholder_top.json",
                getPlaceholderModelJson("top"));
        accept(namespace, prefix, output, "models/item/tarindoor_placeholder.json",
                getPlaceholderItemJson());
        // Generate a simple placeholder texture (1x1 white pixel PNG)
        accept(namespace, prefix, output, "textures/block/tarindoor_placeholder_side.png",
                PLACEHOLDER_PNG);
        accept(namespace, prefix, output, "textures/block/tarindoor_placeholder_bottom.png",
                PLACEHOLDER_PNG);
        accept(namespace, prefix, output, "textures/block/tarindoor_placeholder_top.png",
                PLACEHOLDER_PNG);

        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = def.id() + "_door";

            // Block Models (door-specific, referenced by the variant blockstate)
            accept(namespace, prefix, output, "models/block/" + doorName + "_bottom.json",
                    getModelJson(doorName, "bottom"));
            accept(namespace, prefix, output, "models/block/" + doorName + "_top.json",
                    getModelJson(doorName, "top"));

            // Item Model
            accept(namespace, prefix, output, "models/item/" + doorName + ".json", getItemModelJson(doorName));

            // Textures from zip
            for (String tex : new String[]{"side", "top", "bottom"}) {
                byte[] texData = loadFromZip(def, tex + ".png");
                if (texData != null) {
                    accept(namespace, prefix, output,
                            "textures/block/" + doorName + "_" + tex + ".png", texData);
                }
            }

            // Sounds from zip (unless using shared sound_event)
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().openSoundFileName());
                if (sndData != null) {
                    accept(namespace, prefix, output, "sounds/" + doorName + "_open.ogg", sndData);
                }
            }
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().closeSoundFileName());
                if (sndData != null) {
                    accept(namespace, prefix, output, "sounds/" + doorName + "_close.ogg", sndData);
                }
            }
        }

        accept(namespace, prefix, output, "sounds.json", getDynamicSoundsJson());
        for (String locale : getLocales()) {
            accept(namespace, prefix, output, "lang/" + locale + ".json", getLanguageJson(locale));
        }
    }

    private static void accept(String namespace, String prefix, ResourceOutput output,
                               String path, byte[] data) {
        if (!path.startsWith(prefix)) return;
        output.accept(
                ResourceLocation.fromNamespaceAndPath(namespace, path),
                () -> new ByteArrayInputStream(data));
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of(NAMESPACE, "minecraft") : Set.of(NAMESPACE);
    }

    @Override
    public PackLocationInfo location() {
        return new PackLocationInfo(
                "tarindoor_dynamic",
                Component.literal("Tarindoor Dynamic Resources"),
                PackSource.BUILT_IN,
                Optional.of(new KnownPack(NAMESPACE, "tarindoor", "1"))
        );
    }

    @Override
    public boolean isHidden() { return true; }

    @Override
    public void close() { cache.clear(); }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("Tarindoor"), 34);
        }
        return null;
    }

    // --- Resource loading ---

    @Nullable
    private byte[] loadResource(PackType packType, String namespace, String path) {
        if (packType == PackType.SERVER_DATA) {
            if ("minecraft".equals(namespace) && "tags/block/doors.json".equals(path)) {
                return getDoorTagJson();
            }
            if (!NAMESPACE.equals(namespace)) return null;
            if (path.startsWith("recipe/") && path.endsWith("_door.json")) {
                String id = path.substring("recipe/".length(), path.length() - "_door.json".length());
                TarindoorDefinition def = TarindoorRegistry.getDefinition(id);
                if (def != null && def.recipe() != null) return getRecipeJson(def);
            }
            return null;
        }
        if (!NAMESPACE.equals(namespace)) return null;
        if ("sounds.json".equals(path)) return getDynamicSoundsJson();
        if (path.startsWith("lang/") && path.endsWith(".json")) {
            String locale = path.substring("lang/".length(), path.length() - ".json".length());
            if (getLocales().contains(locale)) return getLanguageJson(locale);
        }

        // Try to match a known door pattern: {id}_door
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = def.id() + "_door";

            if (path.equals("models/item/" + doorName + ".json"))
                return getItemModelJson(doorName);
            if (path.equals("blockstates/" + doorName + ".json"))
                return getBlockstateJson(doorName);
            if (path.equals("models/block/" + doorName + "_bottom.json"))
                return getModelJson(doorName, "bottom");
            if (path.equals("models/block/" + doorName + "_top.json"))
                return getModelJson(doorName, "top");

            // Textures
            String texPrefix = "textures/block/" + doorName + "_";
            if (path.startsWith(texPrefix) && path.endsWith(".png")) {
                String kind = path.substring(texPrefix.length(), path.length() - 4);
                return loadFromZip(def, kind + ".png");
            }

            // Sounds
            if (def.block().soundEventOpen() == null && path.equals("sounds/" + doorName + "_open.ogg"))
                return loadFromZip(def, def.block().openSoundFileName());
            if (def.block().soundEventClose() == null && path.equals("sounds/" + doorName + "_close.ogg"))
                return loadFromZip(def, def.block().closeSoundFileName());
        }

        // Single blockstate for the tarindoor_door block
        if (path.equals("blockstates/tarindoor_door.json"))
            return getSingleBlockstateJson();
        // Fallback item model
        if (path.equals("models/item/tarindoor_door.json"))
            return getDefaultItemModelJson();

        // Placeholder resources for when no doors are loaded
        if (path.equals("models/block/tarindoor_placeholder_bottom.json"))
            return getPlaceholderModelJson("bottom");
        if (path.equals("models/block/tarindoor_placeholder_top.json"))
            return getPlaceholderModelJson("top");
        if (path.equals("models/item/tarindoor_placeholder.json"))
            return getPlaceholderItemJson();
        if (path.startsWith("textures/block/tarindoor_placeholder_"))
            return PLACEHOLDER_PNG;

        return null;
    }

    // --- JSON generators ---

    private static byte[] getBlockstateJson(String doorName) {
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();
        String modelNs = NAMESPACE + ":block/" + doorName;

        for (String dir : new String[]{"east", "south", "west", "north"}) {
            for (String hinge : new String[]{"left", "right"}) {
                for (String half : new String[]{"lower", "upper"}) {
                    for (String open : new String[]{"false", "true"}) {
                        for (String powered : new String[]{"false", "true"}) {
                        for (String visible : new String[]{"false", "true"}) {
                        String key = String.format("facing=%s,half=%s,hinge=%s,open=%s,powered=%s,visible=%s",
                                dir, half, hinge, open, powered, visible);
                        JsonObject variant = new JsonObject();
                        variant.addProperty("model", modelNs + "_" + (half.equals("lower") ? "bottom" : "top"));
                        int y = switch (dir) {
                            case "east" -> 0;
                            case "south" -> 90;
                            case "west" -> 180;
                            case "north" -> 270;
                            default -> 0;
                        };
                        if ("true".equals(open)) {
                            y += 90;
                            if ("right".equals(hinge)) y += 180;
                        }
                        y %= 360;
                        if (y != 0) variant.addProperty("y", y);
                        variants.add(key, variant);
                        }}}}}}
        root.add("variants", variants);
        return toBytes(root);
    }

    /** Single blockstate for tarindoor_door with variant-based door model selection. */
    private static byte[] getSingleBlockstateJson() {
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();
        List<TarindoorDefinition> defs = List.copyOf(TarindoorRegistry.getDefinitions());

        for (String dir : new String[]{"east", "south", "west", "north"}) {
            for (String hinge : new String[]{"left", "right"}) {
                for (String half : new String[]{"lower", "upper"}) {
                    for (String open : new String[]{"false", "true"}) {
                        for (String powered : new String[]{"false", "true"}) {
                        for (String visible : new String[]{"false", "true"}) {
                        for (int v = 0; v < 16; v++) {
                            String doorName = getDoorNameForVariant(defs, v);
                            if (doorName == null) continue;
                            String key = String.format(
                                    "facing=%s,half=%s,hinge=%s,open=%s,powered=%s,visible=%s,variant=%d",
                                    dir, half, hinge, open, powered, visible, v);
                            JsonObject variant = new JsonObject();
                            variant.addProperty("model", NAMESPACE + ":block/" + doorName
                                    + "_" + (half.equals("lower") ? "bottom" : "top"));
                            int y = switch (dir) {
                                case "east" -> 0;
                                case "south" -> 90;
                                case "west" -> 180;
                                case "north" -> 270;
                                default -> 0;
                            };
                            if ("true".equals(open)) {
                                y += 90;
                                if ("right".equals(hinge)) y += 180;
                            }
                            y %= 360;
                            if (y != 0) variant.addProperty("y", y);
                            variants.add(key, variant);
                        }}}}}}}
        root.add("variants", variants);
        return toBytes(root);
    }

    @Nullable
    private static String getDoorNameForVariant(List<TarindoorDefinition> defs, int variant) {
        for (TarindoorDefinition def : defs) {
            if (TarindoorRegistry.getVariantIndex(def.id()) == variant) {
                return def.id() + "_door";
            }
        }
        // Fallback: use the first available door, or placeholder if no doors loaded
        if (!defs.isEmpty()) {
            return defs.get(0).id() + "_door";
        }
        return "tarindoor_placeholder";
    }

    /** Generate a block model JSON from the door definition. */
    private static byte[] getModelJson(String doorName, String half) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        root.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject textures = new JsonObject();
        textures.addProperty("0", NAMESPACE + ":block/" + doorName + "_side");
        textures.addProperty("2", NAMESPACE + ":block/" + doorName + "_" + half);
        textures.addProperty("particle", NAMESPACE + ":block/" + doorName + "_" + half);
        root.add("textures", textures);

        JsonArray elements = new JsonArray();
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray(); from.add(0); from.add(0); from.add(0);
        JsonArray to   = new JsonArray(); to.add(3);   to.add(16);   to.add(16);
        element.add("from", from); element.add("to", to);
        JsonObject faces = new JsonObject();

        for (String dir : new String[]{"north", "south"}) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(0); uv.add(half.equals("top") ? 4 : 12);
            uv.add(16); uv.add(half.equals("top") ? 7 : 15);
            face.add("uv", uv);
            face.addProperty("rotation", half.equals("top") ? 90 : 270);
            face.addProperty("texture", "#0");
            faces.add(dir, face);
        }
        for (String dir : new String[]{"east", "west"}) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(0); uv.add(16); uv.add(16);
            face.add("uv", uv); face.addProperty("texture", "#2");
            faces.add(dir, face);
        }
        if (half.equals("top")) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(0); uv.add(16); uv.add(3);
            face.add("uv", uv); face.addProperty("rotation", 90); face.addProperty("texture", "#0");
            faces.add("up", face);
        } else {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(8); uv.add(16); uv.add(11);
            face.add("uv", uv); face.addProperty("rotation", 90); face.addProperty("texture", "#0");
            faces.add("down", face);
        }
        element.add("faces", faces);
        elements.add(element);
        root.add("elements", elements);
        return toBytes(root);
    }

    private static byte[] getItemModelJson(String doorName) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", NAMESPACE + ":block/" + doorName + "_bottom");
        return toBytes(root);
    }

    /** Item model for tarindoor_door with per-door overrides via custom_model_data. */
    private static byte[] getDefaultItemModelJson() {
        JsonObject root = new JsonObject();
        var defs = TarindoorRegistry.getDefinitions();
        String fallback = defs.isEmpty() ? "tarindoor_placeholder" : (defs.iterator().next().id() + "_door");
        root.addProperty("parent", NAMESPACE + ":block/" + fallback + "_bottom");

        // Generate overrides for each door definition
        JsonArray overrides = new JsonArray();
        for (var def : defs) {
            int variant = TarindoorRegistry.getVariantIndex(def.id());
            JsonObject override = new JsonObject();
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data", variant);
            override.add("predicate", predicate);
            override.addProperty("model", NAMESPACE + ":item/" + def.id() + "_door");
            overrides.add(override);
        }
        root.add("overrides", overrides);
        return toBytes(root);
    }

    private static byte[] getDynamicSoundsJson() {
        JsonObject root = new JsonObject();
        // Door-specific sounds
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = def.id() + "_door";
            addSoundDefinition(root, doorName + "_open", def, true);
            addSoundDefinition(root, doorName + "_close", def, false);
        }
        // Default fallback sounds for the generic block
        if (TarindoorRegistry.getDefinitions().isEmpty()) {
            addFallbackSound(root, "tarindoor_door_open");
            addFallbackSound(root, "tarindoor_door_close");
        }
        return toBytes(root);
    }

    private static void addFallbackSound(JsonObject root, String eventName) {
        JsonObject event = new JsonObject();
        event.addProperty("replace", false);
        JsonArray sounds = new JsonArray();
        JsonObject sound = new JsonObject();
        sound.addProperty("name", "minecraft:block.iron_door.open");
        sound.addProperty("type", "event");
        sounds.add(sound);
        event.add("sounds", sounds);
        root.add(eventName, event);
    }

    private static void addSoundDefinition(JsonObject root, String eventName,
                                           @Nullable TarindoorDefinition def, boolean open) {
        JsonObject event = new JsonObject();
        JsonArray sounds = new JsonArray();
        if (def != null) {
            String soundEvent = open ? def.block().soundEventOpen() : def.block().soundEventClose();
            String soundFile = open ? def.block().openSoundFileName() : def.block().closeSoundFileName();
            if (soundEvent == null && soundFile != null) {
                event.addProperty("subtitle", "subtitles." + NAMESPACE + "." + eventName);
                sounds.add(NAMESPACE + ":" + eventName);
            }
        }
        event.add("sounds", sounds);
        root.add(eventName, event);
    }

    private static Set<String> getLocales() {
        Set<String> locales = new LinkedHashSet<>();
        locales.add("en_us");
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            locales.addAll(def.localizedNames().keySet());
        }
        return locales;
    }

    private static byte[] getLanguageJson(String locale) {
        JsonObject root = new JsonObject();
        // Block registry name translation — uses first door's name, or fallback
        {
            String blockName = TarindoorRegistry.getDefinitions().isEmpty()
                    ? "Custom Train Door"
                    : TarindoorRegistry.getDefinitions().iterator().next().displayName();
            root.addProperty("block." + NAMESPACE + ".tarindoor_door", blockName);
            root.addProperty("item." + NAMESPACE + ".tarindoor_door", blockName);
        }
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = def.id() + "_door";
            String subtitle = "subtitles." + NAMESPACE + "." + doorName;
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null) {
                root.addProperty(subtitle + "_open", "Train door opens");
            }
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null) {
                root.addProperty(subtitle + "_close", "Train door closes");
            }
        }
        return toBytes(root);
    }

    private static byte[] getRecipeJson(TarindoorDefinition def) {
        TarindoorDefinition.TarindoorRecipeConfig recipe = def.recipe();
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:crafting_shaped");
        root.addProperty("category", "redstone");
        JsonArray pattern = new JsonArray();
        recipe.pattern().forEach(pattern::add);
        root.add("pattern", pattern);
        JsonObject key = new JsonObject();
        recipe.keys().forEach((symbol, ingredient) -> {
            JsonObject ingredientObject = new JsonObject();
            ingredientObject.addProperty("item", ingredient);
            key.add(symbol.toString(), ingredientObject);
        });
        root.add("key", key);
        JsonObject result = new JsonObject();
        result.addProperty("id", NAMESPACE + ":tarindoor_door");
        result.addProperty("count", recipe.count());
        // Embed DoorId via NBT component
        JsonObject components = new JsonObject();
        JsonObject beData = new JsonObject();
        beData.addProperty("id", NAMESPACE + ":tarindoor_door_be");
        beData.addProperty("DoorId", def.id());
        components.add("minecraft:block_entity_data", beData);
        result.add("components", components);
        root.add("result", result);
        return toBytes(root);
    }

    private static byte[] getDoorTagJson() {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray values = new JsonArray();
        // Single-block system: all doors are the same block type
        values.add(NAMESPACE + ":tarindoor_door");
        root.add("values", values);
        return toBytes(root);
    }

    @Nullable
    private static byte[] loadFromZip(TarindoorDefinition def, String entryName) {
        Path zipPath = TarindoorRegistry.getZipPath(def);
        if (zipPath == null || !Files.exists(zipPath)) return null;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            if (entry.getSize() > MAX_RESOURCE_BYTES) return null;
            try (InputStream is = zip.getInputStream(entry)) {
                byte[] data = is.readNBytes(MAX_RESOURCE_BYTES + 1);
                return data.length <= MAX_RESOURCE_BYTES ? data : null;
            }
        } catch (IOException e) { return null; }
    }

    /** Placeholder model for when no door definitions are loaded. */
    private static byte[] getPlaceholderModelJson(String half) {
        JsonObject root = new JsonObject();
        root.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject textures = new JsonObject();
        textures.addProperty("0", NAMESPACE + ":block/tarindoor_placeholder_side");
        textures.addProperty("2", NAMESPACE + ":block/tarindoor_placeholder_" + half);
        textures.addProperty("particle", NAMESPACE + ":block/tarindoor_placeholder_" + half);
        root.add("textures", textures);
        JsonArray elements = new JsonArray();
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray(); from.add(0); from.add(0); from.add(0);
        JsonArray to = new JsonArray(); to.add(3); to.add(16); to.add(16);
        element.add("from", from); element.add("to", to);
        JsonObject faces = new JsonObject();
        for (String dir : new String[]{"north", "south"}) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(0); uv.add(half.equals("top") ? 4 : 12);
            uv.add(16); uv.add(half.equals("top") ? 7 : 15);
            face.add("uv", uv);
            face.addProperty("texture", "#0");
            faces.add(dir, face);
        }
        for (String dir : new String[]{"east", "west"}) {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(0); uv.add(16); uv.add(16);
            face.add("uv", uv); face.addProperty("texture", "#2");
            faces.add(dir, face);
        }
        element.add("faces", faces);
        elements.add(element);
        root.add("elements", elements);
        return toBytes(root);
    }

    private static byte[] getPlaceholderItemJson() {
        JsonObject root = new JsonObject();
        root.addProperty("parent", NAMESPACE + ":block/tarindoor_placeholder_bottom");
        return toBytes(root);
    }

    private static byte[] toBytes(JsonObject obj) {
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }
}
