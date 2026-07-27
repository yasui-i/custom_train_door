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
        if (!NAMESPACE.equals(location.getNamespace())) return null;

        String path = location.getPath();
        String cacheKey = packType.name() + ":" + path;
        byte[] data = cache.computeIfAbsent(cacheKey, ignored -> loadResource(packType, path));
        if (data != null) {
            byte[] d = data;
            return () -> new ByteArrayInputStream(d);
        }
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String prefix, ResourceOutput output) {
        if (!NAMESPACE.equals(namespace)) return;

        if (packType == PackType.SERVER_DATA) {
            for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
                if (def.recipe() != null) {
                    accept(prefix, output, "recipe/" + def.id() + "_door.json", getRecipeJson(def));
                }
            }
            return;
        }

        // Only publish resources for ZIP-defined doors. Built-in doors are supplied
        // by src/main/resources and must not be shadowed by this higher-priority pack.
        Set<String> allIds = new LinkedHashSet<>();
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            allIds.add(def.id());
        }

        for (String id : allIds) {
            String doorName = id + "_door";

            // Blockstate JSON
            byte[] bsData = getBlockstateJson(id);
            accept(prefix, output, "blockstates/" + doorName + ".json", bsData);

            // Block Models
            byte[] bottomModel = getModelJson(id, "bottom");
            accept(prefix, output, "models/block/" + doorName + "_bottom.json", bottomModel);
            byte[] topModel = getModelJson(id, "top");
            accept(prefix, output, "models/block/" + doorName + "_top.json", topModel);

            // Item Model (inventory icon)
            byte[] itemModel = getItemModelJson(id);
            accept(prefix, output, "models/item/" + doorName + ".json", itemModel);

            TarindoorDefinition def = TarindoorRegistry.getDefinition(id);
            if (def == null) continue;

            // Dynamic doors: textures from zip
            for (String tex : new String[]{"side", "top", "bottom"}) {
                byte[] texData = loadFromZip(def, tex + ".png");
                if (texData != null) {
                    accept(prefix, output, "textures/block/" + id + "_" + tex + ".png", texData);
                }
            }

            // Dynamic doors: sounds from zip (unless using shared sound_event)
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().openSoundFileName());
                if (sndData != null) {
                    accept(prefix, output, "sounds/" + id + "_door_open.ogg", sndData);
                }
            }
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().closeSoundFileName());
                if (sndData != null) {
                    accept(prefix, output, "sounds/" + id + "_door_close.ogg", sndData);
                }
            }
        }

        accept(prefix, output, "sounds.json", getDynamicSoundsJson());
        for (String locale : getLocales()) {
            accept(prefix, output, "lang/" + locale + ".json", getLanguageJson(locale));
        }
    }

    private static void accept(String prefix, ResourceOutput output, String path, byte[] data) {
        if (!path.startsWith(prefix)) return;
        output.accept(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, path),
                () -> new ByteArrayInputStream(data));
    }

    @Override
    public Set<String> getNamespaces(PackType type) { return Set.of(NAMESPACE); }

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
    private byte[] loadResource(PackType packType, String path) {
        if (packType == PackType.SERVER_DATA) {
            if (path.startsWith("recipe/") && path.endsWith("_door.json")) {
                String id = path.substring("recipe/".length(), path.length() - "_door.json".length());
                TarindoorDefinition def = TarindoorRegistry.getDefinition(id);
                if (def != null && def.recipe() != null) return getRecipeJson(def);
            }
            return null;
        }
        if ("sounds.json".equals(path)) return getDynamicSoundsJson();
        if (path.startsWith("lang/") && path.endsWith(".json")) {
            String locale = path.substring("lang/".length(), path.length() - ".json".length());
            if (getLocales().contains(locale)) return getLanguageJson(locale);
        }
        String id;
        // models/item/{id}_door.json
        if (path.startsWith("models/item/") && path.endsWith(".json")) {
            String name = path.substring("models/item/".length(), path.length() - ".json".length());
            if (name.endsWith("_door")) {
                id = name.substring(0, name.length() - "_door".length());
                if (isKnownDoor(id)) return getItemModelJson(id);
            }
        }
        // blockstates/{id}_door.json
        if (path.startsWith("blockstates/") && path.endsWith(".json")) {
            String name = path.substring("blockstates/".length(), path.length() - ".json".length());
            if (name.endsWith("_door")) {
                id = name.substring(0, name.length() - "_door".length());
                if (isKnownDoor(id)) return getBlockstateJson(id);
            }
        }
        // models/block/{id}_door_{bottom|top}.json
        if (path.startsWith("models/block/") && path.endsWith(".json")) {
            String name = path.substring("models/block/".length(), path.length() - ".json".length());
            for (String suffix : new String[]{"_bottom", "_top"}) {
                if (name.endsWith(suffix)) {
                    String doorPart = name.substring(0, name.length() - suffix.length());
                    if (doorPart.endsWith("_door")) {
                        id = doorPart.substring(0, doorPart.length() - "_door".length());
                        if (isKnownDoor(id)) return getModelJson(id, suffix.substring(1));
                    }
                }
            }
        }
        // textures/block/{id}_{side|top|bottom}.png — dynamic only
        if (path.startsWith("textures/block/")) {
            String texName = path.substring("textures/block/".length());
            if (texName.endsWith(".png")) texName = texName.substring(0, texName.length() - 4);
            for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
                String prefix = def.id() + "_";
                if (texName.startsWith(prefix)) {
                    return loadFromZip(def, texName.substring(prefix.length()) + ".png");
                }
            }
        }
        // sounds/{id}_door_{open|close}.ogg — dynamic only
        if (path.startsWith("sounds/")) {
            String sndName = path.substring("sounds/".length());
            if (sndName.endsWith(".ogg")) sndName = sndName.substring(0, sndName.length() - 4);
            for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
                if (sndName.equals(def.id() + "_door_open") && def.block().openSoundFileName() != null)
                    return loadFromZip(def, def.block().openSoundFileName());
                if (sndName.equals(def.id() + "_door_close") && def.block().closeSoundFileName() != null)
                    return loadFromZip(def, def.block().closeSoundFileName());
            }
        }
        return null;
    }

    private boolean isKnownDoor(String id) {
        return TarindoorRegistry.getDefinition(id) != null;
    }

    // --- JSON generators ---

    private static byte[] getBlockstateJson(String id) {
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();
        String modelNs = NAMESPACE + ":block/" + id + "_door";

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

    private static byte[] getModelJson(String id, String half) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        root.addProperty("render_type", "minecraft:cutout_mipped");
        JsonObject textures = new JsonObject();
        textures.addProperty("0", NAMESPACE + ":block/" + id + "_side");
        textures.addProperty("2", NAMESPACE + ":block/" + id + "_" + half);
        textures.addProperty("particle", NAMESPACE + ":block/" + id + "_" + half);
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

    private static byte[] getItemModelJson(String id) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", NAMESPACE + ":block/" + id + "_door_bottom");
        return toBytes(root);
    }

    private static byte[] getDynamicSoundsJson() {
        JsonObject root = new JsonObject();
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            String id = def.id();
            String sk = "subtitles." + NAMESPACE + "." + id + "_door";
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null) {
                JsonObject e = new JsonObject();
                e.addProperty("subtitle", sk + "_open");
                JsonArray a = new JsonArray(); a.add(NAMESPACE + ":" + id + "_door_open");
                e.add("sounds", a); root.add(id + "_door_open", e);
            }
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null) {
                JsonObject e = new JsonObject();
                e.addProperty("subtitle", sk + "_close");
                JsonArray a = new JsonArray(); a.add(NAMESPACE + ":" + id + "_door_close");
                e.add("sounds", a); root.add(id + "_door_close", e);
            }
        }
        return toBytes(root);
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
            String name = def.localizedNames().get(locale);
            if (name == null && "en_us".equals(locale)) name = def.displayName();
            if (name != null) {
                root.addProperty("block." + NAMESPACE + "." + def.id() + "_door", name);
            }
            String subtitle = "subtitles." + NAMESPACE + "." + def.id() + "_door";
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
        result.addProperty("id", NAMESPACE + ":" + def.id() + "_door");
        result.addProperty("count", recipe.count());
        root.add("result", result);
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
