package ru.realdocs.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.ModEntities;

/** Scanner or computer: which digital archive (network) it works with. */
public class TerminalBE extends OfficeBE {
   public String network = "";

   public TerminalBE(BlockPos pos, BlockState state) {
      super(ModEntities.TERMINAL.get(), pos, state);
   }

   public String net() {
      return this.network.isBlank() ? (this.ownerName.isEmpty() ? "main" : this.ownerName) : this.network;
   }

   protected void writeClient(CompoundTag t) {
      t.putString("Network", this.network);
   }

   protected void readClient(CompoundTag t) {
      this.network = t.getString("Network");
   }

   protected void writeData(CompoundTag t) {
      t.putString("Network", this.network);
   }

   protected void readData(CompoundTag t) {
      this.network = t.getString("Network");
   }
}
