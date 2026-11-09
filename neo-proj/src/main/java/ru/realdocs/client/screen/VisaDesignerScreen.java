package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.render.DesignRenderer;
import ru.realdocs.client.render.StampRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.StampModel;

public final class VisaDesignerScreen extends UiScreen {
   public static final int VW = 420;
   public static final int VH = 260;
   private static final int[] INKS = new int[]{1710618, 16777215, 2046611, 11740702, 1993530, 9067008, 5974662};
   private final Screen parent;
   private final StampModel m;
   private final BufferedImage image;
   private final List<DesignElement> els;
   private int sel = -1;
   private final List<Object[]> labels = new ArrayList<>();
   private int pickerY = -1;
   private final PageTexture preview = new PageTexture();
   private boolean dirty = true;
   private int dragMode;
   private double dragOffX;
   private double dragOffY;
   private int formX = 12;
   private int formY = 12;
   private int formW;
   private int formH;
   private int cardX;
   private int cardW;
   private int prevX;
   private int prevY;
   private int prevW;
   private int prevH;

   public VisaDesignerScreen(Screen parent, StampModel model, BufferedImage image) {
      super(Component.literal(L.t("Конструктор визы")));
      this.parent = parent;
      this.m = model;
      this.image = image;
      if (this.m.visa.isEmpty()) {
         this.m.visa.addAll(defaults(this.m.color));
      }

      this.els = this.m.visa;
   }

   public static List<DesignElement> defaults(int ink) {
      List<DesignElement> out = new ArrayList<>();
      out.add(t("VISA", 22, 16, 150, 40, 34, true, 0, 4, ink, 100));
      out.add(t(L.t("ВИЗА"), 24, 50, 120, 20, 14, true, 0, 6, ink, 100));
      out.add(t("{country}", 190, 22, 208, 24, 17, true, 2, 8, ink, 100));
      out.add(t(L.t("ТИП / TYPE"), 22, 84, 190, 14, 10, false, 0, 0, ink, 70));
      out.add(t("{type}", 22, 98, 190, 24, 19, true, 0, 0, 1973796, 100));
      out.add(t(L.t("НОМЕР / No."), 220, 84, 180, 14, 10, false, 0, 0, ink, 70));
      out.add(t("{num}", 220, 98, 180, 24, 19, true, 0, 0, 11148830, 100));
      out.add(t(L.t("С / FROM"), 22, 128, 190, 14, 10, false, 0, 0, ink, 70));
      out.add(t("{from}", 22, 142, 190, 24, 19, true, 0, 0, 1973796, 100));
      out.add(t(L.t("ПО / UNTIL"), 220, 128, 180, 14, 10, false, 0, 0, ink, 70));
      out.add(t("{until}", 220, 142, 180, 24, 19, true, 0, 0, 1973796, 100));
      out.add(t(L.t("ВЫДАНА / ISSUED BY"), 22, 172, 230, 14, 10, false, 0, 0, ink, 70));
      out.add(t("{issuer}", 22, 186, 230, 24, 17, true, 0, 0, 1973796, 100));
      DesignElement med = new DesignElement(2, 262, 180, 58, 58);
      med.ink = 0;
      med.color = ink & 16777215;
      med.alpha = 50;
      out.add(med);
      return out;
   }

   private static DesignElement t(String text, int x, int y, int w, int h, int size, boolean bold, int align, int spacing, int color, int alpha) {
      DesignElement e = DesignElement.text(text, x, y, w, h, size, spacing);
      e.bold = bold;
      e.align = align;
      e.ink = 0;
      e.color = color & 16777215;
      e.alpha = alpha;
      return e;
   }

