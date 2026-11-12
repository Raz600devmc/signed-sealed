package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.blockentity.GateBE;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;

/** Owner settings of a ticket gate (kind 0) or a scanner/computer (kind 1). */
public final class BlockConfigScreen extends UiScreen {
   private final ArchivePackets.BlockConfig c;
   private String label;
   private String text;
   private String members;
   private int mode;
   private int n;
   private boolean locked;
   private int x0;
   private int y0;
   private int w0;
   private int h0;

   public BlockConfigScreen(ArchivePackets.BlockConfig c) {
      super(Component.literal(L.t("Настройки")));
      this.c = c;
      this.label = c.label;
      this.text = c.text;
      this.members = c.members;
      this.mode = c.mode;
      this.n = c.n;
      this.locked = c.locked;
   }

   private boolean gate() {
      return this.c.kind == 0;
   }

   protected void init() {
      this.w0 = Math.min(320, this.width - 24);
      int noteH = this.gate() ? 0 : this.font.split(Component.literal(L.t("Сканер и компьютер с одной сетью работают с одним архивом.")), this.w0 - 24).size() * 10 + 6;
      this.h0 = (this.gate() ? 262 : 210) + noteH;
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      int y = this.y0 + 40 + noteH;
      UiField lf = this.field(x, y, w, this.gate() ? L.t("например, Станция «Центральная»") : L.t("например, Мэрия"), this.label, 40, s -> this.label = s);
      lf.label(this.gate() ? L.t("Название прохода") : L.t("Подпись на устройстве"));
      y += 36;
      UiField tf = this.field(
         x, y, w, this.gate() ? L.t("пусто — любой билет; иначе слова через запятую") : L.t("например, mayor-office"), this.text, this.gate() ? 80 : 40, s -> this.text = s
      );
      tf.label(this.gate() ? L.t("Какие билеты принимать (шаблон, организация)") : L.t("Сеть цифрового архива"));
      y += 30;
      if (this.gate()) {
         int third = (w - 6) / 3;

         for (int i = 0; i < 3; i++) {
            int k = i;
            this.button(GateBE.MODES[i], x + i * (third + 3), y, i == 2 ? w - 2 * (third + 3) : third, 18, () -> {
               this.mode = k;
               this.rebuildWidgets();
            }).selected(this.mode == i);
         }

         y += 26;
         this.button("−", x, y, 20, 18, () -> {
            this.n = Math.max(1, this.n - 1);
            this.rebuildWidgets();
         });
         this.button("+", x + 66, y, 20, 18, () -> {
            this.n = Math.min(10, this.n + 1);
            this.rebuildWidgets();
         });
         y += 26;
      }

      UiField mf = this.nameField(x, y + 10, w, L.t("ники или @организация через запятую"), this.members, 600, s -> this.members = s, true, false);
      mf.label(L.t("Кому можно пользоваться, когда заперто"));
      y += 36;
      this.button(this.locked ? L.t("Заперто") : L.t("Открыто для всех"), x, y, w, 18, () -> {
         this.locked = !this.locked;
         this.rebuildWidgets();
      }).selected(this.locked);
      int by = this.y0 + this.h0 - 28;
      this.button(L.t("Сохранить"), x, by, 100, 20, () -> {
         Net.sendToServer(new ArchivePackets.BlockConfig(this.c.pos, this.c.kind, this.label, this.text, this.members, this.mode, this.n, this.locked, true));
         this.onClose();
      }).primary();
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 74, by, 62, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, this.gate() ? L.t("Турникет") : L.t("Цифровой архив"));
      if (this.gate()) {
         int x = this.x0 + 12;
         int y = this.y0 + 40 + 36 + 30 + 26;
         g.drawString(this.font, L.t("Режим"), x, y - 36, -6253953, false);
         String p = this.n + L.t(" с");
         g.drawString(this.font, p, x + 43 - this.font.width(p) / 2, y + 5, -1183755, false);
         g.drawString(this.font, L.t("Время прохода и сигнала"), x + 94, y + 5, -6253953, false);
      } else {
         int ny = this.y0 + 26;

         for (var line : this.font.split(Component.literal(L.t("Сканер и компьютер с одной сетью работают с одним архивом.")), this.w0 - 24)) {
            g.drawString(this.font, line, this.x0 + 12, ny, -9543342, false);
            ny += 10;
         }
      }

      super.render(g, mx, my, pt);
   }
}
