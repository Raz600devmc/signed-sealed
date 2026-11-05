package ru.realdocs.client.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ru.realdocs.L;
import ru.realdocs.model.FolderModel;
import ru.realdocs.model.MailModel;
import ru.realdocs.model.StampModel;

/** Envelope front/back with a wax seal, the letter inside, and folder covers. */
public final class MailRenderer {
   public static final int EW = 620;
   public static final int EH = 340;
   public static final int LW = 620;
   public static final int LH = 877;
   public static final int FW = 460;
   public static final int FH = 620;
   private static final Color ENVELOPE = new Color(239, 228, 205);
   private static final Color INK = new Color(30, 34, 58);

   private MailRenderer() {
   }

   private static Graphics2D start(BufferedImage img, double k) {
      Graphics2D g = img.createGraphics();
      StampRenderer.hints(g);
      Fonts.hints(g);
      g.scale(k, k);
      return g;
   }

   private static long seed(MailModel m) {
      return (long)m.id.hashCode() * 977L + 13L;
   }

   public static BufferedImage front(MailModel m, int px) {
      double k = (double)px / EW;
      BufferedImage img = new BufferedImage(px, (int)Math.round(EH * k), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = start(img, k);
      g.setColor(ENVELOPE);
      g.fillRect(0, 0, EW, EH);
      Paper.grain(g, EW, EH, seed(m));
      g.setColor(new Color(0, 0, 0, 22));
      g.setStroke(new BasicStroke(1.2F));
      g.draw(new Rectangle2D.Double(1.0, 1.0, EW - 2, EH - 2));
      Fonts.Face small = Fonts.face(Fonts.SERIF, false, true, 14.0F);
      Fonts.Face hand = Fonts.face(m.font, false, false, 22.0F);
      g.setColor(new Color(90, 84, 70));
      small.draw(g, L.t("От кого:"), 28.0, 40.0);
      g.setColor(INK);
      hand.draw(g, m.from.isEmpty() ? "" : m.from, 28.0, 68.0);
      drawPostage(g, EW - 110, 22, seed(m));
      if (!m.postmark.isEmpty()) {
         drawPostmark(g, EW - 150, 70, m.postmark, seed(m));
      }

      double ax = 250.0;
      double ay = 190.0;
      g.setColor(new Color(90, 84, 70));
      small.draw(g, L.t("Кому:"), ax, ay - 10.0);
      g.setColor(new Color(120, 110, 90, 120));
      g.setStroke(new BasicStroke(1.0F));

      for (int i = 0; i < 3; i++) {
         g.draw(new java.awt.geom.Line2D.Double(ax, ay + 36.0 + i * 38.0, EW - 30.0, ay + 36.0 + i * 38.0));
      }

      Fonts.Face big = Fonts.face(m.font, false, false, 34.0F);
      String to = m.to;

      while (big.width(to) > EW - 30.0 - ax - 10.0 && big.size() > 14.0F) {
         big = Fonts.face(m.font, false, false, big.size() - 2.0F);
      }

      g.setColor(INK);
      big.draw(g, to, ax + 6.0, ay + 30.0);
      if (m.sealed()) {
         Fonts.Face s = Fonts.face(Fonts.SANS, true, false, 12.0F);
         g.setColor(new Color(150, 40, 30, 170));
         s.draw(g, L.t("ЛИЧНО В РУКИ"), 28.0, EH - 28.0);
      }

      g.dispose();
      return img;
   }

   private static void drawPostage(Graphics2D g, double x, double y, long seed) {
      double w = 82.0;
      double h = 98.0;
      g.setColor(new Color(0, 0, 0, 40));
      g.fill(new Rectangle2D.Double(x + 2.0, y + 3.0, w, h));
      g.setColor(new Color(250, 247, 238));
      g.fill(new Rectangle2D.Double(x, y, w, h));
      g.setColor(ENVELOPE);
      double r = 3.2;

      for (double t = 0.0; t <= w; t += 8.0) {
         g.fill(new Ellipse2D.Double(x + t - r, y - r, r * 2.0, r * 2.0));
         g.fill(new Ellipse2D.Double(x + t - r, y + h - r, r * 2.0, r * 2.0));
      }

      for (double t = 0.0; t <= h; t += 8.0) {
         g.fill(new Ellipse2D.Double(x - r, y + t - r, r * 2.0, r * 2.0));
         g.fill(new Ellipse2D.Double(x + w - r, y + t - r, r * 2.0, r * 2.0));
      }

      Color[] hues = new Color[]{new Color(156, 46, 46), new Color(46, 84, 150), new Color(52, 110, 64), new Color(120, 70, 140)};
      Color c = hues[(int)Math.floorMod(seed, (long)hues.length)];
      g.setPaint(new GradientPaint((float)x, (float)y, c.brighter(), (float)(x + w), (float)(y + h), c.darker()));
      g.fill(new Rectangle2D.Double(x + 8.0, y + 8.0, w - 16.0, h - 16.0));
      g.setColor(new Color(255, 245, 220, 220));
      Path2D.Double env = new Path2D.Double();
      double cx = x + w / 2.0;
      double cy = y + h / 2.0 - 6.0;
      env.append(new Rectangle2D.Double(cx - 20.0, cy - 13.0, 40.0, 26.0), false);
      g.setStroke(new BasicStroke(2.0F));
      g.draw(env);
      g.draw(new java.awt.geom.Line2D.Double(cx - 20.0, cy - 13.0, cx, cy + 2.0));
      g.draw(new java.awt.geom.Line2D.Double(cx + 20.0, cy - 13.0, cx, cy + 2.0));
      Fonts.Face f = Fonts.face(Fonts.SANS, true, false, 14.0F);
      f.draw(g, "5", x + w - 22.0, y + h - 14.0);
      Fonts.Face p = Fonts.face(Fonts.SANS, true, false, 9.0F);
      String post = L.t("ПОЧТА");
      p.draw(g, post, x + 12.0, y + h - 14.0);
   }

   private static void drawPostmark(Graphics2D g, double x, double y, String date, long seed) {
      Color ink = new Color(40, 40, 70, 150);
      g.setColor(ink);
      g.setStroke(new BasicStroke(1.6F));
      Random r = new Random(seed);
      AffineTransform old = g.getTransform();
      g.rotate(Math.toRadians(-8.0 + r.nextDouble() * 10.0), x + 40.0, y + 40.0);
      g.draw(new Ellipse2D.Double(x, y, 80.0, 80.0));
      g.draw(new Ellipse2D.Double(x + 8.0, y + 8.0, 64.0, 64.0));
      Fonts.Face f = Fonts.face(Fonts.TYPEWRITER, true, false, 11.0F);
      f.draw(g, date, x + 40.0 - f.width(date) / 2.0, y + 44.0);
      Fonts.Face s = Fonts.face(Fonts.SANS, true, false, 8.0F);
      String p = L.t("ПОЧТА");
      s.draw(g, p, x + 40.0 - s.width(p) / 2.0, y + 22.0);

      for (int i = 0; i < 4; i++) {
         Path2D.Double wave = new Path2D.Double();
         double wy = y + 18.0 + i * 13.0;
         wave.moveTo(x + 84.0, wy);

         for (double t = 0.0; t <= 110.0; t += 11.0) {
            wave.quadTo(x + 84.0 + t + 5.5, wy + (t / 11.0 % 2.0 == 0.0 ? -5.0 : 5.0), x + 84.0 + t + 11.0, wy);
         }

         g.draw(wave);
      }

      g.setTransform(old);
   }

   public static BufferedImage back(MailModel m, int px, DocRenderer.StampSource stamps) {
      double k = (double)px / EW;
      BufferedImage img = new BufferedImage(px, (int)Math.round(EH * k), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = start(img, k);
      g.setColor(ENVELOPE);
      g.fillRect(0, 0, EW, EH);
      Paper.grain(g, EW, EH, seed(m) + 7L);
      double tipY = EH * 0.58;
      Path2D.Double flap = new Path2D.Double();
      flap.moveTo(0.0, 0.0);
      flap.lineTo(EW, 0.0);
      flap.lineTo(EW / 2.0 + 30.0, tipY - 8.0);
      flap.quadTo(EW / 2.0, tipY + 6.0, EW / 2.0 - 30.0, tipY - 8.0);
      flap.closePath();
      g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(232, 219, 193), 0.0F, (float)tipY, new Color(222, 207, 178)));
      g.fill(flap);
      g.setColor(new Color(150, 130, 100, 110));
      g.setStroke(new BasicStroke(1.3F));
      g.draw(flap);
      g.setColor(new Color(150, 130, 100, 70));
      g.draw(new java.awt.geom.Line2D.Double(0.0, EH, EW * 0.42, EH * 0.5));
      g.draw(new java.awt.geom.Line2D.Double(EW, EH, EW * 0.58, EH * 0.5));
      if (m.sealed() || m.broken()) {
         drawSeal(g, m, EW / 2.0, tipY - 4.0, 50.0, stamps);
      }

      g.dispose();
      return img;
   }