   protected void init() {
      this.labels.clear();
      this.pickerY = -1;
      this.formW = Math.max(240, Math.min(320, this.width * 2 / 5));
      this.formH = this.height - 24;
      this.cardX = this.formX + this.formW + 10;
      this.cardW = this.width - this.cardX - 12;
      this.prevW = this.cardW - 30;
      this.prevH = this.prevW * 260 / 420;
      int maxH = this.formH - 90;
      if (this.prevH > maxH) {
         this.prevH = maxH;
         this.prevW = this.prevH * 420 / 260;
      }

      this.prevX = this.cardX + (this.cardW - this.prevW) / 2;
      this.prevY = this.formY + 34;
      int x = this.formX + 10;
      int w = this.formW - 20;
      int y = this.formY + 30;
      this.buildElements(x, y, w);
      int footY = this.formY + this.formH - 30;
      int third = (w - 6) / 3;
      this.button(L.t("Сбросить"), x, footY, third, () -> {
         this.els.clear();
         this.els.addAll(defaults(this.m.color));
         this.sel = -1;
         this.dirtyRebuild();
      }).ghost();
      this.button(L.t("Без оформления"), x + third + 3, footY, third, () -> {
         this.els.clear();
         this.back();
      }).ghost();
      this.button(L.t("Готово"), x + 2 * (third + 3), footY, w - 2 * (third + 3), this::back).primary();
   }

   private void back() {
      this.minecraft.setScreen(this.parent);
   }

   public void onClose() {
      this.back();
   }

   private void dirtyRebuild() {
      this.dirty = true;
      this.rebuildWidgets();
   }

   private void label(String text, int x, int y) {
      this.labels.add(new Object[]{text, x, y, null});
   }

   private void stepper(int x, int y, int w, String label, Supplier<String> value, IntConsumer delta) {
      this.label(label, x + 1, y);
      this.button("−", x, y + 10, 18, 18, () -> {
         delta.accept(-1);
         this.dirty = true;
      });
      this.button("+", x + w - 18, y + 10, 18, 18, () -> {
         delta.accept(1);
         this.dirty = true;
      });
      this.labels.add(new Object[]{null, x + w / 2, y + 15, value});
   }

   private void swatches(int x, int y, int w, int[] palette, int current, Consumer<Integer> set) {
      int n = palette.length;
      int sw = Math.min(26, (w - (n - 1) * 3) / n);

      for (int i = 0; i < n; i++) {
         int c = palette[i];
         this.button("", x + i * (sw + 3), y, sw, 18, () -> {
            set.accept(c);
            this.dirtyRebuild();
         }).swatch(c).selected((current & 16777215) == c);
      }
   }

   private DesignElement newElement(int type) {
      DesignElement e = switch (type) {
         case 0 -> DesignElement.text(L.t("НАДПИСЬ"), 110, 118, 200, 24, 14, 6);
         default -> new DesignElement(type, 170, 90, 80, 80);
         case 3 -> new DesignElement(type, 22, 76, 376, 1);
         case 4 -> new DesignElement(type, 14, 14, 392, 232);
         case 5 -> new DesignElement(type, 4, 60, 412, 20);
      };
      e.ink = 0;
      e.color = this.m.color & 16777215;
      if (type == 5) {
         e.alpha = 25;
      }

      if (type == 4) {
         e.size = 8;
      }

      return e;
   }

