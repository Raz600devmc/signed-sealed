package ru.realdocs.client.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D.Double;

public final class Emblems {
   public static final Color GOLD = new Color(226, 192, 112);

   private Emblems() {
   }

   public static void draw(Graphics2D g, int code, String flag, double x, double y, double size, Color metal, boolean mono) {
      if (code != 0) {
         double cx = x + size / 2.0;
         double cy = y + size / 2.0;
         double r = size / 2.0;
         AffineTransform old = g.getTransform();
         Shape oldClip = g.getClip();
         g.setColor(metal);
         g.setStroke(new BasicStroke((float)(r * 0.035)));
         g.draw(new Double(cx - r * 0.98, cy - r * 0.98, r * 1.96, r * 1.96));
         g.setStroke(new BasicStroke((float)(r * 0.012)));
         g.draw(new Double(cx - r * 0.9, cy - r * 0.9, r * 1.8, r * 1.8));

         for (int i = 0; i < 48; i++) {
            double a = (double)i * Math.PI * 2.0 / 48.0;
            double px = cx + Math.cos(a) * r * 0.94;
            double py = cy + Math.sin(a) * r * 0.94;
            g.fill(new Double(px - r * 0.012, py - r * 0.012, r * 0.024, r * 0.024));
         }

         laurel(g, cx, cy, r * 0.8, metal);
         switch (code) {
            case 2:
               star(g, cx, cy - r * 0.02, r * 0.42, metal);
               break;
            case 3:
               crown(g, cx, cy, r * 0.46, metal);
               break;
            default:
               Shape shield = shield(cx, cy + r * 0.02, r * 0.4, r * 0.5);
               if (mono) {
                  g.setColor(metal);
                  g.setStroke(new BasicStroke((float)(r * 0.03)));
                  g.draw(shield);
                  g.setClip(shield);
                  g.setColor(new Color(metal.getRed(), metal.getGreen(), metal.getBlue(), 90));
                  flagPattern(g, flag, shield.getBounds2D(), true, metal);
               } else {
                  g.setClip(shield);
                  flagPattern(g, flag, shield.getBounds2D(), false, metal);
                  g.setClip(oldClip);
                  g.setColor(metal);
                  g.setStroke(new BasicStroke((float)(r * 0.035)));
                  g.draw(shield);
               }

               g.setClip(oldClip);
               star(g, cx, cy - r * 0.56, r * 0.07, metal);
         }

         g.setTransform(old);
         g.setClip(oldClip);
      }
   }

