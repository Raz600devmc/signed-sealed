package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Rectangle2D.Double;
import java.util.Random;
import ru.realdocs.L;

public final class Security {
   private Security() {
   }

   static long seed(String code) {
      long h = 1125899906842597L;
      if (code != null) {
         for (char c : code.toCharArray()) {
            h = 31L * h + (long)c;
         }
      }

      return h;
   }

   public static void microBorder(Graphics2D g, double x, double y, double w, double h, String text, Color c) {
      Font f = new Font("SansSerif", 0, 3).deriveFont(3.1F);
      g.setFont(f);
      g.setColor(c);
      FontMetrics fm = g.getFontMetrics();
      String unit = text + " · ";
      int uw = Math.max(1, fm.stringWidth(unit));
      StringBuilder sb = new StringBuilder();

      while ((double)fm.stringWidth(sb.toString()) < Math.max(w, h) + (double)uw) {
         sb.append(unit);
      }

      String line = sb.toString();
      Shape oldClip = g.getClip();
      AffineTransform old = g.getTransform();
      g.clip(new Double(x - 1.0, y - 1.0, w + 2.0, h + 2.0));
      g.drawString(line, (float)x, (float)(y + 2.6));
      g.drawString(line, (float)x, (float)(y + h - 0.4));
      g.rotate(Math.PI / 2, x, y);
      g.drawString(line, (float)x, (float)(y - 0.4));
      g.setTransform(old);
      g.rotate(Math.PI / 2, x + w, y);
      g.drawString(line, (float)(x + w), (float)(y + 2.6));
      g.setTransform(old);
      g.setClip(oldClip);
   }

   public static void hologram(Graphics2D g, double x, double y, double w, double h, long seed) {
      Composite oc = g.getComposite();
      Shape oldClip = g.getClip();
      java.awt.geom.Ellipse2D.Double shape = new java.awt.geom.Ellipse2D.Double(x, y, w, h);
      g.setComposite(AlphaComposite.SrcOver.derive(0.42F));
      g.setPaint(
         new LinearGradientPaint(
            (float)x,
            (float)y,
            (float)(x + w),
            (float)(y + h),
            new float[]{0.0F, 0.25F, 0.5F, 0.75F, 1.0F},
            new Color[]{new Color(120, 220, 255), new Color(255, 160, 230), new Color(255, 245, 160), new Color(150, 255, 190), new Color(150, 170, 255)}
         )
      );
      g.fill(shape);
      g.clip(shape);
      g.setComposite(AlphaComposite.SrcOver.derive(0.35F));
      g.setColor(Color.WHITE);
      g.setStroke(new BasicStroke(0.5F));
      Random rnd = new Random(seed);
      double cx = x + w / 2.0;
      double cy = y + h / 2.0;

      for (int i = 0; i < 14; i++) {
         double rr = (double)(i + 1) * Math.max(w, h) / 26.0;
         g.draw(new java.awt.geom.Ellipse2D.Double(cx - rr * (1.0 + 0.2 * rnd.nextDouble()), cy - rr, rr * 2.0, rr * 2.0));
      }

      for (int i = 0; i < 24; i++) {
         double a = (double)i * Math.PI / 12.0;
         g.draw(new java.awt.geom.Line2D.Double(cx, cy, cx + Math.cos(a) * w, cy + Math.sin(a) * h));
      }

      g.setClip(oldClip);
      g.setComposite(AlphaComposite.SrcOver.derive(0.55F));
      g.setColor(new Color(255, 255, 255));
      g.setStroke(new BasicStroke(0.6F));
      g.draw(shape);
      g.setComposite(oc);
   }

