package cn.autoforged.custom_train_door.tarindoor.block;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lerped-animation block entity for tarindoor doors.
 * Uses Create's built-in LerpedFloat chase mechanism with a configurable speed.
 */
public class TarindoorBlockEntity extends SlidingDoorBlockEntity {

    public TarindoorBlockEntity(BlockPos pos, BlockState state) {
        super(getTypeFor(state), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<? extends SlidingDoorBlockEntity> getTypeFor(BlockState state) {
        if (state.getBlock() instanceof TarindoorBlock block) {
            return (BlockEntityType<? extends SlidingDoorBlockEntity>)
                    TarindoorRegistry.getBlockEntityType(block.getDefinition().id());
        }
        // fallback — should never happen
        return (BlockEntityType<? extends SlidingDoorBlockEntity>) (Object)
                BlockEntityType.SIGN; // dummy, won't actually be used
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide()) {
            boolean open = isOpen(this.getBlockState());
            boolean wasSettled = ((SlidingDoorBlockEntityAccessor) this).custom_train_door$getAnimation().settled();
            if (!open && wasSettled && !this.isVisible(this.getBlockState())) {
                TarindoorDefinition def = getDef();
                if (def != null) {
                    var closeSound = TarindoorRegistry.getCloseSound(def.id());
                    if (closeSound != null) {
                        this.level.playSound(null, this.worldPosition,
                                closeSound.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
                    }
                }
            }
        }
        super.tick();
    }

    @Override
    protected void showBlockModel() {
        this.level.setBlock(this.worldPosition,
                this.getBlockState().setValue(SlidingDoorBlock.VISIBLE, true), 3);
    }

    private TarindoorDefinition getDef() {
        if (this.getBlockState().getBlock() instanceof TarindoorBlock block) {
            return block.getDefinition();
        }
        return null;
    }
}
