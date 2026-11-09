package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D.Double;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import ru.realdocs.L;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;

final class Marks {
   private static final Locale RU = new Locale("ru", "RU");
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

   private Marks() {
   }

   private static String up(String s) {
      return s == null ? "" : s.trim().toUpperCase(RU);
   }

   private static String today() {
      return LocalDate.now().format(DATE);
   }

   private static void centered(Graphics2D g, String s, double cx, double baseline) {
      FontMetrics fm = g.getFontMetrics();
      g.drawString(s, (float)(cx - (double)fm.stringWidth(s) / 2.0), (float)baseline);
   }

   static void border(Graphics2D g, StampModel m, BufferedImage img, double w, double h, Color ink, DocModel.StampRef ref) {
      int dir = ref != null && ref.dir != 0 ? ref.dir : 1;
      String date = ref != null && !ref.date.isEmpty() ? ref.date : today();
      int cat = ref == null ? 0 : ref.cat;
      String dirRu = dir == 2 ? L.t("ВЫЕЗД") : L.t("ВЪЕЗД");
      String dirEn = dir == 2 ? "EXIT · DEPARTURE" : "ENTRY · ARRIVAL";
      g.setComposite(AlphaComposite.SrcOver.derive(0.92F));
      g.setColor(ink);
      if (w == h) {
         double c = w / 2.0;
         g.setStroke(new BasicStroke(7.0F));
         g.draw(new Double(10.0, 10.0, w - 20.0, h - 20.0));
         g.setStroke(new BasicStroke(2.2F));
         g.draw(new Double(c - 98.0, c - 98.0, 196.0, 196.0));
         g.setFont(new Font("SansSerif", 1, 17));
         StampRenderer.arc(g, up(m.top.isEmpty() ? m.org : m.top), c, c, 112.0, true);
         g.setFont(new Font("SansSerif", 1, 14));
         StampRenderer.arc(g, up(m.bottom) + " · " + L.t(DocModel.StampRef.CATEGORIES_SHORT[cat]), c, c, 112.0, false);
         arrow(g, c - 30.0, c - 62.0, 60.0, 22.0, dir);
         g.setFont(new Font("Monospaced", 1, 25));
         centered(g, date, c, c + 9.0);
         g.fill(new java.awt.geom.Rectangle2D.Double(c - 70.0, c + 20.0, 140.0, 2.0));
         g.setFont(new Font("SansSerif", 1, 16));
         centered(g, dirRu, c, c + 44.0);
      } else {
         g.setStroke(new BasicStroke(7.0F));
         g.draw(new java.awt.geom.RoundRectangle2D.Double(8.0, 8.0, w - 16.0, h - 16.0, 26.0, 26.0));
         g.setStroke(new BasicStroke(2.0F));
         g.draw(new java.awt.geom.RoundRectangle2D.Double(20.0, 20.0, w - 40.0, h - 40.0, 16.0, 16.0));
         String country = up(m.top.isEmpty() ? m.org : m.top);
         g.setFont(StampRenderer.fit(g, country, w - 70.0, 24.0, 12.0, 1));
         centered(g, country, w / 2.0, 52.0);
         g.fill(new java.awt.geom.Rectangle2D.Double(32.0, 62.0, w - 64.0, 2.5));
         arrow(g, 34.0, 84.0, 66.0, 30.0, dir);
         g.setFont(new Font("SansSerif", 1, 13));
         g.drawString(dirRu, 34, 136);
         g.setFont(new Font("SansSerif", 0, 8));
         g.drawString(dirEn, 34, 147);
         g.setFont(new Font("Monospaced", 1, 34));
         FontMetrics fm = g.getFontMetrics();
         g.drawString(date, (float)(w - 34.0 - (double)fm.stringWidth(date)), 124.0F);
         g.fill(new java.awt.geom.Rectangle2D.Double(32.0, 156.0, w - 64.0, 2.0));
         String cp = up(m.bottom);
         String cat2 = L.t(DocModel.StampRef.CATEGORIES_SHORT[cat]);
         g.setFont(StampRenderer.fit(g, cp, w - 140.0, 14.0, 8.0, 1));
         g.drawString(cp, 34, 176);
         g.setFont(new Font("SansSerif", 1, 13));
         fm = g.getFontMetrics();
         g.drawString(cat2, (float)(w - 34.0 - (double)fm.stringWidth(cat2)), 176.0F);
      }
   }

