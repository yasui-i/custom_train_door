package cn.autoforged.custom_train_door.block;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.sound.ModSounds;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class ModBlockSetTypes {
    public static final BlockSetType CR400BF_TRAIN_DOOR = BlockSetType.register(
            new BlockSetType(
                    CustomTrainDoorMod.MODID + ":cr400bf_train_door",
                    true,
                    true,
                    true,
                    BlockSetType.PressurePlateSensitivity.EVERYTHING,
                    SoundType.NETHERITE_BLOCK,
                    ModSounds.CR400BF_DOOR_CLOSE.get(),
                    ModSounds.CR400BF_DOOR_OPEN.get(),
                    SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
                    SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundEvents.STONE_BUTTON_CLICK_ON
            )
    );

    public static final BlockSetType CRH2A_TRAIN_DOOR = BlockSetType.register(
            new BlockSetType(
                    CustomTrainDoorMod.MODID + ":crh2a_train_door",
                    true,
                    true,
                    true,
                    BlockSetType.PressurePlateSensitivity.EVERYTHING,
                    SoundType.NETHERITE_BLOCK,
                    ModSounds.CRH2A_DOOR_CLOSE.get(),
                    ModSounds.CRH2A_DOOR_OPEN.get(),
                    SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundEvents.IRON_TRAPDOOR_OPEN,
                    SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
                    SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
                    SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundEvents.STONE_BUTTON_CLICK_ON
            )
    );
}
