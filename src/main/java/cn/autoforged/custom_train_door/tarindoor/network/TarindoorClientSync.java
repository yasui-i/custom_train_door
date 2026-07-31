package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID, value = Dist.CLIENT)
public final class TarindoorClientSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/TarindoorSync");
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static TransferState transfer;
    private static boolean usingServerPacks;
    private static CompletableFuture<Void> resourceReloadTail =
            CompletableFuture.completedFuture(null);

    private TarindoorClientSync() {
    }

    public static synchronized void handleStart(
            TarindoorSyncStartPayload payload, IPayloadContext context) {
        if (payload.totalBytes() < 0 || payload.totalBytes() > TarindoorNetwork.MAX_TOTAL_BYTES
                || payload.chunkCount() < 0
                || payload.chunkCount() > (TarindoorNetwork.MAX_TOTAL_BYTES
                / TarindoorSyncChunkPayload.MAX_CHUNK_BYTES) + 1
                || !payload.sha256().matches("[0-9a-f]{64}")) {
            context.disconnect(Component.literal("Invalid custom train door synchronization header"));
            return;
        }
        int expectedChunks = (payload.totalBytes() + TarindoorSyncChunkPayload.MAX_CHUNK_BYTES - 1)
                / TarindoorSyncChunkPayload.MAX_CHUNK_BYTES;
        if (expectedChunks != payload.chunkCount()) {
            context.disconnect(Component.literal("Invalid custom train door chunk count"));
            return;
        }
        transfer = new TransferState(payload.syncId(), payload.totalBytes(),
                payload.chunkCount(), payload.sha256());
        if (payload.chunkCount() == 0) finishTransfer(context);
    }

    public static synchronized void handleChunk(
            TarindoorSyncChunkPayload payload, IPayloadContext context) {
        TransferState state = transfer;
        if (state == null || !state.id.equals(payload.syncId())
                || payload.index() < 0 || payload.index() >= state.chunks.length
                || state.chunks[payload.index()] != null) {
            context.disconnect(Component.literal("Invalid or duplicate custom train door data chunk"));
            return;
        }
        state.chunks[payload.index()] = payload.data();
        state.receivedBytes += payload.data().length;
        state.receivedChunks++;
        if (state.receivedBytes > state.totalBytes) {
            context.disconnect(Component.literal("Custom train door synchronization exceeded its declared size"));
            transfer = null;
            return;
        }
        if (state.receivedChunks == state.chunks.length) finishTransfer(context);
    }

    private static void finishTransfer(IPayloadContext context) {
        TransferState state = transfer;
        transfer = null;
        if (state == null) return;
        try {
            byte[] bundle = joinAndVerify(state);
            installBundle(bundle);
            usingServerPacks = true;
            LOGGER.info("Installed synchronized tarindoor bundle {}; reloading client resources", state.id);
            context.enqueueWork(() -> queueResourceReload()
                    .whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
                        if (error != null) {
                            LOGGER.error("Failed to reload synchronized tarindoor resources", error);
                            if (context.connection().isConnected()) {
                                context.disconnect(Component.literal(
                                        "Could not load synchronized custom train door resources"));
                            }
                        } else if (context.connection().isConnected()) {
                            LOGGER.info("Client resources reloaded; acknowledging tarindoor bundle {}", state.id);
                            context.reply(new TarindoorSyncAckPayload(state.id));
                        }
                    })));
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            LOGGER.error("Rejected synchronized tarindoor bundle", e);
            context.disconnect(Component.literal(
                    "Invalid synchronized custom train door pack: " + e.getMessage()));
        }
    }

    private static byte[] joinAndVerify(TransferState state)
            throws IOException, NoSuchAlgorithmException {
        byte[] result = new byte[state.totalBytes];
        int offset = 0;
        for (byte[] chunk : state.chunks) {
            if (chunk == null || offset + chunk.length > result.length) {
                throw new IOException("Missing or oversized chunk");
            }
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        if (offset != result.length) throw new IOException("Bundle length mismatch");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result));
        if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                state.digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("SHA-256 mismatch");
        }
        return result;
    }

    private static void installBundle(byte[] bundle) throws IOException {
        Path cache = FMLPaths.GAMEDIR.get().resolve("tarindoor")
                .resolve("server-cache").resolve("active").toAbsolutePath().normalize();
        Path expectedRoot = FMLPaths.GAMEDIR.get().resolve("tarindoor")
                .resolve("server-cache").toAbsolutePath().normalize();
        if (!cache.startsWith(expectedRoot)) throw new IOException("Unsafe cache path");
        Files.createDirectories(cache);
        try (DirectoryStream<Path> oldPacks = Files.newDirectoryStream(cache, "*.zip")) {
            for (Path oldPack : oldPacks) Files.deleteIfExists(oldPack);
        }

        Map<String, Integer> slots = new LinkedHashMap<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bundle))) {
            if (input.readInt() != TarindoorNetwork.BUNDLE_MAGIC) {
                throw new IOException("Unknown bundle format");
            }
            if (input.readInt() != TarindoorNetwork.BUNDLE_VERSION) {
                throw new IOException("Unsupported bundle version");
            }
            int count = input.readInt();
            if (count < 0 || count > TarindoorRegistry.MAX_SLOTS) {
                throw new IOException("Invalid door count");
            }
            Set<Integer> usedSlots = new HashSet<>();
            for (int index = 0; index < count; index++) {
                int slot = input.readInt();
                String id = input.readUTF();
                int length = input.readInt();
                if (slot < 0 || slot >= TarindoorRegistry.MAX_SLOTS || !usedSlots.add(slot)
                        || !SAFE_ID.matcher(id).matches() || slots.put(id, slot) != null
                        || length < 0 || length > TarindoorNetwork.MAX_PACK_BYTES) {
                    throw new IOException("Invalid door entry");
                }
                byte[] zip = input.readNBytes(length);
                if (zip.length != length) throw new IOException("Truncated door pack");
                Path target = cache.resolve(String.format(Locale.ROOT, "slot_%02d.zip", slot));
                Path temporary = cache.resolve(String.format(Locale.ROOT, "slot_%02d.zip.tmp", slot));
                Files.write(temporary, zip);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveFailure) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            if (input.available() != 0) throw new IOException("Trailing bundle data");
        }

        TarindoorRegistry.loadSynced(cache, slots);
        for (String id : slots.keySet()) {
            if (TarindoorRegistry.getDefinition(id) == null) {
                throw new IOException("Door pack '" + id + "' failed validation");
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        transfer = null;
        if (!usingServerPacks) return;
        usingServerPacks = false;
        TarindoorRegistry.loadAll();
        queueResourceReload().whenComplete((ignored, error) -> {
            if (error != null) {
                LOGGER.error("Failed to restore local tarindoor resources after disconnect", error);
            } else {
                LOGGER.info("Restored local tarindoor resources after disconnect");
            }
        });
    }

    private static synchronized CompletableFuture<Void> queueResourceReload() {
        CompletableFuture<Void> next = resourceReloadTail
                .handle((ignored, previousError) -> null)
                .thenCompose(ignored -> reloadResourcesOnClientThread());
        resourceReloadTail = next;
        next.whenComplete((ignored, error) -> clearCompletedReload(next));
        return next;
    }

    private static CompletableFuture<Void> reloadResourcesOnClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture<Void> result = new CompletableFuture<>();
        minecraft.execute(() -> {
            try {
                minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
                    if (error == null) {
                        result.complete(null);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private static synchronized void clearCompletedReload(CompletableFuture<Void> completed) {
        if (resourceReloadTail == completed) {
            resourceReloadTail = CompletableFuture.completedFuture(null);
        }
    }

    private static final class TransferState {
        private final UUID id;
        private final int totalBytes;
        private final byte[][] chunks;
        private final String digest;
        private int receivedBytes;
        private int receivedChunks;

        private TransferState(UUID id, int totalBytes, int chunkCount, String digest) {
            this.id = id;
            this.totalBytes = totalBytes;
            this.chunks = new byte[chunkCount][];
            this.digest = digest;
        }
    }
}
