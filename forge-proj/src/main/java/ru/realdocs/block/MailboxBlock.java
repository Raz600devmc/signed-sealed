package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import ru.realdocs.server.MailService;

public class MailboxBlock extends OfficeBlock {
   public MailboxBlock(Properties p) {
      super(p, 2.0, 0.0, 3.0, 14.0, 16.0, 13.0);
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else {
         if (!level.isClientSide && player instanceof ServerPlayer sp) {
            MailService.openMailbox(sp, pos);
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      }
   }
}
