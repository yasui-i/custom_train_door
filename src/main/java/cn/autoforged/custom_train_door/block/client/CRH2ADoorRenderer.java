package cn.autoforged.custom_train_door.block.client;

import cn.autoforged.custom_train_door.mixin.SlidingDoorBlockEntityAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

@SuppressWarnings("rawtypes")
public class CRH2ADoorRenderer extends SafeBlockEntityRenderer<SlidingDoorBlockEntity> {

    public CRH2ADoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SlidingDoorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        boolean visible = blockState.getOptionalValue(SlidingDoorBlock.VISIBLE).orElse(true);
        if (!visible) {
            Direction facing = blockState.getValue(DoorBlock.FACING);
            Direction movementDirection = facing.getClockWise();
            if (blockState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT) {
                movementDirection = movementDirection.getOpposite();
            }

            float value = ((SlidingDoorBlockEntityAccessor) be).custom_train_door$getAnimation()
                    .getValue(partialTicks);

            VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
            Vec3 offset = Vec3.atLowerCornerOf(movementDirection.getNormal())
                    .scale((double) (value * value * 9.0F / 10.0F));

            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                SuperByteBuffer sbb = CachedBuffers.block(
                        blockState.setValue(DoorBlock.OPEN, false).setValue(DoorBlock.HALF, half)
                );
                sbb.translate(0.0F, half == DoubleBlockHalf.UPPER ? 0.9980469F : 0.0F, 0.0F);
                sbb.translate(offset);
                sbb.light(light).renderInto(ms, vb);
            }
        }
    }
}
