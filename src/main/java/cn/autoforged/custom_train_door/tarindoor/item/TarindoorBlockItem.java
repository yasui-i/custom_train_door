package cn.autoforged.custom_train_door.tarindoor.item;

import cn.autoforged.custom_train_door.tarindoor.TarindoorDefinition;
import cn.autoforged.custom_train_door.tarindoor.TarindoorRegistry;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlock;
import cn.autoforged.custom_train_door.tarindoor.block.TarindoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockItem that transfers the "DoorId" NBT tag to the placed
 * TarindoorBlockEntity, allowing each item stack to represent a
 * different door definition without needing separate block types.
 */
public class TarindoorBlockItem extends BlockItem {

    public TarindoorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String doorId = getDoorId(stack);
        TarindoorDefinition def = TarindoorRegistry.getDefinition(doorId);
        if (def != null) {
            return Component.literal(def.displayName());
        }
        return super.getName(stack);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level,
                                                  @Nullable Player player, ItemStack stack,
                                                  BlockState state) {
        // Set VARIANT on the block state for model selection (before super reads BE)
        String doorId = getDoorId(stack);
        if (!doorId.isEmpty()) {
            int variant = TarindoorRegistry.getVariantIndex(doorId);
            if (variant < 16 && state.hasProperty(TarindoorBlock.VARIANT)) {
                state = state.setValue(TarindoorBlock.VARIANT, variant);
                level.setBlock(pos, state, 3);
            }
        }
        // Let super handle BLOCK_ENTITY_DATA → BlockEntity NBT transfer
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        // Also apply DoorId directly in case super didn't (Create BE override)
        if (!doorId.isEmpty()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TarindoorBlockEntity doorBe && doorBe.getDoorId().isEmpty()) {
                doorBe.setDoorId(doorId);
            }
        }
        return result;
    }

    /**
     * Extract the DoorId from an item stack's block entity data component.
     */
    public static String getDoorId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("DoorId")) {
                return tag.getString("DoorId");
            }
        }
        return "";
    }

    /**
     * Create an item stack for a specific door definition.
     */
    public static ItemStack createStack(String doorId) {
        ItemStack stack = new ItemStack(TarindoorRegistry.getDoorBlock().get().asItem());
        if (!doorId.isEmpty()) {
            CompoundTag beTag = new CompoundTag();
            beTag.putString("id", "custom_train_door:tarindoor_door_be");
            beTag.putString("DoorId", doorId);
            stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beTag));
            // Door-specific item icon via custom_model_data model override
            int variant = TarindoorRegistry.getVariantIndex(doorId);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        }
        return stack;
    }
}
