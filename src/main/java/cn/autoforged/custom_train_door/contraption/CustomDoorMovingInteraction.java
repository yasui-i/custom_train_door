package cn.autoforged.custom_train_door.contraption;

import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Uses each custom door's BlockSetType sounds when a player interacts with a
 * door mounted on a contraption.
 */
public class CustomDoorMovingInteraction extends DoorMovingInteraction {

    @Override
    protected BlockState handle(Player player, Contraption contraption, BlockPos localPos, BlockState state) {
        BlockState updatedState = super.handle(player, contraption, localPos, state);

        // Play sound on the server when the door state actually changed
        if (state.hasProperty(DoorBlock.OPEN)
                && updatedState.hasProperty(DoorBlock.OPEN)
                && updatedState.getValue(DoorBlock.OPEN) != state.getValue(DoorBlock.OPEN)
                && updatedState.getBlock() instanceof DoorBlock door) {

            boolean open = updatedState.getValue(DoorBlock.OPEN);
            SoundEvent sound = getDoorSound(contraption, localPos, open, door);
            // Use contraption world for server-side sound
            if (contraption.entity != null && contraption.entity.level() != null
                    && !contraption.entity.level().isClientSide) {
                Vec3 worldPosition = contraption.entity.toGlobalVector(Vec3.atCenterOf(localPos), 0);
                contraption.entity.level().playSound(
                        null,
                        BlockPos.containing(worldPosition),
                        sound,
                        SoundSource.BLOCKS,
                        1.0F,
                        player != null
                                ? player.level().getRandom().nextFloat() * 0.1F + 0.9F
                                : 1.0F);
            }
        }

        return updatedState;
    }

    private SoundEvent getDoorSound(Contraption contraption, BlockPos localPos,
                                     boolean open, DoorBlock door) {
        if (door instanceof TarindoorBlock) {
            StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(localPos);
            if (info != null && info.nbt() != null) {
                String doorId = info.nbt().getString("DoorId");
                Supplier<SoundEvent> supplier = open
                        ? TarindoorRegistry.getOpenSound(doorId)
                        : TarindoorRegistry.getCloseSound(doorId);
                if (supplier != null) {
                    SoundEvent se = supplier.get();
                    if (se != null) return se;
                }
            }
            // Fallback to default tarindoor sounds
            return open
                    ? TarindoorRegistry.getOpenSound("").get()
                    : TarindoorRegistry.getCloseSound("").get();
        }
        return open ? door.type().doorOpen() : door.type().doorClose();
    }

    @Override
    protected void playSound(Player player, SoundEvent sound, float pitch) {
        // DoorMovingInteraction would use the vanilla iron-door opening sound
        // and exclude the clicking player from the server broadcast.
    }
}
