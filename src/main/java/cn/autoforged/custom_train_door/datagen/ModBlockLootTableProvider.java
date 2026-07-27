package cn.autoforged.custom_train_door.datagen;

import cn.autoforged.custom_train_door.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

public class ModBlockLootTableProvider extends BlockLootSubProvider {

    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, registries);
    }

    @Override
    protected void generate() {
        add(ModBlocks.CR400BF_DOOR.get(), this::createDoorTable);
        add(ModBlocks.CRH2A_DOOR.get(), this::createDoorTable);
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(e -> (Block) e.get())
                .toList();
    }
}
