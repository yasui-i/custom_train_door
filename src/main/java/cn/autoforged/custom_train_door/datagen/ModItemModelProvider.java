package cn.autoforged.custom_train_door.datagen;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CustomTrainDoorMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        String doorName = BuiltInRegistries.BLOCK.getKey(ModBlocks.CR400BF_DOOR.get()).getPath();
        withExistingParent(doorName,
                modLoc("block/" + doorName + "_bottom"));

        String crh2aDoorName = BuiltInRegistries.BLOCK.getKey(ModBlocks.CRH2A_DOOR.get()).getPath();
        withExistingParent(crh2aDoorName,
                modLoc("block/" + crh2aDoorName + "_bottom"));
    }
}
