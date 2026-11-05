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

   public UiField(Font font, int x, int y, int w, int h, String hint) {
      super(font, x + 6, y + (h - 8) / 2, w - 12, 9, Component.literal(L.t(hint)));
      this.ox = x;
      this.oy = y;
      this.ow = w;
      this.oh = h;
      this.setBordered(false);
      this.setTextColor(-1183755);
      this.setHint(Component.literal(L.t(hint)).withStyle(s -> s.withColor(7233874)));
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

   public boolean isMouseOver(double mx, double my) {
      return this.isVisible() && mx >= (double)this.ox && mx < (double)(this.ox + this.ow) && my >= (double)this.oy && my < (double)(this.oy + this.oh);
   }

   protected boolean clicked(double mx, double my) {
      return this.active && this.isMouseOver(mx, my);
   }
}
