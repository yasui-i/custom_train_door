package cn.autoforged.custom_train_door.tarindoor.event;

import cn.autoforged.custom_train_door.tarindoor.resource.TarindoorResourcePack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

public final class TarindoorPackEvents {

    private TarindoorPackEvents() {
    }

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES
                && event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        event.addRepositorySource(packConsumer -> {
            PackLocationInfo location = new PackLocationInfo(
                    "tarindoor_dynamic_" + event.getPackType().name().toLowerCase(),
                    Component.literal("Tarindoor Dynamic Resources"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );
            Pack pack = Pack.readMetaAndCreate(
                    location,
                    new Pack.ResourcesSupplier() {
                        @Override
                        public PackResources openPrimary(PackLocationInfo ignored) {
                            return new TarindoorResourcePack();
                        }

                        @Override
                        public PackResources openFull(PackLocationInfo ignored, Pack.Metadata metadata) {
                            return new TarindoorResourcePack();
                        }
                    },
                    event.getPackType(),
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );
            if (pack != null) packConsumer.accept(pack);
        });
    }
}
