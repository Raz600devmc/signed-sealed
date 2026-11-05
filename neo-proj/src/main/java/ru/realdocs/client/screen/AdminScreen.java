package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.client.render.StampRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiButton;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class AdminScreen extends UiScreen {
   private static final int TAB_STAMPS = 0;
   private static final int TAB_PASSPORTS = 1;
   private static final int TAB_REGISTRY = 2;
   private static final int ROW_H = 24;
   private Packets.AdminData data;
   private int tab = -1;
   private boolean pendingOnly;
   private String selected = "";
   private final Set<String> chosenPlayers = new LinkedHashSet<>();
   private int blanks = 1;
   private boolean presetEditor;
   private String query = "";
   private int scroll;
   private UiField search;
   private final PageTexture preview = new PageTexture();
   private int previewKey = Integer.MIN_VALUE;
   private int listX;
   private int listY;
   private int listW;
   private int listH;
   private int rowsY;
   private int detX;
   private int detY;
   private int detW;
   private int detH;
   private int actionsTop;
   private int metaBottom;

   public AdminScreen(Packets.AdminData data) {
      super(Component.literal(L.t("Администрация")));
      this.data = data;
   }

   public void update(Packets.AdminData d) {
      this.data = d;
      if (!d.message.isEmpty()) {
         this.toast(d.message, d.ok ? -10628481 : -1088669);
      }

      if (!this.selected.isEmpty() && !this.exists(this.selected)) {
         this.selected = "";
      }

      this.chosenPlayers.retainAll(d.players);
      this.rebuildWidgets();
   }

   private boolean can(int flag) {
      return (this.data.flags & flag) != 0;
   }

   protected void init() {
      if (this.tab < 0 || !this.tabAllowed(this.tab)) {
         this.tab = this.can(1) ? 0 : (this.can(2) ? 1 : 2);
      }

      int pad = 12;
      this.listX = pad;
      this.listY = 40;
      this.listW = Math.max(200, Math.min(320, (int)((double)this.width * 0.38)));
      this.listH = this.height - this.listY - pad;
      this.detX = this.listX + this.listW + 10;
      this.detY = this.listY;
      this.detW = this.width - this.detX - pad;
      this.detH = this.listH;
      int tx = pad + 8 + this.font.width(L.t("Администрация")) + 16;
      tx = this.tabButton(tx, 0, L.t("Печати"), this.pendingStamps(), this.can(1));
      tx = this.tabButton(tx, 1, L.t("Шаблоны"), this.pendingTemplates(), this.can(2));
      this.tabButton(tx, 2, L.t("Реестр"), 0, this.can(4) || this.can(2));
      this.button(L.t("Обновить"), this.width - pad - 74, 10, 74, 20, this::refresh).ghost();
      int lx = this.listX + 8;
      int lw = this.listW - 16;
      if (this.tab == 2) {
         this.search = this.field(lx, this.listY + 26, lw - 60, L.t("Номер, автор или id"), this.query, 64, null);
         this.button(L.t("Найти"), lx + lw - 56, this.listY + 26, 56, 18, () -> {
            this.query = this.search.getValue().trim();
            this.scroll = 0;
            this.refresh();
         }).primary();
      } else {
         this.search = null;
         int half = (lw - 4) / 2;
         this.button(L.t("Все"), lx, this.listY + 26, half, 18, () -> {
            this.pendingOnly = false;
            this.scroll = 0;
            this.rebuildWidgets();
         }).selected(!this.pendingOnly);
         this.button(L.t("Ожидают решения"), lx + half + 4, this.listY + 26, lw - half - 4, 18, () -> {
            this.pendingOnly = true;
            this.scroll = 0;
            this.rebuildWidgets();
         }).selected(this.pendingOnly);
      }

      this.rowsY = this.listY + 50;
      this.buildDetails();
   }

   private boolean tabAllowed(int t) {
      return switch (t) {
         case 0 -> this.can(1);
         case 1 -> this.can(2);
         default -> this.can(4) || this.can(2);
      };
   }

   private int tabButton(int x, int t, String label, int badge, boolean enabled) {
      String l = label + (badge > 0 ? "  " + badge : "");
      int w = this.font.width(l) + 22;
      UiButton b = this.button(l, x, 10, w, 20, () -> {
         this.tab = t;
         this.selected = "";
         this.scroll = 0;
         this.rebuildWidgets();
      }).selected(this.tab == t);
      b.active = enabled;
      return x + w + 4;
   }

   private int pendingStamps() {
      int n = 0;

      for (StampModel m : this.data.stamps) {
         if (m.pending()) {
            n++;
         }
      }

      return n;
   }

   private int pendingTemplates() {
      int n = 0;

      for (Packets.AdminData.Tpl t : this.data.templates) {
         if (!t.approved()) {
            n++;
         }
      }

      return n;
   }

   private List<AdminScreen.Row> rows() {
      List<AdminScreen.Row> out = new ArrayList<>();
      switch (this.tab) {
         case 0:
            for (StampModel m : this.data.stamps) {
               if (!this.pendingOnly || m.pending()) {
                  String st = m.pending() ? L.t("ожидает") : (m.approved ? L.t("действует") : L.t("отозвана"));
                  int col = m.pending() ? -868018 : (m.approved ? -10628481 : -1088669);
                  String kind = L.t(StampModel.KIND_NAMES[Math.max(0, Math.min(2, m.kind))]);
                  out.add(new AdminScreen.Row(m.id, m.org, kind + " · " + m.id + " · " + m.ownerName, st, col, false));
               }
            }
            break;
         case 1:
            for (Packets.AdminData.Tpl t : this.data.templates) {
               if (!this.pendingOnly || !t.approved()) {
                  out.add(
                     new AdminScreen.Row(
                        t.id(),
                        t.name(),
                        t.id() + " · " + t.owner() + L.t(" · полей: ") + t.fields(),
                        t.approved() ? L.t("утверждён") : L.t("ожидает"),
                        t.approved() ? -10628481 : -868018,
                        false
                     )
                  );
               }
            }
            break;
         default:
            for (Packets.AdminData.Reg r : this.data.registry) {
               String title = r.kind() + (r.number().isEmpty() ? "" : " № " + r.number());
               out.add(
                  new AdminScreen.Row(
                     r.id(),
                     title,
                     r.author() + " · " + r.date(),
                     r.revoked() ? L.t("аннулирован") : L.t("действует"),
                     r.revoked() ? -1088669 : -10628481,
                     r.passport()
                  )
               );
            }
      }

      return out;
   }

   private boolean exists(String id) {
      for (AdminScreen.Row r : this.rows()) {
         if (r.id().equals(id)) {
            return true;
         }
      }

      return false;
   }

   private void buildDetails() {
      int x = this.detX + 10;
      int w = this.detW - 20;
      int bottom = this.detY + this.detH - 10;
      this.actionsTop = bottom;
      if (this.tab == 1 && this.selected.isEmpty()) {
         int half = (w - 4) / 2;
         this.button(L.t("Установить сразу"), x, this.detY + 28, half, 18, () -> {
            this.presetEditor = false;
            this.rebuildWidgets();
         }).selected(!this.presetEditor);
         this.button(L.t("Открыть в редакторе"), x + half + 4, this.detY + 28, w - half - 4, 18, () -> {
            this.presetEditor = true;
            this.rebuildWidgets();
         }).selected(this.presetEditor);
         int y = this.detY + 66;
         int cw = (w - 4) / 2;

         for (int i = 0; i < this.data.presets.size(); i++) {
            String[] p = this.data.presets.get(i);
            this.button(p[1], x + i % 2 * (cw + 4), y + i / 2 * 21, cw, 18, () -> {
               if (this.presetEditor) {
                  this.minecraft.setScreen(new PassportTemplateScreen(p[0]).startTab(1));
               } else {
                  this.act(14, p[0], 0);
               }
            });
         }
      } else if (!this.selected.isEmpty()) {
         int y = bottom - 20;
         int half = (w - 6) / 2;
         if (this.tab == 0) {
            StampModel m = this.stamp(this.selected);
            if (m == null) {
               return;
            }

            if (m.approved) {
               this.button(L.t("Отозвать"), x, y, half, () -> this.act(1, this.selected, 0)).danger();
               this.button(L.t("Удалить"), x + half + 6, y, w - half - 6, () -> this.act(2, this.selected, 0)).danger();
               y -= 30;
               this.button(
                     L.t("Выдать печать") + (this.chosenPlayers.isEmpty() ? "" : " (" + this.chosenPlayers.size() + ")"),
                     x,
                     y,
                     w,
                     () -> this.act(3, this.selected, 0)
                  )
                  .primary()
                  .enabled(!this.chosenPlayers.isEmpty());
               y = this.playersBlock(x, w, y - 6);
            } else if (m.pending()) {
               this.button(L.t("Утвердить"), x, y, half, () -> this.act(0, this.selected, 0)).primary();
               this.button(L.t("Отклонить"), x + half + 6, y, w - half - 6, () -> this.act(2, this.selected, 0)).danger();
            } else {
               this.button(L.t("Восстановить"), x, y, half, () -> this.act(0, this.selected, 0)).primary();
               this.button(L.t("Удалить"), x + half + 6, y, w - half - 6, () -> this.act(2, this.selected, 0)).danger();
            }
         } else if (this.tab == 1) {
            Packets.AdminData.Tpl t = this.tpl(this.selected);
            if (t == null) {
               return;
            }

            int third = (w - 12) / 3;
            if (t.approved()) {
               this.button(L.t("Изменить"), x, y, third, () -> this.act(15, this.selected, 0)).icon(9);
               this.button(L.t("Отозвать"), x + third + 6, y, third, () -> this.act(11, this.selected, 0)).danger();
               this.button(L.t("Удалить"), x + 2 * third + 12, y, w - 2 * third - 12, () -> this.act(12, this.selected, 0)).danger();
               y -= 30;
               this.button("−", x, y, 20, () -> {
                  this.blanks = Math.max(1, this.blanks - 1);
                  this.rebuildWidgets();
               });
               this.button("+", x + 66, y, 20, () -> {
                  this.blanks = Math.min(16, this.blanks + 1);
                  this.rebuildWidgets();
               });
               this.button(
                     L.t("Выдать ")
                        + this.blanks
                        + " "
                        + plural(this.blanks)
                        + (this.chosenPlayers.isEmpty() ? "" : L.t(" · игроков: ") + this.chosenPlayers.size()),
                     x + 92,
                     y,
                     w - 92,
                     () -> this.act(13, this.selected, this.blanks)
                  )
                  .primary()
                  .enabled(!this.chosenPlayers.isEmpty());
               y = this.playersBlock(x, w, y - 6);
            } else {
               this.button(L.t("Утвердить"), x, y, third, () -> this.act(10, this.selected, 0)).primary();
               this.button(L.t("Изменить"), x + third + 6, y, third, () -> this.act(15, this.selected, 0)).icon(9);
               this.button(L.t("Удалить"), x + 2 * third + 12, y, w - 2 * third - 12, () -> this.act(12, this.selected, 0)).danger();
            }
         } else {
            Packets.AdminData.Reg r = this.reg(this.selected);
            if (r == null) {
               return;
            }

            if (r.revoked()) {
               this.button(L.t("Вернуть в силу"), x, y, w, () -> this.act(21, this.selected, r.passport() ? 1 : 0)).primary();
            } else {
               this.button(L.t("Аннулировать"), x, y, w, () -> this.act(20, this.selected, r.passport() ? 1 : 0)).danger();
            }
         }

         this.actionsTop = Math.min(this.actionsTop, y) - 8;
      }
   }

   private static String plural(int n) {
      int a = n % 10;
      int b = n % 100;
      if (a == 1 && b != 11) {
         return L.t("бланк");
      } else {
         return a < 2 || a > 4 || b >= 12 && b <= 14 ? L.t("бланков") : L.t("бланка");
      }
   }

   private int playersBlock(int x, int w, int bottomY) {
      List<String> players = this.data.players;
      List<int[]> pos = new ArrayList<>();
      int cx = x;
      int row = 0;

      for (String p : players) {
         int cw = this.font.width(p) + 20;
         if (cx + cw > x + w && cx > x) {
            cx = x;
            if (++row >= 3) {
               break;
            }
         }

         pos.add(new int[]{cx, row, Math.min(cw, w)});
         cx += cw + 4;
      }

      int rows = pos.isEmpty() ? 1 : row + 1;
      int top = bottomY - rows * 20;

      for (int i = 0; i < pos.size(); i++) {
         String p = players.get(i);
         int[] q = pos.get(i);
         boolean on = this.chosenPlayers.contains(p);
         this.button(p, q[0], top + q[1] * 20, q[2], 16, () -> {
            if (!this.chosenPlayers.remove(p)) {
               this.chosenPlayers.add(p);
            }

            this.rebuildWidgets();
         }).selected(on);
      }

      return top - 14;
   }

   private void act(int action, String id, int count) {
      Net.sendToServer(new Packets.AdminAction(action, id, new ArrayList<>(this.chosenPlayers), count, this.query));
   }

   private void refresh() {
      Net.sendToServer(new Packets.AdminOpen(this.query));
   }

   private StampModel stamp(String id) {
      for (StampModel m : this.data.stamps) {
         if (m.id.equals(id)) {
            return m;
         }
      }

      return null;
   }

   private Packets.AdminData.Tpl tpl(String id) {
      for (Packets.AdminData.Tpl t : this.data.templates) {
         if (t.id().equals(id)) {
            return t;
         }
      }

      return null;
   }

   private Packets.AdminData.Reg reg(String id) {
      for (Packets.AdminData.Reg r : this.data.registry) {
         if (r.id().equals(id)) {
            return r;
         }
      }

      return null;
   }

   private boolean canGive() {
      return this.tab == 0 && this.stamp(this.selected) != null && this.stamp(this.selected).approved
         || this.tab == 1 && this.tpl(this.selected) != null && this.tpl(this.selected).approved();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      this.header(g, L.t("Администрация"), "", 12, 16);

      String listTitle = switch (this.tab) {
         case 0 -> L.t("Печати");
         case 1 -> L.t("Шаблоны документов");
         default -> L.t("Реестр");
      };
      Ui.card(g, this.font, this.listX, this.listY, this.listW, this.listH, listTitle);
      List<AdminScreen.Row> rows = this.rows();
      String cnt = String.valueOf(rows.size());
      g.drawString(this.font, cnt, this.listX + this.listW - 10 - this.font.width(cnt), this.listY + 6, -6253953, false);
      int areaH = this.listY + this.listH - 6 - this.rowsY;
      int visible = Math.max(1, areaH / 24);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, rows.size() - visible)));
      g.enableScissor(this.listX + 1, this.rowsY, this.listX + this.listW - 1, this.listY + this.listH - 4);

      for (int i = 0; i < visible + 1 && i + this.scroll < rows.size(); i++) {
         AdminScreen.Row r = rows.get(i + this.scroll);
         int ry = this.rowsY + i * 24;
         int rx = this.listX + 6;
         int rw = this.listW - 12;
         boolean sel = r.id().equals(this.selected);
         boolean hover = mx >= rx && mx < rx + rw && my >= ry && my < ry + 24 - 2 && my < this.listY + this.listH - 4;
         if (sel) {
            Ui.round(g, rx, ry, rw, 22, -13749176);
            Ui.outline(g, rx, ry, rw, 22, Ui.alpha(-2641559, 0.6F));
         } else if (hover) {
            Ui.round(g, rx, ry, rw, 22, -14407110);
         }

         g.fill(rx + 4, ry + 5, rx + 6, ry + 24 - 7, r.color());
         int stW = this.font.width(r.state());
         g.drawString(this.font, Ui.ellipsize(this.font, r.title(), rw - stW - 22), rx + 11, ry + 3, -1183755, false);
         g.drawString(this.font, Ui.ellipsize(this.font, r.sub(), rw - 16), rx + 11, ry + 12, -6253953, false);
         g.drawString(this.font, r.state(), rx + rw - stW - 5, ry + 3, r.color(), false);
      }

      g.disableScissor();
      if (rows.isEmpty()) {
         String e = this.tab == 2 ? L.t("Ничего не найдено") : (this.pendingOnly ? L.t("Нет заявок на рассмотрении") : L.t("Пока пусто"));
         g.drawCenteredString(this.font, e, this.listX + this.listW / 2, this.rowsY + 20, -9543342);
      }

      if (rows.size() > visible) {
         int trackH = this.listY + this.listH - 8 - this.rowsY;
         int th = Math.max(16, trackH * visible / rows.size());
         int ty = this.rowsY + (trackH - th) * this.scroll / Math.max(1, rows.size() - visible);
         g.fill(this.listX + this.listW - 4, ty, this.listX + this.listW - 2, ty + th, 1627389951);
      }

      this.drawDetails(g);
      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   private void drawDetails(GuiGraphics g) {
      int x = this.detX + 10;
      int w = this.detW - 20;
      if (this.tab == 1 && this.selected.isEmpty()) {
         Ui.card(g, this.font, this.detX, this.detY, this.detW, this.detH, L.t("Готовые шаблоны"));
         g.drawString(
            this.font,
            this.presetEditor
               ? L.t("Откроется редактор: добавьте эмблему или герб и отправьте.")
               : L.t("Страны Европы и Турция. Ставятся сразу утверждёнными."),
            x,
            this.detY + 52,
            -6253953,
            false
         );
         if (this.data.presets.isEmpty()) {
            g.drawString(this.font, L.t("Нет готовых шаблонов"), x, this.detY + 70, -9543342, false);
         }

         int y = this.detY + 66 + (this.data.presets.size() + 1) / 2 * 21 + 8;
         g.drawString(this.font, L.t("Чтобы управлять шаблоном, выберите его слева."), x, y, -9543342, false);
      } else if (this.selected.isEmpty()) {
         Ui.card(g, this.font, this.detX, this.detY, this.detW, this.detH, L.t("Подробности"));
         g.drawCenteredString(this.font, L.t("Выберите запись слева"), this.detX + this.detW / 2, this.detY + this.detH / 2 - 4, -9543342);
      } else {
         List<String[]> meta = new ArrayList<>();
         BufferedImage img = null;
         int key = this.selected.hashCode() * 31 + StampCache.version * 7 + PassportCache.version + this.tab * 1013;
         String title;
         int state;
         String stateText;
         if (this.tab == 0) {
            StampModel row = this.stamp(this.selected);
            if (row == null) {
               return;
            }

            title = row.org;
            meta.add(new String[]{L.t("Вид"), L.t(StampModel.KIND_NAMES[Math.max(0, Math.min(2, row.kind))])});
            meta.add(new String[]{L.t("Номер"), row.id});
            meta.add(new String[]{L.t("Автор"), row.ownerName});
            meta.add(new String[]{L.t("Создана"), row.created});
            stateText = row.pending() ? L.t("ожидает решения") : (row.approved ? L.t("действует") : L.t("отозвана"));
            state = row.pending() ? -868018 : (row.approved ? -10628481 : -1088669);
            StampModel full = StampCache.INSTANCE.def(row.id);
            if (full != null && key != this.previewKey) {
               img = StampRenderer.render(full, full.hasImage ? StampCache.INSTANCE.image(row.id) : null, 520, null);
            }
         } else if (this.tab == 1) {
            Packets.AdminData.Tpl t = this.tpl(this.selected);
            if (t == null) {
               return;
            }

            title = t.name();
            meta.add(new String[]{L.t("Номер"), t.id()});
            meta.add(new String[]{L.t("Автор"), t.owner()});
            meta.add(new String[]{L.t("Полей"), String.valueOf(t.fields())});
            stateText = t.approved() ? L.t("утверждён") : L.t("ожидает решения");
            state = t.approved() ? -10628481 : -868018;
            PassportTemplate full = PassportCache.INSTANCE.template(t.id());
            if (full != null && key != this.previewKey) {
               img = PassportRenderer.renderSpread(full, null, PassportCache.INSTANCE, StampCache.INSTANCE, 1000, false);
            }
         } else {
            Packets.AdminData.Reg r = this.reg(this.selected);
            if (r == null) {
               return;
            }

            title = r.kind() + (r.number().isEmpty() ? "" : " № " + r.number());
            meta.add(new String[]{r.passport() ? L.t("Выдал") : L.t("Автор"), r.author()});
            meta.add(new String[]{L.t("Дата"), r.date()});
            meta.add(new String[]{"id", r.id()});
            stateText = r.revoked() ? L.t("аннулирован") : L.t("действует");
            state = r.revoked() ? -1088669 : -10628481;
         }

         Ui.card(g, this.font, this.detX, this.detY, this.detW, this.detH, title);
         int y = this.detY + 28;
         Ui.pill(g, this.font, stateText, x, y, state);
         y += 20;
         int col2 = x + 54;

         for (String[] m : meta) {
            g.drawString(this.font, m[0], x, y, -6253953, false);
            g.drawString(this.font, Ui.ellipsize(this.font, m[1], w - 54), col2, y, -3555408, false);
            y += 11;
         }

         this.metaBottom = y + 6;
         if (img != null) {
            this.preview.set(img);
            this.previewKey = key;
         }

         int labelY = this.canGive() ? this.actionsTop + 4 : this.actionsTop;
         if (this.tab != 2 && this.preview.ready() && this.previewKey == key) {
            int maxH = labelY - this.metaBottom - 18;
            if (maxH > 30) {
               int pw = w;
               int ph = w * this.preview.h / this.preview.w;
               if (this.tab == 0) {
                  pw = Math.min(w - 24, (double)this.preview.w > (double)this.preview.h * 1.4 ? 220 : 140);
                  ph = pw * this.preview.h / this.preview.w;
               }

               if (ph > maxH) {
                  ph = maxH;
                  pw = maxH * this.preview.w / this.preview.h;
               }

               int px = this.detX + (this.detW - pw) / 2;
               int py = this.metaBottom + 8 + (maxH - ph) / 2;
               if (this.tab == 0) {
                  Ui.paperFrame(g, px - 12, py - 8, pw + 24, ph + 16);
                  g.fill(px - 12, py - 8, px + pw + 12, py + ph + 8, -1185572);
               } else {
                  Ui.paperFrame(g, px, py, pw, ph);
               }

               g.blit(this.preview.id(), px, py, pw, ph, 0.0F, 0.0F, this.preview.w, this.preview.h, this.preview.w, this.preview.h);
            }
         }

         if (this.canGive()) {
            Ui.section(g, this.font, this.data.players.isEmpty() ? L.t("Кому выдать — никого нет онлайн") : L.t("Кому выдать — нажмите на ники"), x, labelY, w);
            if (this.tab == 1) {
               int by = this.detY + this.detH - 10 - 20 - 30;
               String n = String.valueOf(this.blanks);
               Ui.round(g, x + 22, by, 42, 20, -15396595);
               Ui.outline(g, x + 22, by, 42, 20, -11912404);
               g.drawString(this.font, n, x + 43 - this.font.width(n) / 2, by + 6, -1183755, false);
            }
         }
      }
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0
         && mx >= (double)(this.listX + 6)
         && mx < (double)(this.listX + this.listW - 6)
         && my >= (double)this.rowsY
         && my < (double)(this.listY + this.listH - 4)) {
         List<AdminScreen.Row> rows = this.rows();
         int i = (int)((my - (double)this.rowsY) / 24.0) + this.scroll;
         if (i >= 0 && i < rows.size()) {
            this.selected = rows.get(i).id();
            this.previewKey = Integer.MIN_VALUE;
            this.rebuildWidgets();
            return true;
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
      if (mx >= (double)this.listX && mx < (double)(this.listX + this.listW)) {
         this.scroll = this.scroll - (int)Math.signum(delta) * 2;
         if (this.scroll < 0) {
            this.scroll = 0;
         }

         return true;
      } else {
         return super.mouseScrolled(mx, my, scrollX, delta);
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (this.search == null || !this.search.isFocused() || key != 257 && key != 335) {
         return super.keyPressed(key, scan, mods);
      } else {
         this.query = this.search.getValue().trim();
         this.scroll = 0;
         this.refresh();
         return true;
      }
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }

   public void testSelect(int row) {
      List<AdminScreen.Row> rows = this.rows();
      if (row >= 0 && row < rows.size()) {
         this.selected = rows.get(row).id();
         this.previewKey = Integer.MIN_VALUE;
         this.rebuildWidgets();
      }
   }

   private static record Row(String id, String title, String sub, String state, int color, boolean passport) {
   }
}