   private static Path2D.Double blob(double cx, double cy, double r, long seed) {
      Random rnd = new Random(seed);
      Path2D.Double p = new Path2D.Double();
      int n = 22;
      double[] rr = new double[n];

      for (int i = 0; i < n; i++) {
         rr[i] = r * (0.9 + rnd.nextDouble() * 0.16);
      }

      for (int i = 0; i <= n; i++) {
         double a = Math.PI * 2.0 * i / n;
         double x = cx + Math.cos(a) * rr[i % n];
         double y = cy + Math.sin(a) * rr[i % n];
         if (i == 0) {
            p.moveTo(x, y);
         } else {
            double am = Math.PI * 2.0 * (i - 0.5) / n;
            double rm = (rr[i % n] + rr[i - 1]) / 2.0 * 1.04;
            p.quadTo(cx + Math.cos(am) * rm, cy + Math.sin(am) * rm, x, y);
         }
      }

      p.closePath();
      return p;
   }

   private static void drawSeal(Graphics2D g, MailModel m, double cx, double cy, double r, DocRenderer.StampSource stamps) {
      Color wax = new Color(m.waxRgb());
      long seed = seed(m) + 99L;
      AffineTransform old = g.getTransform();
      java.awt.Shape oldClip = g.getClip();
      boolean broken = m.broken();
      for (int half = 0; half < (broken ? 2 : 1); half++) {
         g.setTransform(old);
         g.setClip(oldClip);
         if (broken) {
            double dx = half == 0 ? -7.0 : 9.0;
            double dy = half == 0 ? 4.0 : -10.0;
            g.translate(dx, dy);
            g.rotate(Math.toRadians(half == 0 ? -6.0 : 9.0), cx, cy);
            Path2D.Double clipHalf = new Path2D.Double();
            if (half == 0) {
               clipHalf.moveTo(cx - r * 2.0, cy - r * 2.0);
               clipHalf.lineTo(cx - 6.0, cy - r * 2.0);
               clipHalf.lineTo(cx + 8.0, cy - r * 0.3);
               clipHalf.lineTo(cx - 5.0, cy + r * 0.2);
               clipHalf.lineTo(cx + 6.0, cy + r * 2.0);
               clipHalf.lineTo(cx - r * 2.0, cy + r * 2.0);
            } else {
               clipHalf.moveTo(cx + r * 2.0, cy - r * 2.0);
               clipHalf.lineTo(cx - 6.0, cy - r * 2.0);
               clipHalf.lineTo(cx + 8.0, cy - r * 0.3);
               clipHalf.lineTo(cx - 5.0, cy + r * 0.2);
               clipHalf.lineTo(cx + 6.0, cy + r * 2.0);
               clipHalf.lineTo(cx + r * 2.0, cy + r * 2.0);
            }

            clipHalf.closePath();
            g.clip(clipHalf);
         }

         Path2D.Double b = blob(cx, cy, r, seed);
         g.setColor(new Color(0, 0, 0, 60));
         g.translate(1.5, 2.5);
         g.fill(b);
         g.translate(-1.5, -2.5);
         g.setPaint(new RadialGradientPaint(
            new java.awt.geom.Point2D.Double(cx - r * 0.35, cy - r * 0.4), (float)(r * 1.5), new float[]{0.0F, 0.55F, 1.0F},
            new Color[]{DesignRenderer.lighter(wax, 0.35F), wax, DesignRenderer.darker(wax, 0.35F)}
         ));
         g.fill(b);
         double ir = r * 0.7;
         g.setColor(DesignRenderer.darker(wax, 0.22F));
         g.setStroke(new BasicStroke(2.0F));
         g.draw(new Ellipse2D.Double(cx - ir, cy - ir, ir * 2.0, ir * 2.0));
         g.setColor(DesignRenderer.lighter(wax, 0.25F));
         g.setStroke(new BasicStroke(1.0F));
         g.draw(new Ellipse2D.Double(cx - ir + 1.0, cy - ir + 1.2, ir * 2.0, ir * 2.0));
         BufferedImage impression = null;
         if (!m.sealId.isEmpty() && stamps != null) {
            StampModel sm = stamps.def(m.sealId);
            if (sm != null) {
               try {
                  impression = StampRenderer.render(sm, stamps.image(m.sealId), 220, null);
               } catch (RuntimeException var24) {
                  impression = null;
               }
            }
         }

         double side = ir * 1.75;
         if (impression != null) {
            BufferedImage dark = ImageFxTint.mask(impression, DesignRenderer.darker(wax, 0.4F));
            BufferedImage light = ImageFxTint.mask(impression, DesignRenderer.lighter(wax, 0.3F));
            double s = side / Math.max(impression.getWidth(), impression.getHeight());
            double iw = impression.getWidth() * s;
            double ih = impression.getHeight() * s;
            g.drawImage(light, (int)Math.round(cx - iw / 2.0 + 0.8), (int)Math.round(cy - ih / 2.0 + 0.9), (int)Math.round(iw), (int)Math.round(ih), null);
            g.drawImage(dark, (int)Math.round(cx - iw / 2.0), (int)Math.round(cy - ih / 2.0), (int)Math.round(iw), (int)Math.round(ih), null);
         } else {
            String letter = m.sealer.isEmpty() ? "✉" : m.sealer.substring(0, m.sealer.offsetByCodePoints(0, 1)).toUpperCase();
            Fonts.Face f = Fonts.face(Fonts.SCRIPT, false, false, (float)(ir * 1.25));
            double lw = f.width(letter);
            g.setColor(DesignRenderer.lighter(wax, 0.3F));
            f.draw(g, letter, cx - lw / 2.0 + 0.8, cy + ir * 0.42 + 0.9);
            g.setColor(DesignRenderer.darker(wax, 0.4F));
            f.draw(g, letter, cx - lw / 2.0, cy + ir * 0.42);
         }
      }

      g.setTransform(old);
      g.setClip(oldClip);
   }

