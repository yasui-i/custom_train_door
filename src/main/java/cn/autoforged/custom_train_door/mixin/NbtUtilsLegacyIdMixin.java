package cn.autoforged.custom_train_door.mixin;

import cn.autoforged.custom_train_door.tarindoor.LegacyTarindoorIdMigration;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NbtUtils.class)
public abstract class NbtUtilsLegacyIdMixin {
    @Inject(method = "readBlockState", at = @At("HEAD"))
    private static void customTrainDoor$migrateLegacyBlockState(
            HolderGetter<Block> blocks,
            CompoundTag tag,
            CallbackInfoReturnable<BlockState> callback
    ) {
        LegacyTarindoorIdMigration.migrateBlockState(tag);
    }
}
