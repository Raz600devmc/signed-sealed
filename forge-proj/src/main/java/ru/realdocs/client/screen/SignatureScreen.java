package ru.realdocs.client.screen;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiButton;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.SigStyle;

public final class SignatureScreen extends UiScreen {
   private final Screen parent;
   private final boolean askPosition;
   private final String initPosition;
   private final String doneLabel;
   private final SignatureScreen.Done done;
   private final List<Integer> data = new ArrayList<>();
   private static SigStyle last = new SigStyle();
   private final SigStyle style = new SigStyle(last.brush, last.color, last.size);
   private int toolY;
   private boolean drawing;
   private boolean dirty = true;
   private final PageTexture tex = new PageTexture();
   private EditBox position;
   private UiButton doneBtn;
   private int cx;
   private int cy;
   private int cw;
   private int ch;
   private int cardX;
   private int cardY;
   private int cardW;
   private int cardH;
   private long lastPen;

   public SignatureScreen(Screen parent, boolean askPosition, String initPosition, String doneLabel, SignatureScreen.Done done) {
      super(Component.literal(L.t("Подпись")));
      this.parent = parent;
      this.askPosition = askPosition;
      this.initPosition = initPosition == null ? "" : initPosition;
      this.doneLabel = doneLabel;
      this.done = done;
   }

   protected void init() {
      this.cardW = Math.min(this.width - 24, 520);
      this.cw = this.cardW - 24;
      this.ch = this.cw * 96 / 256;
      int top = (this.askPosition ? 32 : 0) + 46;
      this.cardH = 44 + top + this.ch + 12 + 20 + 12;
      if (this.cardH > this.height - 16) {
         this.ch = Math.max(56, this.height - 16 - (this.cardH - this.ch));
         this.cw = this.ch * 256 / 96;
         this.cardH = 44 + top + this.ch + 12 + 20 + 12;
      }

      this.cardX = (this.width - this.cardW) / 2;
      this.cardY = Math.max(8, (this.height - this.cardH) / 2);
      this.cx = this.cardX + (this.cardW - this.cw) / 2;
      this.cy = this.cardY + 26 + 18 + top;
      this.toolY = this.cy - 46;
      String keep = this.position != null ? this.position.getValue() : this.initPosition;
      if (this.askPosition) {
         UiField f = this.field(this.cardX + 12, this.cardY + 26 + 18 + 10 - 4, this.cardW - 24, L.t("Например, Секретарь"), keep, 60, null);
         f.label(L.t("Ваша должность"));
         this.position = f;
      }

      int tx = this.cardX + 12;
      int tw = this.cardW - 24;
      int n = SigStyle.BRUSHES.length;
      int bw0 = (tw - 3 * (n - 1)) / n;

      for (int i0 = 0; i0 < n; i0++) {
         int i = i0;
         this.button(L.t(SigStyle.BRUSHES_SHORT[i]), tx + i * (bw0 + 3), this.toolY, i == n - 1 ? tw - (n - 1) * (bw0 + 3) : bw0, 18, () -> {
            this.style.brush = i;
            this.styleChanged();
         }).selected(this.style.brush == i);
      }

      int ry = this.toolY + 22;

      for (int i = 0; i < SigStyle.COLORS.length; i++) {
         int k = i;
         this.button("", tx + i * 25, ry, 22, 18, () -> {
            this.style.color = k;
            this.styleChanged();
         }).swatch(SigStyle.COLORS[i]).selected(this.style.color == i);
      }

      int sx = tx + tw - 20;
      this.button("+", sx, ry, 20, 18, () -> {
         this.style.size = Math.min(5, this.style.size + 1);
         this.styleChanged();
      }).enabled(this.style.size < 5);
      this.button("−", sx - 84, ry, 20, 18, () -> {
         this.style.size = Math.max(1, this.style.size - 1);
         this.styleChanged();
      }).enabled(this.style.size > 1);
      int by = this.cy + this.ch + 12;
      int x = this.cardX + 12;
      int w = this.cardW - 24;
      int bw = Math.min(110, (w - 12) / 4);
      this.button(L.t("Очистить"), x, by, bw, () -> {
         this.data.clear();
         this.dirty = true;
      });
      this.button(L.t("Отменить штрих"), x + bw + 4, by, bw, this::undo);
      this.doneBtn = this.button(this.doneLabel, x + w - bw, by, bw, this::finish).primary();
      this.button(L.t("Назад"), x + w - 2 * bw - 4, by, bw, this::onClose).ghost();
   }

