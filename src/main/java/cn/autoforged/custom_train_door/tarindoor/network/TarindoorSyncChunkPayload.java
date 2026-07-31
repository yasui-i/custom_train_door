package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TarindoorSyncChunkPayload(
        UUID syncId,
        int index,
        byte[] data
) implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 900 * 1024;
    public static final Type<TarindoorSyncChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "tarindoor_sync_chunk"));
    public static final StreamCodec<FriendlyByteBuf, TarindoorSyncChunkPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TarindoorSyncChunkPayload::write, TarindoorSyncChunkPayload::new);

    private TarindoorSyncChunkPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt(), buffer.readByteArray(MAX_CHUNK_BYTES));
    }

    private void write(FriendlyByteBuf buffer) {
        if (data.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Tarindoor sync chunk exceeds " + MAX_CHUNK_BYTES + " bytes");
        }
        buffer.writeUUID(syncId);
        buffer.writeVarInt(index);
        buffer.writeByteArray(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
