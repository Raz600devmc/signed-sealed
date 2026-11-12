package ru.realdocs.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ru.realdocs.L;
import ru.realdocs.ModSounds;
import ru.realdocs.entity.NoticeBoardEntity;
import ru.realdocs.entity.PosterEntity;

/** Hangs a 3x2 notice board on a wall; the clicked block is the bottom middle of the board. */
public class NoticeBoardItem extends Item {
   public NoticeBoardItem(Item.Properties p) {
      super(p);
   }

   public InteractionResult useOn(UseOnContext ctx) {
      Player player = ctx.getPlayer();
      Direction face = ctx.getClickedFace();
      Level level = ctx.getLevel();
      if (player != null && face.getAxis().isHorizontal()) {
         BlockPos wall = ctx.getClickedPos();
         if (!NoticeBoardEntity.fits(level, wall, face)) {
            if (!level.isClientSide) {
               player.displayClientMessage(L.c(L.k("Нужна ровная стена 3×2 блока (вы указываете нижний средний блок).")).withStyle(ChatFormatting.RED), true);
            }

            return InteractionResult.FAIL;
         } else {
            if (!level.isClientSide) {
               NoticeBoardEntity b = NoticeBoardEntity.create(level, wall, face);
               if (!level.getEntitiesOfClass(NoticeBoardEntity.class, b.getBoundingBox().deflate(0.05)).isEmpty()
                  || !level.getEntitiesOfClass(PosterEntity.class, b.getBoundingBox().deflate(0.05)).isEmpty()) {
                  player.displayClientMessage(L.c(L.k("Здесь уже что-то висит.")).withStyle(ChatFormatting.RED), true);
                  return InteractionResult.FAIL;
               }

               if (player instanceof ServerPlayer sp) {
                  b.ownerUuid = sp.getUUID().toString();
                  b.ownerName = sp.getGameProfile().getName();
               }

               level.addFreshEntity(b);
               level.playSound(null, wall, (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
               if (!player.getAbilities().instabuild) {
                  ctx.getItemInHand().shrink(1);
               }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      tip.add(L.c(L.k("ПКМ по стене — повесить (3×2 блока)")).withStyle(ChatFormatting.GRAY));
      tip.add(L.c(L.k("Документ в руке + ПКМ по доске — приколоть")).withStyle(ChatFormatting.GRAY));
   }
}
