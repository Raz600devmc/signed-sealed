package ru.realdocs.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.model.PaperElement;
import ru.realdocs.model.PaperModel;

/** NBT for newspaper layouts (stored on the server) and for the light reference kept on items. */
public final class PaperCodec {
   public static final String ITEM_TAG = "RealPaper";

   private PaperCodec() {
   }

   public static CompoundTag write(PaperModel m) {
      CompoundTag t = new CompoundTag();
      t.putInt("V", 1);
      t.putString("Id", m.id);
      t.putString("Name", m.name);
      t.putString("Price", m.price);
      t.putInt("Fmt", m.format);
      t.putInt("Tone", m.tone);
      t.putBoolean("Pub", m.published);
      t.putInt("Issue", m.issue);
      t.putString("Date", m.date);
      t.putString("Editor", m.editor);
      t.putString("EditorUuid", m.editorUuid);
      ListTag pages = new ListTag();

      for (List<PaperElement> p : m.pages) {
         ListTag els = new ListTag();

         for (PaperElement e : p) {
            els.add(writeElement(e));
         }

         CompoundTag pc = new CompoundTag();
         pc.put("E", els);
         pages.add(pc);
      }

      t.put("Pages", pages);
      return t;
   }

   public static PaperModel read(CompoundTag t) {
      PaperModel m = new PaperModel();
      m.id = t.getString("Id");
      m.name = t.getString("Name");
      m.price = t.getString("Price");
      m.format = t.getInt("Fmt");
      m.tone = t.getInt("Tone");
      m.published = t.getBoolean("Pub");
      m.issue = t.getInt("Issue");
      m.date = t.getString("Date");
      m.editor = t.getString("Editor");
      m.editorUuid = t.getString("EditorUuid");
      ListTag pages = t.getList("Pages", 10);

      for (int i = 0; i < pages.size() && i < PaperModel.MAX_PAGES; i++) {
         ListTag els = pages.getCompound(i).getList("E", 10);
         List<PaperElement> p = new ArrayList<>();

         for (int j = 0; j < els.size() && j < PaperModel.MAX_ELEMENTS; j++) {
            p.add(readElement(els.getCompound(j)));
         }

         m.pages.add(p);
      }

      if (m.pages.isEmpty()) {
         m.pages.add(new ArrayList<>());
      }

      return m;
   }

   static CompoundTag writeElement(PaperElement e) {
      CompoundTag x = new CompoundTag();
      x.putInt("T", e.type);
      x.putIntArray("R", new int[]{e.x, e.y, e.w, e.h});
      if (!e.text.isEmpty()) {
         x.putString("S", e.text);
      }

      if (!e.asset.isEmpty()) {
         x.putString("A", e.asset);
      }

      x.putIntArray("P", new int[]{e.font, e.size, e.flags, e.align, e.cols, e.lead, e.color, e.alpha, e.fit, e.filter, e.thick, e.tracking});
      return x;
   }

   static PaperElement readElement(CompoundTag x) {
      int[] r = x.getIntArray("R");
      PaperElement e = new PaperElement(x.getInt("T"), r.length > 0 ? r[0] : 0, r.length > 1 ? r[1] : 0, r.length > 2 ? r[2] : 100, r.length > 3 ? r[3] : 40);
      e.text = x.getString("S");
      e.asset = x.getString("A");
      int[] p = x.getIntArray("P");
      if (p.length >= 12) {
         e.font = p[0];
         e.size = p[1];
         e.flags = p[2];
         e.align = p[3];
         e.cols = p[4];
         e.lead = p[5];
         e.color = p[6];
         e.alpha = p[7];
         e.fit = p[8];
         e.filter = p[9];
         e.thick = p[10];
         e.tracking = p[11];
      }

      return e;
   }

   /** Reference stored on newspaper items (the layout itself stays on the server). */
   public static final class Ref {
      public String id = "";
      public String name = "";
      public int issue;
      public String date = "";
      public int format;
      public int pages = 1;
      public boolean published;
      public String editor = "";
      public String sig = "";
      public String canonical() {
         return "NPC1;" + this.id.length() + ":" + this.id + ";" + this.issue + ";" + this.name.length() + ":" + this.name + ";" + this.date + ";" + this.format;
      }
   }

   public static Ref ref(ItemStack stack) {
      CompoundTag t = Tags.element(stack, ITEM_TAG);
      Ref r = new Ref();
      if (t != null) {
         r.id = t.getString("Id");
         r.name = t.getString("Name");
         r.issue = t.getInt("Issue");
         r.date = t.getString("Date");
         r.format = t.getInt("Fmt");
         r.pages = Math.max(1, t.getInt("Pages"));
         r.published = t.getBoolean("Pub");
         r.editor = t.getString("Editor");
         r.sig = t.getString("Sig");
      }

      return r;
   }

   public static void writeRef(ItemStack stack, Ref r) {
      CompoundTag c = new CompoundTag();
      c.putString("Id", r.id);
      c.putString("Name", r.name);
      c.putInt("Issue", r.issue);
      c.putString("Date", r.date);
      c.putInt("Fmt", r.format);
      c.putInt("Pages", r.pages);
      c.putBoolean("Pub", r.published);
      c.putString("Editor", r.editor);
      if (!r.sig.isEmpty()) {
         c.putString("Sig", r.sig);
      }

      Tags.update(stack, t -> t.put(ITEM_TAG, c));
   }
}
