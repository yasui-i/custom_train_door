package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TarindoorSyncErrorPayload(String message) implements CustomPacketPayload {
    public static final Type<TarindoorSyncErrorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "tarindoor_sync_error"));
    public static final StreamCodec<FriendlyByteBuf, TarindoorSyncErrorPayload> STREAM_CODEC =
            CustomPacketPayload.codec(TarindoorSyncErrorPayload::write, TarindoorSyncErrorPayload::new);

    private TarindoorSyncErrorPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(512));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(message, 512);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
