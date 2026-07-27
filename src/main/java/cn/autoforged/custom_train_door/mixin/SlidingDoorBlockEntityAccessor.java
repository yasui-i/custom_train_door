package cn.autoforged.custom_train_door.mixin;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SlidingDoorBlockEntity.class)
public interface SlidingDoorBlockEntityAccessor {

    @Accessor("animation")
    LerpedFloat custom_train_door$getAnimation();

    @Accessor("deferUpdate")
    boolean custom_train_door$getDeferUpdate();

    @Accessor("deferUpdate")
    void custom_train_door$setDeferUpdate(boolean deferUpdate);
}
