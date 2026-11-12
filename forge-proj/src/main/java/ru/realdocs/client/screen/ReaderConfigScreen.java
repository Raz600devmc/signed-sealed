package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.blockentity.ReaderBE;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;

/** Settings of a document reader: what it accepts, filters, zone, mode, signal length, access and log. */
public final class ReaderConfigScreen extends UiScreen {
   private final CompoundTag data;
   private String label;
   private String filter;
   private String zone;
   private String members;
   private int kinds;
   private int mode;
   private int pulse;
   private boolean holder;
   private boolean locked;
   private boolean clearLog;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int leftW;

   public ReaderConfigScreen(CompoundTag t) {
      super(Component.literal(L.t("Считыватель документов")));
      this.data = t;
      this.label = t.getString("Label");
      this.filter = t.getString("Filter");
      this.zone = t.getString("Zone");
      this.members = t.getString("Members");
      this.kinds = t.getInt("Kinds");
      this.mode = t.getInt("Mode");
      this.pulse = Math.max(1, t.getInt("Pulse"));
      this.holder = t.getBoolean("Holder");
      this.locked = t.getBoolean("Locked");
   }

   protected void init() {
      this.w0 = Math.min(560, this.width - 24);
      this.h0 = Math.min(330, this.height - 16);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      this.leftW = Math.min(300, this.w0 * 3 / 5);
      int x = this.x0 + 12;
      int w = this.leftW - 12;
      int half = (w - 4) / 2;
      int y = this.y0 + 38;
      UiField lf = this.field(x, y, w, L.t("например, Вход в депо"), this.label, 40, s -> this.label = s);
      lf.label(L.t("Подпись на считывателе"));
      y += 34;

      for (int i = 0; i < 4; i++) {
         int bit = 1 << i;
         this.button(L.t(ReaderBE.KIND_NAMES[i]), x + i % 2 * (half + 4), y + i / 2 * 21, i % 2 == 0 ? half : w - half - 4, 18, () -> {
            this.kinds ^= bit;
            this.rebuildWidgets();
         }).selected((this.kinds & bit) != 0);
      }

      y += 42 + 12;
      UiField ff = this.field(x, y, w, L.t("организация, тип, ник… через запятую"), this.filter, 120, s -> this.filter = s);
      ff.label(L.t("Принимать только (пусто — все)"));
      y += 30;
      UiField zf = this.field(x, y, half, L.t("например, Депо"), this.zone, 32, s -> this.zone = s);
      zf.label(L.t("Зона пропусков"));
      this.button(this.holder ? L.t("Только владелец") : L.t("Любой предъявитель"), x + half + 4, y, w - half - 4, 18, () -> {
         this.holder = !this.holder;
         this.rebuildWidgets();
      }).selected(this.holder);
      y += 34;
      this.button(L.t(ReaderBE.MODES[this.mode]), x, y, w - 92, 18, () -> {
         this.mode = (this.mode + 1) % ReaderBE.MODES.length;
         this.rebuildWidgets();
      });
      this.button("−", x + w - 88, y, 18, 18, () -> {
         this.pulse = Math.max(1, this.pulse - 1);
         this.rebuildWidgets();
      });
      this.button("+", x + w - 18, y, 18, 18, () -> {
         this.pulse = Math.min(30, this.pulse + 1);
         this.rebuildWidgets();
      });
      y += 32;
      NameField mf = this.nameField(x, y, w, L.t("ники или @организация через запятую"), this.members, 600, s -> this.members = s, true, false);
      mf.label(L.t("Кому можно пользоваться, когда заперто"));
      y += 22;
      this.button(this.locked ? L.t("Заперто") : L.t("Открыто для всех"), x, y, w, 18, () -> {
         this.locked = !this.locked;
         this.rebuildWidgets();
      }).selected(this.locked);
      int by = this.y0 + this.h0 - 28;
      this.button(L.t("Сохранить"), x, by, 110, 20, this::save).primary();
      int rx = this.x0 + this.leftW + 14;
      int rw = this.x0 + this.w0 - 12 - rx;
      this.button(L.t("Очистить журнал"), rx, by, Math.min(130, rw - 84), 20, () -> {
         this.clearLog = true;
         this.save();
      }).ghost();
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 78, by, 66, 20, this::onClose).ghost();
   }

   private void save() {
      CompoundTag t = new CompoundTag();
      t.putInt("X", this.data.getInt("X"));
      t.putInt("Y", this.data.getInt("Y"));
      t.putInt("Z", this.data.getInt("Z"));
      t.putString("Label", this.label);
      t.putString("Filter", this.filter);
      t.putString("Zone", this.zone);
      t.putString("Members", this.members);
      t.putInt("Kinds", this.kinds);
      t.putInt("Mode", this.mode);
      t.putInt("Pulse", this.pulse);
      t.putBoolean("Holder", this.holder);
      t.putBoolean("Locked", this.locked);
      t.putBoolean("ClearLog", this.clearLog);
      ClientOffice.req("reader_save", t);
      this.onClose();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Считыватель документов"));
      int x = this.x0 + 12;
      int w = this.leftW - 12;
      Ui.label(g, this.font, L.t("Что принимать"), x + 1, this.y0 + 62);
      int py = this.y0 + 38 + 34 + 54 + 30 + 34;
      Ui.label(g, this.font, L.t("Режим"), x + 1, py - 10);
      String p = this.pulse + L.t(" с");
      g.drawCenteredString(this.font, p, x + w - 44, py + 5, -1183755);
      Ui.label(g, this.font, L.t("Сигнал"), x + w - 88, py - 10);
      int rx = this.x0 + this.leftW + 14;
      int rw = this.x0 + this.w0 - 12 - rx;
      Ui.section(g, this.font, L.t("Журнал"), rx, this.y0 + 28, rw);
      g.drawString(this.font, Ui.ellipsize(this.font, L.t("Проходов: ") + this.data.getInt("Passes") + " · " + L.t("Владелец: ") + this.data.getString("Owner"), rw), rx, this.y0 + 42, -9543342, false);
      ListTag log = this.data.getList("Log", 8);
      int ly = this.y0 + 56;
      int maxY = this.y0 + this.h0 - 36;
      if (log.isEmpty() || this.clearLog) {
         g.drawString(this.font, L.t("Пока пусто."), rx, ly, -6253953, false);
      } else {
         for (int i = 0; i < log.size() && ly + 10 <= maxY; i++) {
            String s = log.getString(i);
            int col = s.contains("✔") ? -10628481 : -1088669;
            g.drawString(this.font, Ui.ellipsize(this.font, L.t(s), rw), rx, ly, col, false);
            ly += 11;
         }
      }

      super.render(g, mx, my, pt);
   }
}
