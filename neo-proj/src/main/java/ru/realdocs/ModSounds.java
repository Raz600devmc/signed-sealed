package ru.realdocs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "signedsealed");
   public static final DeferredHolder<SoundEvent, SoundEvent> PAPER = reg("paper");
   public static final DeferredHolder<SoundEvent, SoundEvent> PAGE = reg("page");
   public static final DeferredHolder<SoundEvent, SoundEvent> CARD = reg("card");
   public static final DeferredHolder<SoundEvent, SoundEvent> STAMP = reg("stamp");
   public static final DeferredHolder<SoundEvent, SoundEvent> PEN = reg("pen");
   public static final DeferredHolder<SoundEvent, SoundEvent> SHRED = reg("shred");
   public static final DeferredHolder<SoundEvent, SoundEvent> PRINT = reg("print");
   public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = reg("click");

   private ModSounds() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("signedsealed", name)));
   }
}
