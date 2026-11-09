package ru.realdocs.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.ArchiveService;

/** Archive shelf / filing cabinet: search, look at documents, put in and take out. */
public final class ArchiveScreen extends UiScreen {
   private ArchivePackets.ArchiveData data;
   private String query = "";
   private String label;
   private String members;
   private boolean locked;
   private int scroll;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int listX;
   private int listY;
   private int listW;
   private int rows;
   private final List<Integer> shown = new ArrayList<>();

   public ArchiveScreen(ArchivePackets.ArchiveData data) {
      super(Component.literal(L.t("Архив")));
      this.update(data, false);
   }

   public boolean isFor(ArchivePackets.ArchiveData d) {
      return this.data != null && this.data.pos.equals(d.pos);
   }

   public void update(ArchivePackets.ArchiveData d, boolean rebuild) {
      this.data = d;
      this.label = d.label;
      this.members = d.members;
      this.locked = d.locked;
      if (rebuild) {
         this.rebuildWidgets();
      }
   }

   private void filter() {
      this.shown.clear();
      String[] words = this.query.toLowerCase(Locale.ROOT).strip().split("\\s+");

      for (int i = 0; i < this.data.entries.size(); i++) {
         ArchivePackets.Entry e = this.data.entries.get(i);
         String hay = (e.title + " " + e.meta).toLowerCase(Locale.ROOT);
         boolean ok = true;

         for (String w : words) {
            if (!w.isEmpty() && !hay.contains(w)) {
               ok = false;
            }
         }

         if (ok) {
            this.shown.add(i);
         }
      }
   }

   private void act(int action, int index) {
      Net.sendToServer(new ArchivePackets.ArchiveAction(this.data.pos, action, index, this.label, this.members, this.locked));
   }

   protected void init() {
      this.filter();
      this.w0 = Math.min(this.data.manage ? 560 : 400, this.width - 24);
      this.h0 = Math.min(320, this.height - 24);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      int side = this.data.manage ? 170 : 0;
      this.listX = this.x0 + 12;
      this.listW = this.w0 - 24 - (side > 0 ? side + 12 : 0);
      UiField search = this.field(this.listX, this.y0 + 30, this.listW, L.t("поиск по названию, владельцу, дате…"), this.query, 60, s -> {
         if (!s.equals(this.query)) {
            this.query = s;
            this.scroll = 0;
            this.rebuildWidgets();
         }
      });
      if (!this.query.isEmpty()) {
         this.setFocused(search);
      }
      this.listY = this.y0 + 56;
      int bottom = this.y0 + this.h0 - 34;
      this.rows = Math.max(1, (bottom - this.listY) / 22);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.shown.size() - this.rows)));

      for (int r = 0; r < this.rows && this.scroll + r < this.shown.size(); r++) {
         int idx = this.shown.get(this.scroll + r);
         int ry = this.listY + r * 22;
         this.button(L.t("Открыть"), this.listX + this.listW - 112, ry + 2, 54, 16, () -> this.act(ArchiveService.VIEW, idx));
         this.button(L.t("Достать"), this.listX + this.listW - 54, ry + 2, 54, 16, () -> this.act(ArchiveService.TAKE, idx));
      }

      int by = this.y0 + this.h0 - 28;
      int bw = (this.listW - 4) / 2;
      this.button(L.t("Положить из руки"), this.listX, by, bw, 20, () -> this.act(ArchiveService.PUT, 0)).primary()
         .enabled(this.data.entries.size() < this.data.capacity);
      this.button(L.t("Положить все документы"), this.listX + bw + 4, by, this.listW - bw - 4, 20, () -> this.act(ArchiveService.PUT_ALL, 0))
         .enabled(this.data.entries.size() < this.data.capacity);
      if (this.data.manage) {
         int sx = this.x0 + this.w0 - 12 - side;
         UiField lf = this.field(sx, this.y0 + 40, side, L.t("например, Отдел кадров"), this.label, 40, s -> this.label = s);
         lf.label(L.t("Надпись"));
         UiField mf = this.field(sx, this.y0 + 76, side, L.t("ники через запятую"), this.members, 600, s -> this.members = s);
         mf.label(L.t("Доступ к замку"));
         this.button(this.locked ? L.t("Заперто") : L.t("Открыто для всех"), sx, this.y0 + 100, side, 18, () -> {
            this.locked = !this.locked;
            this.rebuildWidgets();
         }).selected(this.locked);
         this.button(L.t("Сохранить"), sx, this.y0 + 124, side, 18, () -> this.act(ArchiveService.SET, 0)).primary();
      }

      this.button(L.t("Закрыть"), this.x0 + this.w0 - 74, by, 62, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      String title = this.data.label.isEmpty() ? L.t("Архив") : this.data.label;
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, title + "  " + this.data.entries.size() + "/" + this.data.capacity);
      if (this.shown.isEmpty()) {
         g.drawString(this.font, this.data.entries.isEmpty() ? L.t("Пусто.") : L.t("Ничего не найдено."), this.listX, this.listY + 6, -9543342, false);
      }

      for (int r = 0; r < this.rows && this.scroll + r < this.shown.size(); r++) {
         ArchivePackets.Entry e = this.data.entries.get(this.shown.get(this.scroll + r));
         int ry = this.listY + r * 22;
         if (r % 2 == 0) {
            g.fill(this.listX, ry, this.listX + this.listW, ry + 20, 0x14FFFFFF);
         }

         int tw = this.listW - 120;
         g.drawString(this.font, Ui.ellipsize(this.font, e.title, tw), this.listX + 4, ry + 2, -1183755, false);
         g.drawString(this.font, Ui.ellipsize(this.font, e.meta, tw), this.listX + 4, ry + 11, -9543342, false);
      }

      if (this.shown.size() > this.rows) {
         String s = (this.scroll + 1) + "–" + Math.min(this.shown.size(), this.scroll + this.rows) + " / " + this.shown.size();
         g.drawString(this.font, s, this.listX + this.listW - this.font.width(s), this.y0 + 18, -9543342, false);
      }

      if (this.data.manage) {
         int sx = this.x0 + this.w0 - 12 - 170;
         Ui.section(g, this.font, L.t("Настройки"), sx, this.y0 + 26, 170);
         g.drawString(this.font, L.t("Владелец: ") + this.data.owner, sx, this.y0 + 150, -9543342, false);
      }

      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   public boolean mouseScrolled(double mx, double my, double sy) {
      int ns = Math.max(0, Math.min(Math.max(0, this.shown.size() - this.rows), this.scroll - (int)Math.signum(sy)));
      if (ns != this.scroll) {
         this.scroll = ns;
         this.rebuildWidgets();
      }

      return true;
   }
}
