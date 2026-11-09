package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.realdocs.blockentity.GateBE;
import ru.realdocs.server.GateService;

/**
 * Turnstile: a stand with a validator and a barrier arm across the rest of the block.
 * The closed arm blocks the passage (too high to jump over); a valid ticket drops it for a few seconds
 * and gives a redstone pulse.
 */
public class TicketGateBlock extends OfficeBlock implements EntityBlock {
   public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
   private static final VoxelShape[] OUTLINE_CLOSED = new VoxelShape[4];
   private static final VoxelShape[] OUTLINE_OPEN = new VoxelShape[4];
   private static final VoxelShape[] BLOCKED = new VoxelShape[4];
   private static final VoxelShape[] STAND = new VoxelShape[4];

   static {
      for (Direction d : Direction.Plane.HORIZONTAL) {
         int i = d.get2DDataValue();
         STAND[i] = box(d, 0.0, 0.0, 1.0, 5.2, 16.0, 15.0);
         OUTLINE_CLOSED[i] = Shapes.or(STAND[i], box(d, 5.2, 9.0, 7.0, 16.0, 11.0, 9.0));
         OUTLINE_OPEN[i] = Shapes.or(STAND[i], box(d, 5.2, 1.0, 7.0, 7.2, 11.0, 9.0));
         BLOCKED[i] = Shapes.or(STAND[i], box(d, 5.2, 0.0, 7.0, 16.0, 24.0, 9.0));
      }
   }

   public TicketGateBlock(Properties p) {
      super(p, 0.0, 0.0, 1.0, 5.2, 16.0, 15.0);
      this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
   }


   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      super.createBlockStateDefinition(b);
      b.add(new Property[]{POWERED});
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new GateBE(pos, state);
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      int i = state.getValue(FACING).get2DDataValue();
      return state.getValue(POWERED) ? OUTLINE_OPEN[i] : OUTLINE_CLOSED[i];
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      int i = state.getValue(FACING).get2DDataValue();
      return state.getValue(POWERED) ? STAND[i] : BLOCKED[i];
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (!level.isClientSide && placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof GateBE be) {
         be.claim(sp);
      }
   }

   public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer sp) {
         GateService.use(sp, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   public boolean isSignalSource(BlockState state) {
      return true;
   }

   public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(POWERED) ? 15 : 0;
   }

   public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
      return state.getValue(POWERED) && dir == Direction.UP ? 15 : 0;
   }

   public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
      if (state.getValue(POWERED)) {
         long left = level.getBlockEntity(pos) instanceof GateBE be ? be.openUntil - level.getGameTime() : 0L;
         if (left > 0L) {
            level.scheduleTick(pos, this, (int)left);
            return;
         }

         level.setBlock(pos, state.setValue(POWERED, false), 3);
         level.updateNeighborsAt(pos, this);
         level.updateNeighborsAt(pos.below(), this);
      }
   }

   /** Opens the arm for {@code seconds}; a ticket shown while it is open extends the time. */
   public static void pulse(ServerLevel level, BlockPos pos, BlockState state, int seconds) {
      int ticks = Math.max(1, seconds) * 20;
      if (level.getBlockEntity(pos) instanceof GateBE be) {
         be.openUntil = level.getGameTime() + ticks;
      }

      if (!state.getValue(POWERED)) {
         level.setBlock(pos, state.setValue(POWERED, true), 3);
         level.updateNeighborsAt(pos, state.getBlock());
         level.updateNeighborsAt(pos.below(), state.getBlock());
         level.scheduleTick(pos, state.getBlock(), ticks);
      }
   }
}
