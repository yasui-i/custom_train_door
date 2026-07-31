package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record TarindoorSyncStartPayload(
        UUID syncId,
        int totalBytes,
        int chunkCount,
        String sha256
) implements CustomPacketPayload {
    public static final Type<TarindoorSyncStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "tarindoor_sync_start"));
    public static final StreamCodec<FriendlyByteBuf, TarindoorSyncStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TarindoorSyncStartPayload::write, TarindoorSyncStartPayload::new);

    private TarindoorSyncStartPayload(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(64));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(syncId);
        buffer.writeVarInt(totalBytes);
        buffer.writeVarInt(chunkCount);
        buffer.writeUtf(sha256, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
