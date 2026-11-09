package ru.realdocs.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.ModEntities;

/** Ticket gate settings: what it accepts and what it does with the ticket. */
public class GateBE extends OfficeBE {
   public static final int MODE_PUNCH = 0;
   public static final int MODE_TAKE = 1;
   public static final int MODE_CHECK = 2;
   public static final String[] MODES = new String[]{"Компостировать", "Забирать билет", "Только проверять"};
   public String filter = "";
   public int mode = MODE_PUNCH;
   public int pulse = 3;
   public int passes;
   /** Game time when the open arm closes again (not saved: a reload just closes it). */
   public long openUntil;

   public GateBE(BlockPos pos, BlockState state) {
      super(ModEntities.GATE.get(), pos, state);
   }

   protected void writeData(CompoundTag t) {
      t.putString("Filter", this.filter);
      t.putInt("Mode", this.mode);
      t.putInt("Pulse", this.pulse);
      t.putInt("Passes", this.passes);
   }

   protected void readData(CompoundTag t) {
      this.filter = t.getString("Filter");
      this.mode = Math.max(0, Math.min(2, t.getInt("Mode")));
      this.pulse = t.contains("Pulse") ? Math.max(1, Math.min(10, t.getInt("Pulse"))) : 3;
      this.passes = t.getInt("Passes");
   }
}
