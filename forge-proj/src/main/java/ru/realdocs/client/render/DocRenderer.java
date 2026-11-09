package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D.Double;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import ru.realdocs.L;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.SigStyle;
import ru.realdocs.model.StampModel;

public final class DocRenderer {
   public static final int W = 620;
   public static final int H = 877;
   private static final Color PAPER = new Color(251, 248, 240);
   private static final Color INK = new Color(28, 28, 34);
   private static final Color SIG_INK = new Color(22, 36, 128);
   private static final Color GRAY = new Color(120, 118, 110);

   private DocRenderer() {
   }

   public static DocRenderer.Result render(DocModel d, DocRenderer.StampSource src, int pxWidth) {
      return render(d, src, pxWidth, 0);
   }

   public static DocRenderer.Result render(DocModel d, DocRenderer.StampSource src, int pxWidth, int uv) {
      double k = (double)pxWidth / 620.0;
      int pxHeight = (int)Math.round(877.0 * k);
      BufferedImage out = new BufferedImage(pxWidth, pxHeight, 2);
      Graphics2D g = out.createGraphics();
      StampRenderer.hints(g);
      g.scale(k, k);
      g.setColor(PAPER);
      g.fillRect(0, 0, 620, 877);
      Paper.grain(g, 620.0, 877.0, (long)d.docId.hashCode() * 17L + (long)d.number.hashCode());
      g.setColor(new Color(205, 196, 176));
      g.setStroke(new BasicStroke(1.0F));
      g.draw(new Double(20.0, 20.0, 580.0, 837.0));
      if (!d.issued) {
         AffineTransform old = g.getTransform();
         g.translate(310.0, 438.5);
         g.rotate(Math.toRadians(-38.0));
         g.setColor(new Color(0, 0, 0, 20));
         Font f = new Font("Serif", 1, 96);
         g.setFont(f);
         String s = L.t("ЧЕРНОВИК");
         g.drawString(s, (float)(-g.getFontMetrics().stringWidth(s)) / 2.0F, 30.0F);
         g.setTransform(old);
      }

      double left = 50.0;
      double right = 570.0;
      double textW = 520.0;
      double y = 56.0;
      String org = d.org != null && !d.org.isBlank() ? d.org.trim() : "";
      if (org.isEmpty()) {
         g.setColor(new Color(150, 146, 136));
         g.setFont(new Font("Serif", 2, 14));
         drawCentered(g, L.t("(организация не указана)"), 310.0, y + 14.0);
         y += 24.0;
      } else {
         g.setColor(INK);
         Font f = new Font("Serif", 1, 16);
         g.setFont(f);

         for (String ln : wrap(g.getFontMetrics(), org.toUpperCase(new Locale("ru", "RU")), 480.0, 2)) {
            y += 19.0;
            drawCentered(g, ln, 310.0, y);
         }

         y += 6.0;
      }

      g.setColor(INK);
      g.setStroke(new BasicStroke(1.2F));
      g.draw(new java.awt.geom.Line2D.Double(110.0, y, 510.0, y));
      y += 52.0;
      Map<TextAttribute, Object> attrs = new HashMap<>();
      attrs.put(TextAttribute.FAMILY, "Serif");
      attrs.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
      attrs.put(TextAttribute.SIZE, 25.0F);
      attrs.put(TextAttribute.TRACKING, 0.14F);
      g.setFont(Font.getFont(attrs));
      g.setColor(INK);
      drawCentered(g, L.t(d.title()), 310.0, y);
      y += 34.0;
      g.setFont(new Font("Serif", 0, 13));
      String num = d.number != null && !d.number.isBlank() ? d.number.trim() : "____";
      g.setColor(INK);
      g.drawString("№ " + num, 50.0F, (float)y);
      String date = d.date != null && !d.date.isBlank() ? L.t("от ") + d.date + L.t(" г.") : "";
      if (date.isEmpty()) {
         g.setColor(new Color(160, 156, 146));
         date = L.t("от __.__.____ г.");
      }

      FontMetrics fm13 = g.getFontMetrics();
      g.drawString(date, (float)(570.0 - (double)fm13.stringWidth(date)), (float)y);
      y += 12.0;
      if (d.subject != null && !d.subject.isBlank()) {
         g.setColor(INK);
         g.setFont(new Font("Serif", 2, 13));

         for (String ln : wrap(g.getFontMetrics(), d.subject.trim(), 430.0, 3)) {
            y += 17.0;
            drawCentered(g, ln, 310.0, y);
         }

         y += 4.0;
      }

      y += 14.0;
      double bodyBottom = 700.0;
      double lineH = 17.5;
      boolean overflow = false;
      g.setColor(INK);
      Font bodyFont = new Font("Serif", 0, 13);
      g.setFont(bodyFont);
      FontMetrics fm = g.getFontMetrics();
      String body = d.body == null ? "" : d.body.replace("\r", "");
      String[] paragraphs = body.split("\n", -1);

      label133:
      for (String para : paragraphs) {
         if (para.isBlank()) {
            y += 10.5;
         } else {
            List<String> lines = wrapIndented(fm, para.trim(), 520.0, 26.0);

            for (int i = 0; i < lines.size(); i++) {
               if (y + 17.5 > 700.0) {
                  overflow = true;
                  break label133;
               }

               y += 17.5;
               double x = 50.0 + (double)(i == 0 ? 26 : 0);
               g.drawString(lines.get(i), (float)x, (float)y);
            }
         }
      }

      if (overflow) {
         g.setColor(new Color(190, 40, 40));
         g.setFont(new Font("Serif", 1, 12));
         g.drawString(L.t("… текст не помещается на страницу"), 50.0F, 712.0F);
      }

      List<DocModel.Sig> sigs = d.sigs;
      if (sigs.isEmpty()) {
         drawSigCell(g, 50.0, 726.0, d.position, d.author, null, true);
      } else {
         for (int i = 0; i < Math.min(4, sigs.size()); i++) {
            double x0 = 50.0 + (double)(i % 2 * 270);
            double y0 = (double)(726 + i / 2 * 64);
            DocModel.Sig s = sigs.get(i);
            drawSigCell(g, x0, y0, s.position, s.name, s.strokes, false);
         }
      }

      drawStamps(g, d.stamps, 0, 620.0, 877.0, src, k, d.docId);
      if (d.issued) {
         Paper.wear(g, new Double(0.0, 0.0, 620.0, 877.0), Paper.ageDays(d.date), (long)d.docId.hashCode(), true);
      }

      if (d.copy) {
         copyMark(g);
      }

      if (d.issued) {
         String code = d.sec.isEmpty() ? (d.docId.length() >= 8 ? d.docId.substring(0, 8).toUpperCase() : d.docId) : d.sec;
         Security.microBorder(g, 24.0, 24.0, 572.0, 829.0, "REALDOCS · " + code, new Color(160, 150, 125));
         if (!d.sec.isEmpty()) {
            Security.codeStrip(g, 400.0, 815.0, 170.0, d.sec, new Color(60, 58, 54));
         }

         Security.hologram(g, 528.0, 34.0, 54.0, 40.0, Security.seed(code));
      }

      if (d.issued && d.docId != null && !d.docId.isEmpty()) {
         g.setColor(GRAY);
         g.setFont(new Font("Serif", 0, 8));
         String foot = L.t("Документ ") + d.docId.substring(0, Math.min(8, d.docId.length())).toUpperCase() + L.t(" · подлинность проверяется на сервере");
         drawCentered(g, foot, 310.0, 851.0);
      }

      if (uv != 0) {
         Security.uv(g, new Shape[]{new Double(0.0, 0.0, 620.0, 877.0)}, d.sec.isEmpty() ? d.docId : d.sec, uv == 1 && d.issued, () -> {
            g.setColor(Security.uvGlow());
            g.setStroke(new BasicStroke(3.0F));
            g.draw(new java.awt.geom.Ellipse2D.Double(190.0, 318.5, 240.0, 240.0));
            g.setFont(new Font("Serif", 1, 30));
            drawCentered(g, L.t(d.title()), 310.0, 448.5);
         });
      }

      drawDisclaimer(g, 310.0, 865.0);
      g.dispose();
      return new DocRenderer.Result(out, overflow);
   }

