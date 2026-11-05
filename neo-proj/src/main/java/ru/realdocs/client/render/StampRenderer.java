package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.StampModel;

public final class StampRenderer {
   private static final Locale RU = new Locale("ru", "RU");

   private StampRenderer() {
   }

   public static double[] designSize(int shape) {
      switch (shape) {
         case 1:
            return new double[]{340.0, 240.0};
         case 2:
            return new double[]{340.0, 210.0};
         case 3:
            return new double[]{300.0, 300.0};
         default:
            return new double[]{300.0, 300.0};
      }
   }

   public static double[] designSize(StampModel m) {
      if (m.kind == 2) {
         return new double[]{420.0, 260.0};
      } else if (m.kind != 1) {
         return designSize(m.shape);
      } else {
         return m.shape != 0 && m.shape != 1 ? new double[]{340.0, 200.0} : new double[]{300.0, 300.0};
      }
   }

   public static BufferedImage render(StampModel m, BufferedImage img, int width, DocModel.StampRef ref) {
      if (m.kind == 0) {
         return render(m, img, width);
      } else {
         double[] ds = designSize(m);
         double k = (double)width / ds[0];
         BufferedImage out = new BufferedImage(width, (int)Math.round(ds[1] * k), 2);
         Graphics2D g = out.createGraphics();
         hints(g);
         g.scale(k, k);
         Color ink = new Color(m.color & 16777215);
         if (m.kind == 2) {
            Marks.visa(g, m, img, ds[0], ds[1], ink, ref);
         } else {
            Marks.border(g, m, img, ds[0], ds[1], ink, ref);
            Marks.grunge(out, (long)(m.id.hashCode() ^ (ref == null ? 0 : ref.date.hashCode() * 31 + ref.x)));
         }

         g.dispose();
         return out;
      }
   }

   static void arc(Graphics2D g, String text, double cx, double cy, double r, boolean top) {
      arcText(g, text, cx, cy, r, top);
   }

   public static BufferedImage render(StampModel m, BufferedImage img, int width) {
      double[] ds = designSize(m.shape);
      double k = (double)width / ds[0];
      int h = (int)Math.round(ds[1] * k);
      BufferedImage out = new BufferedImage(width, h, 2);
      Graphics2D g = out.createGraphics();
      hints(g);
      g.scale(k, k);
      g.setComposite(AlphaComposite.SrcOver.derive(0.9F));
      Color ink = new Color(m.color & 16777215);
      g.setColor(ink);
      if (m.shape == 3) {
         drawImageOnly(g, img, ds[0], ds[1]);
      } else if (m.shape == 2) {
         drawRect(g, m, img, ds[0], ds[1], ink);
      } else {
         g.translate(ds[0] / 2.0, ds[1] / 2.0);
         g.scale(ds[0] / 300.0, ds[1] / 300.0);
         g.translate(-150, -150);
         drawCircle(g, m, img, ink);
      }

      g.dispose();
      return out;
   }

   static void hints(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
   }

   private static void drawImageOnly(Graphics2D g, BufferedImage img, double w, double h) {
      if (img == null) {
         g.setStroke(new BasicStroke(3.0F, 0, 0, 10.0F, new float[]{9.0F, 7.0F}, 0.0F));
         g.draw(new Double(20.0, 20.0, w - 40.0, h - 40.0, 24.0, 24.0));
      } else {
         double s = Math.min(w / (double)img.getWidth(), h / (double)img.getHeight());
         double iw = (double)img.getWidth() * s;
         double ih = (double)img.getHeight() * s;
         g.drawImage(img, (int)Math.round((w - iw) / 2.0), (int)Math.round((h - ih) / 2.0), (int)Math.round(iw), (int)Math.round(ih), null);
      }
   }

   private static void drawCircle(Graphics2D g, StampModel m, BufferedImage img, Color ink) {
      double cx = 150.0;
      double cy = 150.0;
      g.setColor(ink);
      g.setStroke(new BasicStroke(5.0F));
      g.draw(new java.awt.geom.Ellipse2D.Double(7.0, 7.0, 286.0, 286.0));
      g.setStroke(new BasicStroke(1.6F));
      g.draw(new java.awt.geom.Ellipse2D.Double(15.0, 15.0, 270.0, 270.0));
      g.setStroke(new BasicStroke(2.2F));
      g.draw(new java.awt.geom.Ellipse2D.Double(52.0, 52.0, 196.0, 196.0));
      String top = up(m.top);
      String bottom = up(m.bottom);
      if (!top.isEmpty()) {
         arcText(g, top, 150.0, 150.0, 106.0, true);
      }

      if (!bottom.isEmpty()) {
         arcText(g, bottom, 150.0, 150.0, 124.0, false);
      }

      if (!top.isEmpty() || !bottom.isEmpty()) {
         star(g, 33.0, 150.0, 6.0);
         star(g, 267.0, 150.0, 6.0);
      }

      drawCenter(g, m, img, 150.0, 150.0, 168.0, 168.0, ink, 1.0);
   }

