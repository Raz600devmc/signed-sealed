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
import ru.realdocs.server.OfficeService;

public class PrinterBlock extends OfficeBlock {
   public PrinterBlock(Properties p) {
      super(p, 1.0, 0.0, 2.0, 15.0, 9.0, 14.0);
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else {
         if (!level.isClientSide && player instanceof ServerPlayer sp) {
            OfficeService.openPrinter(sp, pos);
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      }
   }
}
