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
import ru.realdocs.L;
import ru.realdocs.Side;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.data.MailCodec;
import ru.realdocs.model.MailModel;

public class EnvelopeItem extends Item {
   public EnvelopeItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         Side.client(() -> () -> ClientHooks.openEnvelope(hand));
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
      consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
         public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return ru.realdocs.client.held.HeldItemRenderer.get();
         }
      });
   }

   public Component getName(ItemStack stack) {
      MailModel m = MailCodec.readMail(stack);
      if (m.blank()) {
         return super.getName(stack);
      } else {
         return L.c(L.k(m.sealed() ? "Запечатанное письмо" : "Письмо") + (m.to.isEmpty() ? "" : L.k(" для ") + m.to));
      }
   }

   public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level tctx, List<Component> tips, TooltipFlag flag) {
      MailModel m = MailCodec.readMail(stack);
      if (m.blank()) {
         tips.add(L.c(L.k("ПКМ — написать письмо, вложить документы и запечатать")).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("Отправка — через почтовый ящик")).withStyle(ChatFormatting.DARK_GRAY));
      } else {
         if (!m.from.isEmpty()) {
            tips.add(L.c(L.k("От: ") + m.from).withStyle(ChatFormatting.GRAY));
         }

         int n = MailCodec.count(stack, MailCodec.MAIL_TAG);
         if (n > 0) {
            tips.add(L.c(L.k("Вложений: ") + n).withStyle(ChatFormatting.GRAY));
         }

         if (m.sealed()) {
            tips.add(
               L.c(L.k("Запечатано сургучом") + (m.sealOrg.isEmpty() ? "" : " «" + m.sealOrg + "»")).withStyle(ChatFormatting.DARK_RED)
            );
         } else if (m.broken()) {
            tips.add(L.c(L.k("Печать сломана: письмо вскрыто")).withStyle(ChatFormatting.GOLD));
         } else {
            tips.add(L.c(L.k("Не запечатано")).withStyle(ChatFormatting.DARK_GRAY));
         }
      }
   }
}
