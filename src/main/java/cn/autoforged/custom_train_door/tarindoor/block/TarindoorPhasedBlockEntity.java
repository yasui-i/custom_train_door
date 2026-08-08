package cn.autoforged.custom_train_door.tarindoor.block;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Phased-animation block entity for tarindoor doors.
 * Implements a generic keyframe-based animation engine driven by the
 * definition's animation.phases configuration.
 */
public class TarindoorPhasedBlockEntity extends TarindoorBlockEntity {

    private int animationTick = 0;
    private boolean wasOpen = false;
    private boolean animationCompleted = true;

    public TarindoorPhasedBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void tick() {
        // Handle deferred updates (same as CRH2ADoorBlockEntity)
        SlidingDoorBlockEntityAccessor acc = (SlidingDoorBlockEntityAccessor) this;
        if (acc.custom_train_door$getDeferUpdate() && !this.level.isClientSide()) {
            acc.custom_train_door$setDeferUpdate(false);
            this.getBlockState().handleNeighborChanged(this.level, this.worldPosition,
                    Blocks.AIR, this.worldPosition, false);
        }

        super.tick();

        TarindoorDefinition def = getDef();
        if (def == null) return;

        boolean open = isOpen(this.getBlockState());
        LerpedFloat animation = ((SlidingDoorBlockEntityAccessor) this).custom_train_door$getAnimation();

        // Detect state change
        if (open != wasOpen) {
            animationTick = 0;
            wasOpen = open;
            animationCompleted = false;
            if (!this.level.isClientSide() && !open) {
                var closeSound = TarindoorRegistry.getCloseSound(def.id());
                if (closeSound != null) {
                    this.level.playSound(null, this.worldPosition,
                            closeSound.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
                }
            }
        }

        if (animationCompleted) {
            animation.setValueNoUpdate(open ? 1.0f : 0.0f);
            return;
        }

        List<TarindoorDefinition.AnimationPhase> phases = open
                ? def.animation().openingPhases()
                : def.animation().closingPhases();

        if (phases.isEmpty()) {
            // No phases defined — just snap to target
            animation.setValueNoUpdate(open ? 1.0f : 0.0f);
            animationCompleted = true;
            return;
        }

        // Compute total animate ticks for progress calculation
        int totalAnimateTicks = phases.stream()
                .filter(p -> p.type() == TarindoorDefinition.PhaseType.ANIMATE)
                .mapToInt(TarindoorDefinition.AnimationPhase::durationTicks)
                .sum();

        int totalTicks = def.animation().phasedTotalTicks();
        if (totalTicks <= 0) totalTicks = phases.stream()
                .mapToInt(TarindoorDefinition.AnimationPhase::durationTicks).sum();

        int elapsed = animationTick;
        double currentValue = open ? 0.0 : 1.0;
        int animateElapsed = 0;

        for (TarindoorDefinition.AnimationPhase phase : phases) {
            if (elapsed < phase.durationTicks()) {
                if (phase.type() == TarindoorDefinition.PhaseType.PAUSE) {
                    animation.setValueNoUpdate((float) currentValue);
                } else {
                    // ANIMATE — interpolate
                    double fraction;
                    if (totalAnimateTicks > 0) {
                        fraction = (double) (animateElapsed + elapsed + 1) / totalAnimateTicks;
                    } else {
                        fraction = 1.0;
                    }
                    double value = open ? fraction : (1.0 - fraction);
                    animation.setValueNoUpdate((float) Math.max(0.0, Math.min(1.0, value)));
                }
                animationTick++;
                return; // still in this phase
            } else {
                elapsed -= phase.durationTicks();
                if (phase.type() == TarindoorDefinition.PhaseType.ANIMATE) {
                    animateElapsed += phase.durationTicks();
                    if (totalAnimateTicks > 0) {
                        double fraction = (double) animateElapsed / totalAnimateTicks;
                        currentValue = open ? fraction : (1.0 - fraction);
                    }
                }
            }
        }

        // All phases exhausted
        animation.setValueNoUpdate(open ? 1.0f : 0.0f);
        animationCompleted = true;

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
                this.getBlockState().setValue(SlidingDoorBlock.VISIBLE, true), 3);
    }

    private TarindoorDefinition getDef() {
        return getDefinition();
    }
}
