package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.realdocs.blockentity.OfficeBE;

public abstract class OfficeBlock extends HorizontalDirectionalBlock {
   private final VoxelShape[] shapes = new VoxelShape[4];

   protected OfficeBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
      super(p);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));

      for (Direction d : Direction.Plane.HORIZONTAL) {
         this.shapes[d.get2DDataValue()] = box(d, x0, y0, z0, x1, y1, z1);
      }
   }

   /** A box given for a block facing north, turned to face {@code f}. */
   public static VoxelShape box(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
      return switch (f) {
         case SOUTH -> Block.box(16.0 - x1, y0, 16.0 - z1, 16.0 - x0, y1, 16.0 - z0);
         case WEST -> Block.box(z0, y0, 16.0 - x1, z1, y1, 16.0 - x0);
         case EAST -> Block.box(16.0 - z1, y0, x0, 16.0 - z0, y1, x1);
         default -> Block.box(x0, y0, z0, x1, y1, z1);
      };
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      b.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return (BlockState)this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
      return level.getBlockEntity(pos) instanceof OfficeBE be && be.protectedFrom(player) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return this.shapes[((Direction)state.getValue(FACING)).get2DDataValue()];
   }
}
