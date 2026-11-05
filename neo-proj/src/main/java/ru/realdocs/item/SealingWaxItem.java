package ru.realdocs.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import ru.realdocs.L;

public class SealingWaxItem extends Item {
   public SealingWaxItem(Properties props) {
      super(props);
   }

   public void appendHoverText(ItemStack stack, TooltipContext tctx, List<Component> tips, TooltipFlag flag) {
      tips.add(L.c(L.k("Нужен, чтобы запечатать конверт")).withStyle(ChatFormatting.GRAY));
      tips.add(L.c(L.k("С печатью в инвентаре на сургуче останется её оттиск")).withStyle(ChatFormatting.DARK_GRAY));
   }
}
