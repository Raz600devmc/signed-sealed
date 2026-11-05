package ru.realdocs.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class PrinterScreen extends UiScreen {
   private final BlockPos pos;
   private final List<String[]> templates;
   private int sel = 0;
   private int count = 1;
   private int scroll;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int listY;
   private int rows;
   private int run = 8;
   private int runY;

   public PrinterScreen(BlockPos pos, List<String[]> templates) {
      super(Component.literal(L.t("Принтер")));
      this.pos = pos;
      this.templates = templates;
   }

   protected void init() {
      this.w0 = Math.min(360, this.width - 40);
      this.h0 = Math.min(324, this.height - 30);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      int y = this.y0 + 30;
      this.button(L.t("Снять копию с документа в руке"), x, y, w, () -> {
         Net.sendToServer(new Packets.PrinterAction(this.pos, "", 1));
         this.onClose();
      });
      y += 24;
      this.runY = y;
      this.button("−", x, y, 20, 20, () -> {
         this.run = Math.max(1, this.run - (this.run > 16 ? 8 : 1));
         this.rebuildWidgets();
      });
      this.button("+", x + 62, y, 20, 20, () -> {
         this.run = Math.min(64, this.run + (this.run >= 16 ? 8 : 1));
         this.rebuildWidgets();
      });
      this.button(L.t("Тираж газеты из руки"), x + 90, y, w - 90, () -> {
         Net.sendToServer(new Packets.PrinterAction(this.pos, "@paper", this.run));
         this.onClose();
      });
      y += 34;
      this.listY = y + 12;
      this.rows = Math.max(1, (this.y0 + this.h0 - 64 - this.listY) / 22);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.templates.size() - this.rows)));

      for (int i = this.scroll; i < Math.min(this.templates.size(), this.scroll + this.rows); i++) {
         String[] t = this.templates.get(i);
         int k = i;
         int f = parse(t[2]);
         this.button(L.t(t[1]) + "  ·  " + L.t(PassportTemplate.FORMATS[f]), x, this.listY + (i - this.scroll) * 22, w, 20, () -> {
            this.sel = k;
            this.rebuildWidgets();
         }).selected(i == this.sel);
      }

      int by = this.y0 + this.h0 - 30;
      this.button("−", x, by, 20, 20, () -> {
         this.count = Math.max(1, this.count - 1);
         this.rebuildWidgets();
      });
      this.button("+", x + 62, by, 20, 20, () -> {
         this.count = Math.min(16, this.count + 1);
         this.rebuildWidgets();
      });
      this.button(L.t("Печатать бланки"), x + 90, by, w - 90 - 70, () -> {
         if (this.sel >= 0 && this.sel < this.templates.size()) {
            Net.sendToServer(new Packets.PrinterAction(this.pos, this.templates.get(this.sel)[0], this.count));
            this.onClose();
         }
      }).primary().enabled(!this.templates.isEmpty());
      this.button(L.t("Закрыть"), x + w - 66, by, 66, this::onClose).ghost();
   }

   private static int parse(String s) {
      try {
         return Math.max(0, Math.min(PassportTemplate.FORMATS.length - 1, Integer.parseInt(s)));
      } catch (NumberFormatException var2) {
         return 0;
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Принтер"));
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      Ui.section(g, this.font, L.t("Бланки ваших шаблонов"), x, this.listY - 12, w);
      if (this.templates.isEmpty()) {
         int ly = this.listY + 4;

         for (String l : Ui.wrap(this.font, L.t("У вас нет утверждённых шаблонов. Создайте шаблон документа: /seal passport designer"), w)) {
            g.drawString(this.font, l, x, ly, -6253953, false);
            ly += 11;
         }
      }

      String rc = String.valueOf(this.run);
      g.drawString(this.font, rc, x + 41 - this.font.width(rc) / 2, this.runY + 6, -1183755, false);
      String c = String.valueOf(this.count);
      g.drawString(this.font, c, x + 41 - this.font.width(c) / 2, this.y0 + this.h0 - 24, -1183755, false);
      g.drawString(this.font, L.t("Бумага: 1 лист на бланк, для паспорта — книга."), x, this.y0 + this.h0 - 44, -9543342, false);
      super.render(g, mx, my, pt);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
      this.scroll = this.scroll - (int)Math.signum(delta);
      this.rebuildWidgets();
      return true;
   }
}
