package ru.realdocs.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.Plat;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;

/** What is pinned on a notice board: read or take sheets; the owner also sets access and the newspaper feed. */
public final class NoticeBoardScreen extends UiScreen {
   private CompoundTag data;
   private String label;
   private String members;
   private String feed;
   private boolean locked;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int gridW;

   public NoticeBoardScreen(CompoundTag t) {
      super(Component.literal(L.t("Доска объявлений")));
      this.load(t);
   }

   private void load(CompoundTag t) {
      this.data = t;
      this.label = t.getString("Label");
      this.members = t.getString("Members");
      this.feed = t.getString("Feed");
      this.locked = t.getBoolean("Locked");
   }

   public boolean isFor(CompoundTag t) {
      return t.getInt("Entity") == this.data.getInt("Entity");
   }

   public void update(CompoundTag t) {
      this.load(t);
      this.rebuildWidgets();
   }

   private boolean manage() {
      return this.data.getBoolean("Manage");
   }

   private CompoundTag slot(int i) {
      ListTag l = this.data.getList("Slots", 10);
      return i < l.size() ? l.getCompound(i) : new CompoundTag();
   }

   private void act(String action, int slot) {
      CompoundTag t = new CompoundTag();
      t.putInt("Entity", this.data.getInt("Entity"));
      t.putInt("Slot", slot);
      ClientOffice.req(action, t);
   }

   protected void init() {
      this.w0 = Math.min(this.manage() ? 620 : 440, this.width - 24);
      this.h0 = Math.min(300, this.height - 16);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      this.gridW = this.manage() ? Math.min(420, this.w0 - 200) : this.w0 - 24;
      int gx = this.x0 + 12;
      int gy = this.y0 + 30;
      int cw = (this.gridW - 8) / 3;
      int ch = (this.h0 - 30 - 40 - 6) / 2;

      for (int i = 0; i < 6; i++) {
         CompoundTag s = this.slot(i);
         int cx = gx + i % 3 * (cw + 4);
         int cy = gy + i / 3 * (ch + 6);
         if (s.contains("Item")) {
            int bw = (cw - 12) / 2;
            int ii = i;
            this.button(L.t("Читать"), cx + 4, cy + ch - 24, bw, 18, () -> this.act("board_view", ii)).primary();
            this.button(L.t("Снять"), cx + 8 + bw, cy + ch - 24, cw - 12 - bw, 18, () -> this.act("board_take", ii)).enabled(s.getBoolean("Take"));
         }
      }

      if (this.manage()) {
         int sx = this.x0 + this.gridW + 22;
         int sw = this.x0 + this.w0 - 12 - sx;
         int y = this.y0 + 52;
         UiField lf = this.field(sx, y, sw, L.t("например, Объявления мэрии"), this.label, 40, s -> this.label = s);
         lf.label(L.t("Надпись на доске"));
         y += 32;
         UiField ff = this.field(sx, y, sw, L.t("название газеты"), this.feed, 40, s -> this.feed = s);
         ff.label(L.t("Лента: последний выпуск газеты"));
         y += 32;
         NameField mf = this.nameField(sx, y, sw, L.t("ники или @организация через запятую"), this.members, 600, s -> this.members = s, true, false);
         mf.label(L.t("Кто может вешать и снимать"));
         y += 22;
         this.button(this.locked ? L.t("Вешают только допущенные") : L.t("Вешать может каждый"), sx, y, sw, 18, () -> {
            this.locked = !this.locked;
            this.rebuildWidgets();
         }).selected(this.locked);
         y += 26;
         this.button(L.t("Сохранить"), sx, y, sw, 20, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("Entity", this.data.getInt("Entity"));
            t.putString("Label", this.label);
            t.putString("Feed", this.feed);
            t.putString("Members", this.members);
            t.putBoolean("Locked", this.locked);
            ClientOffice.req("board_save", t);
         }).primary();
      }

      this.button(L.t("Закрыть"), this.x0 + this.w0 - 78, this.y0 + this.h0 - 28, 66, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      String title = this.data.getString("Label").isEmpty() ? L.t("Доска объявлений") : L.t("Доска объявлений") + " · " + this.data.getString("Label");
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, title);
      int gx = this.x0 + 12;
      int gy = this.y0 + 30;
      int cw = (this.gridW - 8) / 3;
      int ch = (this.h0 - 30 - 40 - 6) / 2;
      Minecraft mc = Minecraft.getInstance();

      for (int i = 0; i < 6; i++) {
         CompoundTag s = this.slot(i);
         int cx = gx + i % 3 * (cw + 4);
         int cy = gy + i / 3 * (ch + 6);
         g.fill(cx, cy, cx + cw, cy + ch, 0x22FFFFFF);
         Ui.outline(g, cx, cy, cw, ch, 0x55A08C6E);
         if (s.contains("Item") && mc.level != null) {
            ItemStack it = Plat.loadStack(s.getCompound("Item"), mc.level);
            g.renderItem(it, cx + 6, cy + 6);
            int ty = cy + 8;

            for (var line : this.font.split(Component.literal(L.t(s.getString("Title"))), cw - 32)) {
               if (ty + 10 > cy + ch - 28) {
                  break;
               }

               g.drawString(this.font, line, cx + 26, ty, -1183755, false);
               ty += 10;
            }
         } else {
            g.drawCenteredString(this.font, L.t("пусто"), cx + cw / 2, cy + ch / 2 - 4, -7633519);
         }
      }

      String hint = this.data.getBoolean("CanPin")
         ? L.t("Чтобы приколоть: документ в руку и ПКМ по нужному месту доски.")
         : L.t("Вешать объявления на эту доску могут только допущенные.");
      g.drawString(this.font, Ui.ellipsize(this.font, hint, this.w0 - 110), this.x0 + 12, this.y0 + this.h0 - 22, -7633519, false);
      if (this.manage()) {
         int sx = this.x0 + this.gridW + 22;
         int sw = this.x0 + this.w0 - 12 - sx;
         Ui.section(g, this.font, L.t("Настройки"), sx, this.y0 + 28, sw);
      }

      super.render(g, mx, my, pt);
   }
}
