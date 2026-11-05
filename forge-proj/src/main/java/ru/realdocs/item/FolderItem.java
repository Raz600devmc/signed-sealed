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
import ru.realdocs.model.FolderModel;

public class FolderItem extends Item {
   public FolderItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         Side.client(() -> () -> ClientHooks.openFolder(hand));
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
      FolderModel f = MailCodec.readFolder(stack);
      return f.label.isEmpty() ? super.getName(stack) : L.c(L.k("Папка «") + f.label + "»");
   }

   public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level tctx, List<Component> tips, TooltipFlag flag) {
      int n = MailCodec.count(stack, MailCodec.FOLDER_TAG);
      tips.add(L.c(L.k("Документов: ") + n + " / " + FolderModel.MAX_ITEMS).withStyle(ChatFormatting.GRAY));
      tips.add(L.c(L.k("ПКМ — открыть, вложить или достать документы")).withStyle(ChatFormatting.DARK_GRAY));
   }
}
