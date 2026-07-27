package cn.autoforged.custom_train_door;

import cn.autoforged.custom_train_door.block.ModBlockEntities;
import cn.autoforged.custom_train_door.block.ModBlocks;
import cn.autoforged.custom_train_door.item.ModCreativeTab;
import cn.autoforged.custom_train_door.item.ModItems;
import cn.autoforged.custom_train_door.sound.ModSounds;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.DoorMovingInteraction;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

@Mod(CustomTrainDoorMod.MODID)
public class CustomTrainDoorMod {
    public static final String MODID = "custom_train_door";

    public CustomTrainDoorMod(IEventBus modEventBus, ModContainer modContainer) {
        // 1. Load tarindoor definitions from zips
        TarindoorRegistry.loadAll();

        // 2. Register tarindoor dynamic content
        TarindoorRegistry.registerSounds(modEventBus);
        ModSounds.register(modEventBus);

        // Blocks before block entities (BEs reference blocks)
        ModBlocks.register(modEventBus);
        TarindoorRegistry.registerBlocks(modEventBus);

        ModBlockEntities.register(modEventBus);
        TarindoorRegistry.registerBlockEntities(modEventBus);

        // 3. Register BlockItems for tarindoor blocks
        for (DeferredBlock<?> db : TarindoorRegistry.getOrderedBlocks()) {
            ModItems.ITEMS.registerSimpleBlockItem(db);
        }

        ModItems.ITEMS.register(modEventBus);
        ModCreativeTab.CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Static doors
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.CR400BF_DOOR.get(),
                    new SlidingDoorMovementBehaviour());
            MovingInteractionBehaviour.REGISTRY.register(
                    ModBlocks.CR400BF_DOOR.get(),
                    new DoorMovingInteraction());
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.CRH2A_DOOR.get(),
                    new SlidingDoorMovementBehaviour());
            MovingInteractionBehaviour.REGISTRY.register(
                    ModBlocks.CRH2A_DOOR.get(),
                    new DoorMovingInteraction());

            // Dynamic tarindoor doors
            for (DeferredBlock<?> db : TarindoorRegistry.getOrderedBlocks()) {
                MovementBehaviour.REGISTRY.register(
                        db.get(), new SlidingDoorMovementBehaviour());
                MovingInteractionBehaviour.REGISTRY.register(
                        db.get(), new DoorMovingInteraction());
            }
        });
    }
}
