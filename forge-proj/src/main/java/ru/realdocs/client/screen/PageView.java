package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.ui.Ui;

public final class PageView {
   public final PageTexture tex = new PageTexture();
   public int x;
   public int y;
   public int w;
   public int h;

   public void layout(int ax, int ay, int aw, int ah, double aspect) {
      double ww = (double)aw;
      double hh = ww * aspect;
      if (hh > (double)ah) {
         hh = (double)ah;
         ww = hh / aspect;
      }

      this.w = Math.max(40, (int)ww);
      this.h = Math.max(40, (int)hh);
      this.x = ax + (aw - this.w) / 2;
      this.y = ay + (ah - this.h) / 2;
   }

   public boolean contains(double mx, double my) {
      return mx >= (double)this.x && mx < (double)(this.x + this.w) && my >= (double)this.y && my < (double)(this.y + this.h);
   }

   public int nx(double mx) {
      return (int)Math.round(Math.max(0.0, Math.min(1.0, (mx - (double)this.x) / (double)this.w)) * 1000.0);
   }

   public int ny(double my) {
      return (int)Math.round(Math.max(0.0, Math.min(1.0, (my - (double)this.y) / (double)this.h)) * 1000.0);
   }

   public double sx(int nx) {
      return (double)this.x + (double)nx / 1000.0 * (double)this.w;
   }

   public double sy(int ny) {
      return (double)this.y + (double)ny / 1000.0 * (double)this.h;
   }

   public void draw(GuiGraphics g) {
      Ui.paperFrame(g, this.x, this.y, this.w, this.h);
      if (this.tex.ready()) {
         g.blit(this.tex.id(), this.x, this.y, this.w, this.h, 0.0F, 0.0F, this.tex.w, this.tex.h, this.tex.w, this.tex.h);
      }
   }

   public void close() {
      this.tex.close();
   }
}