   private void buildElements(int x, int y, int w) {
      if (this.sel >= this.els.size()) {
         this.sel = this.els.size() - 1;
      }

      this.button("◀", x, y, 18, 18, () -> {
         if (!this.els.isEmpty()) {
            this.sel = (this.sel - 1 + this.els.size()) % this.els.size();
         }

         this.rebuildWidgets();
      }).enabled(this.els.size() > 1);
      this.button("▶", x + w - 18, y, 18, 18, () -> {
         if (!this.els.isEmpty()) {
            this.sel = (this.sel + 1) % this.els.size();
         }

         this.rebuildWidgets();
      }).enabled(this.els.size() > 1);
      this.pickerY = y;
      y += 24;
      int bw = (w - 6) / 3;

      for (int i = 0; i < DesignElement.TYPE_NAMES.length; i++) {
         int ti = i;
         this.button("+ " + L.t(DesignElement.TYPE_NAMES[i]), x + i % 3 * (bw + 3), y + i / 3 * 20, i % 3 == 2 ? w - 2 * (bw + 3) : bw, 18, () -> {
            this.els.add(this.newElement(ti));
            this.sel = this.els.size() - 1;
            this.dirtyRebuild();
         }).enabled(this.els.size() < 24);
      }

      y += 46;
      if (this.sel >= 0 && this.sel < this.els.size()) {
         DesignElement e = this.els.get(this.sel);
         if (e.type == 0) {
            UiField f = this.field(x, y + 10, w, L.t("текст"), e.text, 60, s -> {
               e.text = s;
               this.dirty = true;
            });
            f.label(L.t("Текст  ({num}, {from}… — подстановки)"));
            y += 32;
            int sw = 58;
            this.stepper(x, y, sw, L.t("Размер"), () -> String.valueOf(e.size), d -> e.size = Math.max(5, Math.min(60, e.size + d)));
            this.stepper(x + sw + 6, y, sw, L.t("Разрядка"), () -> String.valueOf(e.spacing), d -> e.spacing = Math.max(0, Math.min(40, e.spacing + 2 * d)));
            int ax = x + 2 * (sw + 6);
            this.label(L.t("Выравнивание"), ax + 1, y);
            String[] al = new String[]{L.t("Л"), L.t("Ц"), L.t("П")};

            for (int i = 0; i < 3; i++) {
               int k = i;
               this.button(al[i], ax + i * 20, y + 10, 18, 18, () -> {
                  e.align = k;
                  this.dirtyRebuild();
               }).selected(e.align == i);
            }

            this.button(L.t("Ж"), x + w - 18, y + 10, 18, 18, () -> {
               e.bold = !e.bold;
               this.dirtyRebuild();
            }).selected(e.bold);
            y += 32;
         } else if (e.type == 4) {
            this.stepper(x, y, 70, L.t("Толщина"), () -> String.valueOf(e.size), d -> e.size = Math.max(5, Math.min(60, e.size + d)));
            y += 32;
         }

         this.label(L.t("Нанесение"), x + 1, y);
         int n = DesignElement.INK_NAMES.length;
         int iw = (w - 3 * (n - 1)) / n;

         for (int i = 0; i < n; i++) {
            int ii = i;
            String name = i == 0 && e.type == 1 ? L.t("Оригинал") : L.t(DesignElement.INK_NAMES[i]);
            this.button(name, x + i * (iw + 3), y + 10, i == n - 1 ? w - (n - 1) * (iw + 3) : iw, 18, () -> {
               e.ink = ii;
               this.dirtyRebuild();
            }).selected(e.ink == i);
         }

         y += 32;
         int aw = 72;
         if (e.ink == 0 && e.type != 1) {
            this.label(L.t("Цвет"), x + 1, y);
            int[] pal = (int[])INKS.clone();
            pal[0] = this.m.color & 16777215;
            this.swatches(x, y + 10, w - aw - 8, pal, e.color, c -> e.color = c);
         }

         this.stepper(x + w - aw, y, aw, L.t("Плотность"), () -> e.alpha + "%", d -> e.alpha = Math.max(10, Math.min(100, e.alpha + 10 * d)));
         y += 32;
         int q = (w - 9) / 4;
         this.button(L.t("Выше"), x, y, q, 18, () -> {
            if (this.sel < this.els.size() - 1) {
               this.els.add(this.sel + 1, this.els.remove(this.sel));
               this.sel++;
            }

            this.dirtyRebuild();
         }).enabled(this.sel < this.els.size() - 1);
         this.button(L.t("Ниже"), x + q + 3, y, q, 18, () -> {
            if (this.sel > 0) {
               this.els.add(this.sel - 1, this.els.remove(this.sel));
               this.sel--;
            }

            this.dirtyRebuild();
         }).enabled(this.sel > 0);
         this.button(L.t("Копия"), x + 2 * (q + 3), y, q, 18, () -> {
            DesignElement c = e.copy();
            c.x += 8;
            c.y += 8;
            this.els.add(c);
            this.sel = this.els.size() - 1;
            this.dirtyRebuild();
         }).enabled(this.els.size() < 24);
         this.button(L.t("Удалить"), x + 3 * (q + 3), y, w - 3 * (q + 3), 18, () -> {
            this.els.remove(this.sel);
            this.sel = Math.min(this.sel, this.els.size() - 1);
            this.dirtyRebuild();
         }).danger();
      } else {
         this.label(L.t("Подстановки в тексте:"), x + 1, y);
         this.label("{type} {num} {from} {until}", x + 1, y + 12);
         this.label("{country} {issuer}", x + 1, y + 24);
         this.label(L.t("Их заполнит сервер, когда визу поставят."), x + 1, y + 40);
      }
   }

