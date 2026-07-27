package cn.autoforged.custom_train_door.block;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import cn.autoforged.custom_train_door.sound.ModSounds;

public class CRH2ADoorBlockEntity extends SlidingDoorBlockEntity {

    private static final int TOTAL_TICKS = 130;
    private static final int OPEN_PAUSE_TICKS = 72;
    private static final int CLOSE_PHASE1_TICKS = 90;
    private static final int CLOSE_PAUSE_TICKS = 6;
    private static final int CLOSE_PHASE2_TICKS = TOTAL_TICKS - CLOSE_PHASE1_TICKS - CLOSE_PAUSE_TICKS;
    private static final double CLOSE_SPEED = 1.0 / (CLOSE_PHASE1_TICKS + CLOSE_PHASE2_TICKS);

    private int animationTick = 0;
    private boolean wasOpen = false;
    private boolean animationCompleted = true;

    public CRH2ADoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRH2A_TRAIN_DOOR.get(), pos, state);
    }

    @Override
    public void tick() {
        SlidingDoorBlockEntityAccessor acc = (SlidingDoorBlockEntityAccessor) this;
        if (acc.custom_train_door$getDeferUpdate() && !this.level.isClientSide()) {
            acc.custom_train_door$setDeferUpdate(false);
            this.getBlockState().handleNeighborChanged(this.level, this.worldPosition,
                    Blocks.AIR, this.worldPosition, false);
        }

        super.tick();

        boolean open = isOpen(this.getBlockState());

        if (open != wasOpen) {
            animationTick = 0;
            wasOpen = open;
            animationCompleted = false;
            if (!this.level.isClientSide()) {
                if (!open) {
                    this.level.playSound(null, this.worldPosition,
                            ModSounds.CRH2A_DOOR_CLOSE.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
                }
            }
        }

        LerpedFloat animation = ((SlidingDoorBlockEntityAccessor) this).custom_train_door$getAnimation();

        if (animationCompleted) {
            animation.setValueNoUpdate(open ? 1.0f : 0.0f);
            return;
        }

        if (open) {
            if (animationTick < OPEN_PAUSE_TICKS) {
                animation.setValueNoUpdate(0);
            } else {
                float progress = (float) (animationTick - OPEN_PAUSE_TICKS + 1)
                        / (TOTAL_TICKS - OPEN_PAUSE_TICKS);
                animation.setValueNoUpdate(Math.min(progress, 1.0f));
                if (animationTick >= TOTAL_TICKS - 1) {
                    animationCompleted = true;
                }
            }
        } else {
            if (animationTick < CLOSE_PHASE1_TICKS) {
                float value = 1.0f - (animationTick + 1) * (float) CLOSE_SPEED;
                animation.setValueNoUpdate(value);
            } else if (animationTick < CLOSE_PHASE1_TICKS + CLOSE_PAUSE_TICKS) {
                float holdValue = 1.0f - CLOSE_PHASE1_TICKS * (float) CLOSE_SPEED;
                animation.setValueNoUpdate(holdValue);
            } else {
                int phase2Tick = animationTick - CLOSE_PHASE1_TICKS - CLOSE_PAUSE_TICKS;
                float value = 1.0f - (CLOSE_PHASE1_TICKS + phase2Tick + 1) * (float) CLOSE_SPEED;
                animation.setValueNoUpdate(Math.max(value, 0.0f));
                if (animationTick >= TOTAL_TICKS - 1) {
                    animationCompleted = true;
                }
            }
        }

        if (!this.level.isClientSide() && animationCompleted && !open
                && !this.isVisible(this.getBlockState())) {
            this.showBlockModel();
            animationCompleted = false;
        }

        animationTick++;
    }

    @Override
    protected void showBlockModel() {
        this.level.setBlock(this.worldPosition,
                (BlockState) this.getBlockState().setValue(SlidingDoorBlock.VISIBLE, true), 3);
    }
}
