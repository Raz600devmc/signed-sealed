package ru.realdocs.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.Describe;
import ru.realdocs.server.DigitalService;

/** Archive terminal: search scanned documents, read their metadata, open and print copies. */
public final class ComputerScreen extends UiScreen {
   private static final int BG = 0xF0081208;
   private static final int GREEN = -11141291;
   private static final int DIM = -13983153;
   private static final int BRIGHT = -5570646;
   private ArchivePackets.ComputerData data;
   private String query;
   private int sel = -1;
   private int scroll;
   private long deleteArm;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int listX;
   private int listY;
   private int listW;
   private int rows;
   private int detX;
   private int detW;

   public ComputerScreen(ArchivePackets.ComputerData data) {
      super(Component.literal(L.t("Цифровой архив")));
      this.data = data;
      this.query = data.query;
   }

   public boolean isFor(ArchivePackets.ComputerData d) {
      return this.data.pos.equals(d.pos);
   }

   public void update(ArchivePackets.ComputerData d) {
      this.data = d;
      this.sel = d.entries.isEmpty() ? -1 : Math.min(Math.max(this.sel, 0), d.entries.size() - 1);
      this.scroll = 0;
      this.rebuildWidgets();
   }

   private String[] meta(ArchivePackets.Entry e) {
      String[] m = e.meta.split("\u0001", -1);
      String[] out = new String[6];

      for (int i = 0; i < 6; i++) {
         out[i] = i < m.length ? m[i] : "";
      }

      return out;
   }

   private void act(int action, String text) {
      Net.sendToServer(new ArchivePackets.ComputerAction(this.data.pos, action, text));
   }

   private void search() {
      Sounds.play(Sounds.CLICK, 1.4F, 0.4F);
      this.act(DigitalService.SEARCH, this.query);
   }

