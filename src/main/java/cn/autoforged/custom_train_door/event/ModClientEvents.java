package cn.autoforged.custom_train_door.event;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.block.ModBlockEntities;
import cn.autoforged.custom_train_door.block.client.CRH2ADoorRenderer;
import cn.autoforged.custom_train_door.block.client.CRTrainDoorRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.CUSTOM_TRAIN_DOOR.get(),
                CRTrainDoorRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.CRH2A_TRAIN_DOOR.get(),
                CRH2ADoorRenderer::new
        );
    }
}