   private void styleChanged() {
      last = new SigStyle(this.style.brush, this.style.color, this.style.size);
      this.dirty = true;
      this.rebuildWidgets();
   }

   private int points() {
      int n = 0;

      for (int v : this.data) {
         if (v != -1) {
            n++;
         }
      }

      return n;
   }

   private void undo() {
      while (!this.data.isEmpty() && this.data.get(this.data.size() - 1) == -1) {
         this.data.remove(this.data.size() - 1);
      }

      while (!this.data.isEmpty() && this.data.get(this.data.size() - 1) != -1) {
         this.data.remove(this.data.size() - 1);
      }

      this.dirty = true;
   }

   private int[] result() {
      List<Integer> d = new ArrayList<>(this.data);

      while (!d.isEmpty() && d.get(d.size() - 1) == -1) {
         d.remove(d.size() - 1);
      }

      int[] out = new int[d.size() + 1];
      out[0] = this.style.header();

      for (int i = 0; i < d.size(); i++) {
         out[i + 1] = d.get(i);
      }

      return out;
   }

   private void finish() {
      if (this.points() < 8) {
         this.error(L.t("Нарисуйте подпись подлиннее."));
      } else {
         String pos = this.position == null ? "" : this.position.getValue().trim();
         if (this.askPosition && pos.isEmpty()) {
            this.error(L.t("Укажите должность."));
         } else {
            this.done.accept(pos, this.result());
         }
      }
   }

   private boolean inCanvas(double mx, double my) {
      return mx >= (double)this.cx && mx < (double)(this.cx + this.cw) && my >= (double)this.cy && my < (double)(this.cy + this.ch);
   }

   private int lx(double mx) {
      return (int)Math.max(0.0, Math.min(255.0, (mx - (double)this.cx) / (double)this.cw * 256.0));
   }

   private int ly(double my) {
      return (int)Math.max(0.0, Math.min(95.0, (my - (double)this.cy) / (double)this.ch * 96.0));
   }

