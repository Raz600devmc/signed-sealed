package ru.realdocs.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import ru.realdocs.L;
import ru.realdocs.client.ClientHooks;

public class ForgeryKitItem extends Item {
   public ForgeryKitItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openForgery(hand));
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
      tips.add(L.c(L.k("Документ в другой руке, набор в этой — ПКМ")).withStyle(ChatFormatting.GRAY));
      tips.add(L.c(L.k("Копия документа с другими данными (нужна бумага, для паспорта — книга)")).withStyle(ChatFormatting.DARK_GRAY));
      tips.add(L.c(L.k("Поддельная печать по оттиску (нужен железный слиток)")).withStyle(ChatFormatting.DARK_GRAY));
      tips.add(L.c(L.k("Проверка по реестру подделку выдаст")).withStyle(ChatFormatting.DARK_RED));
   }
}
