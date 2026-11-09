package ru.realdocs.client.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Rectangle2D.Double;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Random;

public final class Paper {
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

   private Paper() {
   }

   public static void grain(Graphics2D g, double w, double h, long seed) {
      Random r = new Random(seed ^ 25214903917L);
      int dots = (int)(w * h / 260.0);

      for (int i = 0; i < dots; i++) {
         double x = r.nextDouble() * w;
         double y = r.nextDouble() * h;
         g.setColor(r.nextBoolean() ? new Color(0, 0, 0, 7 + r.nextInt(8)) : new Color(255, 255, 255, 10 + r.nextInt(10)));
         g.fill(new Double(x, y, 0.8, 0.8));
      }

      g.setStroke(new BasicStroke(0.35F));
      int fibers = (int)(w * h / 5200.0);

      for (int i = 0; i < fibers; i++) {
         double x = r.nextDouble() * w;
         double y = r.nextDouble() * h;
         double a = r.nextDouble() * Math.PI;
         double len = 3.0 + r.nextDouble() * 7.0;
         g.setColor(new Color(110, 95, 70, 14 + r.nextInt(14)));
         g.draw(
            new java.awt.geom.QuadCurve2D.Double(
               x, y, x + Math.cos(a + 0.6) * len / 2.0, y + Math.sin(a + 0.6) * len / 2.0, x + Math.cos(a) * len, y + Math.sin(a) * len
            )
         );
      }

      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Double(w * 0.25, h * 0.15),
            (float)Math.max(w, h),
            new float[]{0.0F, 1.0F},
            new Color[]{new Color(255, 250, 235, 22), new Color(0, 0, 0, 18)}
         )
      );
      g.fill(new Double(0.0, 0.0, w, h));
   }

   public static int ageDays(String date) {
      if (date != null && !date.isEmpty()) {
         try {
            return (int)Math.max(0L, ChronoUnit.DAYS.between(LocalDate.parse(date, DATE), LocalDate.now()));
         } catch (Exception var2) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static void wear(Graphics2D g, Shape sheet, int days, long seed, boolean folds) {
      if (days > 0) {
         Rectangle2D b = sheet.getBounds2D();
         Shape old = g.getClip();
         g.clip(sheet);
         Random r = new Random(seed * 31L + 7L);
         float age = Math.min(1.0F, (float)days / 90.0F);
         g.setColor(new Color(214, 180, 96, (int)(10.0F + 34.0F * age)));
         g.fill(b);

         for (int i = 0; i < 2 + (int)(age * 4.0F); i++) {
            double x = r.nextBoolean() ? b.getX() + r.nextDouble() * 40.0 : b.getMaxX() - r.nextDouble() * 40.0;
            double y = b.getY() + r.nextDouble() * b.getHeight();
            double rad = 20.0 + r.nextDouble() * 50.0;
            g.setPaint(
               new RadialGradientPaint(
                  new java.awt.geom.Point2D.Double(x, y),
                  (float)rad,
                  new float[]{0.0F, 1.0F},
                  new Color[]{new Color(150, 115, 60, (int)(8.0F + 20.0F * age)), new Color(150, 115, 60, 0)}
               )
            );
            g.fill(new java.awt.geom.Ellipse2D.Double(x - rad, y - rad, rad * 2.0, rad * 2.0));
         }

         if (folds && days >= 7) {
            fold(g, b, true, r, age);
         }

         if (folds && days >= 30) {
            fold(g, b, false, r, age);
         }

         if (days >= 30) {
            double[][] corners = new double[][]{{b.getX(), b.getY()}, {b.getMaxX(), b.getY()}, {b.getX(), b.getMaxY()}, {b.getMaxX(), b.getMaxY()}};

            for (double[] c : corners) {
               double rad = (double)(24.0F + 30.0F * age);
               g.setPaint(
                  new RadialGradientPaint(
                     new java.awt.geom.Point2D.Double(c[0], c[1]),
                     (float)rad,
                     new float[]{0.0F, 1.0F},
                     new Color[]{new Color(120, 95, 60, (int)(30.0F * age + 10.0F)), new Color(120, 95, 60, 0)}
                  )
               );
               g.fill(new java.awt.geom.Ellipse2D.Double(c[0] - rad, c[1] - rad, rad * 2.0, rad * 2.0));
            }
         }

         g.setClip(old);
      }
   }

   private static void fold(Graphics2D g, Rectangle2D b, boolean horizontal, Random r, float age) {
      double jitter = (r.nextDouble() - 0.5) * 6.0;
      if (horizontal) {
         double y = b.getY() + b.getHeight() * (0.33 + r.nextDouble() * 0.02) + jitter;
         g.setPaint(new GradientPaint(0.0F, (float)y, new Color(0, 0, 0, (int)(14.0F + 18.0F * age)), 0.0F, (float)(y + 7.0), new Color(0, 0, 0, 0)));
         g.fill(new Double(b.getX(), y, b.getWidth(), 7.0));
         g.setColor(new Color(255, 255, 255, (int)(40.0F + 40.0F * age)));
         g.setStroke(new BasicStroke(0.8F));
         g.draw(new java.awt.geom.Line2D.Double(b.getX(), y - 0.5, b.getMaxX(), y - 0.5 + jitter * 0.3));
      } else {
         double x = b.getCenterX() + jitter;
         g.setPaint(new GradientPaint((float)x, 0.0F, new Color(0, 0, 0, (int)(12.0F + 16.0F * age)), (float)(x + 6.0), 0.0F, new Color(0, 0, 0, 0)));
         g.fill(new Double(x, b.getY(), 6.0, b.getHeight()));
         g.setColor(new Color(255, 255, 255, (int)(36.0F + 40.0F * age)));
         g.setStroke(new BasicStroke(0.8F));
         g.draw(new java.awt.geom.Line2D.Double(x - 0.5, b.getY(), x - 0.5 + jitter * 0.3, b.getMaxY()));
      }
   }
}