   private void addPoint(double mx, double my, boolean start) {
      if (this.data.size() < 896) {
         int x = this.lx(mx);
         int y = this.ly(my);
         int v = x | y << 8;
         if (start) {
            if (!this.data.isEmpty() && this.data.get(this.data.size() - 1) != -1) {
               this.data.add(-1);
            }

            this.data.add(v);
         } else {
            if (this.data.isEmpty() || this.data.get(this.data.size() - 1) == -1) {
               return;
            }

            int last = this.data.get(this.data.size() - 1);
            int dx = (last & 0xFF) - x;
            int dy = (last >> 8 & 0xFF) - y;
            if (dx * dx + dy * dy < 2) {
               return;
            }

            this.data.add(v);
         }

         this.dirty = true;
      }
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0 && this.inCanvas(mx, my)) {
         this.drawing = true;
         this.addPoint(mx, my, true);
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.drawing && button == 0) {
         this.addPoint(mx, my, false);
         long now = System.currentTimeMillis();
         if (now - this.lastPen > 520L && Math.abs(dx) + Math.abs(dy) > 1.5) {
            this.lastPen = now;
            Sounds.play(Sounds.PEN, 0.9F + (float)Math.random() * 0.2F, 0.5F);
         }

         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   public boolean mouseReleased(double mx, double my, int button) {
      if (button == 0) {
         this.drawing = false;
      }

      return super.mouseReleased(mx, my, button);
   }

   private void redraw() {
      int s = 3;
      BufferedImage img = new BufferedImage(256 * s, 96 * s, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(new Color(251, 248, 240));
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.setColor(new Color(205, 196, 176));
      g.setStroke(new BasicStroke(2.0F));
      g.drawLine(12 * s, 80 * s, 244 * s, 80 * s);
      g.setStroke(new BasicStroke(1.5F));
      g.drawLine(12 * s, 72 * s, 20 * s, 80 * s);
      g.drawLine(20 * s, 72 * s, 12 * s, 80 * s);
      int[] arr = this.result();
      if (arr.length > 1) {
         DocRenderer.drawSignature(g, arr, 0.0, 0.0, (double)(256 * s), (double)(96 * s), new Color(22, 36, 128));
      } else {
         BufferedImage layer = new BufferedImage(img.getWidth(), img.getHeight(), 2);
         Graphics2D lg = layer.createGraphics();
         lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         DocRenderer.drawSignature(lg, this.demo(), 0.0, 0.0, (double)(256 * s), (double)(96 * s), new Color(22, 36, 128));
         lg.dispose();
         Composite oc = g.getComposite();
         g.setComposite(AlphaComposite.SrcOver.derive(0.2F));
         g.drawImage(layer, 0, 0, null);
         g.setComposite(oc);
      }

      g.dispose();
      this.tex.set(img);
      this.dirty = false;
   }

   private int[] demo() {
      List<Integer> d = new ArrayList<>();
      d.add(this.style.header());

      for (int i = 0; i <= 50; i++) {
         double t = (double)i / 50.0;
         int x = (int)(40.0 + t * 170.0);
         int y = (int)(52.0 + Math.sin(t * 9.0) * 16.0 * (1.0 - t * 0.4) - t * 8.0);
         d.add(x | y << 8);
      }

      int[] a = new int[d.size()];

      for (int i = 0; i < a.length; i++) {
         a[i] = d.get(i);
      }

      return a;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      if (this.dirty || !this.tex.ready()) {
         this.redraw();
      }

      Ui.card(g, this.font, this.cardX, this.cardY, this.cardW, this.cardH, L.t("Подпись"));
      g.drawString(this.font, L.t("Зажмите левую кнопку мыши и распишитесь в поле ниже"), this.cardX + 12, this.cardY + 28, -6253953, false);
      Ui.paperFrame(g, this.cx, this.cy, this.cw, this.ch);
      g.blit(this.tex.id(), this.cx, this.cy, this.cw, this.ch, 0.0F, 0.0F, this.tex.w, this.tex.h, this.tex.w, this.tex.h);
      if (this.inCanvas((double)mx, (double)my)) {
         Ui.outline(g, this.cx - 1, this.cy - 1, this.cw + 2, this.ch + 2, Ui.alpha(-2641559, 0.6F));
      }

      this.doneBtn.active = this.points() >= 8;
      String sz = L.t("Толщина ") + this.style.size;
      int center = this.cardX + this.cardW - 12 - 20 - 32;
      g.drawString(this.font, sz, center - this.font.width(sz) / 2, this.toolY + 22 + 5, -3555408, false);
      g.drawString(this.font, L.t(SigStyle.COLOR_NAMES[this.style.color]) + L.t(" чернила"), this.cardX + 12 + 100 + 6, this.toolY + 22 + 5, -6253953, false);
      int left = Math.max(0, (896 - this.data.size()) * 100 / 896);
      int bx = this.cx + this.cw - 64;
      int byy = this.cy + this.ch - 9;
      g.fill(bx, byy, bx + 56, byy + 3, 805306368);
      g.fill(bx, byy, bx + 56 * left / 100, byy + 3, left > 20 ? -2143268752 : -1061142464);
      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public void removed() {
      this.tex.close();
      super.removed();
   }

   public interface Done {
      void accept(String var1, int[] var2);
   }
}