   private static void drawSigCell(Graphics2D g, double x0, double y0, String position, String name, int[] strokes, boolean placeholder) {
      g.setColor(placeholder ? new Color(150, 146, 136) : INK);
      String pos = position != null && !position.isBlank() ? position.trim() : L.t("Должность");
      Font f = StampRenderer.fit(g, pos, 255.0, 11.0, 7.0, 0);
      g.setFont(f);
      g.drawString(pos, (float)x0, (float)(y0 + 10.0));
      g.setColor(INK);
      g.setStroke(new BasicStroke(0.9F));
      g.draw(new java.awt.geom.Line2D.Double(x0, y0 + 50.0, x0 + 250.0, y0 + 50.0));
      if (strokes != null && strokes.length > 0) {
         drawSignature(g, strokes, x0 + 70.0, y0 + 8.0, 130.0, 46.0, SIG_INK);
      }

      String nm = name != null && !name.isBlank() ? "/ " + name.trim() + " /" : "";
      if (nm.isEmpty()) {
         nm = "/ ____________ /";
         g.setColor(new Color(160, 156, 146));
      } else {
         g.setColor(INK);
      }

      Font nf = StampRenderer.fit(g, nm, 250.0, 10.5, 7.0, 0);
      g.setFont(nf);
      FontMetrics fm = g.getFontMetrics();
      g.drawString(nm, (float)(x0 + (double)(250 - fm.stringWidth(nm)) / 2.0), (float)(y0 + 62.0));
   }

