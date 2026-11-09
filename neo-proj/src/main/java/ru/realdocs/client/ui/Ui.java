package ru.realdocs.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import ru.realdocs.L;

public final class Ui {
   public static final int BG_TOP = -267052764;
   public static final int BG_BOTTOM = -183956719;
   public static final int CARD = -266134505;
   public static final int CARD_HEAD = -13753316;
   public static final int CARD_BORDER = -11912660;
   public static final int CARD_INNER = -15067887;
   public static final int DIVIDER = -12767194;
   public static final int TEXT = -1183755;
   public static final int TEXT_DIM = -3555408;
   public static final int MUTED = -6253953;
   public static final int FAINT = -9543342;
   public static final int ACCENT = -2641559;
   public static final int ACCENT_HOVER = -1653630;
   public static final int ACCENT_DARK = -7442891;
   public static final int ACCENT_TEXT = -14673910;
   public static final int FOCUS = -2641559;
   public static final int OK = -10628481;
   public static final int WARN = -868018;
   public static final int ERR = -1088669;
   public static final int INFO = -9524993;
   public static final int BTN = -13424353;
   public static final int BTN_HOVER = -12306646;
   public static final int BTN_BORDER = -11057873;
   public static final int DANGER = -12115414;
   public static final int DANGER_HOVER = -10737101;
   public static final int DANGER_BORDER = -8768448;
   public static final int DANGER_TEXT = -18248;
   public static final int SELECTED = -12897502;
   public static final int FIELD = -15396595;
   public static final int FIELD_BORDER = -11912404;
   public static final int FIELD_HOVER = -9808320;
   public static final int PAPER = -1185572;

   private Ui() {
   }

   public static int mix(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return (int)((float)aa + (float)(ba - aa) * t) << 24
         | (int)((float)ar + (float)(br - ar) * t) << 16
         | (int)((float)ag + (float)(bg - ag) * t) << 8
         | (int)((float)ab + (float)(bb - ab) * t);
   }

   public static int alpha(int c, float a) {
      return (int)((float)(c >>> 24) * Math.max(0.0F, Math.min(1.0F, a))) << 24 | c & 16777215;
   }

   public static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
      if (w > 2 && h > 2) {
         g.fill(x + 1, y, x + w - 1, y + h, color);
         g.fill(x, y + 1, x + 1, y + h - 1, color);
         g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
      } else {
         g.fill(x, y, x + w, y + h, color);
      }
   }

   public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x + 1, y, x + w - 1, y + 1, color);
      g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
      g.fill(x, y + 1, x + 1, y + h - 1, color);
      g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
   }

   public static void shadow(GuiGraphics g, int x, int y, int w, int h, int size) {
      for (int i = size; i >= 1; i--) {
         int a = (int)(70.0 * (1.0 - (double)i / (double)(size + 1)) / (double)size * 2.2);
         round(g, x - i, y - i + 2, w + 2 * i, h + 2 * i, Math.min(255, a) << 24);
      }
   }

   public static void background(GuiGraphics g, int w, int h) {
      Desk.draw(g, w, h);
   }

   public static void card(GuiGraphics g, int x, int y, int w, int h) {
      shadow(g, x, y, w, h, 4);
      round(g, x, y, w, h, -266134505);
      outline(g, x, y, w, h, -11912660);
   }

   public static int card(GuiGraphics g, Font font, int x, int y, int w, int h, String title) {
      card(g, x, y, w, h);
      g.fill(x + 1, y + 1, x + w - 1, y + 19, -13753316);
      g.fill(x + 1, y + 19, x + w - 1, y + 20, -11912660);
      g.fill(x + 8, y + 7, x + 10, y + 13, -2641559);
      g.drawString(font, ellipsize(font, title, w - 24), x + 15, y + 6, -1183755, false);
      return y + 26;
   }

   public static void section(GuiGraphics g, Font font, String text, int x, int y, int w) {
      text = L.t(text);
      g.drawString(font, text, x, y, -6253953, false);
      int tx = x + font.width(text) + 6;
      if (tx < x + w) {
         g.fill(tx, y + 4, x + w, y + 5, -12767194);
      }
   }

   public static void label(GuiGraphics g, Font font, String text, int x, int y) {
      g.drawString(font, L.t(text), x, y, -6253953, false);
   }

   public static int pill(GuiGraphics g, Font font, String text, int x, int y, int color) {
      text = L.t(text);
      int w = font.width(text) + 16;
      round(g, x, y, w, 14, alpha(color, 0.16F) | 268435456);
      outline(g, x, y, w, 14, alpha(color, 0.55F));
      g.fill(x + 5, y + 5, x + 9, y + 9, color);
      g.drawString(font, text, x + 12, y + 3, color, false);
      return w;
   }

   public static String ellipsize(Font font, String s, int w) {
      if (s == null) {
         return "";
      } else {
         s = L.t(s);
         if (font.width(s) <= w) {
            return s;
         } else {
            String dots = "…";
            int dw = font.width(dots);
            return font.plainSubstrByWidth(s, Math.max(0, w - dw)) + dots;
         }
      }
   }

   public static List<String> wrap(Font font, String text, int w) {
      List<String> out = new ArrayList<>();
      text = L.t(text);

      for (FormattedCharSequence line : font.split(Component.literal(text), w)) {
         StringBuilder sb = new StringBuilder();
         line.accept((i, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
         });
         out.add(sb.toString());
      }

      return out;
   }

   public static void paperFrame(GuiGraphics g, int x, int y, int w, int h) {
      shadow(g, x, y, w, h, 6);
      g.fill(x - 1, y - 1, x + w + 1, y + h + 1, -15921388);
   }
}
