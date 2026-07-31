package cn.autoforged.custom_train_door.tarindoor;

import com.google.gson.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans the tarindoor/ folder in the game root directory,
 * reads .zip files, and parses door.json to build TarindoorDefinition objects.
 */
public class TarindoorZipLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/Tarindoor");
    private static final Gson GSON = new Gson();
    private static final Pattern DOOR_ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Pattern LOCALE_ID = Pattern.compile("[a-z0-9_]{2,16}");
    private static final Set<String> RESERVED_IDS = Set.of("cr400bf", "crh2a");
    private static final int MAX_JSON_BYTES = 256 * 1024;

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
        return loadDefinitions(getTarindoorDir(), true);
    }

    /**
     * Load definitions from an explicit directory. Server-synchronized client
     * caches use {@code createUserFiles=false} so they never receive README or
     * other local configuration files.
     */
    public static List<TarindoorDefinition> loadDefinitions(Path dir, boolean createUserFiles) {
        List<TarindoorDefinition> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                if (createUserFiles) createReadme(dir);
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

            byte[] jsonBytes = readEntryBytes(zip, jsonEntry, MAX_JSON_BYTES);
            JsonObject root = GSON.fromJson(
                    new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null) {
                LOGGER.warn("{} contains an empty door.json", zipPath.getFileName());
                return null;
            }

            // 2. Parse required fields
            String id = getString(root, "id");
            if (id == null || !DOOR_ID.matcher(id).matches() || RESERVED_IDS.contains(id)) {
                LOGGER.warn("{} has invalid or reserved door id '{}'; expected [a-z0-9_] and at most 48 characters",
                        zipPath.getFileName(), id);
                return null;
            }

            // 3. Parse localized names
            Map<String, String> names = new HashMap<>();
            if (root.has("name") && root.get("name").isJsonObject()) {
                JsonObject nameObj = root.getAsJsonObject("name");
                for (Map.Entry<String, JsonElement> e : nameObj.entrySet()) {
                    if (LOCALE_ID.matcher(e.getKey()).matches() && e.getValue().isJsonPrimitive()) {
                        names.put(e.getKey(), e.getValue().getAsString());
                    }
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
                    LOGGER.warn("{} missing required texture {}, skipping", zipPath.getFileName(), tex);
                    return null;
                }
            }

            TarindoorDefinition definition =
                    new TarindoorDefinition(id, Map.copyOf(names), animConfig, renderConfig, blockConfig, recipeConfig);
            validateDefinition(definition);
            validateReferencedSound(zip, zipPath, definition.block().soundEventOpen(),
                    definition.block().openSoundFileName(), "open");
            validateReferencedSound(zip, zipPath, definition.block().soundEventClose(),
                    definition.block().closeSoundFileName(), "close");
            return definition;
        }
    }

    private static void validateReferencedSound(ZipFile zip, Path zipPath, String soundEvent,
                                                String fileName, String action) throws IOException {
        if (soundEvent == null && fileName != null && zip.getEntry(fileName) == null) {
            throw new IOException(zipPath.getFileName() + " references missing " + action
                    + " sound file " + fileName);
        }
    }

    private static void validateDefinition(TarindoorDefinition def) {
        if (def.animation().type() == TarindoorDefinition.AnimationType.LERPED) {
            requireFiniteRange("animation.speed", def.animation().lerpedSpeed(), 0.000001, 1.0);
        } else {
            validatePhases("animation.opening", def.animation().openingPhases());
            validatePhases("animation.closing", def.animation().closingPhases());
        }
        requireFiniteRange("render.slide_scale", def.render().slideScale(), 0.0, 4.0);
        requireFiniteRange("render.depth_push.clamp_multiplier",
                def.render().depthPushClampMultiplier(), 0.0, 100.0);
        requireFiniteRange("render.depth_push.scale", def.render().depthPushScale(), -4.0, 4.0);
        requireFiniteRange("block.hardness", def.block().hardness(), 0.0, 10000.0);
        requireFiniteRange("block.resistance", def.block().resistance(), 0.0, 10000.0);

        validateResourceLocation("block.sound_event.open", def.block().soundEventOpen());
        validateResourceLocation("block.sound_event.close", def.block().soundEventClose());
        validateZipEntryName("block.open_sound_file", def.block().openSoundFileName());
        validateZipEntryName("block.close_sound_file", def.block().closeSoundFileName());
        validateRecipe(def.recipe());
    }

    private static void validatePhases(String field, List<TarindoorDefinition.AnimationPhase> phases) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException(field + " must contain at least one phase");
        }
        long total = 0;
        for (TarindoorDefinition.AnimationPhase phase : phases) {
            if (phase.durationTicks() <= 0) {
                throw new IllegalArgumentException(field + " durations must be positive");
            }
            total += phase.durationTicks();
        }
        if (total > 20L * 60L * 10L) {
            throw new IllegalArgumentException(field + " is longer than 10 minutes");
        }
    }

    private static void validateRecipe(TarindoorDefinition.TarindoorRecipeConfig recipe) {
        if (recipe == null) return;
        if (recipe.pattern().isEmpty() || recipe.pattern().size() > 3) {
            throw new IllegalArgumentException("recipe.pattern must contain 1 to 3 rows");
        }
        int width = recipe.pattern().getFirst().length();
        if (width < 1 || width > 3) {
            throw new IllegalArgumentException("recipe.pattern width must be 1 to 3");
        }
        Set<Character> usedKeys = new HashSet<>();
        for (String row : recipe.pattern()) {
            if (row.length() != width) {
                throw new IllegalArgumentException("recipe.pattern rows must have equal width");
            }
            row.chars().mapToObj(c -> (char) c).filter(c -> c != ' ').forEach(usedKeys::add);
        }
        if (!recipe.keys().keySet().containsAll(usedKeys)) {
            throw new IllegalArgumentException("recipe.keys is missing a symbol used by recipe.pattern");
        }
        for (Map.Entry<Character, String> entry : recipe.keys().entrySet()) {
            if (entry.getKey() == ' ' || ResourceLocation.tryParse(entry.getValue()) == null) {
                throw new IllegalArgumentException("recipe.keys contains an invalid ingredient");
            }
        }
        if (recipe.count() < 1 || recipe.count() > 64) {
            throw new IllegalArgumentException("recipe.count must be between 1 and 64");
        }
    }

    private static void validateResourceLocation(String field, String value) {
        if (value != null && ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException(field + " is not a valid resource location");
        }
    }

    private static void validateZipEntryName(String field, String value) {
        if (value == null) return;
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || value.contains("\\") || value.startsWith("/")
                || path.startsWith("..") || value.isBlank()) {
            throw new IllegalArgumentException(field + " is not a safe ZIP entry name");
        }
    }

    private static void requireFiniteRange(String field, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }

    private static byte[] readEntryBytes(ZipFile zip, ZipEntry entry, int maxBytes) throws IOException {
        if (entry.getSize() > maxBytes) {
            throw new IOException(entry.getName() + " exceeds " + maxBytes + " bytes");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] data = input.readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IOException(entry.getName() + " exceeds " + maxBytes + " bytes");
            }
            return data;
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
