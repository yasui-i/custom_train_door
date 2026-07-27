package cn.autoforged.custom_train_door.contraption;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Uses each custom door's BlockSetType sounds when a player interacts with a
 * door mounted on a contraption.
 */
public class CustomDoorMovingInteraction extends DoorMovingInteraction {

    @Override
    protected BlockState handle(Player player, Contraption contraption, BlockPos localPos, BlockState state) {
        BlockState updatedState = super.handle(player, contraption, localPos, state);

        // Recursive calls used to update the second half/paired door pass null.
        // Play exactly once, on the server, so the clicking player is included.
        if (player != null
                && !player.level().isClientSide
                && updatedState != state
                && updatedState.getBlock() instanceof DoorBlock door) {
            boolean open = updatedState.getValue(DoorBlock.OPEN);
            SoundEvent sound = open ? door.type().doorOpen() : door.type().doorClose();
            Vec3 worldPosition = contraption.entity.toGlobalVector(Vec3.atCenterOf(localPos), 0);
            player.level().playSound(
                    null,
                    BlockPos.containing(worldPosition),
                    sound,
                    SoundSource.BLOCKS,
                    1.0F,
                    player.level().getRandom().nextFloat() * 0.1F + 0.9F);
        }

        return updatedState;
    }

    @Override
    protected void playSound(Player player, SoundEvent sound, float pitch) {
        // DoorMovingInteraction would use the vanilla iron-door opening sound
        // and exclude the clicking player from the server broadcast.
    }
}
