package ru.realdocs.data;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Tags {
   private Tags() {
   }

   public static boolean has(ItemStack s) {
      CustomData cd = (CustomData)s.get(DataComponents.CUSTOM_DATA);
      return cd != null && !cd.isEmpty();
   }

   public static CompoundTag get(ItemStack s) {
      CustomData cd = (CustomData)s.get(DataComponents.CUSTOM_DATA);
      return cd == null ? new CompoundTag() : cd.copyTag();
   }

   public static CompoundTag element(ItemStack s, String key) {
      CompoundTag t = get(s);
      return t.contains(key, 10) ? t.getCompound(key) : null;
   }

   public static void update(ItemStack s, Consumer<CompoundTag> edit) {
      CustomData.update(DataComponents.CUSTOM_DATA, s, edit);
   }
}