   private static void arrow(Graphics2D g, double x, double y, double w, double h, int dir) {
      java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
      double my = y + h / 2.0;
      g.fill(new java.awt.geom.Rectangle2D.Double(dir == 2 ? x : x + w - 5.0, y - 2.0, 5.0, h + 4.0));
      double ax0 = dir == 2 ? x + w : x;
      double ax1 = dir == 2 ? x + 10.0 : x + w - 10.0;
      double head = dir == 2 ? ax1 + 0.0 : ax1;
      p.moveTo(ax0, my - 4.0);
      p.lineTo(dir == 2 ? ax1 + 14.0 : ax1 - 14.0, my - 4.0);
      p.lineTo(dir == 2 ? ax1 + 14.0 : ax1 - 14.0, my - 11.0);
      p.lineTo(head, my);
      p.lineTo(dir == 2 ? ax1 + 14.0 : ax1 - 14.0, my + 11.0);
      p.lineTo(dir == 2 ? ax1 + 14.0 : ax1 - 14.0, my + 4.0);
      p.lineTo(ax0, my + 4.0);
      p.closePath();
      g.fill(p);
   }

   static void grunge(BufferedImage img, long seed) {
      Random rnd = new Random(seed);
      int w = img.getWidth();
      int h = img.getHeight();
      int spots = w * h / 90;

      for (int i = 0; i < spots; i++) {
         int x = rnd.nextInt(w);
         int y = rnd.nextInt(h);
         int a = img.getRGB(x, y) >>> 24;
         if (a != 0) {
            int r = 1 + rnd.nextInt(2);

            for (int dx = -r; dx <= r; dx++) {
               for (int dy = -r; dy <= r; dy++) {
                  int px = x + dx;
                  int py = y + dy;
                  if (px >= 0 && py >= 0 && px < w && py < h) {
                     int c = img.getRGB(px, py);
                     int na = (int)((double)(c >>> 24) * (0.25 + rnd.nextDouble() * 0.5));
                     img.setRGB(px, py, na << 24 | c & 16777215);
                  }
               }
            }
         }
      }
   }

   static void visa(Graphics2D g, StampModel m, BufferedImage img, double w, double h, Color ink, DocModel.StampRef ref) {
      String from = ref != null && !ref.date.isEmpty() ? ref.date : today();
      String until = ref != null && !ref.until.isEmpty() ? ref.until : LocalDate.now().plusDays(30L).format(DATE);
      String num = ref != null && !ref.num.isEmpty() ? ref.num : "V00000000";
      int cat = ref == null ? 0 : ref.cat;
      Color light = mixWhite(ink, 0.88F);
      Color mid = mixWhite(ink, 0.65F);
      java.awt.geom.RoundRectangle2D.Double sticker = new java.awt.geom.RoundRectangle2D.Double(2.0, 2.0, w - 4.0, h - 4.0, 14.0, 14.0);
      g.setPaint(new GradientPaint(0.0F, 0.0F, mixWhite(ink, 0.93F), (float)w, (float)h, light));
      g.fill(sticker);
      Shape old = g.getClip();
      g.clip(sticker);
      g.setColor(new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 70));
      g.setStroke(new BasicStroke(0.8F));

      for (int j = 0; j < 22; j++) {
         java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

         for (int x = 0; (double)x <= w; x += 4) {
            double y = (double)(8 + j * 12) + 6.0 * Math.sin((double)x / 26.0 + (double)j * 0.7) + 3.0 * Math.sin((double)x / 7.0 + (double)j);
            if (x == 0) {
               p.moveTo((double)x, y);
            } else {
               p.lineTo((double)x, y);
            }
         }

         g.draw(p);
      }

