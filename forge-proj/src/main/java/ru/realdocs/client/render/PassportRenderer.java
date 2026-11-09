package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Line2D.Double;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.realdocs.L;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;

public final class PassportRenderer {
   public static final int W = 640;
   public static final int H = 460;
   private static final int PAGE_TOP = 14;
   private static final int PAGE_H = 420;
   private static final int PAGE_W = 302;
   private static final int LEFT_X = 10;
   private static final int RIGHT_X = 328;
   private static final Locale UP = Locale.ROOT;
   public static final int TICKET_CUT = 474;
   public static final int COVER_W = 300;
   public static final int COVER_H = 420;

   private PassportRenderer() {
   }

   public static BufferedImage renderSpread(
      PassportTemplate t, PassportModel m, PassportRenderer.Assets assets, DocRenderer.StampSource stamps, int pxWidth, boolean selectGuides
   ) {
      return renderSpread(t, m, assets, stamps, pxWidth, selectGuides, 0, 0);
   }

   public static BufferedImage renderSpread(
      PassportTemplate t,
      PassportModel m,
      PassportRenderer.Assets assets,
      DocRenderer.StampSource stamps,
      int pxWidth,
      boolean selectGuides,
      int spread,
      int uv
   ) {
      if (!t.book()) {
         return renderSheet(t, m, assets, stamps, pxWidth, selectGuides, spread, uv);
      } else {
         double k = (double)pxWidth / 640.0;
         BufferedImage out = new BufferedImage(pxWidth, (int)Math.round(460.0 * k), 2);
         Graphics2D g = out.createGraphics();
         StampRenderer.hints(g);
         g.scale(k, k);
         Color bg = new Color(t.bg & 16777215);
         Color accent = new Color(t.accent & 16777215);
         drawSpread(g, bg, accent);
         drawPattern(g, t.pattern, accent, pageClip(10), spread);
         drawPattern(g, t.pattern, accent, pageClip(328), spread);
         String code = m == null ? "" : (m.sec.isEmpty() ? shortId(m.passId) : m.sec);
         Color micro = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110);
         String microText = (t.title.isBlank() ? "PASSPORT" : t.title.toUpperCase(UP)) + (code.isEmpty() ? "" : " · " + code);
         Security.microBorder(g, 19.0, 23.0, 284.0, 402.0, microText, micro);
         Security.microBorder(g, 337.0, 23.0, 284.0, 402.0, microText, micro);

         for (int px : new int[]{10, 328}) {
            Shape old = g.getClip();
            g.clip(pageClip(px));
            Paper.grain(g, 640.0, 460.0, seedOf(m, t) + (long)px);
            g.setClip(old);
         }

         if (spread == 0) {
            drawDataSpread(g, t, m, assets, stamps, k, selectGuides, accent, code);
         } else {
            drawVisaSpread(g, t, m, stamps, k, spread, accent);
         }

         if (m != null) {
            int days = Paper.ageDays(m.date);
            Paper.wear(g, pageClip(10), days, seedOf(m, t) + (long)spread, false);
            Paper.wear(g, pageClip(328), days, seedOf(m, t) + (long)(spread * 7) + 1L, false);
         }

         if (uv != 0) {
            Security.uv(g, new Shape[]{pageClip(10), pageClip(328)}, code, uv == 1 && m != null, () -> {
               Emblems.draw(g, t.emblemCode == 0 ? 2 : t.emblemCode, t.flag, 71.0, 134.0, 180.0, Security.uvGlow(), true);
               g.setColor(new Color(255, 150, 230));
               g.setFont(new Font("Monospaced", 1, 22));
               drawCentered(g, code, 479.0, 374.0);
            });
         }

         DocRenderer.drawGameMark(g, 10.0, 14.0, 620.0, 420.0, uv != 0);
         DocRenderer.drawDisclaimer(g, 161.0, 447.0);
         DocRenderer.drawDisclaimer(g, 479.0, 447.0);
         g.dispose();
         return out;
      }
   }

   private static String shortId(String id) {
      return id == null ? "" : (id.length() > 8 ? id.substring(0, 8).toUpperCase(UP) : id.toUpperCase(UP));
   }

   private static void drawDataSpread(
      Graphics2D g,
      PassportTemplate t,
      PassportModel m,
      PassportRenderer.Assets assets,
      DocRenderer.StampSource stamps,
      double k,
      boolean guides,
      Color accent,
      String code
   ) {
      double cxl = 161.0;
      double cxr = 479.0;
      BufferedImage emblem = assets != null && !t.emblem.isEmpty() ? assets.image(t.emblem) : null;
      if (emblem != null) {
         g.setComposite(AlphaComposite.SrcOver.derive(0.09F));
         double s = Math.min(280.0 / (double)emblem.getWidth(), 280.0 / (double)emblem.getHeight());
         double ew = (double)emblem.getWidth() * s;
         double eh = (double)emblem.getHeight() * s;
         g.drawImage(emblem, (int)(cxr - ew / 2.0), 124, (int)ew, (int)eh, null);
         g.setComposite(AlphaComposite.SrcOver);
      } else if (t.emblemCode != 0) {
         g.setComposite(AlphaComposite.SrcOver.derive(0.1F));
         Emblems.draw(g, t.emblemCode, t.flag, cxr - 120.0, 124.0, 240.0, accent, true);
         g.setComposite(AlphaComposite.SrcOver);
      }

      if (!t.decor.isEmpty()) {
         DesignRenderer.draw(g, t.decor, t, emblem, new Color(t.bg & 16777215), false, tokens(t, m));
      }

      if (!t.hideHeader) {
         drawHeader(g, t, accent, emblem, cxr);
      }

      g.setColor(darker(accent));
      g.setFont(new Font("Serif", 0, 8));
      g.drawString("1", 290, 422);
      g.drawString("2", 344, 422);

      for (PassportTemplate.Field f : t.fields) {
         drawField(g, t, m, f, assets, guides);
      }

      if (m != null && !code.isEmpty()) {
         Security.codeStrip(g, 346.0, 394.0, 266.0, code, darker(accent));
      }

      if (m != null) {
         DocRenderer.drawStamps(g, m.stamps, 0, 640.0, 460.0, stamps, k, m.passId);
      }
   }

   private static void drawHeader(Graphics2D g, PassportTemplate t, Color accent, BufferedImage emblem, double cxr) {
      g.setColor(accent);
      String title = t.title.toUpperCase(UP);
      g.setFont(StampRenderer.fit(g, title, 272.0, 19.0, 9.0, 1));
      drawCentered(g, title, cxr, 48.0);
      g.setStroke(new BasicStroke(1.2F));
      g.draw(new Double(346.0, 58.0, 612.0, 58.0));
      if (!t.org.isBlank()) {
         g.setFont(spaced(10.5F, 0.12F, 0));
         g.setColor(darker(accent));
         String org = t.org.toUpperCase(UP);
         g.setFont(StampRenderer.fit(g, org, 272.0, 10.5, 7.0, 0));
         drawCentered(g, org, cxr, 76.0);
      }

      if (emblem != null) {
         double s = Math.min(44.0 / (double)emblem.getWidth(), 44.0 / (double)emblem.getHeight());
         g.drawImage(
            emblem,
            (int)(34.0 + (44.0 - (double)emblem.getWidth() * s) / 2.0),
            (int)(32.0 + (44.0 - (double)emblem.getHeight() * s) / 2.0),
            (int)((double)emblem.getWidth() * s),
            (int)((double)emblem.getHeight() * s),
            null
         );
      } else if (t.emblemCode != 0) {
         Emblems.draw(g, t.emblemCode, t.flag, 34.0, 32.0, 44.0, accent, false);
      }

      g.setColor(darker(accent));
      g.setFont(spaced(9.0F, 0.2F, 1));
      String hdr = t.title.toUpperCase(UP);
      g.setFont(StampRenderer.fit(g, hdr, 202.0, 9.0, 6.0, 1));
      g.drawString(hdr, 86, 52);
   }

   private static void drawVisaSpread(Graphics2D g, PassportTemplate t, PassportModel m, DocRenderer.StampSource stamps, double k, int spread, Color accent) {
      Color dark = darker(accent);

      for (int side = 0; side < 2; side++) {
         int x = side == 0 ? 10 : 328;
         double cx = (double)x + 151.0;
         if (t.emblemCode != 0) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.07F));
            Emblems.draw(g, t.emblemCode, t.flag, cx - 100.0, 124.0, 200.0, accent, true);
            g.setComposite(AlphaComposite.SrcOver);
         }

         g.setColor(dark);
         String vt = t.visaTitle.isBlank() ? "VISAS" : t.visaTitle.toUpperCase(UP);
         g.setFont(spaced(11.0F, 0.25F, 1));
         drawCentered(g, vt, cx, 48.0);
         g.setStroke(new BasicStroke(0.8F));
         g.draw(new Double((double)(x + 40), 56.0, (double)(x + 302 - 40), 56.0));
         g.setFont(new Font("Serif", 0, 8));
         int pageNo = 1 + spread * 2 + side;
         String pn = String.valueOf(pageNo + 1);
         if (side == 0) {
            g.drawString(pn, x + 302 - 22, 422);
         } else {
            g.drawString(pn, x + 16, 422);
         }

         if (m != null && !m.serial.isEmpty()) {
            g.setFont(new Font("Monospaced", 0, 7));
            String sn = m.serial;
            if (side == 0) {
               g.drawString(sn, x + 18, 422);
            } else {
               FontMetrics fm = g.getFontMetrics();
               g.drawString(sn, x + 302 - 18 - fm.stringWidth(sn), 422);
            }
         }
      }

      if (m != null) {
         DocRenderer.drawStamps(g, m.stamps, spread, 640.0, 460.0, stamps, k, m.passId);
      }
   }

   private static void drawSpread(Graphics2D g, Color bg, Color accent) {
      Color spine = bg.darker().darker();
      g.setPaint(new GradientPaint(312.0F, 0.0F, spine.brighter(), 328.0F, 0.0F, spine.darker()));
      g.fillRect(312, 14, 16, 420);
      page(g, 10, bg, accent, true);
      page(g, 328, bg, accent, false);
   }

   private static void page(Graphics2D g, int x, Color bg, Color accent, boolean spineRight) {
      double r = 10.0;
      g.setPaint(new GradientPaint((float)x, 14.0F, bg.brighter(), (float)(x + 302), 434.0F, bg));
      g.fill(new java.awt.geom.RoundRectangle2D.Double((double)x, 14.0, 302.0, 420.0, r, r));
      g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160));
      g.setStroke(new BasicStroke(1.4F));
      g.draw(new java.awt.geom.RoundRectangle2D.Double((double)x, 14.0, 302.0, 420.0, r, r));
      g.setStroke(new BasicStroke(0.6F));
      g.draw(new java.awt.geom.RoundRectangle2D.Double((double)(x + 5), 19.0, 292.0, 410.0, 7.0, 7.0));
      Shape old = g.getClip();
      g.setClip(new java.awt.geom.RoundRectangle2D.Double((double)x, 14.0, 302.0, 420.0, r, r));
      int fold = spineRight ? x + 302 : x;
      int edge = spineRight ? x + 302 - 40 : x + 40;
      g.setPaint(new GradientPaint((float)edge, 0.0F, new Color(0, 0, 0, 0), (float)fold, 0.0F, new Color(0, 0, 0, 60)));
      if (spineRight) {
         g.fillRect(edge, 14, fold - edge, 420);
      } else {
         g.fillRect(fold, 14, edge - fold, 420);
      }

      g.setClip(old);
   }

   private static Shape pageClip(int x) {
      return new java.awt.geom.RoundRectangle2D.Double((double)(x + 5), 19.0, 292.0, 410.0, 7.0, 7.0);
   }

   private static void drawField(Graphics2D g, PassportTemplate t, PassportModel m, PassportTemplate.Field f, PassportRenderer.Assets assets, boolean guides) {
      Color accent = new Color(t.accent & 16777215);
      Color ink = new Color(24, 24, 32);
      Color label = new Color(accent.getRed() / 2 + 60, accent.getGreen() / 2 + 60, accent.getBlue() / 2 + 60);
      if (guides) {
         g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
         g.fillRect(f.x, f.y, f.w, f.h);
         g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
         g.setStroke(new BasicStroke(1.0F, 0, 0, 10.0F, new float[]{4.0F, 3.0F}, 0.0F));
         g.drawRect(f.x, f.y, f.w, f.h);
      }

      if (f.type == 2) {
         BufferedImage ph = m != null && !m.photo.isEmpty() && assets != null ? assets.image(m.photo) : null;
         g.setColor(new Color(255, 255, 255, 170));
         g.fillRect(f.x, f.y, f.w, f.h);
         if (ph != null) {
            double s = Math.max((double)f.w / (double)ph.getWidth(), (double)f.h / (double)ph.getHeight());
            double pw = (double)ph.getWidth() * s;
            double phh = (double)ph.getHeight() * s;
            Shape old = g.getClip();
            g.setClip(f.x, f.y, f.w, f.h);
            g.drawImage(ph, (int)((double)f.x + ((double)f.w - pw) / 2.0), (int)((double)f.y + ((double)f.h - phh) / 2.0), (int)pw, (int)phh, null);
            g.setClip(old);
            int right = t.book() ? (f.x < 328 ? 312 : 630) - 14 : t.w() - 22;
            ghost(g, ph, f, right);
         } else {
            g.setColor(new Color(150, 150, 160));
            g.setFont(new Font("Serif", 2, 12));
            drawCentered(g, L.t("фото"), (double)f.x + (double)f.w / 2.0, (double)f.y + (double)f.h / 2.0 + 4.0);
         }

         g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200));
         g.setStroke(new BasicStroke(1.2F));
         g.drawRect(f.x, f.y, f.w, f.h);
         if (m != null) {
            Security.hologram(g, (double)(f.x + f.w - 46), (double)(f.y + f.h - 36), 58.0, 44.0, Security.seed(m.passId));
         }
      } else if (f.type == 4) {
         g.setColor(label);
         g.setFont(new Font("SansSerif", 0, 7));
         g.drawString(f.label.toUpperCase(UP), f.x, f.y + 8);
         g.setColor(new Color(ink.getRed(), ink.getGreen(), ink.getBlue(), 150));
         g.setStroke(new BasicStroke(0.7F));
         g.draw(new Double((double)f.x, (double)(f.y + f.h - 4), (double)(f.x + f.w), (double)(f.y + f.h - 4)));
         if (m != null && m.signature.length > 0) {
            DocRenderer.drawSignature(g, m.signature, (double)(f.x + 6), (double)(f.y + 6), (double)(f.w - 12), (double)(f.h - 14), new Color(22, 36, 128));
         }
      } else {
         g.setColor(label);
         g.setFont(new Font("SansSerif", 0, 7));
         String lb = f.label.toUpperCase(UP);
         g.setFont(StampRenderer.fit(g, lb, (double)f.w, 7.0, 5.0, 0));
         g.drawString(lb, f.x, f.y + 8);
         String value;
         if (m == null) {
            value = f.hint.isBlank() ? "· · ·" : f.hint;
            if (f.type == 3) {
               value = (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + "000000";
            }

            if (PassportTemplate.issueDate(f)) {
               value = L.t("дд.мм.гггг");
            }

            if (f.type == 6) {
               value = "+" + t.validYears + L.t(" лет");
            }
         } else if (f.type == 3) {
            value = m.serial;
         } else if (f.type == 6) {
            value = m.values.containsKey(f.key) ? m.values.get(f.key) : t.expiry(m.date);
         } else if (PassportTemplate.issueDate(f) && !m.date.isEmpty() && !m.values.containsKey(f.key)) {
            value = m.date;
         } else {
            value = m.values.getOrDefault(f.key, "");
         }

         if (value == null) {
            value = "";
         }

         if (!f.prefix.isEmpty() && !value.isEmpty()) {
            value = f.prefix + value;
         }

         boolean serial = f.type == 3;
         g.setColor(m == null ? new Color(130, 130, 140) : (serial ? new Color(150, 30, 30) : ink));
         Font font = serial ? new Font("Monospaced", 1, f.size) : new Font("Serif", f.bold ? 1 : 0, f.size);
         FontMetrics fm = g.getFontMetrics(font);

         double size;
         for (size = (double)f.size; fm.stringWidth(value) > f.w && size > 7.0; fm = g.getFontMetrics(font)) {
            font = font.deriveFont((float)(--size));
         }

         g.setFont(font);
         g.drawString(value, (float)f.x, (float)((double)(f.y + 10) + Math.min((double)(f.h - 12), size + 3.0)));
         g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
         g.setStroke(new BasicStroke(0.6F));
         g.draw(new Double((double)f.x, (double)(f.y + f.h - 2), (double)(f.x + f.w), (double)(f.y + f.h - 2)));
      }
   }

   private static void ghost(Graphics2D g, BufferedImage ph, PassportTemplate.Field f, int right) {
      int gx = f.x + f.w + 10;
      int gw = 44;
      int gh = gw * 4 / 3;
      int gy = f.y + 12;
      if (gx + gw <= right) {
         BufferedImage gray = new BufferedImage(gw, gh, 2);
         Graphics2D q = gray.createGraphics();
         StampRenderer.hints(q);
         double s = Math.max((double)gw / (double)ph.getWidth(), (double)gh / (double)ph.getHeight());
         q.drawImage(
            ph,
            (int)(((double)gw - (double)ph.getWidth() * s) / 2.0),
            (int)(((double)gh - (double)ph.getHeight() * s) / 2.0),
            (int)((double)ph.getWidth() * s),
            (int)((double)ph.getHeight() * s),
            null
         );
         q.dispose();

         for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
               int c = gray.getRGB(x, y);
               int lum = (int)(0.3 * (double)(c >> 16 & 0xFF) + 0.59 * (double)(c >> 8 & 0xFF) + 0.11 * (double)(c & 0xFF));
               gray.setRGB(x, y, 1258291200 | lum << 16 | lum << 8 | lum);
            }
         }

         g.drawImage(gray, gx, gy, null);
      }
   }

   public static Map<String, String> tokens(PassportTemplate t, PassportModel m) {
      Map<String, String> out = new HashMap<>();
      if (m != null) {
         out.putAll(m.values);
         out.put("serial", m.serial);
         out.put("date", m.date);
         out.put("expiry", t.expiry(m.date));
         out.put("holder", m.holderName);
         out.put("issuer", m.issuer);
         return out;
      } else {
         out.put("serial", (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + "000000");
         out.put("date", L.t("дд.мм.гггг"));
         out.put("expiry", L.t("дд.мм.гггг"));

         for (PassportTemplate.Field f : t.fields) {
            out.put(f.key, f.label.toUpperCase(UP));
         }

         return out;
      }
   }

   static Shape sheetShape(int format, int w, int h) {
      double bw = (double)(w - 16);
      double bh = (double)(h - 26);
      switch (format) {
         case 2:
            Area a = new Area(new java.awt.geom.RoundRectangle2D.Double(8.0, 8.0, bw, bh, 16.0, 16.0));
            a.subtract(new Area(new java.awt.geom.Ellipse2D.Double(463.0, -3.0, 22.0, 22.0)));
            a.subtract(new Area(new java.awt.geom.Ellipse2D.Double(463.0, 8.0 + bh - 11.0, 22.0, 22.0)));
            return a;
         case 3:
            java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
            p.moveTo(8.0, 8.0);
            p.lineTo(8.0 + bw, 8.0);
            double bottom = 8.0 + bh;
            p.lineTo(8.0 + bw, bottom - 6.0);
            boolean up = true;

            for (double x = 8.0 + bw; x > 8.0; x -= 7.0) {
               p.lineTo(Math.max(8.0, x - 7.0), up ? bottom : bottom - 6.0);
               up = !up;
            }

            p.closePath();
            return p;
         default:
            return new java.awt.geom.RoundRectangle2D.Double(8.0, 8.0, bw, bh, 32.0, 32.0);
      }
   }

   private static BufferedImage renderSheet(
      PassportTemplate t, PassportModel m, PassportRenderer.Assets assets, DocRenderer.StampSource stamps, int pxWidth, boolean guides, int side, int uv
   ) {
      int w = t.w();
      int h = t.h();
      double k = (double)pxWidth / (double)w;
      BufferedImage out = new BufferedImage(pxWidth, (int)Math.round((double)h * k), 2);
      Graphics2D g = out.createGraphics();
      StampRenderer.hints(g);
      g.scale(k, k);
      Color bg = new Color(t.bg & 16777215);
      Color accent = new Color(t.accent & 16777215);
      Shape body = sheetShape(t.format, w, h);
      Rectangle2D b = body.getBounds2D();
      g.setColor(new Color(0, 0, 0, 46));
      g.translate(1.5, 2.5);
      g.fill(body);
      g.translate(-1.5, -2.5);
      g.setPaint(new GradientPaint((float)b.getX(), (float)b.getY(), lighter(bg, 0.25F), (float)b.getMaxX(), (float)b.getMaxY(), bg));
      g.fill(body);
      drawPattern(g, t.pattern, accent, body, side);
      Shape old = g.getClip();
      g.clip(body);
      Paper.grain(g, (double)w, (double)h, seedOf(m, t));
      g.setClip(old);
      g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
      g.setStroke(new BasicStroke(1.3F));
      g.draw(body);
      String code = m == null ? "" : (m.sec.isEmpty() ? shortId(m.passId) : m.sec);
      Color micro = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 100);
      String microText = (t.title.isBlank() ? "ID" : t.title.toUpperCase(UP)) + (code.isEmpty() ? "" : " · " + code);
      if (t.format != 3) {
         Security.microBorder(g, b.getX() + 9.0, b.getY() + 9.0, b.getWidth() - 18.0, b.getHeight() - 18.0, microText, micro);
      }

      if (t.format == 2) {
         g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 140));
         g.setStroke(new BasicStroke(1.0F, 0, 0, 10.0F, new float[]{4.0F, 4.0F}, 0.0F));
         g.draw(new Double(474.0, b.getY() + 12.0, 474.0, b.getMaxY() - 12.0));
      }

      BufferedImage emblem = assets != null && !t.emblem.isEmpty() ? assets.image(t.emblem) : null;
      double cx = t.format == 2 ? 241.0 : b.getCenterX();
      double cy = b.getCenterY();
      double size = Math.min(b.getWidth(), b.getHeight()) * 0.62;
      g.setComposite(AlphaComposite.SrcOver.derive(0.08F));
      if (emblem != null) {
         double s = Math.min(size / (double)emblem.getWidth(), size / (double)emblem.getHeight());
         g.drawImage(
            emblem,
            (int)(cx - (double)emblem.getWidth() * s / 2.0),
            (int)(cy - (double)emblem.getHeight() * s / 2.0),
            (int)((double)emblem.getWidth() * s),
            (int)((double)emblem.getHeight() * s),
            null
         );
      } else if (t.emblemCode != 0) {
         Emblems.draw(g, t.emblemCode, t.flag, cx - size / 2.0, cy - size / 2.0, size, accent, true);
      }

      g.setComposite(AlphaComposite.SrcOver);
      Map<String, String> tok = tokens(t, m);
      List<DesignElement> els = side == 0 ? t.decor : t.back;
      if (!els.isEmpty()) {
         DesignRenderer.draw(g, els, t, emblem, bg, false, tok);
      }

      if (side == 0) {
         if (!t.hideHeader) {
            sheetHeader(g, t, accent, emblem, b);
         }

         for (PassportTemplate.Field f : t.fields) {
            drawField(g, t, m, f, assets, guides);
         }

         if (m != null && !code.isEmpty() && t.format != 1) {
            double sw = t.format == 2 ? 438.0 : b.getWidth() - 40.0;
            Security.codeStrip(g, b.getX() + 18.0, b.getMaxY() - 30.0, sw, code, darker(accent));
         }
      } else if (m != null && !code.isEmpty()) {
         Security.codeStrip(g, b.getX() + 24.0, b.getMaxY() - 40.0, b.getWidth() - 48.0, code, darker(accent));
      }

      if (m != null) {
         DocRenderer.drawStamps(g, m.stamps, side, (double)w, (double)h, stamps, k, m.passId);
      }

      if (m != null && t.format != 1) {
         Paper.wear(g, body, Paper.ageDays(m.date), seedOf(m, t), true);
      }

      if (uv != 0) {
         Security.uv(g, new Shape[]{body}, code, uv == 1 && m != null, () -> {
            Emblems.draw(g, t.emblemCode == 0 ? 2 : t.emblemCode, t.flag, cx - size / 3.0, cy - size / 3.0, size / 1.5, Security.uvGlow(), true);
            g.setColor(new Color(255, 150, 230));
            g.setFont(new Font("Monospaced", 1, 20));
            drawCentered(g, code, cx, b.getMaxY() - 22.0);
         });
      }

      DocRenderer.drawGameMark(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), uv != 0);
      DocRenderer.drawDisclaimer(g, (double)w / 2.0, (double)(h - 6));
      g.dispose();
      return out;
   }

   private static long seedOf(PassportModel m, PassportTemplate t) {
      return m == null ? (long)t.name.hashCode() : (long)m.passId.hashCode() * 31L + (long)m.serial.hashCode();
   }

   private static void sheetHeader(Graphics2D g, PassportTemplate t, Color accent, BufferedImage emblem, Rectangle2D b) {
      boolean center = t.format == 3;
      double right = t.format == 2 ? 460.0 : b.getMaxX() - 18.0;
      double x = b.getX() + 20.0;
      double top = b.getY() + 10.0;
      double textX = x;
      if (!center) {
         if (emblem != null) {
            double s = Math.min(40.0 / (double)emblem.getWidth(), 40.0 / (double)emblem.getHeight());
            g.drawImage(emblem, (int)x, (int)top, (int)((double)emblem.getWidth() * s), (int)((double)emblem.getHeight() * s), null);
            textX = x + 50.0;
         } else if (t.emblemCode != 0) {
            Emblems.draw(g, t.emblemCode, t.flag, x, top, 40.0, accent, false);
            textX = x + 50.0;
         }
      }

      g.setColor(accent);
      String title = t.title.toUpperCase(UP);
      double maxW = right - textX - (double)(t.format == 1 ? 70 : 0);
      g.setFont(StampRenderer.fit(g, title, center ? b.getWidth() - 40.0 : maxW, 20.0, 9.0, 1));
      if (center) {
         drawCentered(g, title, b.getCenterX(), top + 30.0);
      } else {
         g.drawString(title, (float)textX, (float)(top + 22.0));
      }

      if (!t.org.isBlank()) {
         String org = t.org.toUpperCase(UP);
         g.setColor(darker(accent));
         g.setFont(StampRenderer.fit(g, org, center ? b.getWidth() - 40.0 : maxW, 10.0, 6.0, 0));
         if (center) {
            drawCentered(g, org, b.getCenterX(), top + 48.0);
         } else {
            g.drawString(org, (float)textX, (float)(top + 38.0));
         }
      }

      g.setStroke(new BasicStroke(1.0F));
      g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
      double ly = top + (double)(center ? 58 : 48);
      g.draw(new Double(b.getX() + 18.0, ly, right, ly));
      if (t.format == 1) {
         chip(g, b.getMaxX() - 78.0, top + 2.0);
      }

      if (t.format == 2) {
         g.setColor(darker(accent));
         g.setFont(spaced(9.0F, 0.25F, 1));
         drawCentered(g, L.t("КОНТРОЛЬ"), (474.0 + b.getMaxX()) / 2.0, top + 22.0);
      }
   }

   private static void chip(Graphics2D g, double x, double y) {
      RoundRectangle2D r = new java.awt.geom.RoundRectangle2D.Double(x, y, 50.0, 38.0, 9.0, 9.0);
      g.setPaint(new GradientPaint((float)x, (float)y, new Color(246, 222, 152), (float)(x + 50.0), (float)(y + 38.0), new Color(170, 132, 60)));
      g.fill(r);
      g.setColor(new Color(120, 90, 40, 170));
      g.setStroke(new BasicStroke(0.8F));
      g.draw(r);
      g.draw(new Double(x + 17.0, y, x + 17.0, y + 38.0));
      g.draw(new Double(x + 33.0, y, x + 33.0, y + 38.0));
      g.draw(new Double(x, y + 13.0, x + 17.0, y + 13.0));
      g.draw(new Double(x, y + 25.0, x + 17.0, y + 25.0));
      g.draw(new Double(x + 33.0, y + 13.0, x + 50.0, y + 13.0));
      g.draw(new Double(x + 33.0, y + 25.0, x + 50.0, y + 25.0));
   }

   public static BufferedImage renderCover(PassportTemplate t, PassportRenderer.Assets assets, int pxWidth) {
      int cw = 300;
      int ch = 420;
      double k = (double)pxWidth / (double)cw;
      BufferedImage out = new BufferedImage(pxWidth, (int)Math.round((double)ch * k), 2);
      Graphics2D g = out.createGraphics();
      StampRenderer.hints(g);
      g.scale(k, k);
      Color c = new Color(t.coverColor & 16777215);
      java.awt.geom.RoundRectangle2D.Double body = new java.awt.geom.RoundRectangle2D.Double(0.0, 0.0, (double)cw, (double)ch, 18.0, 18.0);
      g.setPaint(
         new RadialGradientPaint(
            new java.awt.geom.Point2D.Double((double)cw * 0.35, (double)ch * 0.3),
            (float)ch * 0.9F,
            new float[]{0.0F, 1.0F},
            new Color[]{lighter(c, 0.12F), darkerBy(c, 0.35F)}
         )
      );
      g.fill(body);
      Shape old = g.getClip();
      g.clip(body);
      DesignRenderer.texture(g, t.coverTexture, cw, ch, (long)t.coverColor * 31L + (long)t.name.hashCode());
      g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(0, 0, 0, 90), 16.0F, 0.0F, new Color(0, 0, 0, 0)));
      g.fillRect(0, 0, 16, ch);
      g.setClip(old);
      g.setColor(new Color(0, 0, 0, 70));
      g.setStroke(new BasicStroke(0.8F, 1, 1, 1.0F, new float[]{2.5F, 2.5F}, 0.0F));
      g.draw(new java.awt.geom.RoundRectangle2D.Double(7.0, 7.0, (double)(cw - 14), (double)(ch - 14), 13.0, 13.0));
      DesignRenderer.frame(g, t.coverFrame, cw, ch);
      BufferedImage em = assets != null && !t.emblem.isEmpty() ? assets.image(t.emblem) : null;
      List<DesignElement> els = t.cover.isEmpty() ? t.defaultCover() : t.cover;
      DesignRenderer.draw(g, els, t, em, c, true);
      g.setColor(new Color(226, 192, 112, 150));
      g.setFont(new Font("SansSerif", 0, 6));
      drawCentered(g, L.t("ВНУТРИИГРОВОЙ ДОКУМЕНТ · НЕ ЯВЛЯЕТСЯ НАСТОЯЩИМ"), (double)cw / 2.0, (double)(ch - 22));
      g.dispose();
      return out;
   }

   private static void embossText(Graphics2D g, String s, double cx, double baseline, Paint foil, Color shadow) {
      g.setColor(shadow);
      drawCentered(g, s, cx + 0.8, baseline + 1.0);
      g.setPaint(foil);
      drawCentered(g, s, cx, baseline);
   }

   private static Font spaced(float size, float tracking, int style) {
      Map<TextAttribute, Object> a = new HashMap<>();
      a.put(TextAttribute.FAMILY, "Serif");
      a.put(TextAttribute.SIZE, size);
      a.put(TextAttribute.TRACKING, tracking);
      if ((style & 1) != 0) {
         a.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
      }

      return Font.getFont(a);
   }

   private static Font fitSpaced(Graphics2D g, String s, double maxW, float start, float min, float tracking) {
      for (float size = start; size > min; size -= 0.5F) {
         Font f = spaced(size, tracking, 1);
         if ((double)g.getFontMetrics(f).stringWidth(s) <= maxW) {
            return f;
         }
      }

      return spaced(min, tracking, 1);
   }

   private static void drawPattern(Graphics2D g, int pattern, Color accent, Shape clip, int spread) {
      Color c = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 26);
      g.setColor(c);
      g.setStroke(new BasicStroke(0.6F));
      Shape old = g.getClip();
      g.setClip(clip);
      double ph = (double)spread * 0.9;
      Rectangle2D bb = clip.getBounds2D();
      int maxX = (int)Math.ceil(bb.getMaxX());
      int maxY = (int)Math.ceil(bb.getMaxY());
      int rows = (int)(bb.getHeight() / 19.0) + 2;
      switch (pattern) {
         case 1:
            for (int j = 0; j < rows; j++) {
               java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

               for (int x = (int)bb.getMinX(); x <= maxX; x += 3) {
                  double y = bb.getMinY()
                     + 9.0
                     + (double)(j * 19)
                     + 10.0 * Math.sin((double)x / 34.0 + (double)j * 0.55 + ph)
                     + 5.0 * Math.sin((double)x / 9.5 + (double)j + ph);
                  if (x <= (int)bb.getMinX()) {
                     p.moveTo((double)x, y);
                  } else {
                     p.lineTo((double)x, y);
                  }
               }

               g.draw(p);
            }

            Rectangle2D b = clip.getBounds2D();
            double cx = b.getCenterX();
            double cy = b.getCenterY();

            for (int i = 0; i < 36; i++) {
               double a = (double)i * Math.PI / 18.0;
               g.draw(new java.awt.geom.Ellipse2D.Double(cx + Math.cos(a) * 34.0 - 44.0, cy + Math.sin(a) * 34.0 - 44.0, 88.0, 88.0));
            }
            break;
         case 2:
            for (int i = -maxY; i < maxX; i += 12) {
               g.draw(new Double((double)i, (double)maxY, (double)(i + maxY), 0.0));
            }
            break;
         case 3:
            for (int x = 20; x < maxX; x += 14) {
               for (int y = 20; y < maxY; y += 14) {
                  g.fill(new java.awt.geom.Ellipse2D.Double((double)x, (double)y, 2.0, 2.0));
               }
            }
      }

      g.setClip(old);
   }

   private static Color darker(Color c) {
      return new Color((int)((double)c.getRed() * 0.7), (int)((double)c.getGreen() * 0.7), (int)((double)c.getBlue() * 0.7));
   }

   private static Color darkerBy(Color c, float t) {
      return new Color((int)((float)c.getRed() * (1.0F - t)), (int)((float)c.getGreen() * (1.0F - t)), (int)((float)c.getBlue() * (1.0F - t)));
   }

   private static Color lighter(Color c, float t) {
      return new Color(
         (int)((float)c.getRed() + (float)(255 - c.getRed()) * t),
         (int)((float)c.getGreen() + (float)(255 - c.getGreen()) * t),
         (int)((float)c.getBlue() + (float)(255 - c.getBlue()) * t)
      );
   }

   private static void drawCentered(Graphics2D g, String s, double cx, double baseline) {
      FontMetrics fm = g.getFontMetrics();
      g.drawString(s, (float)(cx - (double)fm.stringWidth(s) / 2.0), (float)baseline);
   }

   public interface Assets {
      BufferedImage image(String var1);
   }
}