   public static void codeStrip(Graphics2D g, double x, double y, double w, String code, Color ink) {
      if (code != null && !code.isEmpty()) {
         Random rnd = new Random(seed(code));
         double bx = x;
         g.setColor(ink);

         while (bx < x + w * 0.62) {
            double bw = 0.6 + (double)rnd.nextInt(3) * 0.6;
            g.fill(new Double(bx, y, bw, 12.0));
            bx += bw + 0.6 + (double)rnd.nextInt(2) * 0.7;
         }

         g.setFont(new Font("Monospaced", 1, 9));
         FontMetrics fm = g.getFontMetrics();
         g.drawString(code, (float)(x + w - (double)fm.stringWidth(code)), (float)(y + 10.0));
      }
   }

   public static void uv(Graphics2D g, Shape[] pages, String code, boolean valid, Runnable emblem) {
      Composite oc = g.getComposite();
      Shape oldClip = g.getClip();

      for (Shape page : pages) {
         g.setClip(oldClip);
         g.clip(page);
         g.setComposite(AlphaComposite.SrcOver.derive(0.88F));
         Rectangle2D b = page.getBounds2D();
         g.setPaint(new GradientPaint((float)b.getX(), (float)b.getY(), new Color(30, 14, 64), (float)b.getMaxX(), (float)b.getMaxY(), new Color(14, 8, 36)));
         g.fill(b);
         if (valid) {
            Random rnd = new Random(seed(code) ^ (long)(b.getX() * 31.0));
            g.setStroke(new BasicStroke(0.7F, 1, 1));
            Color[] fib = new Color[]{new Color(120, 255, 200), new Color(255, 120, 220), new Color(140, 200, 255), new Color(255, 240, 120)};

            for (int i = 0; i < 70; i++) {
               double fx = b.getX() + rnd.nextDouble() * b.getWidth();
               double fy = b.getY() + rnd.nextDouble() * b.getHeight();
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
               p.moveTo(fx, fy);
               double a = rnd.nextDouble() * Math.PI * 2.0;
               double len = 5.0 + rnd.nextDouble() * 9.0;
               p.quadTo(fx + Math.cos(a + 0.8) * len * 0.6, fy + Math.sin(a + 0.8) * len * 0.6, fx + Math.cos(a) * len, fy + Math.sin(a) * len);
               g.setComposite(AlphaComposite.SrcOver.derive(0.55F + rnd.nextFloat() * 0.4F));
               g.setColor(fib[rnd.nextInt(fib.length)]);
               g.draw(p);
            }

            if (code != null && !code.isEmpty()) {
               AffineTransform old = g.getTransform();
               g.setComposite(AlphaComposite.SrcOver.derive(0.16F));
               g.setColor(new Color(160, 255, 230));
               g.setFont(new Font("Monospaced", 1, 9));
               g.rotate(Math.toRadians(-18.0), b.getCenterX(), b.getCenterY());
               int row = 0;

               for (double yy = b.getY() - 40.0; yy < b.getMaxY() + 40.0; row++) {
                  for (double xx = b.getX() - 80.0; xx < b.getMaxX() + 40.0; xx += 120.0) {
                     g.drawString(code, (float)(xx + (double)(row % 2 * 60)), (float)yy);
                  }

                  yy += 52.0;
               }

               g.setTransform(old);
            }
         }
      }

      g.setClip(oldClip);
      if (valid && emblem != null) {
         g.setComposite(AlphaComposite.SrcOver.derive(0.85F));
         emblem.run();
      }

      g.setComposite(oc);
      if (!valid) {
         g.setColor(new Color(255, 120, 120, 200));
         g.setFont(new Font("SansSerif", 1, 14));
         Rectangle2D b = pages[pages.length - 1].getBounds2D();
         String s = L.t("УФ-признаки не обнаружены");
         FontMetrics fm = g.getFontMetrics();
         Rectangle2D all = pages[0].getBounds2D().createUnion(b);
         g.drawString(s, (float)(all.getCenterX() - (double)fm.stringWidth(s) / 2.0), (float)all.getCenterY());
      }
   }

   public static Color uvGlow() {
      return new Color(120, 255, 210);
   }
}
