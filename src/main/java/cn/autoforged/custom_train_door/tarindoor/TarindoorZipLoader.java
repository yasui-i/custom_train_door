package cn.autoforged.custom_train_door.tarindoor;

import com.google.gson.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans the tarindoor/ folder in the game root directory,
 * reads .zip files, and parses door.json to build TarindoorDefinition objects.
 */
public class TarindoorZipLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");
    private static final Gson GSON = new Gson();

    /** Path to the tarindoor folder. */
    public static Path getTarindoorDir() {
        return FMLPaths.GAMEDIR.get().resolve("tarindoor");
    }

    /**
     * Load all door definitions from zip files in the tarindoor folder.
     * Creates the folder on first launch if it doesn't exist.
     * Also stores zip path mappings in TarindoorRegistry.
     */
    public static List<TarindoorDefinition> loadDefinitions() {
        Path dir = getTarindoorDir();
        List<TarindoorDefinition> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                createReadme(dir);
                LOGGER.info("Created tarindoor folder at {}", dir);
                return result;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create tarindoor folder: {}", e.getMessage());
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.zip")) {
            for (Path zipPath : stream) {
                try {
                    TarindoorDefinition def = loadFromZip(zipPath);
                    if (def != null) {
                        if (!seenIds.add(def.id())) {
                            LOGGER.warn("Duplicate door id '{}' in {}, skipping", def.id(), zipPath.getFileName());
                            continue;
                        }
                        result.add(def);
                        TarindoorRegistry.storeZipPath(def.id(), zipPath);
                        LOGGER.info("Loaded door '{}' from {}", def.id(), zipPath.getFileName());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load door from {}: {}", zipPath.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list tarindoor folder: {}", e.getMessage());
        }

        LOGGER.info("Loaded {} tarindoor door(s)", result.size());
        return result;
    }

    /**
     * Parse a single zip file into a TarindoorDefinition.
     */
    private static TarindoorDefinition loadFromZip(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            // 1. Read door.json
            ZipEntry jsonEntry = zip.getEntry("door.json");
            if (jsonEntry == null) {
                LOGGER.warn("{} missing door.json", zipPath.getFileName());
                return null;
            }

            JsonObject root;
            try (InputStream is = zip.getInputStream(jsonEntry);
                 Reader reader = new InputStreamReader(is)) {
                root = GSON.fromJson(reader, JsonObject.class);
            }

            // 2. Parse required fields
            String id = getString(root, "id");
            if (id == null || id.isEmpty()) {
                LOGGER.warn("{} door.json missing 'id'", zipPath.getFileName());
                return null;
            }

            // 3. Parse localized names
            Map<String, String> names = new HashMap<>();
            if (root.has("name") && root.get("name").isJsonObject()) {
                JsonObject nameObj = root.getAsJsonObject("name");
                for (Map.Entry<String, JsonElement> e : nameObj.entrySet()) {
                    names.put(e.getKey(), e.getValue().getAsString());
                }
            }

            // 4. Parse animation config
            TarindoorDefinition.TarindoorAnimationConfig animConfig = parseAnimation(root, id);

            // 5. Parse render config
            TarindoorDefinition.TarindoorRenderConfig renderConfig = parseRender(root);

            // 6. Parse block config
            TarindoorDefinition.TarindoorBlockConfig blockConfig = parseBlockConfig(root);

            // 7. Parse recipe config (optional)
            TarindoorDefinition.TarindoorRecipeConfig recipeConfig = parseRecipeConfig(root);

            // 8. Validate texture files exist in zip
            for (String tex : new String[]{"side.png", "top.png", "bottom.png"}) {
                if (zip.getEntry(tex) == null) {
                    LOGGER.warn("{} missing texture {}", zipPath.getFileName(), tex);
                }
            }

            return new TarindoorDefinition(id, names, animConfig, renderConfig, blockConfig, recipeConfig);
        }
    }

    private static TarindoorDefinition.TarindoorAnimationConfig parseAnimation(JsonObject root, String id) {
        if (!root.has("animation")) {
            return TarindoorDefinition.TarindoorAnimationConfig.lerped(1.0 / 120.0);
        }
        JsonObject anim = root.getAsJsonObject("animation");
        String type = getString(anim, "type");

        if ("phased".equals(type)) {
            int totalTicks = getInt(anim, "total_ticks", 130);
            List<TarindoorDefinition.AnimationPhase> opening = parsePhases(anim, "opening");
            List<TarindoorDefinition.AnimationPhase> closing = parsePhases(anim, "closing");
            return TarindoorDefinition.TarindoorAnimationConfig.phased(totalTicks, opening, closing);
        } else {
            double speed = getDouble(anim, "speed", 1.0 / 120.0);
            return TarindoorDefinition.TarindoorAnimationConfig.lerped(speed);
        }
    }

    private static List<TarindoorDefinition.AnimationPhase> parsePhases(JsonObject anim, String key) {
        List<TarindoorDefinition.AnimationPhase> phases = new ArrayList<>();
        if (anim.has(key) && anim.get(key).isJsonArray()) {
            for (JsonElement el : anim.getAsJsonArray(key)) {
                JsonObject phaseObj = el.getAsJsonObject();
                String phaseType = getString(phaseObj, "type");
                int duration = getInt(phaseObj, "duration", 0);
                if ("pause".equals(phaseType)) {
                    phases.add(TarindoorDefinition.AnimationPhase.pause(duration));
                } else {
                    phases.add(TarindoorDefinition.AnimationPhase.animate(duration));
                }
            }
        }
        return phases;
    }

    private static TarindoorDefinition.TarindoorRenderConfig parseRender(JsonObject root) {
        if (!root.has("render")) {
            return TarindoorDefinition.TarindoorRenderConfig.cr400bfStyle();
        }
        JsonObject r = root.getAsJsonObject("render");
        double slideScale = getDouble(r, "slide_scale", 13.0 / 16.0);

        boolean depthEnabled = false;
        double clampMul = 12.0;
        double depthScale = 0.1;
        if (r.has("depth_push") && r.get("depth_push").isJsonObject()) {
            JsonObject dp = r.getAsJsonObject("depth_push");
            depthEnabled = getBool(dp, "enabled", false);
            clampMul = getDouble(dp, "clamp_multiplier", 12.0);
            depthScale = getDouble(dp, "scale", 0.1);
        }

        return new TarindoorDefinition.TarindoorRenderConfig(slideScale, depthEnabled, clampMul, depthScale);
    }

    private static TarindoorDefinition.TarindoorBlockConfig parseBlockConfig(JsonObject root) {
        if (!root.has("block")) {
            return TarindoorDefinition.TarindoorBlockConfig.defaults(null, null);
        }
        JsonObject b = root.getAsJsonObject("block");
        float hardness = (float) getDouble(b, "hardness", 5.0);
        float resistance = (float) getDouble(b, "resistance", 6.0);

        String mapColorName = getString(b, "map_color");
        MapColor mapColor = parseMapColor(mapColorName);

        String soundTypeName = getString(b, "sound_type");
        SoundType soundType = parseSoundType(soundTypeName);

        String openSound = getString(b, "open_sound_file");
        String closeSound = getString(b, "close_sound_file");

        String soundEventOpen = null;
        String soundEventClose = null;
        if (b.has("sound_event") && b.get("sound_event").isJsonObject()) {
            JsonObject se = b.getAsJsonObject("sound_event");
            soundEventOpen = getString(se, "open");
            soundEventClose = getString(se, "close");
        }

        return new TarindoorDefinition.TarindoorBlockConfig(hardness, resistance, mapColor, soundType,
                openSound, closeSound, soundEventOpen, soundEventClose);
    }

    private static TarindoorDefinition.TarindoorRecipeConfig parseRecipeConfig(JsonObject root) {
        if (!root.has("recipe")) return null;
        JsonObject r = root.getAsJsonObject("recipe");

        List<String> pattern = new ArrayList<>();
        if (r.has("pattern") && r.get("pattern").isJsonArray()) {
            for (JsonElement el : r.getAsJsonArray("pattern")) {
                pattern.add(el.getAsString());
            }
        }

        Map<Character, String> keys = new HashMap<>();
        if (r.has("keys") && r.get("keys").isJsonObject()) {
            JsonObject keysObj = r.getAsJsonObject("keys");
            for (Map.Entry<String, JsonElement> e : keysObj.entrySet()) {
                if (e.getKey().length() == 1) {
                    keys.put(e.getKey().charAt(0), e.getValue().getAsString());
                }
            }
        }

        int count = getInt(r, "count", 1);
        return new TarindoorDefinition.TarindoorRecipeConfig(pattern, keys, count);
    }

    // --- JSON helpers ---
    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsInt();
        }
        return def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsDouble();
        }
        return def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsBoolean();
        }
        return def;
    }

    // --- SoundType / MapColor parsing ---
    private static MapColor parseMapColor(String name) {
        if (name == null) return MapColor.METAL;
        return switch (name) {
            case "none" -> MapColor.NONE;
            case "grass" -> MapColor.GRASS;
            case "sand" -> MapColor.SAND;
            case "wool" -> MapColor.WOOL;
            case "fire" -> MapColor.FIRE;
            case "ice" -> MapColor.ICE;
            case "metal" -> MapColor.METAL;
            case "plant" -> MapColor.PLANT;
            case "snow" -> MapColor.SNOW;
            case "clay" -> MapColor.CLAY;
            case "dirt" -> MapColor.DIRT;
            case "stone" -> MapColor.STONE;
            case "water" -> MapColor.WATER;
            case "wood" -> MapColor.WOOD;
            case "quartz" -> MapColor.QUARTZ;
            case "color_orange" -> MapColor.COLOR_ORANGE;
            case "color_magenta" -> MapColor.COLOR_MAGENTA;
            case "color_light_blue" -> MapColor.COLOR_LIGHT_BLUE;
            case "color_yellow" -> MapColor.COLOR_YELLOW;
            case "color_light_green" -> MapColor.COLOR_LIGHT_GREEN;
            case "color_pink" -> MapColor.COLOR_PINK;
            case "color_gray" -> MapColor.COLOR_GRAY;
            case "color_light_gray" -> MapColor.COLOR_LIGHT_GRAY;
            case "color_cyan" -> MapColor.COLOR_CYAN;
            case "color_purple" -> MapColor.COLOR_PURPLE;
            case "color_blue" -> MapColor.COLOR_BLUE;
            case "color_brown" -> MapColor.COLOR_BROWN;
            case "color_green" -> MapColor.COLOR_GREEN;
            case "color_red" -> MapColor.COLOR_RED;
            case "color_black" -> MapColor.COLOR_BLACK;
            case "gold" -> MapColor.GOLD;
            case "diamond" -> MapColor.DIAMOND;
            case "lapis" -> MapColor.LAPIS;
            case "emerald" -> MapColor.EMERALD;
            case "podzol" -> MapColor.PODZOL;
            case "nether" -> MapColor.NETHER;
            case "terracotta_white" -> MapColor.TERRACOTTA_WHITE;
            case "terracotta_orange" -> MapColor.TERRACOTTA_ORANGE;
            case "terracotta_magenta" -> MapColor.TERRACOTTA_MAGENTA;
            case "terracotta_light_blue" -> MapColor.TERRACOTTA_LIGHT_BLUE;
            case "terracotta_yellow" -> MapColor.TERRACOTTA_YELLOW;
            case "terracotta_light_green" -> MapColor.TERRACOTTA_LIGHT_GREEN;
            case "terracotta_pink" -> MapColor.TERRACOTTA_PINK;
            case "terracotta_gray" -> MapColor.TERRACOTTA_GRAY;
            case "terracotta_light_gray" -> MapColor.TERRACOTTA_LIGHT_GRAY;
            case "terracotta_cyan" -> MapColor.TERRACOTTA_CYAN;
            case "terracotta_purple" -> MapColor.TERRACOTTA_PURPLE;
            case "terracotta_blue" -> MapColor.TERRACOTTA_BLUE;
            case "terracotta_brown" -> MapColor.TERRACOTTA_BROWN;
            case "terracotta_green" -> MapColor.TERRACOTTA_GREEN;
            case "terracotta_red" -> MapColor.TERRACOTTA_RED;
            case "terracotta_black" -> MapColor.TERRACOTTA_BLACK;
            case "crimson_nylium" -> MapColor.CRIMSON_NYLIUM;
            case "crimson_stem" -> MapColor.CRIMSON_STEM;
            case "crimson_hyphae" -> MapColor.CRIMSON_HYPHAE;
            case "warped_nylium" -> MapColor.WARPED_NYLIUM;
            case "warped_stem" -> MapColor.WARPED_STEM;
            case "warped_hyphae" -> MapColor.WARPED_HYPHAE;
            default -> MapColor.METAL;
        };
    }

    @SuppressWarnings("deprecation")
    private static SoundType parseSoundType(String name) {
        if (name == null) return SoundType.NETHERITE_BLOCK;
        return switch (name) {
            case "wood" -> SoundType.WOOD;
            case "gravel" -> SoundType.GRAVEL;
            case "grass" -> SoundType.GRASS;
            case "lily_pad" -> SoundType.LILY_PAD;
            case "stone" -> SoundType.STONE;
            case "metal" -> SoundType.METAL;
            case "glass" -> SoundType.GLASS;
            case "wool" -> SoundType.WOOL;
            case "sand" -> SoundType.SAND;
            case "snow" -> SoundType.SNOW;
            case "powder_snow" -> SoundType.POWDER_SNOW;
            case "ladder" -> SoundType.LADDER;
            case "anvil" -> SoundType.ANVIL;
            case "slime_block" -> SoundType.SLIME_BLOCK;
            case "honey_block" -> SoundType.HONEY_BLOCK;
            case "wet_grass" -> SoundType.WET_GRASS;
            case "coral_block" -> SoundType.CORAL_BLOCK;
            case "bamboo" -> SoundType.BAMBOO;
            case "bamboo_sapling" -> SoundType.BAMBOO_SAPLING;
            case "scaffolding" -> SoundType.SCAFFOLDING;
            case "sweet_berry_bush" -> SoundType.SWEET_BERRY_BUSH;
            case "crop" -> SoundType.CROP;
            case "hard_crop" -> SoundType.HARD_CROP;
            case "vine" -> SoundType.VINE;
            case "nether_wood" -> SoundType.NETHER_WOOD;
            case "cherry_wood" -> SoundType.CHERRY_WOOD;
            case "bamboo_wood" -> SoundType.BAMBOO_WOOD;
            case "netherite_block" -> SoundType.NETHERITE_BLOCK;
            case "ancient_debris" -> SoundType.ANCIENT_DEBRIS;
            case "bone_block" -> SoundType.BONE_BLOCK;
            case "netherrack" -> SoundType.NETHERRACK;
            case "nylium" -> SoundType.NYLIUM;
            case "basalt" -> SoundType.BASALT;
            case "soul_soil" -> SoundType.SOUL_SOIL;
            case "polished_deepslate" -> SoundType.POLISHED_DEEPSLATE;
            case "deepslate" -> SoundType.DEEPSLATE;
            case "deepslate_bricks" -> SoundType.DEEPSLATE_BRICKS;
            case "dripstone_block" -> SoundType.DRIPSTONE_BLOCK;
            case "moss" -> SoundType.MOSS;
            case "spore_blossom" -> SoundType.SPORE_BLOSSOM;
            case "tuff" -> SoundType.TUFF;
            case "tuff_bricks" -> SoundType.TUFF_BRICKS;
            case "calcite" -> SoundType.CALCITE;
            case "amethyst" -> SoundType.AMETHYST;
            case "amethyst_cluster" -> SoundType.AMETHYST_CLUSTER;
            case "large_amethyst_bud" -> SoundType.LARGE_AMETHYST_BUD;
            case "pointed_dripstone" -> SoundType.POINTED_DRIPSTONE;
            case "copper" -> SoundType.COPPER;
            case "copper_bulb" -> SoundType.COPPER_BULB;
            case "nether_gold_ore" -> SoundType.NETHER_GOLD_ORE;
            case "nether_ore" -> SoundType.NETHER_ORE;
            case "froglight" -> SoundType.FROGLIGHT;
            case "frogspawn" -> SoundType.FROGSPAWN;
            case "mud" -> SoundType.MUD;
            case "mud_bricks" -> SoundType.MUD_BRICKS;
            case "packed_mud" -> SoundType.PACKED_MUD;
            case "roots" -> SoundType.ROOTS;
            case "moss_carpet" -> SoundType.MOSS_CARPET;
            case "azalea" -> SoundType.AZALEA;
            case "azalea_leaves" -> SoundType.AZALEA_LEAVES;
            case "decorated_pot" -> SoundType.DECORATED_POT;
            case "vault" -> SoundType.VAULT;
            case "heavy_core" -> SoundType.HEAVY_CORE;
            case "cobweb" -> SoundType.COBWEB;
            case "wet_sponge" -> SoundType.WET_SPONGE;
            default -> SoundType.NETHERITE_BLOCK;
        };
    }

    private static void createReadme(Path dir) throws IOException {
        Files.writeString(dir.resolve("README.txt"),
                """
                Tarindoor - Custom Train Door Pack Folder
                ==========================================

                Place .zip files here containing custom door definitions.
                Each zip must contain:
                  - door.json       (door configuration)
                  - side.png        (side texture)
                  - top.png         (top face texture)
                  - bottom.png      (bottom face texture)
                  - door_open.ogg   (optional - open sound)
                  - door_close.ogg  (optional - close sound)

                See the mod documentation for the door.json schema.
                """);
    }
}
