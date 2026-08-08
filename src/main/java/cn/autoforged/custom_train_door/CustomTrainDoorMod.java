package cn.autoforged.custom_train_door;

import cn.autoforged.custom_train_door.block.ModBlockEntities;
import cn.autoforged.custom_train_door.block.ModBlocks;
import cn.autoforged.custom_train_door.contraption.CustomDoorMovingInteraction;
import cn.autoforged.custom_train_door.item.ModCreativeTab;
import cn.autoforged.custom_train_door.item.ModItems;
import cn.autoforged.custom_train_door.sound.ModSounds;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;

import cn.autoforged.custom_train_door.tarindoor.event.TarindoorPackEvents;
import cn.autoforged.custom_train_door.tarindoor.item.TarindoorBlockItem;
import cn.autoforged.custom_train_door.tarindoor.network.TarindoorNetwork;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

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

        // 3. Register custom BlockItem for the single tarindoor door block
        ModItems.ITEMS.register("tarindoor_door",
                () -> new TarindoorBlockItem(TarindoorRegistry.getDoorBlock().get(),
                        new Item.Properties()));

        ModItems.ITEMS.register(modEventBus);
        ModCreativeTab.CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(TarindoorPackEvents::addPackFinders);
        modEventBus.addListener(TarindoorNetwork::registerPayloads);
        modEventBus.addListener(TarindoorNetwork::registerConfigurationTask);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Static doors
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.CR400BF_DOOR.get(),
                    new SlidingDoorMovementBehaviour());
            MovingInteractionBehaviour.REGISTRY.register(
                    ModBlocks.CR400BF_DOOR.get(),
                    new CustomDoorMovingInteraction());
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.CRH2A_DOOR.get(),
                    new SlidingDoorMovementBehaviour());
            MovingInteractionBehaviour.REGISTRY.register(
                    ModBlocks.CRH2A_DOOR.get(),
                    new CustomDoorMovingInteraction());

            // Dynamic tarindoor door
            MovementBehaviour.REGISTRY.register(
                    TarindoorRegistry.getDoorBlock().get(), new SlidingDoorMovementBehaviour());
            MovingInteractionBehaviour.REGISTRY.register(
                    TarindoorRegistry.getDoorBlock().get(), new CustomDoorMovingInteraction());
        });
    }
}