   private static void arcText(Graphics2D g, String text, double cx, double cy, double r, boolean top) {
      double size = 23.0;
      Font font = new Font("Serif", 1, (int)size);

      while (true) {
         font = font.deriveFont((float)size);
         FontMetrics fm = g.getFontMetrics(font);
         double total = 0.0;

         for (int i = 0; i < text.length(); i++) {
            total += (double)fm.charWidth(text.charAt(i));
         }

         if (total / r <= 2.55 || size <= 9.0) {
            g.setFont(font);
            double theta = total / r;
            double a0 = top ? (-Math.PI / 2) - theta / 2.0 : (Math.PI / 2) + theta / 2.0;

            for (int i = 0; i < text.length(); i++) {
               char ch = text.charAt(i);
               double w = (double)fm.charWidth(ch);
               double step = w / r;
               double a = top ? a0 + step / 2.0 : a0 - step / 2.0;
               double x = cx + r * Math.cos(a);
               double y = cy + r * Math.sin(a);
               AffineTransform old = g.getTransform();
               g.translate(x, y);
               g.rotate(top ? a + (Math.PI / 2) : a - (Math.PI / 2));
               g.drawString(String.valueOf(ch), (float)(-w / 2.0), 0.0F);
               g.setTransform(old);
               a0 = top ? a0 + step : a0 - step;
            }

            return;
         }

         size--;
      }
   }

   private static void drawRect(Graphics2D g, StampModel m, BufferedImage img, double w, double h, Color ink) {
      g.setColor(ink);
      g.setStroke(new BasicStroke(5.0F));
      g.draw(new Double(6.0, 6.0, w - 12.0, h - 12.0, 26.0, 26.0));
      g.setStroke(new BasicStroke(1.8F));
      g.draw(new Double(16.0, 16.0, w - 32.0, h - 32.0, 18.0, 18.0));
      String top = up(m.top);
      String bottom = up(m.bottom);
      double topY = 20.0;
      double botY = h - 20.0;
      if (!top.isEmpty()) {
         Font f = fit(g, top, w - 70.0, 26.0, 10.0, 1);
         g.setFont(f);
         FontMetrics fm = g.getFontMetrics();
         g.drawString(top, (float)((w - (double)fm.stringWidth(top)) / 2.0), (float)(topY + 8.0 + (double)fm.getAscent() * 0.85));
         topY += (double)(fm.getHeight() + 6);
         g.setStroke(new BasicStroke(1.4F));
         g.draw(new java.awt.geom.Line2D.Double(30.0, topY + 4.0, w - 30.0, topY + 4.0));
         topY += 8.0;
      }

      if (!bottom.isEmpty()) {
         Font f = fit(g, bottom, w - 70.0, 22.0, 10.0, 1);
         g.setFont(f);
         FontMetrics fm = g.getFontMetrics();
         g.drawString(bottom, (float)((w - (double)fm.stringWidth(bottom)) / 2.0), (float)(botY - 6.0 - (double)fm.getDescent()));
         botY -= (double)(fm.getHeight() + 6);
         g.setStroke(new BasicStroke(1.4F));
         g.draw(new java.awt.geom.Line2D.Double(30.0, botY - 2.0, w - 30.0, botY - 2.0));
         botY -= 6.0;
      }

      double bh = Math.max(30.0, botY - topY);
      drawCenter(g, m, img, w / 2.0, topY + bh / 2.0, w - 80.0, bh - 6.0, ink, 1.0);
   }

   private static void drawCenter(Graphics2D g, StampModel m, BufferedImage img, double cx, double cy, double bw, double bh, Color ink, double alpha) {
      if (img != null) {
         double s = Math.min(bw / (double)img.getWidth(), bh / (double)img.getHeight());
         double iw = (double)img.getWidth() * s;
         double ih = (double)img.getHeight() * s;
         g.drawImage(img, (int)Math.round(cx - iw / 2.0), (int)Math.round(cy - ih / 2.0), (int)Math.round(iw), (int)Math.round(ih), null);
      } else {
         List<String> lines = new ArrayList<>();

         for (String s : m.center.split("[|\\n]")) {
            String t = up(s.trim());
            if (!t.isEmpty()) {
               lines.add(t);
            }

            if (lines.size() == 4) {
               break;
            }
         }

         g.setColor(ink);
         if (lines.isEmpty()) {
            star(g, cx, cy, Math.min(bw, bh) * 0.32);
         } else {
            double lineH = Math.min(46.0, bh / (double)lines.size());
            double size = Math.max(10.0, Math.min(36.0, lineH * 0.85));
            double totalH = lineH * (double)lines.size();
            double y = cy - totalH / 2.0;

            for (String line : lines) {
               Font f = fit(g, line, bw, size, 8.0, 1);
               g.setFont(f);
               FontMetrics fm = g.getFontMetrics();
               double baseline = y + (lineH + (double)fm.getAscent() - (double)fm.getDescent()) / 2.0;
               g.drawString(line, (float)(cx - (double)fm.stringWidth(line) / 2.0), (float)baseline);
               y += lineH;
            }
         }
      }
   }

   static Font fit(Graphics2D g, String text, double maxWidth, double startSize, double minSize, int style) {
      double size = startSize;

      Font f;
      for (f = new Font("Serif", style, (int)Math.round(startSize)); size > minSize; size--) {
         f = f.deriveFont((float)size);
         if ((double)g.getFontMetrics(f).stringWidth(text) <= maxWidth) {
            return f;
         }
      }

      return f.deriveFont((float)minSize);
   }

   static void star(Graphics2D g, double cx, double cy, double r) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

      for (int i = 0; i < 10; i++) {
         double ang = (-Math.PI / 2) + (double)i * Math.PI / 5.0;
         double rad = i % 2 == 0 ? r : r * 0.42;
         double x = cx + rad * Math.cos(ang);
         double y = cy + rad * Math.sin(ang);
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            p.lineTo(x, y);
         }
      }

      p.closePath();
      g.fill(p);
   }

   private static String up(String s) {
      return s == null ? "" : s.toUpperCase(RU);
   }
}
