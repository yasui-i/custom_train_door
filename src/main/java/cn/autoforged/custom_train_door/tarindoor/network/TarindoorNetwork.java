package cn.autoforged.custom_train_door.tarindoor.network;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;

public final class TarindoorNetwork {
    public static final ConfigurationTask.Type CONFIGURATION_TASK =
            new ConfigurationTask.Type(CustomTrainDoorMod.MODID + ":tarindoor_sync");
    static final int BUNDLE_MAGIC = 0x54444F52; // TDOR
    static final int BUNDLE_VERSION = 1;
    static final int MAX_PACK_BYTES = 32 * 1024 * 1024;
    static final int MAX_TOTAL_BYTES = 128 * 1024 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger("custom_train_door/TarindoorSync");

    private TarindoorNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").executesOn(HandlerThread.NETWORK);
        registrar.configurationToClient(TarindoorSyncStartPayload.TYPE,
                TarindoorSyncStartPayload.STREAM_CODEC, TarindoorClientSync::handleStart);
        registrar.configurationToClient(TarindoorSyncChunkPayload.TYPE,
                TarindoorSyncChunkPayload.STREAM_CODEC, TarindoorClientSync::handleChunk);
        registrar.configurationToClient(TarindoorSyncErrorPayload.TYPE,
                TarindoorSyncErrorPayload.STREAM_CODEC,
                (payload, context) -> context.disconnect(Component.literal(payload.message())));
        registrar.configurationToServer(TarindoorSyncAckPayload.TYPE,
                TarindoorSyncAckPayload.STREAM_CODEC, (payload, context) -> {
                    LOGGER.info("Client acknowledged tarindoor synchronization {}", payload.syncId());
                    context.finishCurrentTask(CONFIGURATION_TASK);
                });
    }

    public static void registerConfigurationTask(RegisterConfigurationTasksEvent event) {
        if (event.getListener().getConnection().isMemoryConnection()) {
            LOGGER.debug("Skipping tarindoor synchronization for the integrated server");
            return;
        }
        event.register(new SyncTask(createTransfer()));
    }

    private static Transfer createTransfer() {
        try {
            byte[] bundle = createBundle();
            if (bundle.length > MAX_TOTAL_BYTES) {
                return Transfer.error("Server tarindoor packs exceed the "
                        + (MAX_TOTAL_BYTES / 1024 / 1024) + " MiB synchronization limit");
            }
            String digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bundle));
            return new Transfer(UUID.randomUUID(), bundle, digest, null);
        } catch (IOException | NoSuchAlgorithmException e) {
            LOGGER.error("Could not prepare tarindoor synchronization", e);
            return Transfer.error("Server could not prepare custom train door packs: " + e.getMessage());
        }
    }

    private static byte[] createBundle() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(BUNDLE_MAGIC);
            output.writeInt(BUNDLE_VERSION);
            List<TarindoorDefinition> definitions = TarindoorRegistry.getDefinitions().stream()
                    .sorted(Comparator.comparingInt(def -> TarindoorRegistry.getSlot(def.id())))
                    .toList();
            output.writeInt(definitions.size());
            for (TarindoorDefinition definition : definitions) {
                int slot = TarindoorRegistry.getSlot(definition.id());
                Path zipPath = TarindoorRegistry.getZipPath(definition);
                if (slot < 0 || zipPath == null || !Files.isRegularFile(zipPath)) {
                    throw new IOException("Missing source ZIP for door " + definition.id());
                }
                long size = Files.size(zipPath);
                if (size > MAX_PACK_BYTES) {
                    throw new IOException("Door pack " + definition.id() + " exceeds "
                            + (MAX_PACK_BYTES / 1024 / 1024) + " MiB");
                }
                byte[] zip = Files.readAllBytes(zipPath);
                output.writeInt(slot);
                output.writeUTF(definition.id());
                output.writeInt(zip.length);
                output.write(zip);
                if (bytes.size() > MAX_TOTAL_BYTES) {
                    throw new IOException("Combined door packs exceed "
                            + (MAX_TOTAL_BYTES / 1024 / 1024) + " MiB");
                }
            }
        }
        return bytes.toByteArray();
    }

    private record SyncTask(Transfer transfer) implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            if (transfer.error != null) {
                sender.accept(new TarindoorSyncErrorPayload(transfer.error));
                return;
            }
            int chunks = (transfer.data.length + TarindoorSyncChunkPayload.MAX_CHUNK_BYTES - 1)
                    / TarindoorSyncChunkPayload.MAX_CHUNK_BYTES;
            sender.accept(new TarindoorSyncStartPayload(
                    transfer.id, transfer.data.length, chunks, transfer.digest));
            for (int index = 0; index < chunks; index++) {
                int start = index * TarindoorSyncChunkPayload.MAX_CHUNK_BYTES;
                int end = Math.min(start + TarindoorSyncChunkPayload.MAX_CHUNK_BYTES,
                        transfer.data.length);
                sender.accept(new TarindoorSyncChunkPayload(
                        transfer.id, index, Arrays.copyOfRange(transfer.data, start, end)));
            }
        }

        @Override
        public ConfigurationTask.Type type() {
            return CONFIGURATION_TASK;
        }
    }

    private record Transfer(UUID id, byte[] data, String digest, String error) {
        private static Transfer error(String message) {
            return new Transfer(new UUID(0, 0), new byte[0], "", message);
        }
    }
}
