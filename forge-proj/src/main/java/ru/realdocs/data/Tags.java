package ru.realdocs.data;

import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Item NBT access with the same API as the 1.21 build (which uses data components). */
public final class Tags {
   private Tags() {
   }

   public static boolean has(ItemStack s) {
      return s.hasTag() && !s.getTag().isEmpty();
   }

   public static CompoundTag get(ItemStack s) {
      return s.hasTag() ? s.getTag().copy() : new CompoundTag();
   }

   public static CompoundTag element(ItemStack s, String key) {
      CompoundTag t = s.getTagElement(key);
      return t == null ? null : t.copy();
   }

   public static void update(ItemStack s, Consumer<CompoundTag> edit) {
      edit.accept(s.getOrCreateTag());
   }
}
