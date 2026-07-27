package cn.autoforged.custom_train_door.block;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import cn.autoforged.custom_train_door.sound.ModSounds;

public class CRTrainDoorBlockEntity extends SlidingDoorBlockEntity {

    public CRTrainDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CUSTOM_TRAIN_DOOR.get(), pos, state);
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide()) {
            boolean open = isOpen(this.getBlockState());
            boolean wasSettled = ((SlidingDoorBlockEntityAccessor) this).custom_train_door$getAnimation().settled();
            if (!open && wasSettled && !this.isVisible(this.getBlockState())) {
                this.level.playSound(null, this.worldPosition,
                        ModSounds.CR400BF_DOOR_CLOSE.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
            }
        }
        super.tick();
    }

    @Override
    protected void showBlockModel() {
        this.level.setBlock(this.worldPosition,
                (BlockState) this.getBlockState().setValue(SlidingDoorBlock.VISIBLE, true), 3);
    }
}
