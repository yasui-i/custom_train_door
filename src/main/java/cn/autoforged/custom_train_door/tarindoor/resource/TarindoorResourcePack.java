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
    private static final Set<String> STATIC_DOOR_IDS = Set.of("cr400bf", "crh2a");
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    @Override
    @Nullable
    public IoSupplier<InputStream> getRootResource(String... elements) { return null; }

    @Override
    @Nullable
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        if (!NAMESPACE.equals(location.getNamespace())) return null;

        String path = location.getPath();
        byte[] data = cache.computeIfAbsent(path, this::loadResource);
        if (data != null) {
            byte[] d = data;
            return () -> new ByteArrayInputStream(d);
        }
        return null;
    }

    @Override
    public void listResources(PackType packType, String namespace, String prefix, ResourceOutput output) {
        if (!NAMESPACE.equals(namespace)) return;

        // Static doors + Dynamic doors
        Set<String> allIds = new LinkedHashSet<>(STATIC_DOOR_IDS);
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
            allIds.add(def.id());
        }

        for (String id : allIds) {
            String doorName = id + "_door";

            // Blockstate JSON
            byte[] bsData = getBlockstateJson(id);
            output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace, "blockstates/" + doorName),
                    () -> new ByteArrayInputStream(bsData));

            // Block Models
            byte[] bottomModel = getModelJson(id, "bottom");
            output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace, "models/block/" + doorName + "_bottom"),
                    () -> new ByteArrayInputStream(bottomModel));
            byte[] topModel = getModelJson(id, "top");
            output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace, "models/block/" + doorName + "_top"),
                    () -> new ByteArrayInputStream(topModel));

            // Item Model (inventory icon)
            byte[] itemModel = getItemModelJson(id);
            output.accept(
                    ResourceLocation.fromNamespaceAndPath(namespace, "models/item/" + doorName),
                    () -> new ByteArrayInputStream(itemModel));

            // Static doors: textures are in src/main/resources
            if (STATIC_DOOR_IDS.contains(id)) continue;

            TarindoorDefinition def = TarindoorRegistry.getDefinition(id);
            if (def == null) continue;

            // Dynamic doors: textures from zip
            for (String tex : new String[]{"side", "top", "bottom"}) {
                byte[] texData = loadFromZip(def, tex + ".png");
                if (texData != null) {
                    byte[] d = texData;
                    output.accept(
                            ResourceLocation.fromNamespaceAndPath(namespace, "textures/block/" + id + "_" + tex),
                            () -> new ByteArrayInputStream(d));
                }
            }

            // Dynamic doors: sounds from zip (unless using shared sound_event)
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().openSoundFileName());
                if (sndData != null) {
                    byte[] d = sndData;
                    output.accept(
                            ResourceLocation.fromNamespaceAndPath(namespace, "sounds/" + id + "_door_open"),
                            () -> new ByteArrayInputStream(d));
                }
            }
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null) {
                byte[] sndData = loadFromZip(def, def.block().closeSoundFileName());
                if (sndData != null) {
                    byte[] d = sndData;
                    output.accept(
                            ResourceLocation.fromNamespaceAndPath(namespace, "sounds/" + id + "_door_close"),
                            () -> new ByteArrayInputStream(d));
                }
            }
        }

        // Dynamic sounds.json
        byte[] sndJson = getDynamicSoundsJson();
        output.accept(
                ResourceLocation.fromNamespaceAndPath(namespace, "sounds_tarindoor_dynamic"),
                () -> new ByteArrayInputStream(sndJson));
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
    private byte[] loadResource(String path) {
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
        return STATIC_DOOR_IDS.contains(id) || TarindoorRegistry.getDefinition(id) != null;
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
            face.add("uv", uv); face.addProperty("texture", "#0");
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
            face.add("uv", uv); face.addProperty("texture", "#0");
            faces.add("up", face);
        } else {
            JsonObject face = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(8); uv.add(16); uv.add(11);
            face.add("uv", uv); face.addProperty("texture", "#0");
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
            JsonObject e = new JsonObject();
            e.addProperty("subtitle", sk + "_open");
            JsonArray a = new JsonArray(); a.add(NAMESPACE + ":" + id + "_door_open");
            e.add("sounds", a); root.add(id + "_door_open", e);
            e = new JsonObject();
            e.addProperty("subtitle", sk + "_close");
            a = new JsonArray(); a.add(NAMESPACE + ":" + id + "_door_close");
            e.add("sounds", a); root.add(id + "_door_close", e);
        }
        return toBytes(root);
    }

    @Nullable
    private static byte[] loadFromZip(TarindoorDefinition def, String entryName) {
        Path zipPath = TarindoorRegistry.getZipPath(def);
        if (zipPath == null || !Files.exists(zipPath)) return null;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (InputStream is = zip.getInputStream(entry)) { return is.readAllBytes(); }
        } catch (IOException e) { return null; }
    }

    private static byte[] toBytes(JsonObject obj) {
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }
}
