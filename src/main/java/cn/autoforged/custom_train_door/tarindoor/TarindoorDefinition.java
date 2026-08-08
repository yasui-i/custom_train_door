package cn.autoforged.custom_train_door.tarindoor;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete door definition parsed from a tarindoor zip's door.json.
 */
public record TarindoorDefinition(
        String id,
        Map<String, String> localizedNames,
        TarindoorAnimationConfig animation,
        TarindoorRenderConfig render,
        TarindoorBlockConfig block,
        TarindoorRecipeConfig recipe
) {
    /** Animation type. */
    public enum AnimationType {
        LERPED,
        PHASED
    }

    /** A single phase in the animation timeline. */
    public enum PhaseType {
        PAUSE,    // hold the current value
        ANIMATE   // linearly interpolate toward the target
    }

    /** A phase with type and duration in ticks. */
    public record AnimationPhase(PhaseType type, int durationTicks) {
        public static AnimationPhase pause(int ticks) { return new AnimationPhase(PhaseType.PAUSE, ticks); }
        public static AnimationPhase animate(int ticks) { return new AnimationPhase(PhaseType.ANIMATE, ticks); }
    }

    /** Animation parameters. */
    public record TarindoorAnimationConfig(
            AnimationType type,
            double lerpedSpeed,           // used when type == LERPED
            int phasedTotalTicks,         // used when type == PHASED
            List<AnimationPhase> openingPhases,
            List<AnimationPhase> closingPhases
    ) {
        /** Lerped animation with custom speed. */
        public static TarindoorAnimationConfig lerped(double speed) {
            return new TarindoorAnimationConfig(AnimationType.LERPED, speed, 0, List.of(), List.of());
        }

        /** Phased animation with custom phase lists. */
        public static TarindoorAnimationConfig phased(int totalTicks, List<AnimationPhase> opening, List<AnimationPhase> closing) {
            return new TarindoorAnimationConfig(AnimationType.PHASED, 0, totalTicks, opening, closing);
        }

        /** Phased animation mimicking CRH2A: open pause 72 + move 58, close move 90 + pause 6 + move 34. */
        public static TarindoorAnimationConfig crh2aStyle() {
            return phased(130,
                    List.of(AnimationPhase.pause(72), AnimationPhase.animate(58)),
                    List.of(AnimationPhase.animate(90), AnimationPhase.pause(6), AnimationPhase.animate(34))
            );
        }
    }

    /** Rendering parameters. */
    public record TarindoorRenderConfig(
            double slideScale,
            boolean depthPushEnabled,
            double depthPushClampMultiplier,
            double depthPushScale
    ) {
        /** CR400BF-style: slide 13/16 + depth push 0.1. */
        public static TarindoorRenderConfig cr400bfStyle() {
            return new TarindoorRenderConfig(13.0 / 16.0, true, 12.0, 0.1);
        }

        /** CRH2A-style: slide 9/10, no depth push. */
        public static TarindoorRenderConfig crh2aStyle() {
            return new TarindoorRenderConfig(9.0 / 10.0, false, 0, 0);
        }
    }

    /** Block properties. */
    public record TarindoorBlockConfig(
            float hardness,
            float resistance,
            MapColor mapColor,
            SoundType soundType,
            @org.jetbrains.annotations.Nullable String openSoundFileName,
            @org.jetbrains.annotations.Nullable String closeSoundFileName,
            @org.jetbrains.annotations.Nullable String soundEventOpen,
            @org.jetbrains.annotations.Nullable String soundEventClose
    ) {
        static final Set<String> VALID_SOUND_TYPES = Set.of(
                "wood", "gravel", "grass", "lily_pad", "stone", "metal",
                "glass", "wool", "sand", "snow", "powder_snow", "ladder",
                "anvil", "slime_block", "honey_block", "wet_grass", "coral_block",
                "bamboo", "bamboo_sapling", "scaffolding", "sweet_berry_bush",
                "crop", "hard_crop", "vine", "nether_wood", "cherry_wood",
                "bamboo_wood", "netherite_block", "ancient_debris", "bone_block",
                "netherrack", "nylium", "basalt", "soul_soil", "polished_deepslate",
                "deepslate", "deepslate_bricks", "dripstone_block", "moss",
                "spore_blossom", "tuff", "tuff_bricks", "calcite", "amethyst",
                "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
                "small_amethyst_bud", "pointed_dripstone", "copper", "copper_bulb",
                "nether_gold_ore", "nether_ore", "froglight", "frogspawn",
                "mud", "muddy_mangrove_roots", "mud_bricks", "packed_mud",
                "hanging_roots", "roots", "moss_carpet", "cave_vines",
                "nether_sprouts", "azalea", "azalea_leaves", "big_dripleaf",
                "decorated_pot", "decorated_pot_cracked", "trial_spawner",
                "vault", "heavy_core", "cobweb", "wet_sponge"
        );

        public static TarindoorBlockConfig defaults(String openSoundFile, String closeSoundFile) {
            return new TarindoorBlockConfig(
                    5.0f, 6.0f,
                    MapColor.METAL,
                    SoundType.NETHERITE_BLOCK,
                    openSoundFile,
                    closeSoundFile,
                    null,
                    null
            );
        }
    }

    /** Optional recipe definition. */
    public record TarindoorRecipeConfig(
            List<String> pattern,
            Map<Character, String> keys,
            int count
    ) {
        /** 6 iron ingots in 3x2 pattern. */
        public static TarindoorRecipeConfig ironDoor() {
            return new TarindoorRecipeConfig(
                    List.of("II", "II", "II"),
                    Map.of('I', "minecraft:iron_ingot"),
                    1
            );
        }
    }

    /** Get the English display name (fallback: id). */
    public String displayName() {
        if (localizedNames.containsKey("en_us")) return localizedNames.get("en_us");
        if (localizedNames.containsKey("zh_cn")) return localizedNames.get("zh_cn");
        return id;
    }
}
