package ru.realdocs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.realdocs.ModEntities;
import ru.realdocs.ModSounds;
import ru.realdocs.Plat;
import ru.realdocs.Side;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.model.PaperModel;

/** A published newspaper, leaflet or poster pinned to a wall. */
public class PosterEntity extends Entity {
   private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(PosterEntity.class, EntityDataSerializers.ITEM_STACK);
   private static final EntityDataAccessor<Integer> DIR = SynchedEntityData.defineId(PosterEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(PosterEntity.class, EntityDataSerializers.INT);
   private BlockPos attach = BlockPos.ZERO;
   private int check;

   public PosterEntity(EntityType<? extends PosterEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
   }

   public static PosterEntity create(Level level, BlockPos wall, Direction face, ItemStack item, int size) {
      PosterEntity p = new PosterEntity(ModEntities.POSTER.get(), level);
      p.attach = wall.immutable();
      p.entityData.set(DIR, face.get3DDataValue());
      p.entityData.set(SIZE, size);
      p.entityData.set(ITEM, item.copyWithCount(1));
      p.placeAt();
      return p;
   }

   protected void defineSynchedData(SynchedEntityData.Builder b) {
      b.define(ITEM, ItemStack.EMPTY);
      b.define(DIR, Direction.SOUTH.get3DDataValue());
      b.define(SIZE, 1);
   }

   public ItemStack item() {
      return this.entityData.get(ITEM);
   }

   public Direction facing() {
      Direction d = Direction.from3DDataValue(this.entityData.get(DIR));
      return d.getAxis().isHorizontal() ? d : Direction.SOUTH;
   }

   public int size() {
      return Math.max(1, Math.min(3, this.entityData.get(SIZE)));
   }

   public float posterW() {
      return 0.9F * this.size();
   }

   public float posterH() {
      return this.posterW() * PaperModel.H / PaperModel.W;
   }

   private void placeAt() {
      Direction f = this.facing();
      double x = this.attach.getX() + 0.5 + f.getStepX() * 0.515;
      double y = this.attach.getY() + 0.5 + (this.posterH() - 1.0) / 2.0 - 0.05;
      double z = this.attach.getZ() + 0.5 + f.getStepZ() * 0.515;
      this.setPos(x, y, z);
      this.setYRot(f.toYRot());
   }

   protected AABB makeBoundingBox() {
      Direction f = this.facing();
      double hw = this.posterW() / 2.0;
      double hh = this.posterH() / 2.0;
      double t = 0.03;
      double ex = f.getAxis() == Direction.Axis.X ? t : hw;
      double ez = f.getAxis() == Direction.Axis.Z ? t : hw;
      Vec3 c = this.position();
      return new AABB(c.x - ex, c.y - hh, c.z - ez, c.x + ex, c.y + hh, c.z + ez);
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DIR.equals(key) || SIZE.equals(key)) {
         this.setBoundingBox(this.makeBoundingBox());
      }
   }

   public boolean survives() {
      return this.level().getBlockState(this.attach).isFaceSturdy(this.level(), this.attach, this.facing());
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide && this.check++ % 40 == 0 && !this.survives()) {
         this.drop();
      }
   }

   private void drop() {
      if (!this.isRemoved()) {
         ItemStack it = this.item();
         if (!it.isEmpty()) {
            this.spawnAtLocation(it.copy(), 0.0F);
         }

         this.level().playSound(null, this.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
         this.discard();
      }
   }

   public boolean isPickable() {
      return true;
   }

   public boolean isAttackable() {
      return true;
   }

   public boolean hurt(DamageSource source, float amount) {
      if (this.isInvulnerableTo(source)) {
         return false;
      } else {
         if (!this.level().isClientSide) {
            this.drop();
         }

         return true;
      }
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else if (player.isSecondaryUseActive()) {
         if (!this.level().isClientSide && player instanceof ServerPlayer) {
            int next = this.size() % 3 + 1;
            this.entityData.set(SIZE, next);
            this.placeAt();
            this.setBoundingBox(this.makeBoundingBox());
            this.level().playSound(null, this.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.7F, 1.3F);
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         if (this.level().isClientSide) {
            ItemStack it = this.item();
            Side.client(() -> () -> ClientHooks.readPoster(it));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }

   public ItemStack getPickResult() {
      return this.item().copy();
   }

   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 64.0 * 64.0;
   }

   protected void readAdditionalSaveData(CompoundTag t) {
      this.attach = new BlockPos(t.getInt("AX"), t.getInt("AY"), t.getInt("AZ"));
      this.entityData.set(DIR, t.getInt("Dir"));
      this.entityData.set(SIZE, Math.max(1, t.getInt("Size")));
      this.entityData.set(ITEM, Plat.loadStack(t.getCompound("Item"), this.level()));
      this.placeAt();
   }

   protected void addAdditionalSaveData(CompoundTag t) {
      t.putInt("AX", this.attach.getX());
      t.putInt("AY", this.attach.getY());
      t.putInt("AZ", this.attach.getZ());
      t.putInt("Dir", this.entityData.get(DIR));
      t.putInt("Size", this.size());
      t.put("Item", Plat.saveStack(this.item(), this.level()));
   }
}
