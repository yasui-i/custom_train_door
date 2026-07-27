package cn.autoforged.custom_train_door.datagen;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {

    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                               @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, CustomTrainDoorMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.CR400BF_DOOR.get())
                .add(ModBlocks.CRH2A_DOOR.get());
        tag(BlockTags.DOORS)
                .add(ModBlocks.CR400BF_DOOR.get())
                .add(ModBlocks.CRH2A_DOOR.get());
        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.CR400BF_DOOR.get())
                .add(ModBlocks.CRH2A_DOOR.get());
    }
}