   public static void drawSignature(Graphics2D g, int[] strokes, double x, double y, double w, double h, Color ink) {
      double s = Math.min(w / 256.0, h / 96.0);
      double ox = x + (w - 256.0 * s) / 2.0;
      double oy = y + (h - 96.0 * s) / 2.0;
      SigStyle st = SigStyle.of(strokes);
      boolean header = SigStyle.hasHeader(strokes);
      Color c = header ? new Color(SigStyle.COLORS[st.color]) : ink;
      Composite oc = g.getComposite();
      List<double[]> cur = new ArrayList<>();
      int seed = 1;

      for (int i = header ? 1 : 0; i <= strokes.length; i++) {
         boolean end = i == strokes.length || strokes[i] == -1;
         if (end) {
            drawStroke(g, cur, ox, oy, s, st, c, seed++);
            cur.clear();
         } else {
            int v = strokes[i];
            cur.add(new double[]{(double)(v & 0xFF), (double)(v >> 8 & 0xFF)});
         }
      }

      g.setComposite(oc);
   }

   private static void drawStroke(Graphics2D g, List<double[]> pts, double ox, double oy, double s, SigStyle st, Color c, int seed) {
      if (!pts.isEmpty()) {
         double base = 1.6 * (double)st.size * s;
         g.setColor(c);
         if (pts.size() == 1) {
            double[] p = pts.get(0);
            double r = Math.max(1.0, base * 0.6);
            g.fill(new java.awt.geom.Ellipse2D.Double(ox + p[0] * s - r, oy + p[1] * s - r, 2.0 * r, 2.0 * r));
         } else {
            switch (st.brush) {
               case 1:
                  double wPrev = base * 1.3;

                  for (int ix = 1; ix < pts.size(); ix++) {
                     double[] a = pts.get(ix - 1);
                     double[] b = pts.get(ix);
                     double d = Math.hypot(b[0] - a[0], b[1] - a[1]);
                     double target = base * Math.max(0.3, 2.0 - Math.min(1.7, d / 5.0));
                     double ww = wPrev + (target - wPrev) * 0.35;
                     double tEnd = Math.min(1.0, (double)Math.min(ix, pts.size() - ix) / 4.0);
                     ww *= 0.45 + 0.55 * tEnd;
                     g.setStroke(new BasicStroke((float)Math.max(0.6, ww), 1, 1));
                     g.draw(new java.awt.geom.Line2D.Double(ox + a[0] * s, oy + a[1] * s, ox + b[0] * s, oy + b[1] * s));
                     wPrev = ww;
                  }
                  break;
               case 2:
                  double nx = Math.cos(Math.toRadians(-45.0)) * base * 1.25;
                  double ny = Math.sin(Math.toRadians(-45.0)) * base * 1.25;
                  g.setStroke(new BasicStroke((float)Math.max(0.5, s * 0.6)));

                  for (int ix = 1; ix < pts.size(); ix++) {
                     double[] a = pts.get(ix - 1);
                     double[] b = pts.get(ix);
                     double ax = ox + a[0] * s;
                     double ay = oy + a[1] * s;
                     double bx = ox + b[0] * s;
                     double by = oy + b[1] * s;
                     java.awt.geom.Path2D.Double q = new java.awt.geom.Path2D.Double();
                     q.moveTo(ax - nx, ay - ny);
                     q.lineTo(ax + nx, ay + ny);
                     q.lineTo(bx + nx, by + ny);
                     q.lineTo(bx - nx, by - ny);
                     q.closePath();
                     g.fill(q);
                     g.draw(q);
                  }
                  break;
               case 3:
                  g.setComposite(AlphaComposite.SrcOver.derive(0.82F));
                  g.setStroke(new BasicStroke((float)Math.max(1.5, base * 2.1), 1, 1));
                  g.draw(smooth(pts, ox, oy, s));
                  g.setComposite(AlphaComposite.SrcOver);
                  break;
               case 4:
                  Random rnd = new Random((long)seed * 7919L + (long)pts.size());
                  Color graphite = new Color((c.getRed() + 70) / 2, (c.getGreen() + 70) / 2, (c.getBlue() + 74) / 2);
                  g.setColor(graphite);

                  for (int pass = 0; pass < 3; pass++) {
                     g.setComposite(AlphaComposite.SrcOver.derive(0.33F));
                     g.setStroke(new BasicStroke((float)Math.max(0.6, base * 0.55), 1, 1));
                     java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

                     for (int i = 0; i < pts.size(); i++) {
                        double jx = (rnd.nextDouble() - 0.5) * s * 1.1;
                        double jy = (rnd.nextDouble() - 0.5) * s * 1.1;
                        double px = ox + pts.get(i)[0] * s + jx;
                        double py = oy + pts.get(i)[1] * s + jy;
                        if (i == 0) {
                           p.moveTo(px, py);
                        } else {
                           p.lineTo(px, py);
                        }
                     }

                     g.draw(p);
                  }

                  g.setComposite(AlphaComposite.SrcOver);
                  break;
               default:
                  g.setStroke(new BasicStroke((float)Math.max(0.9, base), 1, 1));
                  g.draw(smooth(pts, ox, oy, s));
            }
         }
      }
   }

