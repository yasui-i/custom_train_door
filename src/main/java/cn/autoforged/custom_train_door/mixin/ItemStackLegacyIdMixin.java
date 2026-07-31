package cn.autoforged.custom_train_door.mixin;

import cn.autoforged.custom_train_door.tarindoor.LegacyTarindoorIdMigration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
public abstract class ItemStackLegacyIdMixin {
    @Inject(method = "parse", at = @At("HEAD"))
    private static void customTrainDoor$migrateLegacyItemStack(
            HolderLookup.Provider registries,
            Tag tag,
            CallbackInfoReturnable<Optional<ItemStack>> callback
    ) {
        LegacyTarindoorIdMigration.migrateItemStack(tag);
    }
}
