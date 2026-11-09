package ru.realdocs.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import ru.realdocs.blockentity.ArchiveBE;
import ru.realdocs.server.ArchiveService;

/** Archive shelf (shows what it holds) and filing cabinet (holds more). */
public class ArchiveBlock extends OfficeBlock implements EntityBlock {
   public final int capacity;

   public ArchiveBlock(Properties p, int capacity, double x0, double y0, double z0, double x1, double y1, double z1) {
      super(p, x0, y0, z0, x1, y1, z1);
      this.capacity = capacity;
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return simpleCodec(pp -> new ArchiveBlock(pp, this.capacity, 0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new ArchiveBE(pos, state);
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (!level.isClientSide && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ArchiveBE be) {
         be.claim(sp);
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         ArchiveService.open(sp, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
      if (!state.is(newState.getBlock())) {
         if (level.getBlockEntity(pos) instanceof ArchiveBE be) {
            for (ItemStack s : be.items) {
               Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
            }

            be.items.clear();
         }

         super.onRemove(state, level, pos, newState, moving);
      }
   }

   protected boolean hasAnalogOutputSignal(BlockState state) {
      return true;
   }

   protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof ArchiveBE be && !be.items.isEmpty() ? 1 + be.items.size() * 14 / Math.max(1, this.capacity) : 0;
   }
}
