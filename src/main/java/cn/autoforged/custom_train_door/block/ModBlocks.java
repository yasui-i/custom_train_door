package cn.autoforged.custom_train_door.block;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CustomTrainDoorMod.MODID);

    public static final DeferredBlock<CRTrainDoorBlock> CR400BF_DOOR = registerBlock("cr400bf_door",
            () -> new CRTrainDoorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY),
                    ModBlockSetTypes.CR400BF_TRAIN_DOOR,
                    false
            ));

    public static final DeferredBlock<CRH2ADoorBlock> CRH2A_DOOR = registerBlock("crh2a_door",
            () -> new CRH2ADoorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(5.0f, 6.0f)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY),
                    ModBlockSetTypes.CRH2A_TRAIN_DOOR,
                    false
            ));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> deferredBlock = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.registerSimpleBlockItem(deferredBlock);
        return deferredBlock;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
