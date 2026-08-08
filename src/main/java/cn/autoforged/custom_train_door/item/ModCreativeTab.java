package cn.autoforged.custom_train_door.item;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import cn.autoforged.custom_train_door.block.ModBlocks;
import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.item.TarindoorBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CustomTrainDoorMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CUSTOM_TRAIN_DOOR_TAB =
            CREATIVE_TABS.register("custom_train_door_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CustomTrainDoorMod.MODID))
                    .icon(() -> new ItemStack(ModBlocks.CR400BF_DOOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.CR400BF_DOOR.get());
                        output.accept(ModBlocks.CRH2A_DOOR.get());
                        // Add each tarindoor door definition as a separate NBT-tagged item
                        for (TarindoorDefinition def : TarindoorRegistry.getDefinitions()) {
                            output.accept(TarindoorBlockItem.createStack(def.id()));
                        }
                    }).build());
}