   private double scale() {
      return (double)this.prevW / 420.0;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, L.t("Конструктор визы"));
      int x = this.formX + 10;
      int w = this.formW - 20;

      for (Object[] l : this.labels) {
         if (l[0] != null) {
            Ui.label(g, this.font, (String)l[0], (Integer)l[1], (Integer)l[2]);
         } else {
            String v = (String)((Supplier)l[3]).get();
            g.drawString(this.font, v, (Integer)l[1] - this.font.width(v) / 2, (Integer)l[2], -1183755, false);
         }
      }

      if (this.pickerY >= 0) {
         String s = this.els.isEmpty()
            ? L.t("нет элементов")
            : (this.sel < 0 ? this.els.size() + L.t(" шт. · не выбран") : this.sel + 1 + " / " + this.els.size() + " · " + this.els.get(this.sel).label());
         s = Ui.ellipsize(this.font, s, w - 48);
         Ui.round(g, x + 21, this.pickerY, w - 42, 18, -15067887);
         g.drawString(this.font, s, x + w / 2 - this.font.width(s) / 2, this.pickerY + 5, this.sel >= 0 ? -1183755 : -6253953, false);
      }

      Ui.card(g, this.font, this.cardX, this.formY, this.cardW, this.formH, L.t("Предпросмотр визы"));
      if (this.dirty || !this.preview.ready()) {
         StampModel p = this.m.copy();
         DesignRenderer.placeholders = true;

         try {
            this.preview.set(StampRenderer.render(p, this.image, 900, null));
         } finally {
            DesignRenderer.placeholders = false;
         }

         this.dirty = false;
      }

      g.fill(this.prevX - 8, this.prevY - 8, this.prevX + this.prevW + 8, this.prevY + this.prevH + 8, -1054762);
      g.blit(this.preview.id(), this.prevX, this.prevY, this.prevW, this.prevH, 0.0F, 0.0F, this.preview.w, this.preview.h, this.preview.w, this.preview.h);

      for (int i = 0; i < this.els.size(); i++) {
         DesignElement e = this.els.get(i);
         this.box(g, e, i == this.sel ? -2641559 : 1426063360, i == this.sel);
      }

      int ty = this.prevY + this.prevH + 16;
      g.drawCenteredString(this.font, L.t("Тащите элемент мышью; уголок — размер, стрелки — сдвиг."), this.cardX + this.cardW / 2, ty, -6253953);
      g.drawCenteredString(this.font, L.t("Образец: номер, даты и тип подставятся при постановке визы."), this.cardX + this.cardW / 2, ty + 11, -9543342);
      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   private void box(GuiGraphics g, DesignElement e, int color, boolean handle) {
      double k = this.scale();
      int x1 = this.prevX + (int)((double)e.x * k);
      int y1 = this.prevY + (int)((double)e.y * k);
      int x2 = this.prevX + (int)((double)(e.x + e.w) * k);
      int y2 = this.prevY + (int)((double)(e.y + Math.max(e.h, 2)) * k);
      g.enableScissor(this.prevX, this.prevY, this.prevX + this.prevW, this.prevY + this.prevH);
      g.renderOutline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, color);
      if (handle) {
         g.fill(x2 - 4, y2 - 4, x2 + 2, y2 + 2, color);
      }

