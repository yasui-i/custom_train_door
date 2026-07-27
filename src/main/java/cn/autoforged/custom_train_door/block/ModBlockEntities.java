package cn.autoforged.custom_train_door.block;
import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CustomTrainDoorMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CRTrainDoorBlockEntity>> CUSTOM_TRAIN_DOOR =
            BLOCK_ENTITY_TYPES.register("custom_train_door",
                    () -> BlockEntityType.Builder.of(
                            CRTrainDoorBlockEntity::new,
                            ModBlocks.CR400BF_DOOR.get()
                    ).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CRH2ADoorBlockEntity>> CRH2A_TRAIN_DOOR =
            BLOCK_ENTITY_TYPES.register("crh2a_train_door",
                    () -> BlockEntityType.Builder.of(
                            CRH2ADoorBlockEntity::new,
                            ModBlocks.CRH2A_DOOR.get()
                    ).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