   protected void init() {
      this.w0 = Math.min(600, this.width - 20);
      this.h0 = Math.min(340, this.height - 20);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      this.listX = this.x0 + 12;
      this.listW = (this.w0 - 36) * 55 / 100;
      this.detX = this.listX + this.listW + 12;
      this.detW = this.x0 + this.w0 - 12 - this.detX;
      UiField f = this.field(this.listX, this.y0 + 32, this.listW - 64, L.t("слова для поиска: название, фамилия, дата, номер…"), this.query, 80, s -> this.query = s);
      this.setFocused(f);
      this.button(L.t("Найти"), this.listX + this.listW - 60, this.y0 + 32, 60, 18, this::search).primary();
      this.listY = this.y0 + 58;
      this.rows = Math.max(1, (this.y0 + this.h0 - 36 - this.listY) / 20);
      int by = this.y0 + this.h0 - 28;
      ArchivePackets.Entry e = this.sel >= 0 && this.sel < this.data.entries.size() ? this.data.entries.get(this.sel) : null;
      int bw = (this.detW - 6) / 3;
      this.button(L.t("Открыть"), this.detX, by, bw, 20, () -> {
         if (e != null) {
            this.act(DigitalService.VIEW, e.id);
         }
      }).enabled(e != null);
      this.button(L.t("Печать копии"), this.detX + bw + 3, by, bw, 20, () -> {
         if (e != null) {
            this.act(DigitalService.PRINT, e.id);
         }
      }).enabled(e != null && this.data.canPrint && e.type == Describe.T_DOC);
      this.button(L.t("Удалить"), this.detX + 2 * (bw + 3), by, this.detW - 2 * (bw + 3), 20, () -> {
         if (e != null) {
            long now = System.currentTimeMillis();
            if (now - this.deleteArm > 3000L) {
               this.deleteArm = now;
               this.info(L.t("Запись удалится из архива навсегда. Нажмите ещё раз."));
            } else {
               this.act(DigitalService.DELETE, e.id);
            }
         }
      }).danger().enabled(e != null && this.data.manage);
      this.button(L.t("Выход"), this.listX, by, 60, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      g.fill(this.x0 - 3, this.y0 - 3, this.x0 + this.w0 + 3, this.y0 + this.h0 + 3, 0xFF2A2C30);
      g.fill(this.x0, this.y0, this.x0 + this.w0, this.y0 + this.h0, BG);

      for (int y = this.y0; y < this.y0 + this.h0; y += 3) {
         g.fill(this.x0, y, this.x0 + this.w0, y + 1, 0x10000000);
      }

      String head = L.t("ЦИФРОВОЙ АРХИВ") + " :: " + this.data.network + " :: " + L.t("записей: ") + this.data.total;
      g.drawString(this.font, head, this.x0 + 12, this.y0 + 12, BRIGHT, false);
      g.fill(this.x0 + 12, this.y0 + 24, this.x0 + this.w0 - 12, this.y0 + 25, DIM);
      List<ArchivePackets.Entry> es = this.data.entries;
      if (es.isEmpty()) {
         g.drawString(this.font, this.data.total == 0 ? L.t("> архив пуст: отсканируйте документы на сканере этой сети") : L.t("> ничего не найдено"), this.listX, this.listY + 4, GREEN, false);
      }

      for (int r = 0; r < this.rows && this.scroll + r < es.size(); r++) {
         int i = this.scroll + r;
         ArchivePackets.Entry e = es.get(i);
         int ry = this.listY + r * 20;
         boolean hover = mx >= this.listX && mx < this.listX + this.listW && my >= ry && my < ry + 19;
         if (i == this.sel) {
            g.fill(this.listX, ry, this.listX + this.listW, ry + 19, 0x5533FF55);
         } else if (hover) {
            g.fill(this.listX, ry, this.listX + this.listW, ry + 19, 0x2233FF55);
         }

         String[] m = this.meta(e);
         String t = "[" + L.t(Describe.TYPE_NAMES[Math.max(0, Math.min(4, e.type))]).substring(0, 1) + "] " + e.title;
         g.drawString(this.font, Ui.ellipsize(this.font, t, this.listW - 70), this.listX + 3, ry + 5, i == this.sel ? BRIGHT : GREEN, false);
         g.drawString(this.font, m[2], this.listX + this.listW - 3 - this.font.width(m[2]), ry + 5, DIM, false);
      }

      if (es.size() > this.rows) {
         String s = (this.scroll + 1) + "–" + Math.min(es.size(), this.scroll + this.rows) + "/" + es.size();
         g.drawString(this.font, s, this.listX + this.listW - this.font.width(s), this.y0 + 12, DIM, false);
      }

      g.fill(this.detX - 6, this.y0 + 32, this.detX - 5, this.y0 + this.h0 - 34, DIM);
      if (this.sel >= 0 && this.sel < es.size()) {
         ArchivePackets.Entry e = es.get(this.sel);
         String[] m = this.meta(e);
         int y = this.y0 + 34;

         for (String l : Ui.wrap(this.font, e.title, this.detW)) {
            g.drawString(this.font, l, this.detX, y, BRIGHT, false);
            y += 10;
         }

         y += 6;
         y = this.row(g, L.t("Тип"), L.t(Describe.TYPE_NAMES[Math.max(0, Math.min(4, e.type))]), y);
         y = this.row(g, L.t("Номер в архиве"), e.id, y);
         y = this.row(g, L.t("Автор / выдал"), m[0], y);
         y = this.row(g, L.t("Владелец / адресат"), m[1], y);
         y = this.row(g, L.t("Дата документа"), m[2], y);
         y = this.row(g, L.t("Оцифровал"), m[4] + ", " + m[3], y);
         int st = 0;

         try {
            st = Integer.parseInt(m[5]);
         } catch (NumberFormatException var12) {
         }

         this.row(g, L.t("Подлинность при сканировании"), L.t(DigitalService.STATUS[Math.max(0, Math.min(3, st))]), y);
      } else {
         g.drawString(this.font, L.t("> выберите запись"), this.detX, this.y0 + 34, DIM, false);
      }

      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   private int row(GuiGraphics g, String k, String v, int y) {
      g.drawString(this.font, k, this.detX, y, DIM, false);
      y += 10;

      for (String l : Ui.wrap(this.font, v.isEmpty() ? "—" : v, this.detW)) {
         g.drawString(this.font, l, this.detX, y, GREEN, false);
         y += 10;
      }

      return y + 3;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (mx >= this.listX && mx < this.listX + this.listW && my >= this.listY && my < this.listY + this.rows * 20) {
         int i = this.scroll + (int)((my - this.listY) / 20.0);
         if (i >= 0 && i < this.data.entries.size()) {
            this.sel = i;
            Sounds.play(Sounds.CLICK, 1.6F, 0.3F);
            this.rebuildWidgets();
            return true;
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      int ns = Math.max(0, Math.min(Math.max(0, this.data.entries.size() - this.rows), this.scroll - (int)Math.signum(sy)));
      if (ns != this.scroll) {
         this.scroll = ns;
      }

      return true;
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 257 || key == 335) {
         this.search();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }
}