      g.disableScissor();
   }

   private int hit(double mx, double my) {
      double px = (mx - (double)this.prevX) / this.scale();
      double py = (my - (double)this.prevY) / this.scale();
      double pad = 4.0 / this.scale();
      if (this.sel >= 0 && this.sel < this.els.size() && inside(this.els.get(this.sel), px, py, pad)) {
         return this.sel;
      } else {
         for (int i = this.els.size() - 1; i >= 0; i--) {
            if (inside(this.els.get(i), px, py, pad)) {
               return i;
            }
         }

         return -1;
      }
   }

   private static boolean inside(DesignElement e, double px, double py, double pad) {
      return px >= (double)e.x - pad && px <= (double)(e.x + e.w) + pad && py >= (double)e.y - pad && py <= (double)(e.y + Math.max(e.h, 1)) + pad;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      boolean inPrev = mx >= (double)this.prevX && mx < (double)(this.prevX + this.prevW) && my >= (double)this.prevY && my < (double)(this.prevY + this.prevH);
      if (button == 0 && inPrev) {
         int i = this.hit(mx, my);
         if (i >= 0) {
            DesignElement e = this.els.get(i);
            double px = (mx - (double)this.prevX) / this.scale();
            double py = (my - (double)this.prevY) / this.scale();
            double c = 8.0 / this.scale();
            boolean corner = px >= (double)(e.x + e.w) - c && py >= (double)(e.y + Math.max(e.h, 1)) - c && e.type != 3;
            if (i != this.sel) {
               this.sel = i;
               this.rebuildWidgets();
            }

            this.dragMode = corner ? 2 : 1;
            this.dragOffX = px - (double)e.x;
            this.dragOffY = py - (double)e.y;
            return true;
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragMode != 0 && this.sel >= 0 && this.sel < this.els.size()) {
         DesignElement e = this.els.get(this.sel);
         double px = (mx - (double)this.prevX) / this.scale();
         double py = (my - (double)this.prevY) / this.scale();
         if (this.dragMode == 1) {
            e.x = snap(Math.max((double)(-e.w) / 2.0, Math.min(420.0 - (double)e.w / 2.0, px - this.dragOffX)));
            e.y = snap(Math.max((double)(-e.h) / 2.0, Math.min(260.0 - (double)e.h / 2.0, py - this.dragOffY)));
         } else {
            e.w = Math.max(6, Math.min(420, snap(px - (double)e.x)));
            if (e.type != 3) {
               e.h = Math.max(6, Math.min(260, snap(py - (double)e.y)));
            }

            if (e.type == 2 || e.type == 1) {
               e.w = e.h = Math.min(Math.max(e.w, e.h), 260);
            }
         }

         this.dirty = true;
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   public boolean mouseReleased(double mx, double my, int button) {
      this.dragMode = 0;
      return super.mouseReleased(mx, my, button);
   }

   public boolean keyPressed(int key, int scan, int mods) {
      boolean typing = this.getFocused() instanceof UiField;
      if (!typing && this.sel >= 0 && this.sel < this.els.size()) {
         DesignElement e = this.els.get(this.sel);
         int step = (mods & 1) != 0 ? 10 : 1;
         switch (key) {
            case 261:
               this.els.remove(this.sel);
               this.sel = Math.min(this.sel, this.els.size() - 1);
               this.dirtyRebuild();
               return true;
            case 262:
               e.x += step;
               break;
            case 263:
               e.x -= step;
               break;
            case 264:
               e.y += step;
               break;
            case 265:
               e.y -= step;
               break;
            default:
               return super.keyPressed(key, scan, mods);
         }

         this.dirty = true;
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   private static int snap(double v) {
      return (int)Math.round(v / 2.0) * 2;
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }
}