   private static java.awt.geom.Path2D.Double smooth(List<double[]> pts, double ox, double oy, double s) {
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      double[] p0 = pts.get(0);
      path.moveTo(ox + p0[0] * s, oy + p0[1] * s);

      for (int i = 1; i < pts.size() - 1; i++) {
         double[] a = pts.get(i);
         double[] b = pts.get(i + 1);
         path.quadTo(ox + a[0] * s, oy + a[1] * s, ox + (a[0] + b[0]) / 2.0 * s, oy + (a[1] + b[1]) / 2.0 * s);
      }

      double[] last = pts.get(pts.size() - 1);
      path.lineTo(ox + last[0] * s, oy + last[1] * s);
      return path;
   }

   static void drawStampPlaceholder(Graphics2D g) {
      g.setColor(new Color(120, 120, 140, 150));
      g.setStroke(new BasicStroke(2.0F, 0, 0, 10.0F, new float[]{6.0F, 5.0F}, 0.0F));
      g.draw(new java.awt.geom.Ellipse2D.Double(-60.0, -60.0, 120.0, 120.0));
      g.setFont(new Font("Serif", 1, 14));
      drawCentered(g, L.t("печать…"), 0.0, 5.0);
   }

   public static double stampWidth(StampModel sm) {
      if (sm.kind == 2) {
         return 250.0;
      } else if (sm.kind != 1) {
         switch (sm.shape) {
            case 0:
               return 132.0;
            case 3:
               return 120.0;
            default:
               return 158.0;
         }
      } else {
         return sm.shape != 0 && sm.shape != 1 ? 150.0 : 118.0;
      }
   }

   public static void drawStamps(Graphics2D g, List<DocModel.StampRef> refs, int page, double pw, double ph, DocRenderer.StampSource src, double k, String seed) {
      for (DocModel.StampRef ref : refs) {
         if (ref.page == page) {
            StampModel sm = src == null ? null : src.def(ref.id);
            double sc = (double)ref.scale / 100.0;
            AffineTransform old = g.getTransform();
            g.translate((double)ref.x / 1000.0 * pw, (double)ref.y / 1000.0 * ph);
            g.rotate(Math.toRadians((double)ref.rot));
            if (sm == null) {
               g.scale(sc, sc);
               drawStampPlaceholder(g);
            } else {
               BufferedImage img = sm.hasImage ? src.image(ref.id) : null;
               double w = stampWidth(sm) * sc;
               BufferedImage st = StampRenderer.render(sm, img, Math.max(64, (int)Math.round(w * k * 2.0)), ref);
               double h = (double)st.getHeight() * (w / (double)st.getWidth());
               g.setComposite(AlphaComposite.SrcOver.derive(sm.kind == 2 ? 1.0F : 0.93F));
               g.drawImage(st, (int)Math.round(-w / 2.0), (int)Math.round(-h / 2.0), (int)Math.round(w), (int)Math.round(h), null);
               g.setComposite(AlphaComposite.SrcOver);
            }

            g.setTransform(old);
         }
      }
   }

   static void copyMark(Graphics2D g) {
      AffineTransform old = g.getTransform();
      g.setColor(new Color(90, 90, 100, 46));
      g.setFont(new Font("SansSerif", 1, 34));

      for (int row = -2; row < 9; row++) {
         g.setTransform(old);
         g.translate(0.0, (double)row * 130.0);
         g.rotate(Math.toRadians(-30.0), 310.0, 438.5);
         String s = L.t("КОПИЯ") + " · COPY · " + L.t("КОПИЯ");
         g.drawString(s, (float)(310.0 - (double)g.getFontMetrics().stringWidth(s) / 2.0), 438.5F);
      }

      g.setTransform(old);
   }

