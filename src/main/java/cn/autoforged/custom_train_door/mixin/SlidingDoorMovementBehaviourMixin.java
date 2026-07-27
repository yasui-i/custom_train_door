package cn.autoforged.custom_train_door.mixin;

import cn.autoforged.custom_train_door.block.CRH2ADoorBlock;
import cn.autoforged.custom_train_door.block.CRTrainDoorBlock;
import cn.autoforged.custom_train_door.sound.ModSounds;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlidingDoorMovementBehaviour.class)
public abstract class SlidingDoorMovementBehaviourMixin {

    @Invoker("shouldOpen")
    protected abstract boolean invokeShouldOpen(MovementContext context);

    @Invoker("shouldUpdate")
    protected abstract boolean invokeShouldUpdate(MovementContext context, boolean shouldOpen);

    @Invoker("getDoorFacing")
    protected abstract Direction invokeGetDoorFacing(MovementContext context);

    @Inject(method = "tickOpen", at = @At("HEAD"), cancellable = true, remap = false)
    private void custom_train_door$tickOpen(MovementContext context, boolean currentlyOpen, CallbackInfo ci) {
        if (!(context.state.getBlock() instanceof CRTrainDoorBlock
                || context.state.getBlock() instanceof CRH2ADoorBlock
                || context.state.getBlock() instanceof TarindoorBlock)) {
            return;
        }
        ci.cancel();
        boolean shouldOpen = this.invokeShouldOpen(context);
        if (!this.invokeShouldUpdate(context, shouldOpen)) {
            return;
        }
        if (currentlyOpen != shouldOpen) {
            BlockPos pos = context.localPos;
            Contraption contraption = context.contraption;
            StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(pos);
            if (info != null && info.state().hasProperty(DoorBlock.OPEN)) {
                BlockState newState = info.state().cycle(DoorBlock.OPEN);
                contraption.entity.setBlock(pos,
                        new StructureTemplate.StructureBlockInfo(info.pos(), newState, info.nbt()));
                BlockPos otherPos = newState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        ? pos.above() : pos.below();
                StructureTemplate.StructureBlockInfo otherInfo = contraption.getBlocks().get(otherPos);
                if (otherInfo != null && otherInfo.state().hasProperty(DoorBlock.OPEN)) {
                    BlockState otherState = otherInfo.state().cycle(DoorBlock.OPEN);
                    contraption.entity.setBlock(otherPos,
                            new StructureTemplate.StructureBlockInfo(otherInfo.pos(), otherState, otherInfo.nbt()));
                    contraption.invalidateColliders();
                }

                Direction facing = this.invokeGetDoorFacing(context);
                BlockPos inWorldDoor = BlockPos.containing(context.position).relative(facing);
                BlockState inWorldDoorState = context.world.getBlockState(inWorldDoor);
                if (inWorldDoorState.getBlock() instanceof DoorBlock db
                        && inWorldDoorState.hasProperty(DoorBlock.OPEN)
                        && inWorldDoorState.hasProperty(DoorBlock.FACING)
                        && inWorldDoorState.getOptionalValue(DoorBlock.FACING)
                                .orElse(Direction.UP).getAxis() == facing.getAxis()) {
                    db.setOpen(null, context.world, inWorldDoorState, inWorldDoor, shouldOpen);
                }

                boolean isCRH2A = context.state.getBlock() instanceof CRH2ADoorBlock;
                boolean isTarindoor = context.state.getBlock() instanceof TarindoorBlock;
                if (isTarindoor) {
                    TarindoorBlock tb = (TarindoorBlock) context.state.getBlock();
                    String id = tb.getDefinition().id();
                    if (shouldOpen) {
                        var snd = TarindoorRegistry.getOpenSound(id);
                        if (snd != null) {
                            context.world.playSound(null, BlockPos.containing(context.position),
                                    snd.get(), SoundSource.BLOCKS, 0.125F, 1.0F);
                        }
                    } else {
                        var snd = TarindoorRegistry.getCloseSound(id);
                        if (snd != null) {
                            context.world.playSound(null, BlockPos.containing(context.position),
                                    snd.get(), SoundSource.BLOCKS, 0.125F, 1.0F);
                        }
                    }
                } else if (shouldOpen) {
                    context.world.playSound(null, BlockPos.containing(context.position),
                            isCRH2A ? ModSounds.CRH2A_DOOR_OPEN.get() : ModSounds.CR400BF_DOOR_OPEN.get(),
                            SoundSource.BLOCKS, 0.125F, 1.0F);
                } else {
                    context.world.playSound(null, BlockPos.containing(context.position),
                            isCRH2A ? ModSounds.CRH2A_DOOR_CLOSE.get() : ModSounds.CR400BF_DOOR_CLOSE.get(),
                            SoundSource.BLOCKS, 0.125F, 1.0F);
                }
            }
        }
    }
}
