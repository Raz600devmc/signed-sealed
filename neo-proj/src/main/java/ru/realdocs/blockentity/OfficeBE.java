package ru.realdocs.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.Perms;

/** Common part of the mod's block entities: owner, a lock and the people allowed through it. */
public abstract class OfficeBE extends BlockEntity {
   public String ownerUuid = "";
   public String ownerName = "";
   public String label = "";
   public boolean locked;
   public final List<String> members = new ArrayList<>();

   protected OfficeBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
      super(type, pos, state);
   }

   public boolean isOwner(ServerPlayer sp) {
      return this.ownerUuid.isEmpty() || this.ownerUuid.equals(sp.getUUID().toString()) || Perms.has(sp, Perms.ADMIN);
   }

   public boolean canUse(ServerPlayer sp) {
      if (!this.locked || this.isOwner(sp)) {
         return true;
      } else {
         String me = sp.getGameProfile().getName().toLowerCase(Locale.ROOT);

         for (String m : this.members) {
            if (m.toLowerCase(Locale.ROOT).equals(me)) {
               return true;
            }
         }

         return false;
      }
   }

   /** A locked block can only be broken by its owner or an operator (works on both sides). */
   public boolean protectedFrom(Player p) {
      return this.locked && !this.ownerUuid.isEmpty() && !this.ownerUuid.equals(p.getUUID().toString()) && !p.hasPermissions(2);
   }

   public String membersText() {
      return String.join(", ", this.members);
   }

   public void setMembers(String text) {
      this.members.clear();

      for (String s : text.split("[,;\\s]+")) {
         String n = s.strip();
         if (!n.isEmpty() && n.length() <= 16 && this.members.size() < 32 && !this.members.contains(n)) {
            this.members.add(n);
         }
      }
   }

   public void claim(ServerPlayer sp) {
      if (this.ownerUuid.isEmpty()) {
         this.ownerUuid = sp.getUUID().toString();
         this.ownerName = sp.getGameProfile().getName();
         this.setChanged();
      }
   }

   protected void writeCommon(CompoundTag t) {
      t.putString("Owner", this.ownerUuid);
      t.putString("OwnerName", this.ownerName);
      t.putString("Label", this.label);
      t.putBoolean("Locked", this.locked);
      t.putString("Members", String.join(",", this.members));
   }

   protected void readCommon(CompoundTag t) {
      this.ownerUuid = t.getString("Owner");
      this.ownerName = t.getString("OwnerName");
      this.label = t.getString("Label");
      this.locked = t.getBoolean("Locked");
      this.members.clear();

      for (String s : t.getString("Members").split(",")) {
         if (!s.isBlank()) {
            this.members.add(s.strip());
         }
      }
   }

   /** Data the client needs for rendering (kept small). */
   protected void writeClient(CompoundTag t) {
   }

   protected void readClient(CompoundTag t) {
   }

   protected abstract void writeData(CompoundTag t, HolderLookup.Provider reg);

   protected abstract void readData(CompoundTag t, HolderLookup.Provider reg);

   protected void saveAdditional(CompoundTag t, HolderLookup.Provider reg) {
      super.saveAdditional(t, reg);
      this.writeCommon(t);
      this.writeData(t, reg);
   }

   protected void loadAdditional(CompoundTag t, HolderLookup.Provider reg) {
      super.loadAdditional(t, reg);
      this.readCommon(t);
      if (t.contains("ClientOnly")) {
         this.readClient(t);
      } else {
         this.readData(t, reg);
      }
   }

   public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
      CompoundTag t = new CompoundTag();
      t.putBoolean("ClientOnly", true);
      this.writeCommon(t);
      this.writeClient(t);
      return t;
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public void sync() {
      this.setChanged();
      if (this.level != null && !this.level.isClientSide) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }
}
