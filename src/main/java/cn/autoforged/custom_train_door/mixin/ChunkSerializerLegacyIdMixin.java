package cn.autoforged.custom_train_door.mixin;

import cn.autoforged.custom_train_door.tarindoor.LegacyTarindoorIdMigration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerLegacyIdMixin {
    @Inject(method = "read", at = @At("HEAD"))
    private static void customTrainDoor$migrateLegacyChunkPalette(
            ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos chunkPos,
            CompoundTag chunk,
            CallbackInfoReturnable<ProtoChunk> callback
    ) {
        LegacyTarindoorIdMigration.migrateChunkPalettes(chunk);
    }
}
