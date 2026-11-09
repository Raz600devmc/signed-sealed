package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import ru.realdocs.L;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.PassportTemplate;

public final class DesignRenderer {
   public static boolean placeholders;
   public static final Color GOLD_LIGHT = new Color(246, 222, 152);
   public static final Color GOLD_DARK = new Color(176, 136, 62);
   public static final Color SILVER_LIGHT = new Color(240, 243, 247);
   public static final Color SILVER_DARK = new Color(148, 154, 166);
   private static Map<String, String> tokens;
   private static final Map<BufferedImage, Map<Integer, BufferedImage>> TINT = new WeakHashMap<>();

   private DesignRenderer() {
   }

   static Color solid(DesignElement e, Color base) {
      return switch (e.ink) {
         case 1 -> new Color(226, 192, 112);
         case 2 -> new Color(208, 213, 221);
         case 3 -> darker(base, 0.32F);
         default -> new Color(e.color & 16777215);
      };
   }

   static Paint paint(DesignElement e, Color base) {
      return (Paint)(switch (e.ink) {
         case 1 -> new GradientPaint((float)e.x, (float)e.y, GOLD_LIGHT, (float)(e.x + e.w), (float)(e.y + e.h), GOLD_DARK);
         case 2 -> new GradientPaint((float)e.x, (float)e.y, SILVER_LIGHT, (float)(e.x + e.w), (float)(e.y + e.h), SILVER_DARK);
         default -> solid(e, base);
      });
   }

   public static void draw(Graphics2D g, List<DesignElement> els, PassportTemplate t, BufferedImage emblem, Color base, boolean emboss) {
      draw(g, els, t, emblem, base, emboss, null);
   }

   public static void draw(Graphics2D g, List<DesignElement> els, PassportTemplate t, BufferedImage emblem, Color base, boolean emboss, Map<String, String> tok) {
      Map<String, String> prev = tokens;
      tokens = tok;

      try {
         drawAll(g, els, t, emblem, base, emboss);
      } finally {
         tokens = prev;
      }
   }

   public static String expand(String text, Map<String, String> tok) {
      if (tok != null && text.indexOf(123) >= 0) {
         StringBuilder sb = new StringBuilder();
         int i = 0;

         while (i < text.length()) {
            int a = text.indexOf(123, i);
            int z = a < 0 ? -1 : text.indexOf(125, a);
            if (a < 0 || z < 0) {
               sb.append(text, i, text.length());
               break;
            }

            sb.append(text, i, a);
            String key = text.substring(a + 1, z);
            String v = tok.get(key);
            sb.append(v == null ? text.substring(a, z + 1) : v);
            i = z + 1;
         }

         return sb.toString();
      } else {
         return text;
      }
   }

