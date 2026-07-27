package cn.autoforged.custom_train_door.datagen;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.DoorBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CustomTrainDoorMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        registerCustomDoor(ModBlocks.CR400BF_DOOR.get(), "cr400bf_door");
        registerCustomDoor(ModBlocks.CRH2A_DOOR.get(), "crh2a_door");
    }

    private void registerCustomDoor(DoorBlock door, String texName) {
        String name = BuiltInRegistries.BLOCK.getKey(door).getPath();

        ModelFile bottomModel = models().getBuilder(name + "_bottom")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .renderType("cutout_mipped")
                .texture("0", modLoc("block/" + texName + "_side"))
                .texture("2", modLoc("block/" + texName + "_bottom"))
                .texture("particle", modLoc("block/" + texName + "_bottom"))
                .element().from(0, 0, 0).to(3, 16, 16)
                .face(Direction.NORTH).uvs(0, 12, 16, 15).rotation(ModelBuilder.FaceRotation.COUNTERCLOCKWISE_90).texture("#0").end()
                .face(Direction.EAST).uvs(0, 0, 16, 16).texture("#2").end()
                .face(Direction.SOUTH).uvs(0, 12, 16, 15).rotation(ModelBuilder.FaceRotation.COUNTERCLOCKWISE_90).texture("#0").end()
                .face(Direction.WEST).uvs(0, 0, 16, 16).texture("#2").end()
                .face(Direction.DOWN).uvs(0, 8, 16, 11).rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).texture("#0").end()
                .end();

        ModelFile topModel = models().getBuilder(name + "_top")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .renderType("cutout_mipped")
                .texture("0", modLoc("block/" + texName + "_side"))
                .texture("2", modLoc("block/" + texName + "_top"))
                .texture("particle", modLoc("block/" + texName + "_top"))
                .element().from(0, 0, 0).to(3, 16, 16)
                .face(Direction.NORTH).uvs(0, 4, 16, 7).rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).texture("#0").end()
                .face(Direction.EAST).uvs(0, 0, 16, 16).texture("#2").end()
                .face(Direction.SOUTH).uvs(0, 4, 16, 7).rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).texture("#0").end()
                .face(Direction.WEST).uvs(0, 0, 16, 16).texture("#2").end()
                .face(Direction.UP).uvs(0, 0, 16, 3).rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).texture("#0").end()
                .end();

        doorBlock(door,
                bottomModel, bottomModel, bottomModel, bottomModel,
                topModel, topModel, topModel, topModel);
    }
}
