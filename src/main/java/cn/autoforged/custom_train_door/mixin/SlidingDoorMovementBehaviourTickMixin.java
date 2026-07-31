package cn.autoforged.custom_train_door.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;

import cn.autoforged.custom_train_door.block.CRH2ADoorBlock;
import cn.autoforged.custom_train_door.block.CRTrainDoorBlock;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

@Mixin(SlidingDoorMovementBehaviour.class)
public class SlidingDoorMovementBehaviourTickMixin {

    private static final ThreadLocal<MovementContext> CURRENT_CONTEXT = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void custom_train_door$captureContext(MovementContext context, CallbackInfo ci) {
        CURRENT_CONTEXT.set(context);
    }

    @ModifyArg(
        method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/animation/LerpedFloat;chase(DDLnet/createmod/catnip/animation/LerpedFloat$Chaser;)Lnet/createmod/catnip/animation/LerpedFloat;"),
        index = 1,
        remap = false
    )
    private double custom_train_door$modifyChaseSpeed(double speed) {
        MovementContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) {
            if (ctx.state.getBlock() instanceof TarindoorBlock tb) {
                var definition = tb.getDefinition();
                if (definition != null) return definition.animation().lerpedSpeed();
            }
            if (ctx.state.getBlock() instanceof CRTrainDoorBlock) {
                return 1.0 / 120.0;
            }
            if (ctx.state.getBlock() instanceof CRH2ADoorBlock) {
                return 1.0 / 130.0;
            }
        }
        return speed;
    }

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"),
        remap = true
    )
    private void custom_train_door$suppressPlayLocalSound(Level level, double x, double y, double z,
                                                          SoundEvent sound, SoundSource source,
                                                          float volume, float pitch, boolean distanceDelay) {
        MovementContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null && (ctx.state.getBlock() instanceof CRTrainDoorBlock
                || ctx.state.getBlock() instanceof CRH2ADoorBlock
                || ctx.state.getBlock() instanceof TarindoorBlock)) {
            return;
        }
        level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
    }
}
