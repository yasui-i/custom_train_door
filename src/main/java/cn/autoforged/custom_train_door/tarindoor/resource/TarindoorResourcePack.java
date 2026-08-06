package cn.autoforged.custom_train_door.tarindoor.resource;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.google.gson.JsonArray;
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

        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door";

            // Blockstate JSON
            accept(namespace, prefix, output, "blockstates/" + doorName + ".json", getBlockstateJson(doorName));

            // Block Models
            accept(namespace, prefix, output, "models/block/" + doorName + "_bottom.json", getModelJson(doorName, "bottom"));
            accept(namespace, prefix, output, "models/block/" + doorName + "_top.json", getModelJson(doorName, "top"));

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
            String doorName = TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door";

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
                        String key = String.format("facing=%s,half=%s,hinge=%s,open=%s", dir, half, hinge, open);
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
                    }
                }
            }
        }
        root.add("variants", variants);
        return toBytes(root);
    }

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

    private static byte[] getDynamicSoundsJson() {
        JsonObject root = new JsonObject();
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door";
            addSoundDefinition(root, doorName + "_open", def, true);
            addSoundDefinition(root, doorName + "_close", def, false);
        }
        return toBytes(root);
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
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String doorName = TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door";
            String name = def.localizedNames().get(locale);
            if (name == null && "en_us".equals(locale)) name = def.displayName();
            if (name != null) {
                root.addProperty("block." + NAMESPACE + "." + doorName, name);
            }
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
        result.addProperty("id", NAMESPACE + ":" + TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door");
        result.addProperty("count", recipe.count());
        root.add("result", result);
        return toBytes(root);
    }

    private static byte[] getDoorTagJson() {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray values = new JsonArray();
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            values.add(NAMESPACE + ":" + TarindoorRegistry.slotName(TarindoorRegistry.getSlot(def.id())) + "_door");
        }
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

    private static byte[] toBytes(JsonObject obj) {
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }
}
