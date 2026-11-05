package ru.realdocs.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;

public final class Desk {
   private static final int TW = 512;
   private static final int TH = 256;
   private static PageTexture wood;
   private static PageTexture icons;
   public static final int ICON = 32;
   public static final int I_STAMP = 0;
   public static final int I_CHECK = 1;
   public static final int I_UV = 2;
   public static final int I_LOUPE = 3;
   public static final int I_EXPORT = 4;
   public static final int I_CLOSE = 5;
   public static final int I_PEN = 6;
   public static final int I_BOOK = 7;
   public static final int I_FLIP = 8;
   public static final int I_EDIT = 9;
   public static final int I_PAGE = 10;
   public static final int I_PRINT = 11;
   private static final int ICONS = 12;
   private static final String[][] LABELS = new String[][]{
      {"Взять печать", "0"},
      {"Убрать печать", "0"},
      {"Проверить подлинность", "1"},
      {"УФ-свет", "2"},
      {"Лупа", "3"},
      {"Экспорт в PNG", "4"},
      {"Экспорт в PDF", "4"},
      {"Закрыть", "5"},
      {"Добавить подпись", "6"},
      {"Подписать", "6"},
      {"Открыть паспорт", "7"},
      {"Закрыть паспорт", "7"},
      {"Перевернуть: оборот", "8"},
      {"Перевернуть: лицевая", "8"},
      {"Изменить данные", "9"},
      {"Копия документа", "11"},
      {"Напечатать", "11"}
   };

   private Desk() {
   }