   static Shape shield(double cx, double cy, double hw, double hh) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      double top = cy - hh;
      double bottom = cy + hh;
      p.moveTo(cx - hw, top);
      p.lineTo(cx + hw, top);
      p.lineTo(cx + hw, cy + hh * 0.15);
      p.curveTo(cx + hw, cy + hh * 0.7, cx + hw * 0.35, bottom - hh * 0.1, cx, bottom);
      p.curveTo(cx - hw * 0.35, bottom - hh * 0.1, cx - hw, cy + hh * 0.7, cx - hw, cy + hh * 0.15);
      p.closePath();
      return p;
   }

   static void laurel(Graphics2D g, double cx, double cy, double r, Color c) {
      g.setColor(c);
      g.setStroke(new BasicStroke((float)(r * 0.022), 1, 1));

      for (int side = -1; side <= 1; side += 2) {
         double a0 = Math.toRadians(side < 0 ? 102.0 : 78.0);
         double a1 = Math.toRadians(side < 0 ? 228.0 : -48.0);
         java.awt.geom.Path2D.Double stem = new java.awt.geom.Path2D.Double();
         int n = 24;

         for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * (double)i / (double)n;
            double px = cx + Math.cos(a) * r;
            double py = cy + Math.sin(a) * r;
            if (i == 0) {
               stem.moveTo(px, py);
            } else {
               stem.lineTo(px, py);
            }
         }

         g.draw(stem);
         int leaves = 8;

         for (int ix = 0; ix < leaves; ix++) {
            double t = ((double)ix + 0.6) / ((double)leaves + 0.4);
            double a = a0 + (a1 - a0) * t;
            double px = cx + Math.cos(a) * r;
            double py = cy + Math.sin(a) * r;
            double da = Math.signum(a1 - a0);
            double dx = -Math.sin(a) * da;
            double dy = Math.cos(a) * da;
            double rot = Math.atan2(dy, dx) + (Math.PI / 2);
            double size = r * (0.15 - 0.05 * t);

            for (int k = -1; k <= 1; k += 2) {
               AffineTransform old = g.getTransform();
               g.translate(px, py);
               g.rotate(rot + (double)k * 0.62);
               g.fill(new Double(-size * 0.28, -size, size * 0.56, size));
               g.setTransform(old);
            }
         }

         double px = cx + Math.cos(a1) * r;
         double py = cy + Math.sin(a1) * r;
         double da = Math.signum(a1 - a0);
         double rot = Math.atan2(Math.cos(a1) * da, -Math.sin(a1) * da) + (Math.PI / 2);
         AffineTransform old = g.getTransform();
         g.translate(px, py);
         g.rotate(rot);
         g.fill(new Double(-r * 0.03, -r * 0.11, r * 0.06, r * 0.11));
         g.setTransform(old);
      }
   }

   static void star(Graphics2D g, double cx, double cy, double r, Color c) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

      for (int i = 0; i < 10; i++) {
         double a = (-Math.PI / 2) + (double)i * Math.PI / 5.0;
         double rad = i % 2 == 0 ? r : r * 0.42;
         double x = cx + rad * Math.cos(a);
         double y = cy + rad * Math.sin(a);
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            p.lineTo(x, y);
         }
      }

      p.closePath();
      g.setColor(c);
      g.fill(p);
   }

   static void crown(Graphics2D g, double cx, double cy, double r, Color c) {
      g.setColor(c);
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      double w = r * 1.6;
      double h = r * 1.0;
      double x0 = cx - w / 2.0;
      double y0 = cy - h / 2.0;
      p.moveTo(x0, y0 + h);
      p.lineTo(x0, y0 + h * 0.25);
      p.lineTo(x0 + w * 0.22, y0 + h * 0.55);
      p.lineTo(x0 + w * 0.5, y0);
      p.lineTo(x0 + w * 0.78, y0 + h * 0.55);
      p.lineTo(x0 + w, y0 + h * 0.25);
      p.lineTo(x0 + w, y0 + h);
      p.closePath();
      g.fill(p);
      g.fill(new java.awt.geom.Rectangle2D.Double(x0 - w * 0.04, y0 + h * 1.05, w * 1.08, h * 0.16));

      for (double fx : new double[]{0.0, 0.5, 1.0}) {
         double bx = x0 + w * fx;
         double by = fx == 0.5 ? y0 : y0 + h * 0.25;
         g.fill(new Double(bx - r * 0.09, by - r * 0.16, r * 0.18, r * 0.18));
      }
   }

   private static Color col(String hex) {
      try {
         return new Color(Integer.parseInt(hex.trim(), 16));
      } catch (Exception var2) {
         return Color.GRAY;
      }
   }

   static void flagPattern(Graphics2D g, String spec, Rectangle2D b, boolean mono, Color monoColor) {
      String type = "h";
      String[] cs = new String[]{"1F3A93", "FFFFFF", "B3261E"};
      if (spec != null && spec.contains(":")) {
         type = spec.substring(0, spec.indexOf(58));
         cs = spec.substring(spec.indexOf(58) + 1).split(",");
      }

      Color[] c = new Color[cs.length];

      for (int i = 0; i < cs.length; i++) {
         Color k = col(cs[i]);
         if (mono) {
            float lum = (0.3F * (float)k.getRed() + 0.59F * (float)k.getGreen() + 0.11F * (float)k.getBlue()) / 255.0F;
            int a = (int)(40.0F + (1.0F - lum) * 150.0F);
            k = new Color(monoColor.getRed(), monoColor.getGreen(), monoColor.getBlue(), Math.min(255, a));
         }

         c[i] = k;
      }

      double x = b.getX();
      double y = b.getY();
      double w = b.getWidth();
      double h = b.getHeight();
      switch (type) {
         case "v":
            for (int ix = 0; ix < c.length; ix++) {
               g.setColor(c[ix]);
               g.fill(new java.awt.geom.Rectangle2D.Double(x + w * (double)ix / (double)c.length, y, w / (double)c.length + 0.5, h));
            }
            break;
         case "nordic":
            g.setColor(c[0]);
            g.fill(b);
            g.setColor(c[1]);
            double t = w * 0.2;
            g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.32 - t / 2.0, y, t, h));
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * 0.42 - t / 2.0, w, t));
            if (c.length > 2) {
               g.setColor(c[2]);
               double t2 = t * 0.5;
               g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.32 - t2 / 2.0, y, t2, h));
               g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * 0.42 - t2 / 2.0, w, t2));
            }
            break;
         case "cross":
            g.setColor(c[0]);
            g.fill(b);
            g.setColor(c[1]);
            g.setStroke(new BasicStroke((float)(w * 0.22)));
            g.draw(new java.awt.geom.Line2D.Double(x, y, x + w, y + h));
            g.draw(new java.awt.geom.Line2D.Double(x + w, y, x, y + h));
            g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.38, y, w * 0.24, h));
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * 0.38, w, h * 0.24));
            g.setColor(c.length > 2 ? c[2] : c[1]);
            g.setStroke(new BasicStroke((float)(w * 0.07)));
            g.draw(new java.awt.geom.Line2D.Double(x, y, x + w, y + h));
            g.draw(new java.awt.geom.Line2D.Double(x + w, y, x, y + h));
            g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.43, y, w * 0.14, h));
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * 0.43, w, h * 0.14));
            break;
         case "crescent": {
            g.setColor(c[0]);
            g.fill(b);
            Color s = c.length > 1 ? c[1] : Color.WHITE;
            double rr = w * 0.3;
            double mx = x + w * 0.42;
            double my = y + h * 0.42;
            Area moon = new Area(new Double(mx - rr, my - rr, rr * 2.0, rr * 2.0));
            double r2 = rr * 0.8;
            moon.subtract(new Area(new Double(mx - r2 + rr * 0.25, my - r2, r2 * 2.0, r2 * 2.0)));
            g.setColor(s);
            g.fill(moon);
            java.awt.geom.Path2D.Double st = new java.awt.geom.Path2D.Double();
            double sx = mx + rr * 0.75;
            double sy = my;
            double sr = rr * 0.42;

            for (int i = 0; i < 10; i++) {
               double ax = (double)i * Math.PI / 5.0;
               double rad = i % 2 == 0 ? sr : sr * 0.42;
               double px = sx + rad * Math.cos(ax);
               double py = sy + rad * Math.sin(ax);
               if (i == 0) {
                  st.moveTo(px, py);
               } else {
                  st.lineTo(px, py);
               }
            }

            st.closePath();
            g.fill(st);
            break;
         }
         case "swiss": {
            g.setColor(c[0]);
            g.fill(b);
            g.setColor(c.length > 1 ? c[1] : Color.WHITE);
            double s = Math.min(w, h) * 0.6;
            double a = s / 3.2;
            double mx = x + w / 2.0;
            double my = y + h * 0.45;
            g.fill(new java.awt.geom.Rectangle2D.Double(mx - a / 2.0, my - s / 2.0, a, s));
            g.fill(new java.awt.geom.Rectangle2D.Double(mx - s / 2.0, my - a / 2.0, s, a));
            break;
         }
         case "czech":
            g.setColor(c[0]);
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h / 2.0));
            g.setColor(c.length > 1 ? c[1] : Color.RED);
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h / 2.0, w, h / 2.0 + 1.0));
            java.awt.geom.Path2D.Double tri = new java.awt.geom.Path2D.Double();
            tri.moveTo(x, y);
            tri.lineTo(x + w * 0.5, y + h / 2.0);
            tri.lineTo(x, y + h);
            tri.closePath();
            g.setColor(c.length > 2 ? c[2] : Color.BLUE);
            g.fill(tri);
            break;
         case "greek":
            Color blue = c[0];
            Color white = c.length > 1 ? c[1] : Color.WHITE;

            for (int i = 0; i < 9; i++) {
               g.setColor(i % 2 == 0 ? blue : white);
               g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * (double)i / 9.0, w, h / 9.0 + 0.5));
            }

            g.setColor(blue);
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y, w * 0.45, h * 5.0 / 9.0));
            g.setColor(white);
            g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.18, y, w * 0.09, h * 5.0 / 9.0));
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * 2.0 / 9.0, w * 0.45, h / 9.0));
            break;
         default:
            for (int i = 0; i < c.length; i++) {
               g.setColor(c[i]);
               g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h * (double)i / (double)c.length, w, h / (double)c.length + 0.5));
            }
      }
   }
}
