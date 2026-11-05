package ru.realdocs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import ru.realdocs.ModSounds;

public final class Sounds {
   public static final DeferredHolder<SoundEvent, SoundEvent> PAPER = ModSounds.PAPER;
   public static final DeferredHolder<SoundEvent, SoundEvent> PAGE = ModSounds.PAGE;
   public static final DeferredHolder<SoundEvent, SoundEvent> CARD = ModSounds.CARD;
   public static final DeferredHolder<SoundEvent, SoundEvent> STAMP = ModSounds.STAMP;
   public static final DeferredHolder<SoundEvent, SoundEvent> PEN = ModSounds.PEN;
   public static final DeferredHolder<SoundEvent, SoundEvent> CLICK = ModSounds.CLICK;

   private Sounds() {
   }

   public static void play(DeferredHolder<SoundEvent, SoundEvent> s, float pitch) {
      play(s, pitch, 0.9F);
   }

   public static void play(DeferredHolder<SoundEvent, SoundEvent> s, float pitch, float volume) {
      try {
         Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)s.get(), pitch, volume));
      } catch (Exception var4) {
      }
   }
}
