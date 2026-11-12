package ru.realdocs.blockentity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.ModEntities;

/** Reader settings: which documents it accepts, extra filters, what it does with them, and a short log. */
public class ReaderBE extends OfficeBE {
   public static final int K_PASSPORT = 1;
   public static final int K_CARD = 2;
   public static final int K_TICKET = 4;
   public static final int K_DOCUMENT = 8;
   public static final String[] KIND_NAMES = new String[]{"Паспорта", "Удостоверения", "Билеты", "Документы и пропуска"};
   public static final int MODE_CHECK = 0;
   public static final int MODE_PUNCH = 1;
   public static final int MODE_TAKE = 2;
   public static final String[] MODES = new String[]{"Только проверять", "Компостировать (один раз)", "Забирать документ"};
   public static final int MAX_LOG = 12;
   public int kinds = K_PASSPORT | K_CARD | K_TICKET | K_DOCUMENT;
   public String filter = "";
   public String zone = "";
   public int mode = MODE_CHECK;
   /** Seconds of redstone signal, 1..30. */
   public int pulse = 2;
   /** Only the person the document was issued to may use it. */
   public boolean holderOnly = true;
   public int passes;
   public final List<String> log = new ArrayList<>();
   /** Game time when the shown result ends (not saved). */
   public long until;

   public ReaderBE(BlockPos pos, BlockState state) {
      super(ModEntities.READER.get(), pos, state);
   }

   public void addLog(String line) {
      this.log.add(line);

      while (this.log.size() > MAX_LOG) {
         this.log.remove(0);
      }

      this.setChanged();
   }

   protected void writeData(CompoundTag t) {
      t.putInt("Kinds", this.kinds);
      t.putString("Filter", this.filter);
      t.putString("Zone", this.zone);
      t.putInt("Mode", this.mode);
      t.putInt("Pulse", this.pulse);
      t.putBoolean("HolderOnly", this.holderOnly);
      t.putInt("Passes", this.passes);
      ListTag l = new ListTag();

      for (String s : this.log) {
         l.add(StringTag.valueOf(s));
      }

      t.put("Log", l);
   }

   protected void readData(CompoundTag t) {
      this.kinds = t.contains("Kinds") ? t.getInt("Kinds") & 15 : 15;
      this.filter = t.getString("Filter");
      this.zone = t.getString("Zone");
      this.mode = Math.max(0, Math.min(2, t.getInt("Mode")));
      this.pulse = t.contains("Pulse") ? Math.max(1, Math.min(30, t.getInt("Pulse"))) : 2;
      this.holderOnly = !t.contains("HolderOnly") || t.getBoolean("HolderOnly");
      this.passes = t.getInt("Passes");
      this.log.clear();
      ListTag l = t.getList("Log", 8);

      for (int i = 0; i < l.size(); i++) {
         this.log.add(l.getString(i));
      }
   }
}
