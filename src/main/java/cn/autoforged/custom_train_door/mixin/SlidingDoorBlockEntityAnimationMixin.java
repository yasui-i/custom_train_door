package cn.autoforged.custom_train_door.mixin;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import cn.autoforged.custom_train_door.block.CRH2ADoorBlockEntity;
import cn.autoforged.custom_train_door.block.CRTrainDoorBlockEntity;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlockEntity;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorPhasedBlockEntity;

@Mixin(SlidingDoorBlockEntity.class)
public class SlidingDoorBlockEntityAnimationMixin {

    @ModifyArg(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/animation/LerpedFloat;chase(DDLnet/createmod/catnip/animation/LerpedFloat$Chaser;)Lnet/createmod/catnip/animation/LerpedFloat;"),
        index = 1,
        remap = false
    )
    private double custom_train_door$modifyChaseSpeed(double speed) {
        if ((Object) this instanceof TarindoorBlockEntity) {
            TarindoorBlockEntity tbe = (TarindoorBlockEntity) (Object) this;
            if (tbe.getBlockState().getBlock() instanceof TarindoorBlock block) {
                var definition = block.getDefinition();
                if (definition != null) return definition.animation().lerpedSpeed();
            }
        }
        if ((Object) this instanceof TarindoorPhasedBlockEntity) {
            return 0;
        }
        if ((Object) this instanceof CRTrainDoorBlockEntity) {
            return 1.0 / 120.0;
        }
        if ((Object) this instanceof CRH2ADoorBlockEntity) {
            return 0;
        }
        return speed;
    }
}
