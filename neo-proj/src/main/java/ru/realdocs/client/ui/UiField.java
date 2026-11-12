package ru.realdocs.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;

public class UiField extends EditBox {
   private final int ox;
   private final int oy;
   private final int ow;
   private final int oh;
   private String label = "";
   private boolean light;

   public UiField(Font font, int x, int y, int w, int h, String hint) {
      super(font, x + 6, y + (h - 8) / 2, w - 12, 9, Component.literal(L.t(hint)));
      this.ox = x;
      this.oy = y;
      this.ow = w;
      this.oh = h;
      this.setBordered(false);
      this.setTextColor(-1183755);
      this.setHint(Component.literal(Ui.ellipsize(font, L.t(hint), w - 14)).withStyle(s -> s.withColor(7233874)));
   }

   /** White sunken field of the in-game computer. */
   public UiField light() {
      this.light = true;
      this.setTextColor(-16777216);
      this.setTextColorUneditable(-8355712);
      return this;
   }

   public boolean isLight() {
      return this.light;
   }

   public UiField label(String l) {
      this.label = l == null ? "" : L.t(l);
      return this;
   }

   public int outerX() {
      return this.ox;
   }

   public int outerY() {
      return this.oy;
   }

   public int outerW() {
      return this.ow;
   }

   public int outerH() {
      return this.oh;
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      if (this.isVisible()) {
         Font font = Minecraft.getInstance().font;
         boolean hov = mx >= this.ox && mx < this.ox + this.ow && my >= this.oy && my < this.oy + this.oh;
         if (this.light) {
            if (!this.label.isEmpty()) {
               g.drawString(font, Ui.ellipsize(font, this.label, this.ow - 2), this.ox + 1, this.oy - 10, -16777216, false);
            }

            g.fill(this.ox, this.oy, this.ox + this.ow, this.oy + this.oh, -1);
            g.fill(this.ox, this.oy, this.ox + this.ow, this.oy + 1, -8355712);
            g.fill(this.ox, this.oy, this.ox + 1, this.oy + this.oh, -8355712);
            g.fill(this.ox + 1, this.oy + 1, this.ox + this.ow - 1, this.oy + 2, -12566464);
            g.fill(this.ox + 1, this.oy + 1, this.ox + 2, this.oy + this.oh - 1, -12566464);
            g.fill(this.ox, this.oy + this.oh - 1, this.ox + this.ow, this.oy + this.oh, -1);
            g.fill(this.ox + this.ow - 1, this.oy, this.ox + this.ow, this.oy + this.oh, -2830136);
            this.renderLightText(g, font);
            return;
         }

         if (!this.label.isEmpty()) {
            g.drawString(font, Ui.ellipsize(font, this.label, this.ow - 2), this.ox + 1, this.oy - 10, this.isFocused() ? -2641559 : -6253953, false);
         }

         Ui.round(g, this.ox, this.oy, this.ow, this.oh, -15396595);
         Ui.outline(g, this.ox, this.oy, this.ow, this.oh, this.isFocused() ? -2641559 : (hov ? -9808320 : -11912404));
         if (this.isFocused()) {
            g.fill(this.ox + 1, this.oy + this.oh - 2, this.ox + this.ow - 1, this.oy + this.oh - 1, Ui.alpha(-2641559, 0.5F));
         }

         super.renderWidget(g, mx, my, pt);
      }
   }

   private int lightScroll;

   /** EditBox always draws its text with a shadow, which smears on a white field: draw it flat ourselves. */
   private void renderLightText(GuiGraphics g, Font font) {
      String v = this.getValue();
      int cur = Math.min(this.getCursorPosition(), v.length());
      int iw = this.getWidth();
      this.lightScroll = Math.max(0, Math.min(this.lightScroll, v.length()));
      if (cur < this.lightScroll) {
         this.lightScroll = cur;
      }

      while (this.lightScroll < cur && font.width(v.substring(this.lightScroll, cur)) > iw - 2) {
         this.lightScroll++;
      }

      String vis = font.plainSubstrByWidth(v.substring(this.lightScroll), iw);
      int tx = this.getX();
      int ty = this.getY();
      if (v.isEmpty() && !this.isFocused()) {
         g.drawString(font, Ui.ellipsize(font, this.getMessage().getString(), iw), tx, ty, -6250336, false);
         return;
      }

      String sel = this.getHighlighted();
      if (!sel.isEmpty()) {
         int a;
         int b;
         if (cur >= sel.length() && v.startsWith(sel, cur - sel.length())) {
            a = cur - sel.length();
            b = cur;
         } else {
            a = cur;
            b = Math.min(v.length(), cur + sel.length());
         }

         int va = Math.max(a, this.lightScroll) - this.lightScroll;
         int vb = Math.min(b - this.lightScroll, vis.length());
         if (vb > va) {
            int x1 = tx + font.width(vis.substring(0, va));
            int x2 = tx + font.width(vis.substring(0, vb));
            g.fill(x1, ty - 1, x2, ty + 9, -16756070);
         }
      }

      g.drawString(font, vis, tx, ty, this.active ? -16777216 : -8355712, false);
      if (!sel.isEmpty()) {
         int a = cur >= sel.length() && v.startsWith(sel, cur - sel.length()) ? cur - sel.length() : cur;
         int va = Math.max(a, this.lightScroll) - this.lightScroll;
         int vb = Math.min(a + sel.length() - this.lightScroll, vis.length());
         if (vb > va) {
            g.drawString(font, vis.substring(va, vb), tx + font.width(vis.substring(0, va)), ty, -1, false);
         }
      }

      if (this.isFocused() && net.minecraft.Util.getMillis() / 300L % 2L == 0L) {
         int cx = tx + font.width(vis.substring(0, Math.max(0, Math.min(vis.length(), cur - this.lightScroll))));
         g.fill(cx, ty - 1, cx + 1, ty + 9, -16777216);
      }
   }

   @Override
   public void onClick(double mx, double my) {
      if (!this.light) {
         super.onClick(mx, my);
      } else {
         Font font = Minecraft.getInstance().font;
         String v = this.getValue();
         int rel = net.minecraft.util.Mth.floor(mx) - this.getX();
         String vis = font.plainSubstrByWidth(v.substring(Math.min(this.lightScroll, v.length())), this.getWidth());
         int idx = this.lightScroll + font.plainSubstrByWidth(vis, Math.max(0, rel)).length();
         this.setCursorPosition(idx);
         this.setHighlightPos(idx);
      }
   }

   public boolean isMouseOver(double mx, double my) {
      return this.isVisible() && mx >= (double)this.ox && mx < (double)(this.ox + this.ow) && my >= (double)this.oy && my < (double)(this.oy + this.oh);
   }

   protected boolean clicked(double mx, double my) {
      return this.active && this.isMouseOver(mx, my);
   }
}
