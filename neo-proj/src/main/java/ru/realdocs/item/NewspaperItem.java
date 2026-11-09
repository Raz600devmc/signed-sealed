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
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import ru.realdocs.L;
import ru.realdocs.Side;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.model.PaperModel;

public class NewspaperItem extends Item {
   public NewspaperItem(Properties props) {
      super(props);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (level.isClientSide) {
         Side.client(() -> () -> ClientHooks.openNewspaper(hand));
      }

      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   /** Shift + right click on a wall pins a published copy to it. */
   public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
      Player player = ctx.getPlayer();
      net.minecraft.core.Direction face = ctx.getClickedFace();
      if (player == null || !player.isSecondaryUseActive() || !face.getAxis().isHorizontal()) {
         return net.minecraft.world.InteractionResult.PASS;
      } else {
         ItemStack stack = ctx.getItemInHand();
         PaperCodec.Ref r = PaperCodec.ref(stack);
         Level level = ctx.getLevel();
         if (!r.published) {
            if (!level.isClientSide) {
               player.displayClientMessage(L.c(L.k("Повесить можно только опубликованный выпуск.")).withStyle(ChatFormatting.RED), true);
            }

            return net.minecraft.world.InteractionResult.FAIL;
         } else {
            net.minecraft.core.BlockPos wall = ctx.getClickedPos();
            if (!level.getBlockState(wall).isFaceSturdy(level, wall, face)) {
               return net.minecraft.world.InteractionResult.FAIL;
            } else {
               if (!level.isClientSide) {
                  ru.realdocs.entity.PosterEntity p = ru.realdocs.entity.PosterEntity.create(level, wall, face, stack, 1);
                  if (!level.getEntitiesOfClass(ru.realdocs.entity.PosterEntity.class, p.getBoundingBox().deflate(0.05)).isEmpty()) {
                     player.displayClientMessage(L.c(L.k("Здесь уже что-то висит.")).withStyle(ChatFormatting.RED), true);
                     return net.minecraft.world.InteractionResult.FAIL;
                  }

                  level.addFreshEntity(p);
                  level.playSound(null, wall, (net.minecraft.sounds.SoundEvent)ru.realdocs.ModSounds.PAPER.get(), net.minecraft.sounds.SoundSource.BLOCKS, 0.9F, 1.0F);
                  if (!player.getAbilities().instabuild) {
                     stack.shrink(1);
                  }
               }

               return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
            }
         }
      }
   }

   public static String kind(int format) {
      return PaperModel.FORMATS[Math.max(0, Math.min(PaperModel.FORMATS.length - 1, format))];
   }

   public Component getName(ItemStack stack) {
      PaperCodec.Ref r = PaperCodec.ref(stack);
      if (r.published) {
         return L.c(L.k(kind(r.format)) + " «" + r.name + "»" + (r.issue > 0 ? " № " + r.issue : ""));
      } else if (!r.id.isEmpty()) {
         return L.c(L.k("Макет: ") + (r.name.isEmpty() ? L.k(kind(r.format)) : "«" + r.name + "»"));
      } else {
         return super.getName(stack);
      }
   }

   public void appendHoverText(ItemStack stack, TooltipContext tctx, List<Component> tips, TooltipFlag flag) {
      PaperCodec.Ref r = PaperCodec.ref(stack);
      if (r.published) {
         tips.add(L.c(L.k("Выпуск от ") + r.date + L.k(", страниц: ") + r.pages).withStyle(ChatFormatting.GRAY));
         if (!r.editor.isEmpty()) {
            tips.add(L.c(L.k("Редактор: ") + r.editor).withStyle(ChatFormatting.GRAY));
         }

         tips.add(L.c(L.k("ПКМ — читать. Тираж печатает редакция на принтере")).withStyle(ChatFormatting.DARK_GRAY));
         tips.add(L.c(L.k("Shift+ПКМ по стене — повесить")).withStyle(ChatFormatting.DARK_GRAY));
      } else if (!r.id.isEmpty()) {
         tips.add(L.c(L.k("Черновик вёрстки, страниц: ") + r.pages).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("ПКМ — продолжить вёрстку и опубликовать")).withStyle(ChatFormatting.DARK_GRAY));
      } else {
         tips.add(L.c(L.k("ПКМ — сверстать газету, листовку или плакат")).withStyle(ChatFormatting.GRAY));
         tips.add(L.c(L.k("Тексты, заголовки, картинки, шрифты — до 8 страниц")).withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}
