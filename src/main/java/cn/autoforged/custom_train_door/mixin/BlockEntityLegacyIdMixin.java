package cn.autoforged.custom_train_door.mixin;

import cn.autoforged.custom_train_door.tarindoor.LegacyTarindoorIdMigration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public abstract class BlockEntityLegacyIdMixin {
    @Inject(method = "loadStatic", at = @At("HEAD"))
    private static void customTrainDoor$migrateLegacyBlockEntity(
            BlockPos pos,
            BlockState state,
            CompoundTag tag,
            HolderLookup.Provider registries,
            CallbackInfoReturnable<BlockEntity> callback
    ) {
        LegacyTarindoorIdMigration.migrateBlockEntity(tag);
    }
}
