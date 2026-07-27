package cn.autoforged.custom_train_door.tarindoor.event;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.block.client.TarindoorDoorRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID, value = Dist.CLIENT)
public class TarindoorClientEvents {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (var holder : TarindoorRegistry.getBlockEntityHolders()) {
            BlockEntityType type = (BlockEntityType) holder.get();
            event.registerBlockEntityRenderer(type, ctx -> new TarindoorDoorRenderer(ctx));
        }
    }
}
