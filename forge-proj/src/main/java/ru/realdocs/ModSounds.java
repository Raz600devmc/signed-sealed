package ru.realdocs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "signedsealed");
   public static final RegistryObject<SoundEvent> PAPER = reg("paper");
   public static final RegistryObject<SoundEvent> PAGE = reg("page");
   public static final RegistryObject<SoundEvent> CARD = reg("card");
   public static final RegistryObject<SoundEvent> STAMP = reg("stamp");
   public static final RegistryObject<SoundEvent> PEN = reg("pen");
   public static final RegistryObject<SoundEvent> SHRED = reg("shred");
   public static final RegistryObject<SoundEvent> PRINT = reg("print");
   public static final RegistryObject<SoundEvent> CLICK = reg("click");

   private ModSounds() {
   }

   private static RegistryObject<SoundEvent> reg(String name) {
      return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("signedsealed", name)));
   }
}
