package cn.autoforged.custom_train_door.sound;

import cn.autoforged.custom_train_door.CustomTrainDoorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, CustomTrainDoorMod.MODID);

    public static final SoundEvent CR400BF_DOOR_OPEN_EVENT = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "cr400bf_door_open"), 16.0F);

    public static final SoundEvent CR400BF_DOOR_CLOSE_EVENT = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "cr400bf_door_close"), 16.0F);

    public static final DeferredHolder<SoundEvent, SoundEvent> CR400BF_DOOR_OPEN = SOUND_EVENTS.register(
            "cr400bf_door_open",
            () -> CR400BF_DOOR_OPEN_EVENT
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CR400BF_DOOR_CLOSE = SOUND_EVENTS.register(
            "cr400bf_door_close",
            () -> CR400BF_DOOR_CLOSE_EVENT
    );

    public static final SoundEvent CRH2A_DOOR_OPEN_EVENT = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "crh2a_door_open"), 16.0F);

    public static final SoundEvent CRH2A_DOOR_CLOSE_EVENT = SoundEvent.createFixedRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CustomTrainDoorMod.MODID, "crh2a_door_close"), 16.0F);

    public static final DeferredHolder<SoundEvent, SoundEvent> CRH2A_DOOR_OPEN = SOUND_EVENTS.register(
            "crh2a_door_open",
            () -> CRH2A_DOOR_OPEN_EVENT
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> CRH2A_DOOR_CLOSE = SOUND_EVENTS.register(
            "crh2a_door_close",
            () -> CRH2A_DOOR_CLOSE_EVENT
    );

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}