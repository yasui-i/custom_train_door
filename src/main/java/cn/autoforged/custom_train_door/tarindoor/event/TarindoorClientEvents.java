package cn.autoforged.custom_train_door.tarindoor.event;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.block.client.TarindoorDoorRenderer;
import cn.autoforged.custom_train_door.tarindoor.resource.TarindoorResourcePack;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class TarindoorClientEvents {

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(packConsumer -> {
                Pack pack = Pack.readMetaAndCreate(
                        new PackLocationInfo(
                                "tarindoor_dynamic",
                                net.minecraft.network.chat.Component.literal("Tarindoor Dynamic Resources"),
                                PackSource.BUILT_IN,
                                java.util.Optional.empty()
                        ),
                        new Pack.ResourcesSupplier() {
                            @Override
                            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo loc) {
                                return new TarindoorResourcePack();
                            }

                            @Override
                            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo loc,
                                                                                     Pack.Metadata metadata) {
                                return new TarindoorResourcePack();
                            }
                        },
                        PackType.CLIENT_RESOURCES,
                        new PackSelectionConfig(false, Pack.Position.TOP, false)
                );
                packConsumer.accept(pack);
            });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var holder : TarindoorRegistry.getBlockEntityHolders()) {
            BlockEntityType type = (BlockEntityType) holder.get();
            event.registerBlockEntityRenderer(type, ctx -> new TarindoorDoorRenderer(ctx));
        }
    }
}
