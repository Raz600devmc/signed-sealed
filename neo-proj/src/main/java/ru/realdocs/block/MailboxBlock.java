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
import ru.realdocs.server.MailService;

public class MailboxBlock extends OfficeBlock {
   public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);

   public MailboxBlock(Properties p) {
      super(p, 2.0, 0.0, 3.0, 14.0, 16.0, 13.0);
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         MailService.openMailbox(sp, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
