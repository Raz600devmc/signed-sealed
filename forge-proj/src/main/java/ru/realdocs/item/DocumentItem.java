package ru.realdocs.item;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import ru.realdocs.L;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.client.held.HeldItemRenderer;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.model.DocModel;

public class DocumentItem extends Item {
   public DocumentItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openDocument(hand));
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   public void initializeClient(Consumer<IClientItemExtensions> consumer) {
      consumer.accept(new IClientItemExtensions() {
         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return HeldItemRenderer.get();
         }
      });
   }

   public Component getName(ItemStack stack) {
      DocModel d = NbtCodec.readDoc(stack);
      if (d.issued) {
         return L.c((d.copy ? L.k("Копия: ") : "") + L.k(d.templateName()) + (d.number.isEmpty() ? "" : " № " + d.number));
      } else {
         return (Component)(d.body.isEmpty() && d.org.isEmpty() && d.subject.isEmpty() ? super.getName(stack) : L.c(L.k("Черновик: ") + L.k(d.templateName())));
      }
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
      DocModel d = NbtCodec.readDoc(stack);
      if (d.issued) {
         if (!d.org.isEmpty()) {
            tips.add(L.c(d.org).withStyle(ChatFormatting.GRAY));
         }

         tips.add(L.c(L.k("Автор: ") + d.author + ", " + d.date).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("Подписей: ") + d.sigs.size() + L.k(", печатей: ") + d.stamps.size()).withStyle(ChatFormatting.DARK_GRAY));
         tips.add(L.c(L.k("ПКМ — открыть. Проверка: /seal verify")).withStyle(ChatFormatting.DARK_GRAY));
      } else {
         tips.add(L.c(L.k("ПКМ — заполнить и подписать")).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("Печать: возьмите её в основную руку, документ — в другую")).withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}
