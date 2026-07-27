package cn.autoforged.custom_train_door.block;

import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;

public class CRH2ADoorBlock extends SlidingDoorBlock {

    public CRH2ADoorBlock(Properties properties, BlockSetType type, boolean folds) {
        super(properties, type, folds);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? null
                : new CRH2ADoorBlockEntity(pos, state);
    }

    @Override
    public Class<SlidingDoorBlockEntity> getBlockEntityClass() {
        return SlidingDoorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SlidingDoorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.CRH2A_TRAIN_DOOR.get();
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(this)) {
            if (state.getValue(OPEN) != open) {
                BlockState changedState = state.setValue(OPEN, open);
                if (open) {
                    changedState = changedState.setValue(VISIBLE, false);
                }
                level.setBlock(pos, changedState, 10);
                DoorHingeSide hinge = changedState.getValue(HINGE);
                Direction facing = changedState.getValue(FACING);
                BlockPos otherPos = pos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
                BlockState otherDoor = level.getBlockState(otherPos);
                if (isDoubleDoor(changedState, hinge, facing, otherDoor)) {
                    this.setOpen(entity, level, otherDoor, otherPos, open);
                }
                level.playSound(entity, pos,
                        open ? this.type().doorOpen() : this.type().doorClose(),
                        SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
                level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            }
        }
    }

    @Override
    public void neighborChanged(BlockState pState, Level pLevel, BlockPos pPos, Block pBlock, BlockPos pFromPos, boolean pIsMoving) {
        boolean lower = pState.getValue(HALF) == DoubleBlockHalf.LOWER;
        boolean isPowered = isDoorPowered(pLevel, pPos, pState);
        if (!this.defaultBlockState().is(pBlock)) {
            if (isPowered != pState.getValue(POWERED)) {
                SlidingDoorBlockEntity rawBe = this.getBlockEntity(pLevel, lower ? pPos : pPos.below());
                if (rawBe == null || !((SlidingDoorBlockEntityAccessor) rawBe).custom_train_door$getDeferUpdate()) {
                    BlockState changedState = pState.setValue(POWERED, isPowered).setValue(OPEN, isPowered);
                    if (isPowered) {
                        changedState = changedState.setValue(VISIBLE, false);
                    }
                    if (isPowered != pState.getValue(OPEN)) {
                        pLevel.playSound(null, pPos,
                                isPowered ? this.type().doorOpen() : this.type().doorClose(),
                                SoundSource.BLOCKS, 1.0F, pLevel.getRandom().nextFloat() * 0.1F + 0.9F);
                        pLevel.gameEvent(null, isPowered ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pPos);
                        DoorHingeSide hinge = changedState.getValue(HINGE);
                        Direction facing = changedState.getValue(FACING);
                        BlockPos otherPos = pPos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
                        BlockState otherDoor = pLevel.getBlockState(otherPos);
                        if (isDoubleDoor(changedState, hinge, facing, otherDoor)) {
                            otherDoor = otherDoor.setValue(POWERED, isPowered).setValue(OPEN, isPowered);
                            if (isPowered) {
                                otherDoor = otherDoor.setValue(VISIBLE, false);
                            }
                            pLevel.setBlock(otherPos, otherDoor, 2);
                        }
                    }
                    pLevel.setBlock(pPos, changedState, 2);
                }
            }
        }
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        state = state.cycle(OPEN);
        boolean isOpen = state.getValue(OPEN);
        if (isOpen) {
            state = state.setValue(VISIBLE, false);
        }
        level.setBlock(pos, state, 10);
        level.gameEvent(player, this.isOpen(state) ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        DoorHingeSide hinge = state.getValue(HINGE);
        Direction facing = state.getValue(FACING);
        BlockPos otherPos = pos.relative(hinge == DoorHingeSide.LEFT ? facing.getClockWise() : facing.getCounterClockWise());
        BlockState otherDoor = level.getBlockState(otherPos);
        if (isDoubleDoor(state, hinge, facing, otherDoor)) {
            this.useWithoutItem(otherDoor, level, otherPos, player, hitResult);
        } else if (isOpen) {
            level.playSound(player, pos,
                    this.type().doorOpen(),
                    SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
            level.gameEvent(player, GameEvent.BLOCK_OPEN, pos);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
