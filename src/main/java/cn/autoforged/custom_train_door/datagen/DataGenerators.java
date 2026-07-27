package cn.autoforged.custom_train_door.datagen;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = CustomTrainDoorMod.MODID)
public class DataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.addProvider(event.includeClient(),
                new ModBlockStateProvider(packOutput, existingFileHelper));

        generator.addProvider(event.includeClient(),
                new ModItemModelProvider(packOutput, existingFileHelper));

        generator.addProvider(event.includeServer(),
                new LootTableProvider(packOutput, Set.of(),
                        List.of(new LootTableProvider.SubProviderEntry(
                                ModBlockLootTableProvider::new,
                                LootContextParamSets.BLOCK)),
                        lookupProvider));

        ModBlockTagProvider blockTags = new ModBlockTagProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTags);

        generator.addProvider(event.includeServer(),
                new ModItemTagProvider(packOutput, lookupProvider, blockTags.contentsGetter(), existingFileHelper));

        generator.addProvider(event.includeServer(),
                new ModRecipeProvider(packOutput, lookupProvider));
    }
}
