package ru.realdocs.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;

public class UiButton extends Button {
   private UiButton.Style style = UiButton.Style.NORMAL;
   private boolean selected;
   private float hover;
   private int swatch = -1;
   private int icon = -1;
   private String hint = "";

   public UiButton(int x, int y, int w, int h, String label, Runnable action) {
      super(x, y, w, h, Component.literal(L.t(label)), b -> action.run(), DEFAULT_NARRATION);
      this.icon = Desk.iconFor(label);
   }

   public UiButton style(UiButton.Style s) {
      this.style = s;
      return this;
   }

   public UiButton primary() {
      return this.style(UiButton.Style.PRIMARY);
   }

   public UiButton danger() {
      return this.style(UiButton.Style.DANGER);
   }

   public UiButton ghost() {
      return this.style(UiButton.Style.GHOST);
   }

   public UiButton selected(boolean s) {
      this.selected = s;
      return this;
   }

   public boolean isSelected() {
      return this.selected;
   }

   public UiButton swatch(int rgb) {
      this.swatch = rgb;
      return this;
   }

   public UiButton enabled(boolean on) {
      this.active = on;
      return this;
   }

   public void setLabel(String s) {
      this.setMessage(Component.literal(L.t(s)));
      this.icon = Desk.iconFor(s);
   }

   public UiButton hint(String key) {
      this.hint = key == null ? "" : key;
      return this;
   }

   public UiButton icon(int i) {
      this.icon = i;
      return this;
   }

   protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      Font font = Minecraft.getInstance().font;
      boolean hov = this.active && this.isHoveredOrFocused();
      this.hover = this.hover + ((hov ? 1.0F : 0.0F) - this.hover) * 0.35F;
      int bg;
      int border;
      int text;
      switch (this.style) {
         case PRIMARY:
            bg = Ui.mix(-2641559, -1653630, this.hover);
            border = Ui.mix(-7442891, -1653630, this.hover);
            text = -14673910;
            break;
         case DANGER:
            bg = Ui.mix(-12115414, -10737101, this.hover);
            border = -8768448;
            text = -18248;
            break;
         case GHOST:
            bg = Ui.mix(0, 587202559, this.hover);
            border = Ui.mix(0, -11057873, this.hover);
            text = Ui.mix(-3555408, -1183755, this.hover);
            break;
         default:
            bg = Ui.mix(-13424353, -12306646, this.hover);
            border = Ui.mix(-11057873, -9808320, this.hover);
            text = -1183755;
      }

      if (this.selected) {
         bg = Ui.mix(-12897502, -11911384, this.hover);
         border = -2641559;
         text = -2641559;
      }

      if (!this.active) {
         bg = this.style == UiButton.Style.GHOST ? 0 : -14410473;
         border = this.style == UiButton.Style.GHOST ? 0 : -13095645;
         text = this.selected ? -7442891 : -8754084;
      }

      int x = this.getX();
      int y = this.getY();
      int w = this.width;
      int h = this.height;
      Ui.round(g, x, y, w, h, bg);
      Ui.outline(g, x, y, w, h, border);
      if (this.style != UiButton.Style.GHOST && this.style != UiButton.Style.PRIMARY && this.active) {
         g.fill(x + 1, y + 1, x + w - 1, y + 2, 352321535);
      }

      String s = this.getMessage().getString();
      int isz = Math.min(16, h - 4);
      boolean ic = this.icon >= 0 && isz >= 10;
      int left = this.swatch >= 0 ? 14 : (ic ? isz + 4 : 0);
      if (ic && w < font.width(s) + isz + 12) {
         Desk.icon(g, this.icon, x + (w - isz) / 2, y + (h - isz) / 2, isz);
      } else {
         s = Ui.ellipsize(font, s, w - (w < 34 ? 2 : 8) - left);
         int tw = font.width(s) + left;
         int tx = x + (w - tw) / 2;
         if (this.swatch >= 0) {
            g.fill(tx, y + h / 2 - 5, tx + 10, y + h / 2 + 5, -16052974);
            g.fill(tx + 1, y + h / 2 - 4, tx + 9, y + h / 2 + 4, 0xFF000000 | this.swatch);
         }

         if (ic) {
            if (!this.active) {
               g.setColor(0.5F, 0.5F, 0.5F, 0.7F);
            }

            Desk.icon(g, this.icon, tx, y + (h - isz) / 2, isz);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
         }

         g.drawString(font, s, tx + left, y + (h - 8) / 2, text, false);
         if (!this.hint.isEmpty() && this.active && tx + tw + font.width(this.hint) + 8 < x + w - 4) {
            int hx = x + w - font.width(this.hint) - 6;
            int hy = y + (h - 8) / 2;
            Ui.outline(g, hx - 3, hy - 2, font.width(this.hint) + 6, 12, Ui.alpha(text, 0.35F));
            g.drawString(font, this.hint, hx, hy, Ui.alpha(text, 0.6F), false);
         }
      }
   }

   public static enum Style {
      NORMAL,
      PRIMARY,
      DANGER,
      GHOST;
   }
}