      g.setClip(old);
      g.setColor(ink);
      g.setStroke(new BasicStroke(2.5F));
      g.draw(sticker);
      Security.microBorder(g, 9.0, 9.0, w - 18.0, h - 18.0, "VISA · " + up(m.org), new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 150));
      if (!m.visa.isEmpty()) {
         Map<String, String> tok = new HashMap<>();
         tok.put("type", L.t(DocModel.StampRef.CATEGORIES[cat]).toUpperCase(RU));
         tok.put("num", num);
         tok.put("from", from);
         tok.put("until", until);
         tok.put("country", up(m.top.isEmpty() ? m.org : m.top));
         tok.put("issuer", up(m.bottom.isEmpty() ? m.org : m.bottom));
         tok.put("org", up(m.org));
         PassportTemplate vt = new PassportTemplate();
         vt.emblemCode = 2;
         g.clip(sticker);
         DesignRenderer.draw(g, m.visa, vt, img, light, false, tok);
         g.setClip(old);
         Security.hologram(g, w - 84.0, h - 68.0, 58.0, 44.0, (long)num.hashCode());
      } else {
         g.setColor(ink);
         g.setFont(new Font("Serif", 1, 34));
         g.drawString("VISA", 22, 50);
         g.setFont(new Font("Serif", 1, 14));
         g.drawString(L.t("ВИЗА"), 24, 68);
         String country = up(m.top.isEmpty() ? m.org : m.top);
         g.setFont(StampRenderer.fit(g, country, w - 190.0, 17.0, 9.0, 1));
         FontMetrics fm = g.getFontMetrics();
         g.drawString(country, (float)(w - 22.0 - (double)fm.stringWidth(country)), 42.0F);
         double y = 98.0;
         row(g, L.t("ТИП / TYPE"), L.t(DocModel.StampRef.CATEGORIES[cat]).toUpperCase(RU), 22.0, y, ink);
         row(g, L.t("НОМЕР / No."), num, 220.0, y, new Color(170, 30, 30));
         y += 44.0;
         row(g, L.t("С / FROM"), from, 22.0, y, ink);
         row(g, L.t("ПО / UNTIL"), until, 220.0, y, ink);
         y += 44.0;
         row(g, L.t("ВЫДАНА / ISSUED BY"), up(m.bottom.isEmpty() ? m.org : m.bottom), 22.0, y, ink);
         double ex = w - 164.0;
         double ey = h - 80.0;
         double es = 58.0;
         if (img != null) {
            double s = Math.min(es / (double)img.getWidth(), es / (double)img.getHeight());
            g.setComposite(AlphaComposite.SrcOver.derive(0.85F));
            g.drawImage(
               img,
               (int)(ex + (es - (double)img.getWidth() * s) / 2.0),
               (int)(ey + (es - (double)img.getHeight() * s) / 2.0),
               (int)((double)img.getWidth() * s),
               (int)((double)img.getHeight() * s),
               null
            );
            g.setComposite(AlphaComposite.SrcOver);
         } else {
            g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 120));
            g.setStroke(new BasicStroke(2.0F));
            g.draw(new Double(ex + 6.0, ey + 6.0, es - 12.0, es - 12.0));
            StampRenderer.star(g, ex + es / 2.0, ey + es / 2.0, 20.0);
         }

         Security.hologram(g, w - 84.0, h - 68.0, 58.0, 44.0, (long)num.hashCode());
      }
   }

   private static void row(Graphics2D g, String label, String value, double x, double y, Color ink) {
      g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 170));
      g.setFont(new Font("SansSerif", 0, 10));
      g.drawString(label, (float)x, (float)y);
      g.setColor(new Color(30, 30, 36));
      g.setFont(StampRenderer.fit(g, value, 190.0, 19.0, 10.0, 1));
      g.drawString(value, (float)x, (float)(y + 21.0));
   }

   private static Color mixWhite(Color c, float t) {
      return new Color(
         (int)((float)c.getRed() + (float)(255 - c.getRed()) * t),
         (int)((float)c.getGreen() + (float)(255 - c.getGreen()) * t),
         (int)((float)c.getBlue() + (float)(255 - c.getBlue()) * t)
      );
   }
}
