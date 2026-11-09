package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import ru.realdocs.blockentity.TerminalBE;
import ru.realdocs.server.DigitalService;

/** Scanner (scanner = true) or archive computer. */
public class TerminalBlock extends OfficeBlock implements EntityBlock {
   public final boolean scanner;

   public TerminalBlock(Properties p, boolean scanner, double x0, double y0, double z0, double x1, double y1, double z1) {
      super(p, x0, y0, z0, x1, y1, z1);
      this.scanner = scanner;
   }


   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new TerminalBE(pos, state);
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (!level.isClientSide && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TerminalBE be) {
         be.claim(sp);
      }
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         if (this.scanner) {
            DigitalService.useScanner(sp, pos);
         } else {
            DigitalService.openComputer(sp, pos);
         }
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
