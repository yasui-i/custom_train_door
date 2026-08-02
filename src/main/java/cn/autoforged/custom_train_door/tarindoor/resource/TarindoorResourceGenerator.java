package cn.autoforged.custom_train_door.tarindoor.resource;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.google.gson.*;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipFile;

public class TarindoorResourceGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");
    private static final String NAMESPACE = CustomTrainDoorMod.MODID;
    private static final Set<String> STATIC_DOOR_IDS = Set.of("cr400bf", "crh2a");

    public static Path getOutputDir() {
        return FMLPaths.GAMEDIR.get().resolve("tarindoor_resources");
    }

    public static void generate() {
        Path root = getOutputDir();
        Path assets = root.resolve("assets").resolve(NAMESPACE);
        Path packMcmeta = root.resolve("pack.mcmeta");

        // Skip if already generated and pack.mcmeta exists
        if (Files.exists(packMcmeta)) return;

        try {
            Files.createDirectories(assets.resolve("blockstates"));
            Files.createDirectories(assets.resolve("models/block"));
            Files.createDirectories(assets.resolve("models/item"));
            Files.createDirectories(assets.resolve("textures/block"));
            Files.createDirectories(assets.resolve("sounds"));
        } catch (IOException e) {
            LOGGER.error("Failed to create tarindoor resource dirs: {}", e.getMessage());
            return;
        }

        Set<String> ids = new LinkedHashSet<>(STATIC_DOOR_IDS);
        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) ids.add(def.id());

        for (String id : ids) {
            writeJson(assets.resolve("blockstates/" + id + "_door.json"), makeBlockstate(id));
            writeJson(assets.resolve("models/block/" + id + "_door_bottom.json"), makeModel(id, "bottom"));
            writeJson(assets.resolve("models/block/" + id + "_door_top.json"), makeModel(id, "top"));
            writeJson(assets.resolve("models/item/" + id + "_door.json"), makeItemModel(id));

            if (STATIC_DOOR_IDS.contains(id)) continue;

            TarindoorDefinition def = TarindoorRegistry.getDefinition(id);
            if (def == null) continue;

            for (String tex : new String[]{"side", "top", "bottom"}) {
                extract(def, tex + ".png", assets.resolve("textures/block/" + id + "_" + tex + ".png"));
            }
            if (def.block().soundEventOpen() == null && def.block().openSoundFileName() != null)
                extract(def, def.block().openSoundFileName(), assets.resolve("sounds/" + id + "_door_open.ogg"));
            if (def.block().soundEventClose() == null && def.block().closeSoundFileName() != null)
                extract(def, def.block().closeSoundFileName(), assets.resolve("sounds/" + id + "_door_close.ogg"));
        }

        JsonObject mcmeta = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Tarindoor generated resources");
        pack.addProperty("pack_format", 34);
        mcmeta.add("pack", pack);
        writeJson(packMcmeta, mcmeta);
        LOGGER.info("Generated tarindoor resources for {} door(s)", ids.size());
    }

    private static JsonObject makeBlockstate(String id) {
        JsonObject root = new JsonObject();
        JsonObject variants = new JsonObject();
        String modelNs = NAMESPACE + ":block/" + id + "_door";
        for (String dir : new String[]{"east", "south", "west", "north"}) {
            for (String hinge : new String[]{"left", "right"}) {
                for (String half : new String[]{"lower", "upper"}) {
                    for (String open : new String[]{"false", "true"}) {
                        String key = String.format("facing=%s,half=%s,hinge=%s,open=%s", dir, half, hinge, open);
                        JsonObject v = new JsonObject();
                        v.addProperty("model", modelNs + "_" + (half.equals("lower") ? "bottom" : "top"));
                        variants.add(key, v);
                    }
                }
            }
        }
        root.add("variants", variants);
        return root;
    }

    private static JsonObject makeModel(String id, String half) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        JsonObject tex = new JsonObject();
        tex.addProperty("0", NAMESPACE + ":block/" + id + "_side");
        tex.addProperty("2", NAMESPACE + ":block/" + id + "_" + half);
        tex.addProperty("particle", NAMESPACE + ":block/" + id + "_" + half);
        root.add("textures", tex);

        JsonArray els = new JsonArray();
        JsonObject el = new JsonObject();
        JsonArray from = new JsonArray(); from.add(0); from.add(0); from.add(0);
        JsonArray to   = new JsonArray(); to.add(3);   to.add(16);  to.add(16);
        el.add("from", from); el.add("to", to);
        JsonObject faces = new JsonObject();
        for (String d : new String[]{"north", "south"}) {
            JsonObject f = new JsonObject();
            JsonArray uv = new JsonArray();
            uv.add(0); uv.add("top".equals(half) ? 4 : 12);
            uv.add(16); uv.add("top".equals(half) ? 7 : 15);
            f.add("uv", uv); f.addProperty("texture", "#0"); faces.add(d, f);
        }
        for (String d : new String[]{"east", "west"}) {
            JsonObject f = new JsonObject();
            JsonArray uv = new JsonArray(); uv.add(0); uv.add(0); uv.add(16); uv.add(16);
            f.add("uv", uv); f.addProperty("texture", "#2"); faces.add(d, f);
        }
        JsonObject f = new JsonObject();
        if ("top".equals(half)) {
            f.add("uv", uv(0, 0, 16, 3)); f.addProperty("texture", "#0"); faces.add("up", f);
        } else {
            f.add("uv", uv(0, 8, 16, 11)); f.addProperty("texture", "#0"); faces.add("down", f);
        }
        el.add("faces", faces);
        els.add(el);
        root.add("elements", els);
        return root;
    }

    private static JsonArray uv(int a, int b, int c, int d) {
        JsonArray arr = new JsonArray(); arr.add(a); arr.add(b); arr.add(c); arr.add(d);
        return arr;
    }

    private static JsonObject makeItemModel(String id) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", NAMESPACE + ":block/" + id + "_door_bottom");
        return root;
    }

    private static void writeJson(Path target, JsonObject obj) {
        try {
            Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(obj));
        } catch (IOException ignored) {}
    }

    private static void extract(TarindoorDefinition def, String entryName, Path target) {
        if (Files.exists(target)) return;
        Path zipPath = TarindoorRegistry.getZipPath(def);
        if (zipPath == null || !Files.exists(zipPath)) return;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return;
            try (InputStream is = zip.getInputStream(entry)) {
                Files.write(target, is.readAllBytes());
            }
        } catch (IOException ignored) {}
    }
}
