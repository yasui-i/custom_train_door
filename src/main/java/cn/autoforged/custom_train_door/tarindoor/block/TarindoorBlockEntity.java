package cn.autoforged.custom_train_door.tarindoor.block;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class TarindoorBlockEntity extends SlidingDoorBlockEntity {

    private String doorId = "";

    public TarindoorBlockEntity(BlockPos pos, BlockState state) {
        super(getSelfType(), pos, state);
    }

    @SuppressWarnings("unchecked")
    private static BlockEntityType<? extends SlidingDoorBlockEntity> getSelfType() {
        return (BlockEntityType<? extends SlidingDoorBlockEntity>)
                TarindoorRegistry.getDoorBlockEntity().get();
    }

    public String getDoorId() { return doorId; }

    public void setDoorId(String id) {
        this.doorId = id != null ? id : "";
        setChanged();
    }

    @Nullable
    public TarindoorDefinition getDefinition() {
        if (doorId.isEmpty()) return null;
        return TarindoorRegistry.getDefinition(doorId);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (!doorId.isEmpty()) compound.putString("DoorId", doorId);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.doorId = compound.getString("DoorId");
    }

    @Override
    public void tick() {
        if (!this.level.isClientSide()) {
            boolean open = isOpen(this.getBlockState());
            boolean wasSettled = ((SlidingDoorBlockEntityAccessor) this).custom_train_door$getAnimation().settled();
            if (!open && wasSettled && !this.isVisible(this.getBlockState())) {
                TarindoorDefinition def = getDefinition();
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
}