   private static void drawAll(Graphics2D g, List<DesignElement> els, PassportTemplate t, BufferedImage emblem, Color base, boolean emboss) {
      for (DesignElement e : els) {
         Composite oc = g.getComposite();
         float a = (float)e.alpha / 100.0F;
         boolean metal = e.ink != 0;
         if (emboss && metal) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.35F * a));
            g.translate(0.8, 1.0);
            drawOne(g, e, t, emblem, base, true);
            g.translate(-0.8, -1.0);
         }

         if (e.ink == 3 && emboss) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.25F * a));
            g.translate(-0.6, -0.7);
            drawOne(g, e, t, emblem, lighter(base, 0.25F), false);
            g.translate(0.6, 0.7);
         }

         g.setComposite(AlphaComposite.SrcOver.derive(a));
         drawOne(g, e, t, emblem, base, false);
         g.setComposite(oc);
      }
   }

   private static void drawOne(Graphics2D g, DesignElement e, PassportTemplate t, BufferedImage emblem, Color base, boolean shadow) {
      Paint p = (Paint)(shadow ? Color.BLACK : paint(e, base));
      Color solid = shadow ? Color.BLACK : solid(e, base);
      switch (e.type) {
         case 0:
            text(g, e, p);
            break;
         case 1:
            if (emblem == null) {
               if (!shadow && placeholders) {
                  placeholder(g, e);
               }

               return;
            }

            BufferedImage img = e.ink == 0 ? emblem : tinted(emblem, e.ink, solid, shadow);
            double s = Math.min((double)e.w / (double)emblem.getWidth(), (double)e.h / (double)emblem.getHeight());
            double iw = (double)emblem.getWidth() * s;
            double ih = (double)emblem.getHeight() * s;
            g.drawImage(
               img,
               (int)Math.round((double)e.x + ((double)e.w - iw) / 2.0),
               (int)Math.round((double)e.y + ((double)e.h - ih) / 2.0),
               (int)Math.round(iw),
               (int)Math.round(ih),
               null
            );
            break;
         case 2:
            int code = t.emblemCode == 0 ? 1 : t.emblemCode;
            double size = (double)Math.min(e.w, e.h);
            Emblems.draw(g, code, t.flag, (double)e.x + ((double)e.w - size) / 2.0, (double)e.y + ((double)e.h - size) / 2.0, size, solid, shadow || e.ink != 0);
            break;
         case 3:
            g.setPaint(p);
            g.fill(new Double((double)e.x, (double)e.y, (double)e.w, (double)Math.max(1, e.h)));
            break;
         case 4:
            g.setPaint(p);
            g.setStroke(new BasicStroke((float)Math.max(0.8, (double)e.size / 8.0)));
            double r = Math.min(14.0, (double)Math.min(e.w, e.h) / 6.0);
            g.draw(new java.awt.geom.RoundRectangle2D.Double((double)e.x, (double)e.y, (double)e.w, (double)e.h, r, r));
            break;
         case 5:
            g.setPaint(p);
            g.fill(new Double((double)e.x, (double)e.y, (double)e.w, (double)e.h));
      }
   }

   private static void placeholder(Graphics2D g, DesignElement e) {
      g.setColor(new Color(255, 255, 255, 70));
      g.setStroke(new BasicStroke(1.0F, 0, 0, 10.0F, new float[]{4.0F, 3.0F}, 0.0F));
      g.draw(new Double((double)e.x, (double)e.y, (double)e.w, (double)e.h));
      g.setFont(new Font("SansSerif", 0, 9));
      FontMetrics fm = g.getFontMetrics();
      String s = L.t("эмблема не загружена");
      g.drawString(s, (float)((double)e.x + (double)(e.w - fm.stringWidth(s)) / 2.0), (float)((double)e.y + (double)e.h / 2.0 + 3.0));
   }

   private static Font font(float size, float tracking, boolean bold) {
      Map<TextAttribute, Object> a = new HashMap<>();
      a.put(TextAttribute.FAMILY, "Serif");
      a.put(TextAttribute.SIZE, size);
      a.put(TextAttribute.TRACKING, tracking);
      if (bold) {
         a.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
      }

      return Font.getFont(a);
   }

   private static void text(Graphics2D g, DesignElement e, Paint p) {
      String txt = expand(e.text, tokens);
      if (!txt.isBlank()) {
         String[] lines = txt.split("\\|");
         float size = (float)e.size;

         while (true) {
            Font f = font(size, (float)e.spacing / 100.0F, e.bold);
            FontMetrics fm = g.getFontMetrics(f);
            int widest = 0;

            for (String l : lines) {
               widest = Math.max(widest, fm.stringWidth(l.trim()));
            }

            if (widest <= e.w || size <= 5.0F) {
               g.setFont(f);
               g.setPaint(p);
               fm = g.getFontMetrics();
               double lineH = (double)size * 1.18;
               double total = lineH * (double)lines.length;
               double baseline = (double)e.y + ((double)e.h - total) / 2.0 + (double)size * 0.88;

               for (String raw : lines) {
                  String l = raw.trim();
                  int lw = fm.stringWidth(l);
                  double x = e.align == 0 ? (double)e.x : (e.align == 2 ? (double)(e.x + e.w - lw) : (double)e.x + (double)(e.w - lw) / 2.0);
                  g.drawString(l, (float)x, (float)baseline);
                  baseline += lineH;
               }

               return;
            }

            size -= 0.5F;
         }
      }
   }

   static BufferedImage tinted(BufferedImage src, int ink, Color solid, boolean shadow) {
      int key = shadow ? -1 : ink * 16777216 + (solid.getRGB() & 16777215);
      Map<Integer, BufferedImage> m = TINT.computeIfAbsent(src, kx -> new HashMap<>());
      BufferedImage c = m.get(key);
      if (c != null) {
         return c;
      } else {
         int w = src.getWidth();
         int h = src.getHeight();
         BufferedImage out = new BufferedImage(w, h, 2);
         Color lightC = ink == 2 ? SILVER_LIGHT : GOLD_LIGHT;
         Color darkC = ink == 2 ? SILVER_DARK : GOLD_DARK;

         for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
               int v = src.getRGB(x, y);
               int a = v >>> 24;
               if (a != 0) {
                  if (shadow) {
                     out.setRGB(x, y, a << 24);
                  } else {
                     double lum = (0.3 * (double)(v >> 16 & 0xFF) + 0.59 * (double)(v >> 8 & 0xFF) + 0.11 * (double)(v & 0xFF)) / 255.0;
                     int r;
                     int gg;
                     int b;
                     if (ink != 1 && ink != 2) {
                        double k = 0.75 + 0.25 * lum;
                        r = (int)((double)solid.getRed() * k);
                        gg = (int)((double)solid.getGreen() * k);
                        b = (int)((double)solid.getBlue() * k);
                     } else {
                        double t = ((double)x / (double)w + (double)y / (double)h) / 2.0;
                        double sheen = 0.5 + 0.5 * Math.sin(t * Math.PI * 2.2);
                        double k = Math.min(1.0, 0.35 + 0.55 * lum + 0.2 * sheen);
                        r = (int)((double)darkC.getRed() + (double)(lightC.getRed() - darkC.getRed()) * k);
                        gg = (int)((double)darkC.getGreen() + (double)(lightC.getGreen() - darkC.getGreen()) * k);
                        b = (int)((double)darkC.getBlue() + (double)(lightC.getBlue() - darkC.getBlue()) * k);
                     }

                     out.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
                  }
               }
            }
         }

         m.put(key, out);
         return out;
      }
   }

   public static void texture(Graphics2D g, int kind, int w, int h, long seed) {
      Random rnd = new Random(seed);
      switch (kind) {
         case 0:
            for (int i = 0; i < 9000; i++) {
               double x = rnd.nextDouble() * (double)w;
               double y = rnd.nextDouble() * (double)h;
               g.setColor(rnd.nextBoolean() ? new Color(0, 0, 0, 18) : new Color(255, 255, 255, 10));
               g.fill(new Double(x, y, 0.9, 0.9));
            }
            break;
         case 2:
            g.setStroke(new BasicStroke(0.35F));

            for (double x = 0.0; x < (double)w; x++) {
               g.setColor(new Color(0, 0, 0, 10 + rnd.nextInt(10)));
               g.draw(new java.awt.geom.Line2D.Double(x, 0.0, x, (double)h));
            }

            for (double y = 0.0; y < (double)h; y++) {
               g.setColor(new Color(255, 255, 255, 6 + rnd.nextInt(8)));
               g.draw(new java.awt.geom.Line2D.Double(0.0, y, (double)w, y));
            }
      }
   }

   public static void frame(Graphics2D g, int kind, int w, int h) {
      if (kind != 0) {
         Paint foil = new GradientPaint(0.0F, 0.0F, GOLD_LIGHT, (float)w, (float)h, GOLD_DARK);
         g.setPaint(foil);
         g.setStroke(new BasicStroke(1.6F));
         g.draw(new java.awt.geom.RoundRectangle2D.Double(16.0, 16.0, (double)(w - 32), (double)(h - 32), 10.0, 10.0));
         if (kind >= 2) {
            g.setStroke(new BasicStroke(0.7F));
            g.draw(new java.awt.geom.RoundRectangle2D.Double(21.0, 21.0, (double)(w - 42), (double)(h - 42), 7.0, 7.0));
         }

         if (kind == 3) {
            double[][] corners = new double[][]{
               {16.0, 16.0, 1.0, 1.0},
               {(double)(w - 16), 16.0, -1.0, 1.0},
               {16.0, (double)(h - 16), 1.0, -1.0},
               {(double)(w - 16), (double)(h - 16), -1.0, -1.0}
            };

            for (double[] c : corners) {
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
               double x = c[0];
               double y = c[1];
               double sx = c[2];
               double sy = c[3];
               p.moveTo(x + sx * 4.0, y + sy * 26.0);
               p.curveTo(x + sx * 4.0, y + sy * 12.0, x + sx * 12.0, y + sy * 4.0, x + sx * 26.0, y + sy * 4.0);
               p.moveTo(x + sx * 9.0, y + sy * 20.0);
               p.curveTo(x + sx * 9.0, y + sy * 13.0, x + sx * 13.0, y + sy * 9.0, x + sx * 20.0, y + sy * 9.0);
               g.setStroke(new BasicStroke(0.9F));
               g.draw(p);
               java.awt.geom.Path2D.Double d = new java.awt.geom.Path2D.Double();
               d.moveTo(x + sx * 14.0, y + sy * 10.0);
               d.lineTo(x + sx * 18.0, y + sy * 14.0);
               d.lineTo(x + sx * 14.0, y + sy * 18.0);
               d.lineTo(x + sx * 10.0, y + sy * 14.0);
               d.closePath();
               g.fill(d);
            }

            for (double[] m : new double[][]{{(double)w / 2.0, 16.0}, {(double)w / 2.0, (double)(h - 16)}}) {
               java.awt.geom.Path2D.Double d = new java.awt.geom.Path2D.Double();
               d.moveTo(m[0] - 8.0, m[1]);
               d.lineTo(m[0], m[1] - 3.0);
               d.lineTo(m[0] + 8.0, m[1]);
               d.lineTo(m[0], m[1] + 3.0);
               d.closePath();
               g.fill(d);
            }
         }
      }
   }

   static Color darker(Color c, float t) {
      return new Color((int)((float)c.getRed() * (1.0F - t)), (int)((float)c.getGreen() * (1.0F - t)), (int)((float)c.getBlue() * (1.0F - t)));
   }

   static Color lighter(Color c, float t) {
      return new Color(
         (int)((float)c.getRed() + (float)(255 - c.getRed()) * t),
         (int)((float)c.getGreen() + (float)(255 - c.getGreen()) * t),
         (int)((float)c.getBlue() + (float)(255 - c.getBlue()) * t)
      );
   }
}