   public static BufferedImage letter(MailModel m, int px) {
      double k = (double)px / LW;
      BufferedImage img = new BufferedImage(px, (int)Math.round(LH * k), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = start(img, k);
      g.setColor(new Color(250, 247, 239));
      g.fillRect(0, 0, LW, LH);
      Paper.grain(g, LW, LH, seed(m) + 3L);
      g.setColor(new Color(120, 150, 200, 60));
      g.setStroke(new BasicStroke(0.8F));
      double lineH = 32.0;

      for (double y = 110.0; y < LH - 40.0; y += lineH) {
         g.draw(new java.awt.geom.Line2D.Double(40.0, y, LW - 40.0, y));
      }

      g.setColor(new Color(200, 90, 90, 80));
      g.draw(new java.awt.geom.Line2D.Double(70.0, 30.0, 70.0, LH - 30.0));
      Fonts.Face f = Fonts.face(m.font, false, false, 21.0F);
      g.setColor(INK);
      if (!m.date.isEmpty()) {
         f.draw(g, m.date, LW - 50.0 - f.width(m.date), 70.0);
      }

      double y = 110.0 - 7.0;
      double maxW = LW - 80.0 - 50.0;

      for (String para : m.text.replace("\r", "").split("\n", -1)) {
         for (String line : wrap(f, para, maxW)) {
            if (y > LH - 60.0) {
               break;
            }

            f.draw(g, line, 80.0, y);
            y += lineH;
         }
      }

      if (!m.from.isEmpty()) {
         y = Math.min(LH - 60.0, y + lineH * 0.5);
         String sig = "— " + m.from;
         f.draw(g, sig, LW - 50.0 - f.width(sig), y);
      }

      g.dispose();
      return img;
   }

   static List<String> wrap(Fonts.Face f, String text, double max) {
      List<String> out = new ArrayList<>();
      if (text.isEmpty()) {
         out.add("");
         return out;
      } else {
         StringBuilder line = new StringBuilder();

         for (String w : text.split(" ")) {
            String cand = line.length() == 0 ? w : line + " " + w;
            if (f.width(cand) <= max || line.length() == 0) {
               if (f.width(cand) > max && line.length() == 0) {
                  StringBuilder part = new StringBuilder();

                  for (char c : w.toCharArray()) {
                     if (f.width(part.toString() + c) > max && part.length() > 0) {
                        out.add(part.toString());
                        part.setLength(0);
                     }

                     part.append(c);
                  }

                  line = part;
               } else {
                  line = new StringBuilder(cand);
               }
            } else {
               out.add(line.toString());
               line = new StringBuilder(w);
            }
         }

         out.add(line.toString());
         return out;
      }
   }

   public static BufferedImage folder(FolderModel f, int count, int px) {
      double k = (double)px / FW;
      BufferedImage img = new BufferedImage(px, (int)Math.round(FH * k), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = start(img, k);
      Color base = new Color(f.rgb());
      g.setPaint(new GradientPaint(0.0F, 0.0F, DesignRenderer.lighter(base, 0.08F), FW, FH, DesignRenderer.darker(base, 0.12F)));
      g.fillRect(0, 0, FW, FH);
      DesignRenderer.texture(g, 0, FW, FH, f.label.hashCode());
      Color print = DesignRenderer.darker(base, 0.55F);
      g.setColor(print);
      Fonts.Face head = Fonts.face(Fonts.OLD, true, false, 54.0F, 0.18F);
      String title = L.t("ДЕЛО");
      head.draw(g, title, (FW - head.width(title)) / 2.0, 120.0);
      g.setStroke(new BasicStroke(2.0F));
      g.draw(new java.awt.geom.Line2D.Double(60.0, 140.0, FW - 60.0, 140.0));
      Fonts.Face small = Fonts.face(Fonts.OLD, false, false, 18.0F);
      small.draw(g, "№", 70.0, 190.0);
      g.setStroke(new BasicStroke(1.0F));
      g.draw(new java.awt.geom.Line2D.Double(95.0, 192.0, FW - 70.0, 192.0));
      double lx = 60.0;
      double ly = 250.0;
      double lw = FW - 120.0;
      double lh = 150.0;
      g.setColor(new Color(0, 0, 0, 45));
      g.fill(new Rectangle2D.Double(lx + 3.0, ly + 4.0, lw, lh));
      g.setColor(new Color(248, 245, 236));
      g.fill(new Rectangle2D.Double(lx, ly, lw, lh));
      g.setColor(new Color(150, 140, 120));
      g.draw(new Rectangle2D.Double(lx + 6.0, ly + 6.0, lw - 12.0, lh - 12.0));
      if (!f.label.isEmpty()) {
         Fonts.Face tf = Fonts.face(Fonts.TYPEWRITER, true, false, 26.0F);
         List<String> lines = wrap(tf, f.label, lw - 30.0);

         while (lines.size() > 3 && tf.size() > 12.0F) {
            tf = Fonts.face(Fonts.TYPEWRITER, true, false, tf.size() - 2.0F);
            lines = wrap(tf, f.label, lw - 30.0);
         }

         double ty = ly + lh / 2.0 - (lines.size() - 1) * tf.size() * 0.6 + tf.size() * 0.35;
         g.setColor(new Color(30, 30, 36));

         for (String l : lines) {
            tf.draw(g, l, lx + (lw - tf.width(l)) / 2.0, ty);
            ty += tf.size() * 1.2;
         }
      }

      g.setColor(print);
      Fonts.Face c = Fonts.face(Fonts.TYPEWRITER, false, false, 16.0F);
      String cnt = L.t("Листов: ") + count;
      c.draw(g, cnt, 70.0, FH - 70.0);
      g.setColor(new Color(230, 225, 210));
      g.setStroke(new BasicStroke(3.0F));
      for (int i = 0; i < 2; i++) {
         double yy = FH / 2.0 - 20.0 + i * 40.0;
         g.draw(new java.awt.geom.QuadCurve2D.Double(FW - 26.0, yy, FW - 6.0, yy + 10.0, FW, yy + 30.0));
         g.fill(new Ellipse2D.Double(FW - 30.0, yy - 4.0, 8.0, 8.0));
      }

      g.dispose();
      return img;
   }

   /** Tinting helpers for the wax impression. */
   static final class ImageFxTint {
      private ImageFxTint() {
      }

      static BufferedImage mask(BufferedImage src, Color c) {
         int w = src.getWidth();
         int h = src.getHeight();
         int[] px = src.getRGB(0, 0, w, h, null, 0, w);
         int rgb = c.getRGB() & 0xFFFFFF;

         for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            int lum = (int)(0.3 * (px[i] >> 16 & 0xFF) + 0.59 * (px[i] >> 8 & 0xFF) + 0.11 * (px[i] & 0xFF));
            int ink = a * (255 - Math.min(255, lum + 40)) / 255;
            px[i] = Math.min(255, ink * 2) << 24 | rgb;
         }

         BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         out.setRGB(0, 0, w, h, px, 0, w);
         return out;
      }
   }
}
