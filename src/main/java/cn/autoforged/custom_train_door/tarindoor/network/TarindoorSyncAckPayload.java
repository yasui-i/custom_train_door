package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TarindoorSyncAckPayload(UUID syncId) implements CustomPacketPayload {
    public static final Type<TarindoorSyncAckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "tarindoor_sync_ack"));
    public static final StreamCodec<FriendlyByteBuf, TarindoorSyncAckPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TarindoorSyncAckPayload::write, TarindoorSyncAckPayload::new);

    private TarindoorSyncAckPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(syncId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