   public static void drawGameMark(Graphics2D g, double x, double y, double w, double h) {
      drawGameMark(g, x, y, w, h, false);
   }

   public static void drawGameMark(Graphics2D g, double x, double y, double w, double h, boolean dark) {
      Shape oldClip = g.getClip();
      AffineTransform oldTx = g.getTransform();
      Composite oldComp = g.getComposite();
      g.clip(new Double(x, y, w, h));
      g.setComposite(AlphaComposite.SrcOver.derive(dark ? 0.26F : 0.16F));
      g.setColor(dark ? new Color(255, 200, 200) : new Color(150, 20, 20));
      g.setFont(new Font("SansSerif", 1, 15));
      String s = L.t("ВНУТРИИГРОВОЙ ДОКУМЕНТ · НЕ ЯВЛЯЕТСЯ НАСТОЯЩИМ · ");
      double cx = x + w / 2.0;
      double cy = y + h / 2.0;
      g.rotate(Math.toRadians(-24.0), cx, cy);
      FontMetrics fm = g.getFontMetrics();
      double diag = Math.hypot(w, h);
      int sw = Math.max(1, fm.stringWidth(s));
      int row = 0;

      for (double yy = cy - diag / 2.0; yy < cy + diag / 2.0; row++) {
         double start = cx - diag / 2.0 - (double)(row % 2 * sw) / 2.0;

         for (double xx = start; xx < cx + diag / 2.0; xx += (double)sw) {
            g.drawString(s, (float)xx, (float)yy);
         }

         yy += 46.0;
      }

      g.setTransform(oldTx);
      g.setComposite(oldComp);
      g.setClip(oldClip);
   }

   public static void drawDisclaimer(Graphics2D g, double cx, double baseline) {
      g.setColor(new Color(130, 126, 118));
      Map<TextAttribute, Object> a = new HashMap<>();
      a.put(TextAttribute.FAMILY, "Serif");
      a.put(TextAttribute.SIZE, 7.0F);
      a.put(TextAttribute.TRACKING, 0.06F);
      g.setFont(Font.getFont(a));
      drawCentered(g, L.t("ВНУТРИИГРОВОЙ ДОКУМЕНТ · ЮРИДИЧЕСКОЙ СИЛЫ НЕ ИМЕЕТ"), cx, baseline);
   }

   static void drawCentered(Graphics2D g, String s, double cx, double baseline) {
      FontMetrics fm = g.getFontMetrics();
      g.drawString(s, (float)(cx - (double)fm.stringWidth(s) / 2.0), (float)baseline);
   }

   static List<String> wrap(FontMetrics fm, String text, double width, int maxLines) {
      List<String> all = wrapIndented(fm, text, width, 0.0);
      if (all.size() <= maxLines) {
         return all;
      } else {
         List<String> cut = new ArrayList<>(all.subList(0, maxLines));
         String last = cut.get(maxLines - 1);
         cut.set(maxLines - 1, last + "…");
         return cut;
      }
   }

   static List<String> wrapIndented(FontMetrics fm, String text, double width, double firstIndent) {
      List<String> lines = new ArrayList<>();
      StringBuilder cur = new StringBuilder();
      double avail = width - firstIndent;

      for (String word : text.split("\\s+")) {
         if (!word.isEmpty()) {
            while ((double)fm.stringWidth(word) > avail) {
               int cut = 1;

               while (cut < word.length() && (double)fm.stringWidth(word.substring(0, cut + 1)) <= avail) {
                  cut++;
               }

               if (cur.length() > 0) {
                  lines.add(cur.toString());
                  cur.setLength(0);
               }

               lines.add(word.substring(0, cut));
               avail = width;
               word = word.substring(cut);
            }

            String candidate = cur.length() == 0 ? word : cur + " " + word;
            if ((double)fm.stringWidth(candidate) <= avail) {
               cur.setLength(0);
               cur.append(candidate);
            } else {
               lines.add(cur.toString());
               avail = width;
               cur.setLength(0);
               cur.append(word);
            }
         }
      }

      if (cur.length() > 0) {
         lines.add(cur.toString());
      }

      if (lines.isEmpty()) {
         lines.add("");
      }

      return lines;
   }

   public static record Result(BufferedImage image, boolean overflow) {
   }

   public interface StampSource {
      StampModel def(String var1);

      BufferedImage image(String var1);
   }
}
