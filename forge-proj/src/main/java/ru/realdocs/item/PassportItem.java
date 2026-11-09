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
import ru.realdocs.model.PassportModel;

public class PassportItem extends Item {
   public PassportItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPassport(hand));
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
      PassportModel p = NbtCodec.readPass(stack);
      String[] kinds = new String[]{"Паспорт", "Удостоверение", "Билет", "Штраф"};
      String[] blanks = new String[]{"Бланк паспорта", "Бланк удостоверения", "Бланк билета", "Бланк штрафа"};
      int f = Math.max(0, Math.min(3, p.format));
      if (p.issued) {
         String who = switch (f) {
            case 2 -> (String)p.values.getOrDefault("from", "") + (p.values.containsKey("to") ? " → " + (String)p.values.get("to") : "");
            case 3 -> (String)p.values.getOrDefault("violator", "");
            default -> (String)p.values.getOrDefault("surname", "") + " " + (String)p.values.getOrDefault("name", "");
         };
         return L.c(L.k(kinds[f]) + (who.isBlank() ? "" : ": " + who.trim()));
      } else {
         return L.c(L.k(blanks[f]));
      }
   }

   public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
      PassportModel p = NbtCodec.readPass(stack);
      if (p.issued) {
         tips.add(L.c(L.k("Номер: ") + p.serial).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("Владелец: ") + p.holderName + L.k(", выдан ") + p.date).withStyle(ChatFormatting.GRAY));
         net.minecraft.nbt.CompoundTag punch = ru.realdocs.data.Tags.element(stack, "RealPunch");
         if (punch != null) {
            tips.add(L.c(L.k("Прокомпостирован: ") + punch.getString("Gate") + ", " + punch.getString("Date")).withStyle(ChatFormatting.GOLD));
         }

         tips.add(L.c(L.k("ПКМ — открыть. /seal show <игрок> — показать другому игроку")).withStyle(ChatFormatting.DARK_GRAY));
      } else {
         tips.add(
            L.c(
                  p.format == 0
                     ? L.k("Внутриигровой бланк. ПКМ — заполнить, сфотографироваться, подписать")
                     : L.k("Внутриигровой бланк. ПКМ — заполнить и подписать")
               )
               .withStyle(ChatFormatting.GRAY)
         );
      }

      tips.add(L.c(L.k("Внутриигровой документ")).withStyle(ChatFormatting.DARK_GRAY));
   }
}
