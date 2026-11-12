package ru.realdocs.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import ru.realdocs.L;
import ru.realdocs.ModEntities;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.server.BoardService;
import ru.realdocs.server.OrgService;

/**
 * A 3x2 notice board hanging on a wall. Six sheets are pinned in a grid and shown on the board;
 * anyone can read them, the owner decides who may pin and take them. Optionally the first slot
 * always shows the latest issue of a newspaper.
 */
public class NoticeBoardEntity extends Entity {
   public static final int SLOTS = 6;
   public static final float W = 3.0F;
   public static final float H = 2.0F;
   public static final float DEPTH = 0.0625F;
   @SuppressWarnings("unchecked")
   private static final EntityDataAccessor<ItemStack>[] ITEMS = new EntityDataAccessor[SLOTS];
   private static final EntityDataAccessor<Integer> DIR = SynchedEntityData.defineId(NoticeBoardEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> LABEL = SynchedEntityData.defineId(NoticeBoardEntity.class, EntityDataSerializers.STRING);
   private BlockPos attach = BlockPos.ZERO;
   private int check;
   public String ownerUuid = "";
   public String ownerName = "";
   public final List<String> members = new ArrayList<>();
   /** When locked only the owner and the access list may pin or take sheets. */
   public boolean locked;
   /** Newspaper title whose latest issue is kept in the first slot; empty = off. */
   public String feed = "";
   public final String[] pinnedBy = new String[SLOTS];

   static {
      for (int i = 0; i < SLOTS; i++) {
         ITEMS[i] = SynchedEntityData.defineId(NoticeBoardEntity.class, EntityDataSerializers.ITEM_STACK);
      }
   }

   public NoticeBoardEntity(EntityType<? extends NoticeBoardEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
      this.setNoGravity(true);
      java.util.Arrays.fill(this.pinnedBy, "");
   }

   public static NoticeBoardEntity create(Level level, BlockPos wall, Direction face) {
      NoticeBoardEntity b = new NoticeBoardEntity(ModEntities.NOTICE_BOARD.get(), level);
      b.attach = wall.immutable();
      b.entityData.set(DIR, face.get3DDataValue());
      b.placeAt();
      return b;
   }

   protected void defineSynchedData() {
      for (int i = 0; i < SLOTS; i++) {
         this.entityData.define(ITEMS[i], ItemStack.EMPTY);
      }

      this.entityData.define(DIR, Direction.SOUTH.get3DDataValue());
      this.entityData.define(LABEL, "");
   }

   public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
      return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);
   }

   public ItemStack item(int slot) {
      return this.entityData.get(ITEMS[slot]);
   }

   public void setItem(int slot, ItemStack s) {
      this.entityData.set(ITEMS[slot], s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
   }

   public String label() {
      return this.entityData.get(LABEL);
   }

   public void setLabel(String s) {
      this.entityData.set(LABEL, s == null ? "" : s);
   }

   public Direction facing() {
      Direction d = Direction.from3DDataValue(this.entityData.get(DIR));
      return d.getAxis().isHorizontal() ? d : Direction.SOUTH;
   }

   /** Unit vector to the right when looking at the board. */
   public Direction right() {
      return this.facing().getCounterClockWise();
   }

   private void placeAt() {
      Direction f = this.facing();
      double x = this.attach.getX() + 0.5 + f.getStepX() * (0.5 + DEPTH / 2.0);
      double y = this.attach.getY() + 1.0;
      double z = this.attach.getZ() + 0.5 + f.getStepZ() * (0.5 + DEPTH / 2.0);
      this.setPos(x, y, z);
      this.setYRot(f.toYRot());
   }

   protected AABB makeBoundingBox() {
      Direction f = this.facing();
      double hw = W / 2.0;
      double hh = H / 2.0;
      double t = DEPTH / 2.0;
      double ex = f.getAxis() == Direction.Axis.X ? t : hw;
      double ez = f.getAxis() == Direction.Axis.Z ? t : hw;
      Vec3 c = this.position();
      return new AABB(c.x - ex, c.y - hh, c.z - ez, c.x + ex, c.y + hh, c.z + ez);
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (DIR.equals(key)) {
         this.setBoundingBox(this.makeBoundingBox());
      }
   }

   /** All six wall blocks behind the board must be solid. */
   public static boolean fits(Level level, BlockPos wall, Direction face) {
      Direction r = face.getCounterClockWise();

      for (int i = -1; i <= 1; i++) {
         for (int j = 0; j <= 1; j++) {
            BlockPos p = wall.relative(r, i).above(j);
            if (!level.getBlockState(p).isFaceSturdy(level, p, face)) {
               return false;
            }

            if (!level.getBlockState(p.relative(face)).canBeReplaced()) {
               return false;
            }
         }
      }

      return true;
   }

   public boolean survives() {
      return fits(this.level(), this.attach, this.facing());
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         if (this.check++ % 40 == 0 && !this.survives()) {
            this.drop(true);
         } else if (this.check % 100 == 1 && !this.feed.isEmpty()) {
            BoardService.refreshFeed(this);
         }
      }
   }

   public void drop(boolean withBoard) {
      if (!this.isRemoved()) {
         for (int i = 0; i < SLOTS; i++) {
            ItemStack it = this.item(i);
            if (!it.isEmpty() && !(i == 0 && !this.feed.isEmpty())) {
               this.spawnAtLocation(it.copy(), 0.0F);
            }
         }

         if (withBoard) {
            this.spawnAtLocation(new ItemStack(ModItems.NOTICE_BOARD.get()), 0.0F);
         }

         this.level().playSound(null, this.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
         this.discard();
      }
   }

   public boolean isOwner(ServerPlayer sp) {
      return this.ownerUuid.isEmpty() || this.ownerUuid.equals(sp.getUUID().toString()) || Perms.has(sp, Perms.ADMIN);
   }

   /** Owner, admins and everyone on the access list (names or @organizations). */
   public boolean isMember(ServerPlayer sp) {
      if (this.isOwner(sp)) {
         return true;
      } else {
         String me = sp.getGameProfile().getName().toLowerCase(Locale.ROOT);

         for (String m : this.members) {
            if (m.startsWith("@") ? OrgService.member(sp.server, m.substring(1), sp.getUUID().toString()) : m.toLowerCase(Locale.ROOT).equals(me)) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean canPin(ServerPlayer sp) {
      return !this.locked || this.isMember(sp);
   }

   public boolean canTake(ServerPlayer sp, int slot) {
      return this.isMember(sp) || !this.locked && this.pinnedBy[slot].equals(sp.getUUID().toString());
   }

   public void setMembers(String text) {
      this.members.clear();

      for (String s : (text == null ? "" : text).split("[,;]+")) {
         String n = s.strip();
         if (!n.isEmpty() && n.length() <= 41 && this.members.size() < 32 && !this.members.contains(n)) {
            this.members.add(n);
         }
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
            if (source.getEntity() instanceof ServerPlayer sp && !this.isOwner(sp) && !sp.getAbilities().instabuild) {
               sp.displayClientMessage(L.c(L.k("Снять доску может только владелец: ") + this.ownerName).withStyle(ChatFormatting.RED), true);
               return false;
            }

            this.drop(!(source.getEntity() instanceof Player p && p.getAbilities().instabuild));
         }

         return true;
      }
   }

   /** Which of the six cells a point on the board is in (0..2 top row, 3..5 bottom row). */
   public int slotAt(Vec3 local) {
      Direction r = this.right();
      double u = local.x * r.getStepX() + local.z * r.getStepZ();
      int col = (int)Math.floor(u + 1.5);
      int row = local.y > 0.0 ? 0 : 1;
      return Math.max(0, Math.min(2, col)) + row * 3;
   }

   public InteractionResult interactAt(Player player, Vec3 local, InteractionHand hand) {
      if (hand != InteractionHand.MAIN_HAND) {
         return InteractionResult.PASS;
      } else {
         if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            BoardService.interact(sp, this, this.slotAt(local));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }

   public ItemStack getPickResult() {
      return new ItemStack(ModItems.NOTICE_BOARD.get());
   }

   public boolean shouldRenderAtSqrDistance(double d) {
      return d < 64.0 * 64.0;
   }

   protected void readAdditionalSaveData(CompoundTag t) {
      this.attach = new BlockPos(t.getInt("AX"), t.getInt("AY"), t.getInt("AZ"));
      this.entityData.set(DIR, t.getInt("Dir"));
      this.setLabel(t.getString("Label"));
      this.ownerUuid = t.getString("Owner");
      this.ownerName = t.getString("OwnerName");
      this.locked = t.getBoolean("Locked");
      this.feed = t.getString("Feed");
      this.members.clear();
      ListTag ml = t.getList("Members", 8);

      for (int i = 0; i < ml.size(); i++) {
         this.members.add(ml.getString(i));
      }

      ListTag il = t.getList("Items", 10);

      for (int i = 0; i < SLOTS; i++) {
         CompoundTag c = i < il.size() ? il.getCompound(i) : new CompoundTag();
         this.setItem(i, c.contains("Item") ? Plat.loadStack(c.getCompound("Item"), this.level()) : ItemStack.EMPTY);
         this.pinnedBy[i] = c.getString("By");
      }

      this.placeAt();
   }

   protected void addAdditionalSaveData(CompoundTag t) {
      t.putInt("AX", this.attach.getX());
      t.putInt("AY", this.attach.getY());
      t.putInt("AZ", this.attach.getZ());
      t.putInt("Dir", this.entityData.get(DIR));
      t.putString("Label", this.label());
      t.putString("Owner", this.ownerUuid);
      t.putString("OwnerName", this.ownerName);
      t.putBoolean("Locked", this.locked);
      t.putString("Feed", this.feed);
      ListTag ml = new ListTag();

      for (String m : this.members) {
         ml.add(StringTag.valueOf(m));
      }

      t.put("Members", ml);
      ListTag il = new ListTag();

      for (int i = 0; i < SLOTS; i++) {
         CompoundTag c = new CompoundTag();
         ItemStack s = this.item(i);
         if (!s.isEmpty()) {
            c.put("Item", Plat.saveStack(s, this.level()));
         }

         c.putString("By", this.pinnedBy[i]);
         il.add(c);
      }

      t.put("Items", il);
   }
}
