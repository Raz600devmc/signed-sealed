package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import ru.realdocs.blockentity.ReaderBE;
import ru.realdocs.server.ReaderService;

/**
 * Wall-mounted document reader: checks a passport, ID card, ticket, document or pass and gives a
 * redstone pulse when it is valid and matches the settings. The signal also powers the block it
 * hangs on, like a button.
 */
public class ReaderBlock extends OfficeBlock implements EntityBlock {
   /** 0 idle, 1 accepted (powered), 2 refused. */
   public static final IntegerProperty RESULT = IntegerProperty.create("result", 0, 2);

   public ReaderBlock(Properties p) {
      super(p, 4.0, 2.0, 12.0, 12.0, 14.0, 16.0);
      this.registerDefaultState(this.defaultBlockState().setValue(RESULT, 0));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(new Property[]{RESULT});
   }

   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      Direction face = ctx.getClickedFace();
      Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
      return this.defaultBlockState().setValue(FACING, f);
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new ReaderBE(pos, state);
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (!level.isClientSide && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ReaderBE be) {
         be.claim(sp);
      }
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         ReaderService.use(sp, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   public boolean isSignalSource(BlockState state) {
      return true;
   }

   public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(RESULT) == 1 ? 15 : 0;
   }

   public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(RESULT) == 1 && dir == state.getValue(FACING) ? 15 : 0;
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
      if (!state.is(newState.getBlock()) && state.getValue(RESULT) == 1) {
         notify(level, pos, state);
      }

      super.onRemove(state, level, pos, newState, moved);
   }

   static void notify(Level level, BlockPos pos, BlockState state) {
      level.updateNeighborsAt(pos, state.getBlock());
      level.updateNeighborsAt(pos.relative(state.getValue(FACING).getOpposite()), state.getBlock());
   }

   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (state.getValue(RESULT) != 0) {
         long left = level.getBlockEntity(pos) instanceof ReaderBE be ? be.until - level.getGameTime() : 0L;
         if (left > 0L) {
            level.scheduleTick(pos, this, (int)left);
            return;
         }

         level.setBlock(pos, state.setValue(RESULT, 0), 3);
         notify(level, pos, state);
      }
   }

   /** Shows the result for a while; result 1 also powers redstone for {@code ticks}. */
   public static void show(ServerLevel level, BlockPos pos, BlockState state, int result, int ticks) {
      if (level.getBlockEntity(pos) instanceof ReaderBE be) {
         be.until = level.getGameTime() + Math.max(1, ticks);
      }

      boolean wasPowered = state.getValue(RESULT) == 1;
      BlockState ns = state.setValue(RESULT, result);
      level.setBlock(pos, ns, 3);
      if (wasPowered != (result == 1)) {
         notify(level, pos, ns);
      }

      level.scheduleTick(pos, state.getBlock(), Math.max(1, ticks));
   }
}
