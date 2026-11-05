package ru.realdocs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import ru.realdocs.ModSounds;

public final class Sounds {
   public static final RegistryObject<SoundEvent> PAPER = ModSounds.PAPER;
   public static final RegistryObject<SoundEvent> PAGE = ModSounds.PAGE;
   public static final RegistryObject<SoundEvent> CARD = ModSounds.CARD;
   public static final RegistryObject<SoundEvent> STAMP = ModSounds.STAMP;
   public static final RegistryObject<SoundEvent> PEN = ModSounds.PEN;
   public static final RegistryObject<SoundEvent> CLICK = ModSounds.CLICK;

   private Sounds() {
   }

   public static void play(RegistryObject<SoundEvent> s, float pitch) {
      play(s, pitch, 0.9F);
   }

   public static void play(RegistryObject<SoundEvent> s, float pitch, float volume) {
      try {
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)s.get(), pitch, volume));
      } catch (Exception var4) {
      }
   }
}
