package ru.realdocs.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import ru.realdocs.server.OfficeService;

public class PrinterBlock extends OfficeBlock {
   public static final MapCodec<PrinterBlock> CODEC = simpleCodec(PrinterBlock::new);

   public PrinterBlock(Properties p) {
      super(p, 1.0, 0.0, 2.0, 15.0, 9.0, 14.0);
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         OfficeService.openPrinter(sp, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
