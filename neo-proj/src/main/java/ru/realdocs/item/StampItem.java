package ru.realdocs.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import ru.realdocs.L;
import ru.realdocs.data.Tags;
import ru.realdocs.server.DocService;

public class StampItem extends Item {
   public StampItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         DocService.applyStamp(sp, hand);
         player.getCooldowns().addCooldown(this, 15);
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   public Component getName(ItemStack stack) {
      return (Component)(Tags.has(stack) && Tags.get(stack).contains("StampOrg")
         ? L.c(L.k("Печать: ") + Tags.get(stack).getString("StampOrg"))
         : super.getName(stack));
   }

   public void appendHoverText(ItemStack stack, TooltipContext tctx, List<Component> tips, TooltipFlag flag) {
      if (Tags.has(stack) && Tags.get(stack).contains("StampId")) {
         tips.add(L.c(L.k("Номер: ") + Tags.get(stack).getString("StampId")).withStyle(ChatFormatting.GRAY));
      }

      tips.add(L.c(L.k("ПКМ с печатью в основной руке и документом в другой — поставить печать")).withStyle(ChatFormatting.DARK_GRAY));
   }
}