   private static BufferedImage paintWood() {
      BufferedImage img = new BufferedImage(512, 256, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Random r = new Random(1234L);
      int planks = 4;
      int ph = 256 / planks;

      for (int p = 0; p < planks; p++) {
         int y0 = p * ph;
         Color base = new Color(78 + r.nextInt(10), 50 + r.nextInt(8), 32 + r.nextInt(6));
         g.setPaint(new GradientPaint(0.0F, (float)y0, base.brighter(), 0.0F, (float)(y0 + ph), base));
         g.fill(new Double(0.0, (double)y0, 512.0, (double)ph));

         for (int k = 0; k < 46; k++) {
            double yy = (double)y0 + r.nextDouble() * (double)ph;
            double amp = 0.6 + r.nextDouble() * 2.2;
            double ph0 = r.nextDouble() * Math.PI * 2.0;
            int waves = 1 + r.nextInt(3);
            java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();

            for (int x = 0; x <= 512; x += 4) {
               double y = yy + amp * Math.sin((double)x * Math.PI * 2.0 * (double)waves / 512.0 + ph0);
               if (x == 0) {
                  path.moveTo((double)x, y);
               } else {
                  path.lineTo((double)x, y);
               }
            }

            boolean dark = r.nextBoolean();
            g.setColor(dark ? new Color(30, 18, 10, 30 + r.nextInt(40)) : new Color(140, 98, 62, 20 + r.nextInt(30)));
            g.setStroke(new BasicStroke((float)(0.5 + r.nextDouble() * 1.2)));
            g.draw(path);
         }

         if (r.nextInt(3) == 0) {
            double kx = 40.0 + r.nextDouble() * 432.0;
            double ky = (double)y0 + (double)ph * (0.3 + r.nextDouble() * 0.4);

            for (int i = 6; i > 0; i--) {
               g.setColor(new Color(40, 24, 14, 40));
               g.draw(new java.awt.geom.Ellipse2D.Double(kx - (double)(i * 4), ky - (double)i * 1.6, (double)(i * 8), (double)i * 3.2));
            }
         }

         g.setColor(new Color(20, 12, 8, 160));
         g.fill(new Double(0.0, (double)(y0 + ph) - 1.5, 512.0, 1.5));
         g.setColor(new Color(160, 120, 80, 40));
         g.fill(new Double(0.0, (double)y0, 512.0, 1.0));
      }

      g.dispose();
      return img;
   }

   public static void draw(GuiGraphics g, int w, int h) {
      if (wood == null || !wood.ready()) {
         wood = new PageTexture();

         try {
            wood.set(paintWood());
         } catch (Exception var11) {
            wood = null;
         }
      }

      if (wood != null && wood.ready()) {
         int tw = 384;
         int th = tw * 256 / 512;

         for (int y = 0; y < h; y += th) {
            for (int x = 0; x < w; x += tw) {
               int dw = Math.min(tw, w - x);
               int dh = Math.min(th, h - y);
               float uw = (float)dw / (float)tw * (float)wood.w;
               float vh = (float)dh / (float)th * (float)wood.h;
               g.blit(wood.id(), x, y, dw, dh, 0.0F, 0.0F, (int)uw, (int)vh, wood.w, wood.h);
            }
         }
      } else {
         g.fillGradient(0, 0, w, h, -12966376, -14412018);
      }

      g.fillGradient(0, 0, w, h / 2, 654303920, 0);
      g.fillGradient(0, h / 2, w, h, 0, 1879048192);
      g.fillGradient(0, 0, w, 18, 1342177280, 0);
      int side = Math.max(20, w / 10);

      for (int i = 0; i < side; i += 2) {
         int a = (int)(90.0 * (1.0 - (double)i / (double)side));
         g.fill(i, 0, i + 2, h, a << 24);
         g.fill(w - i - 2, 0, w - i, h, a << 24);
      }
   }

   public static void paperShadow(GuiGraphics g, int x, int y, int w, int h) {
      for (int i = 8; i >= 1; i--) {
         int a = (int)(60.0 * (1.0 - (double)i / 9.0) / 4.0);
         g.fill(x - i + 4, y - i + 6, x + w + i + 4, y + h + i + 6, a << 24);
      }

      g.fill(x + 2, y + 3, x + w + 3, y + h + 4, 1073741824);
   }

   private static BufferedImage paintIcons() {
      int s = 32;
      BufferedImage img = new BufferedImage(s * 12, s, 2);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      Color brass = new Color(222, 186, 108);
      Color dark = new Color(60, 42, 28);
      Color paper = new Color(244, 238, 224);

      for (int i = 0; i < 12; i++) {
         Graphics2D q = (Graphics2D)g.create(i * s, 0, s, s);
         q.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         switch (i) {
            case 0:
               q.setColor(new Color(120, 78, 44));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(12.0, 3.0, 8.0, 12.0, 4.0, 4.0));
               q.setColor(new Color(150, 100, 60));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(13.5, 13.0, 5.0, 6.0, 2.0, 2.0));
               q.setColor(new Color(70, 72, 80));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(6.0, 18.0, 20.0, 5.0, 2.0, 2.0));
               q.setColor(new Color(40, 60, 150));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(5.0, 23.0, 22.0, 5.0, 2.0, 2.0));
               break;
            case 1: {
               q.setColor(paper);
               q.fill(new java.awt.geom.RoundRectangle2D.Double(6.0, 3.0, 18.0, 24.0, 3.0, 3.0));
               q.setColor(new Color(60, 170, 90));
               q.setStroke(new BasicStroke(3.2F, 1, 1));
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
               p.moveTo(10.0, 16.0);
               p.lineTo(14.0, 21.0);
               p.lineTo(25.0, 8.0);
               q.draw(p);
               break;
            }
            case 2:
               q.setPaint(
                  new RadialGradientPaint(
                     new java.awt.geom.Point2D.Double(16.0, 18.0),
                     13.0F,
                     new float[]{0.0F, 1.0F},
                     new Color[]{new Color(210, 120, 255, 220), new Color(120, 40, 220, 0)}
                  )
               );
               q.fill(new java.awt.geom.Ellipse2D.Double(3.0, 5.0, 26.0, 26.0));
               q.setColor(new Color(40, 40, 50));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(10.0, 3.0, 12.0, 8.0, 3.0, 3.0));
               q.setColor(new Color(190, 140, 255));
               q.fill(new Double(12.0, 10.0, 8.0, 3.0));
               break;
            case 3:
               q.setColor(new Color(200, 220, 235, 120));
               q.fill(new java.awt.geom.Ellipse2D.Double(5.0, 4.0, 17.0, 17.0));
               q.setColor(brass);
               q.setStroke(new BasicStroke(3.0F));
               q.draw(new java.awt.geom.Ellipse2D.Double(5.0, 4.0, 17.0, 17.0));
               q.setColor(dark);
               q.setStroke(new BasicStroke(4.5F, 1, 1));
               q.draw(new java.awt.geom.Line2D.Double(20.0, 19.0, 27.0, 27.0));
               break;
            case 4: {
               q.setColor(paper);
               q.fill(new java.awt.geom.RoundRectangle2D.Double(5.0, 8.0, 16.0, 20.0, 3.0, 3.0));
               q.setColor(brass);
               q.setStroke(new BasicStroke(3.0F, 1, 1));
               q.draw(new java.awt.geom.Line2D.Double(16.0, 16.0, 27.0, 5.0));
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
               p.moveTo(20.0, 5.0);
               p.lineTo(27.0, 5.0);
               p.lineTo(27.0, 12.0);
               q.draw(p);
               break;
            }
            case 5:
               q.setColor(new Color(200, 190, 175));
               q.setStroke(new BasicStroke(3.2F, 1, 1));
               q.draw(new java.awt.geom.Line2D.Double(9.0, 9.0, 23.0, 23.0));
               q.draw(new java.awt.geom.Line2D.Double(23.0, 9.0, 9.0, 23.0));
               break;
            case 6:
               q.rotate(Math.toRadians(40.0), 16.0, 16.0);
               q.setColor(new Color(30, 30, 38));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(13.0, 1.0, 6.0, 22.0, 3.0, 3.0));
               q.setColor(brass);
               q.fill(new Double(13.0, 6.0, 6.0, 2.0));
               q.setColor(new Color(200, 200, 210));
               java.awt.geom.Path2D.Double nib = new java.awt.geom.Path2D.Double();
               nib.moveTo(13.0, 23.0);
               nib.lineTo(19.0, 23.0);
               nib.lineTo(16.0, 31.0);
               nib.closePath();
               q.fill(nib);
               break;
            case 7:
               q.setColor(new Color(110, 30, 40));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(7.0, 3.0, 18.0, 26.0, 3.0, 3.0));
               q.setColor(brass);
               q.draw(new java.awt.geom.Ellipse2D.Double(12.0, 9.0, 8.0, 8.0));
               q.fill(new Double(11.0, 21.0, 10.0, 2.0));
               break;
            case 8:
               q.setColor(brass);
               q.setStroke(new BasicStroke(2.8F, 1, 1));
               q.draw(new java.awt.geom.Arc2D.Double(5.0, 6.0, 22.0, 20.0, 20.0, 140.0, 0));
               q.draw(new java.awt.geom.Arc2D.Double(5.0, 6.0, 22.0, 20.0, 200.0, 140.0, 0));
               q.fill(triangle(25.0, 12.0, 29.0, 7.0, 21.0, 7.0));
               q.fill(triangle(7.0, 20.0, 3.0, 25.0, 11.0, 25.0));
               break;
            case 9:
               q.setColor(paper);
               q.fill(new java.awt.geom.RoundRectangle2D.Double(4.0, 6.0, 18.0, 22.0, 3.0, 3.0));
               q.rotate(Math.toRadians(45.0), 20.0, 14.0);
               q.setColor(new Color(220, 170, 60));
               q.fill(new Double(17.0, 2.0, 6.0, 18.0));
               q.setColor(dark);
               q.fill(triangle(17.0, 20.0, 23.0, 20.0, 20.0, 25.0));
               break;
            case 10:
               q.setColor(paper);
               q.fill(new java.awt.geom.RoundRectangle2D.Double(7.0, 4.0, 18.0, 24.0, 2.0, 2.0));
               q.setColor(new Color(150, 140, 125));

               for (int k = 0; k < 4; k++) {
                  q.fill(new Double(10.0, (double)(9 + k * 4), 12.0, 1.5));
               }
               break;
            case 11:
               q.setColor(new Color(220, 220, 214));
               q.fill(new java.awt.geom.RoundRectangle2D.Double(4.0, 12.0, 24.0, 12.0, 4.0, 4.0));
               q.setColor(paper);
               q.fill(new Double(9.0, 4.0, 14.0, 9.0));
               q.fill(new Double(9.0, 21.0, 14.0, 8.0));
               q.setColor(new Color(60, 200, 110));
               q.fill(new java.awt.geom.Ellipse2D.Double(22.0, 15.0, 3.0, 3.0));
         }

         q.dispose();
      }

      g.dispose();
      return img;
   }

   private static Shape triangle(double x0, double y0, double x1, double y1, double x2, double y2) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      p.moveTo(x0, y0);
      p.lineTo(x1, y1);
      p.lineTo(x2, y2);
      p.closePath();
      return p;
   }

   public static void icon(GuiGraphics g, int icon, int x, int y, int size) {
      if (icons == null || !icons.ready()) {
         icons = new PageTexture();

         try {
            icons.set(paintIcons());
         } catch (Exception var6) {
            icons = null;
            return;
         }
      }

      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.blit(icons.id(), x, y, size, size, (float)(icon * 32), 0.0F, 32, 32, icons.w, icons.h);
      RenderSystem.disableBlend();
   }

   public static int iconFor(String label) {
      if (label != null && !label.isEmpty()) {
         for (String[] l : LABELS) {
            if (l[0].equals(label) || L.t(l[0]).equals(label)) {
               return Integer.parseInt(l[1]);
            }
         }

         return -1;
      } else {
         return -1;
      }
   }
}
