package ru.realdocs.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public abstract class OfficeBlock extends HorizontalDirectionalBlock {
   private final VoxelShape[] shapes = new VoxelShape[4];

   protected OfficeBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
      super(p);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
      this.shapes[Direction.NORTH.get2DDataValue()] = Block.box(x0, y0, z0, x1, y1, z1);
      this.shapes[Direction.SOUTH.get2DDataValue()] = Block.box(16.0 - x1, y0, 16.0 - z1, 16.0 - x0, y1, 16.0 - z0);
      this.shapes[Direction.WEST.get2DDataValue()] = Block.box(z0, y0, 16.0 - x1, z1, y1, 16.0 - x0);
      this.shapes[Direction.EAST.get2DDataValue()] = Block.box(16.0 - z1, y0, x0, 16.0 - z0, y1, x1);
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> b) {
      b.add(new Property[]{FACING});
   }

   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return (BlockState)this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return this.shapes[((Direction)state.getValue(FACING)).get2DDataValue()];
   }
}
