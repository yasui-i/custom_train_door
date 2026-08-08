package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID, value = Dist.CLIENT)
public final class TarindoorClientSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/TarindoorSync");
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_]{1,48}");
    private static final Gson GSON = new Gson();
    private static final Path CACHE_ROOT = FMLPaths.GAMEDIR.get().resolve("tarindoor")
            .resolve("server-cache").toAbsolutePath().normalize();
    private static TransferState transfer;
    private static boolean cacheHit;
    private static boolean usingServerPacks;
    private static boolean pendingReload;
    private static int reloadTickDelay;

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

        // Check content-addressed cache before downloading
        Path cacheDir = getCacheDir(payload.sha256());
        if (Files.isDirectory(cacheDir) && hasZipFiles(cacheDir)) {
            cacheHit = true;
            transfer = null;
            try {
                Map<String, Integer> slots = readCacheSlots(cacheDir);
                TarindoorRegistry.loadSynced(cacheDir, slots);
                usingServerPacks = true;
                LOGGER.info("Using cached tarindoor bundle {} ({} doors, skipped {} chunks)",
                        payload.sha256(), slots.size(), payload.chunkCount());
                if (context.connection().isConnected()) {
                    context.reply(new TarindoorSyncAckPayload(payload.syncId()));
                }
                // Definitions already loaded from cache — no reload needed
            } catch (Exception e) {
                LOGGER.error("Failed to load cached tarindoor bundle, will download", e);
                // Fall through to normal download
                cacheHit = false;
                transfer = new TransferState(payload.syncId(), payload.totalBytes(),
                        payload.chunkCount(), payload.sha256());
                if (payload.chunkCount() == 0) finishTransfer(context);
            }
            return;
        }

        // Check if local tarindoor packs produce the same hash as server
        String localHash = computeLocalBundleHash();
        if (localHash != null && localHash.equals(payload.sha256())) {
            cacheHit = true;
            transfer = null;
            try {
                // Save local packs into cache for future instant loads
                saveLocalPacksToCache(cacheDir);
                usingServerPacks = true;
                LOGGER.info("Local tarindoor packs match server hash {}; saved to cache, skipped download",
                        payload.sha256());
            } catch (Exception e) {
                LOGGER.warn("Failed to save local packs to cache, using local directly", e);
                usingServerPacks = true;
            }
            if (context.connection().isConnected()) {
                context.reply(new TarindoorSyncAckPayload(payload.syncId()));
            }
            // Definitions already match — no reload needed
            return;
        }

        cacheHit = false;
        transfer = new TransferState(payload.syncId(), payload.totalBytes(),
                payload.chunkCount(), payload.sha256());
        if (payload.chunkCount() == 0) finishTransfer(context);
    }

    public static synchronized void handleChunk(
            TarindoorSyncChunkPayload payload, IPayloadContext context) {
        TransferState state = transfer;
        if (state == null) {
            // Cache hit or transfer already finished — silently discard remaining chunks
            if (cacheHit) return;
            context.disconnect(Component.literal("Invalid custom train door data chunk"));
            return;
        }
        if (!state.id.equals(payload.syncId())
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
            installBundle(bundle, state.digest);
            usingServerPacks = true;
            LOGGER.info("Installed synchronized tarindoor bundle {}", state.id);
            if (context.connection().isConnected()) {
                context.reply(new TarindoorSyncAckPayload(state.id));
            }
            // Queue resource reload on render thread — executes during
            // the "joining world" loading screen, before world rendering
            scheduleReload();
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            LOGGER.error("Rejected synchronized tarindoor bundle (client will use fallback)", e);
            if (context.connection().isConnected()) {
                context.reply(new TarindoorSyncAckPayload(state.id));
            }
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

    private static void installBundle(byte[] bundle, String sha256) throws IOException {
        Path cacheDir = getCacheDir(sha256);
        if (!cacheDir.startsWith(CACHE_ROOT)) throw new IOException("Unsafe cache path");
        Files.createDirectories(cacheDir);
        Map<String, Integer> slots = new LinkedHashMap<>();

try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bundle))) {
            if (input.readInt() != TarindoorNetwork.BUNDLE_MAGIC) {
                throw new IOException("Unknown bundle format");
            }
            if (input.readInt() != TarindoorNetwork.BUNDLE_VERSION) {
                throw new IOException("Unsupported bundle version");
            }
            int count = input.readInt();
            if (count < 0 || count > Integer.MAX_VALUE) {
                throw new IOException("Invalid door count");
            }

            Set<String> usedIds = new HashSet<>();
            for (int index = 0; index < count; index++) {
                int slot = input.readInt(); // ignored — legacy slot field
                String id = input.readUTF();
                int length = input.readInt();
                if (!SAFE_ID.matcher(id).matches() || !usedIds.add(id)
                        || slots.put(id, 0) != null
                        || length < 0 || length > TarindoorNetwork.MAX_PACK_BYTES) {
                    throw new IOException("Invalid door entry");
                }
                byte[] zip = input.readNBytes(length);
                if (zip.length != length) throw new IOException("Truncated door pack");
                Path target = cacheDir.resolve(id + ".zip");
                Path temporary = cacheDir.resolve(id + ".zip.tmp");
                Files.write(temporary, zip);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveFailure) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                // Also copy to tarindoor/ root so local loadAll() picks it up on next launch
                Path localTarget = CACHE_ROOT.getParent().resolve(id + ".zip");
                Path localTmp = CACHE_ROOT.getParent().resolve(id + ".zip.tmp");
                try {
                    Files.write(localTmp, zip);
                    try {
                        Files.move(localTmp, localTarget, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException atomicMoveFailure) {
                        Files.move(localTmp, localTarget, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to copy synced door pack '{}' to tarindoor folder", id, e);
                }
            }
            if (input.available() != 0) throw new IOException("Trailing bundle data");
        }

        // Persist slot mapping so the cache can be loaded without re-parsing the bundle
        writeCacheSlots(cacheDir, slots);
        // Remove old cache directories to free disk space
        clearOldCaches(sha256);
        TarindoorRegistry.loadSynced(cacheDir, slots);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        transfer = null;
        pendingReload = false;
        if (!usingServerPacks) return;
        usingServerPacks = false;
        TarindoorRegistry.loadAll();
        LOGGER.info("Restored local tarindoor resources after disconnect");
    }

    /**
     * Queue a resource reload on the render thread. Because this is called
     * during config phase (on the network thread), the reload task gets
     * queued and executes during the "joining world" loading screen,
     * before the world is rendered.
     */
    private static void scheduleReload() {
        pendingReload = true;
        reloadTickDelay = 60; // 3 seconds after first tick
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!pendingReload) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (--reloadTickDelay > 0) return;
        pendingReload = false;
        LOGGER.info("Reloading resources for synced tarindoor packs...");
        mc.reloadResourcePacks()
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.error("Tarindoor resource reload failed: {}", error.getMessage());
                    } else {
                        LOGGER.info("Tarindoor resource reload complete");
                        mc.levelRenderer.allChanged();
                    }
                });
    }

    // --- Content-addressed cache helpers ---

    private static Path getCacheDir(String sha256) {
        return CACHE_ROOT.resolve(sha256);
    }

    private static boolean hasZipFiles(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.zip")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            return false;
        }
    }

    @org.jetbrains.annotations.Nullable
    private static String computeLocalBundleHash() {
        try {
            Collection<cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition> definitions =
                    TarindoorRegistry.getDefinitions();
            if (definitions.isEmpty()) return null;

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(TarindoorNetwork.BUNDLE_MAGIC);
                output.writeInt(TarindoorNetwork.BUNDLE_VERSION);
                List<cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition> sorted =
                        definitions.stream()
                                .sorted(Comparator.comparing(
                                        cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition::id))
                                .toList();
                output.writeInt(sorted.size());
                for (var def : sorted) {
                    Path zipPath = TarindoorRegistry.getZipPath(def);
                    if (zipPath == null || !Files.isRegularFile(zipPath)) {
                        return null;
                    }
                    long size = Files.size(zipPath);
                    if (size > TarindoorNetwork.MAX_PACK_BYTES) return null;
                    byte[] zip = Files.readAllBytes(zipPath);
                    output.writeInt(0); // legacy slot field, always 0
                    output.writeUTF(def.id());
                    output.writeInt(zip.length);
                    output.write(zip);
                    if (bytes.size() > TarindoorNetwork.MAX_TOTAL_BYTES) return null;
                }
            }
            byte[] bundle = bytes.toByteArray();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bundle));
        } catch (Exception e) {
            LOGGER.warn("Failed to compute local bundle hash, will download", e);
            return null;
        }
    }

    private static void saveLocalPacksToCache(Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (var def : TarindoorRegistry.getDefinitions()) {
            Path zipPath = TarindoorRegistry.getZipPath(def);
            if (zipPath == null || !Files.isRegularFile(zipPath)) continue;
            Path target = cacheDir.resolve(def.id() + ".zip");
            Files.copy(zipPath, target, StandardCopyOption.REPLACE_EXISTING);
            slots.put(def.id(), 0);
        }
        writeCacheSlots(cacheDir, slots);
    }

    private static Map<String, Integer> readCacheSlots(Path cacheDir) throws IOException {
        String json = Files.readString(cacheDir.resolve("slots.json"));
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        Map<String, Integer> slots = new LinkedHashMap<>();
        for (var entry : obj.entrySet()) {
            slots.put(entry.getKey(), entry.getValue().getAsInt());
        }
        return slots;
    }

    private static void writeCacheSlots(Path cacheDir, Map<String, Integer> slots) throws IOException {
        JsonObject obj = new JsonObject();
        slots.forEach((id, slot) -> obj.addProperty(id, slot));
        Files.writeString(cacheDir.resolve("slots.json"), GSON.toJson(obj));
    }

    private static void clearOldCaches(String currentSha256) {
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(CACHE_ROOT,
                p -> Files.isDirectory(p) && !p.getFileName().toString().equals(currentSha256))) {
            for (Path old : dirs) {
                try (Stream<Path> files = Files.walk(old)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); }
                                catch (IOException ignored) { }
                            });
                } catch (IOException e) {
                    LOGGER.warn("Failed to clean old cache {}", old.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to list cache directories for cleanup", e);
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
