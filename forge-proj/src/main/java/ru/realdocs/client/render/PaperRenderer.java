package ru.realdocs.client.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.realdocs.L;
import ru.realdocs.model.PaperElement;
import ru.realdocs.model.PaperModel;

/** Draws newspaper pages: multi-column articles, headlines, filtered pictures and rules. */
public final class PaperRenderer {
   public interface Images {
      BufferedImage image(String id);
   }

   public static final class Result {
      public final BufferedImage image;
      /** Indices of article blocks whose text did not fit. */
      public final Set<Integer> overflow;

      Result(BufferedImage image, Set<Integer> overflow) {
         this.image = image;
         this.overflow = overflow;
      }
   }

   private PaperRenderer() {
   }

   public static Result render(PaperModel m, int page, int pxWidth, Images images, boolean editor) {
      double k = (double)pxWidth / PaperModel.W;
      int pxHeight = (int)Math.round(PaperModel.H * k);
      BufferedImage out = new BufferedImage(pxWidth, pxHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      StampRenderer.hints(g);
      Fonts.hints(g);
      g.scale(k, k);
      int tone = PaperModel.TONE_RGB[Math.max(0, Math.min(PaperModel.TONE_RGB.length - 1, m.tone))];
      g.setColor(new Color(tone));
      g.fillRect(0, 0, PaperModel.W, PaperModel.H);
      Paper.grain(g, PaperModel.W, PaperModel.H, (long)m.id.hashCode() * 31L + page);
      List<PaperElement> els = m.page(page);
      Map<String, String> tokens = m.tokens(page);
      Set<Integer> overflow = new HashSet<>();

      for (int i = 0; i < els.size(); i++) {
         PaperElement e = els.get(i);
         Composite oc = g.getComposite();
         Shape clip = g.getClip();
         g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.05F, Math.min(1.0F, e.alpha / 100.0F))));

         try {
            switch (e.type) {
               case PaperElement.TEXT:
                  if (!article(g, e, tokens)) {
                     overflow.add(i);
                  }
                  break;
               case PaperElement.TITLE:
                  title(g, e, tokens);
                  break;
               case PaperElement.IMAGE:
                  image(g, e, images, k, editor);
                  break;
               case PaperElement.LINE:
               case PaperElement.BAND:
                  g.setColor(new Color(e.color));
                  g.fill(new Rectangle2D.Double(e.x, e.y, e.w, e.h));
                  break;
               case PaperElement.FRAME:
                  g.setColor(new Color(e.color));
                  float t = Math.max(1, e.thick);
                  g.setStroke(new BasicStroke(t));
                  g.draw(new Rectangle2D.Double(e.x + t / 2.0, e.y + t / 2.0, e.w - t, e.h - t));
            }
         } catch (RuntimeException var15) {
         }

         g.setClip(clip);
         g.setComposite(oc);
      }

      g.dispose();
      return new Result(out, overflow);
   }

   // ---------------------------------------------------------------- text model

   /** A run of text inside one word, with its own style. */
   private record Seg(String text, boolean bold, boolean italic) {
   }

   private static final class Word {
      final List<Seg> segs = new ArrayList<>(1);
      double width;
   }

   private static final class Para {
      final List<Word> words = new ArrayList<>();
      int heading;
   }

   private static final class Faces {
      final Fonts.Face[] f = new Fonts.Face[4];
      final int family;
      final float size;
      final float tracking;

      Faces(int family, float size, float tracking) {
         this.family = family;
         this.size = size;
         this.tracking = tracking;
      }

      Fonts.Face get(boolean bold, boolean italic) {
         int i = (bold ? 1 : 0) + (italic ? 2 : 0);
         if (this.f[i] == null) {
            this.f[i] = Fonts.face(this.family, bold, italic, this.size, this.tracking);
         }

         return this.f[i];
      }
   }

   public static String expand(String text, Map<String, String> tokens) {
      return DesignRenderer.expand(text, tokens);
   }

   /** Splits text into paragraphs and words; ** toggles bold, __ toggles italic, "# " starts a heading line. */
   private static List<Para> parse(String text, boolean baseBold, boolean baseItalic, boolean caps) {
      List<Para> out = new ArrayList<>();

      for (String raw : text.replace("\r", "").split("\n", -1)) {
         Para p = new Para();
         String line = raw;
         if (line.startsWith("## ")) {
            p.heading = 2;
            line = line.substring(3);
         } else if (line.startsWith("# ")) {
            p.heading = 1;
            line = line.substring(2);
         }

         if (caps) {
            line = line.toUpperCase(Locale.ROOT);
         }

         boolean bold = baseBold;
         boolean italic = baseItalic;
         Word w = new Word();
         StringBuilder cur = new StringBuilder();
         int i = 0;

         while (i <= line.length()) {
            char c = i < line.length() ? line.charAt(i) : ' ';
            boolean end = i == line.length();
            if (!end && c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
               if (cur.length() > 0) {
                  w.segs.add(new Seg(cur.toString(), bold || p.heading > 0, italic));
                  cur.setLength(0);
               }

               bold = !bold;
               i += 2;
            } else if (!end && c == '_' && i + 1 < line.length() && line.charAt(i + 1) == '_') {
               if (cur.length() > 0) {
                  w.segs.add(new Seg(cur.toString(), bold || p.heading > 0, italic));
                  cur.setLength(0);
               }

               italic = !italic;
               i += 2;
            } else if (end || c == ' ' || c == '\t') {
               if (cur.length() > 0) {
                  w.segs.add(new Seg(cur.toString(), bold || p.heading > 0, italic));
                  cur.setLength(0);
               }

               if (!w.segs.isEmpty()) {
                  p.words.add(w);
                  w = new Word();
               }

               i++;
            } else {
               cur.append(c);
               i++;
            }
         }

         out.add(p);
      }

      return out;
   }

   private static double measure(Word w, Faces faces) {
      double s = 0.0;

      for (Seg g : w.segs) {
         s += faces.get(g.bold, g.italic).width(g.text);
      }

      return s;
   }

   private static double drawWord(Graphics2D g, Word w, Faces faces, double x, double baseline) {
      for (Seg s : w.segs) {
         Fonts.Face f = faces.get(s.bold, s.italic);
         f.draw(g, s.text, x, baseline);
         x += f.width(s.text);
      }

      return x;
   }

   /** Breaks a word that is wider than the column into pieces. */
   private static List<Word> split(Word w, Faces faces, double max) {
      List<Word> out = new ArrayList<>();
      Word cur = new Word();
      double cw = 0.0;

      for (Seg s : w.segs) {
         Fonts.Face f = faces.get(s.bold, s.italic);
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < s.text.length(); i++) {
            char c = s.text.charAt(i);
            double add = f.width(String.valueOf(c));
            if (cw + add > max && (cw > 0.0 || sb.length() > 0)) {
               if (sb.length() > 0) {
                  cur.segs.add(new Seg(sb.toString(), s.bold, s.italic));
                  sb.setLength(0);
               }

               cur.width = cw;
               out.add(cur);
               cur = new Word();
               cw = 0.0;
            }

            sb.append(c);
            cw += add;
         }

         if (sb.length() > 0) {
            cur.segs.add(new Seg(sb.toString(), s.bold, s.italic));
         }
      }

      if (!cur.segs.isEmpty()) {
         cur.width = cw;
         out.add(cur);
      }

      return out;
   }

   /** Multi-column justified article. Returns false if the text did not fit into the block. */
   private static boolean article(Graphics2D g, PaperElement e, Map<String, String> tokens) {
      String text = expand(e.text, tokens);
      if (text.isBlank()) {
         return true;
      }

      g.setColor(new Color(e.color));
      g.clip(new Rectangle2D.Double(e.x - 2, e.y - 2, e.w + 4, e.h + 4));
      float size = Math.max(5, e.size);
      Faces body = new Faces(e.font, size, e.tracking / 100.0F);
      Faces h1 = new Faces(e.font, size * 1.45F, e.tracking / 100.0F);
      Faces h2 = new Faces(e.font, size * 1.18F, e.tracking / 100.0F);
      int cols = Math.max(1, Math.min(5, e.cols));
      double gutter = Math.max(8.0, size * 1.1);
      double colW = (e.w - gutter * (cols - 1)) / cols;
      if (colW < size * 2) {
         return false;
      }

      double lineH = size * e.lead / 100.0;
      double ascent = body.get(false, false).ascent();
      List<Para> paras = parse(text, e.bold(), e.italic(), e.has(PaperElement.F_CAPS));
      double indent = e.has(PaperElement.F_INDENT) ? size * 1.4 : 0.0;
      int col = 0;
      double y = 0.0;
      boolean first = true;
      boolean fits = true;
      int dropLines = 0;
      double dropW = 0.0;
      if (e.has(PaperElement.F_DROPCAP)) {
         for (Para p : paras) {
            if (p.words.isEmpty()) {
               continue;
            }

            if (p.heading == 0) {
               Seg s0 = p.words.get(0).segs.get(0);
               String letter = s0.text.substring(0, s0.text.offsetByCodePoints(0, 1));
               dropLines = 3;
               float capSize = (float)(lineH * (dropLines - 1) + ascent * 0.98);
               Fonts.Face cap = Fonts.face(e.font, true, false, capSize);
               double capH = cap.capHeight();
               float fixed = (float)(capSize * (lineH * (dropLines - 1) + ascent * 0.72) / Math.max(1.0, capH));
               cap = Fonts.face(e.font, true, false, fixed);
               dropW = cap.width(letter) + size * 0.35;
               cap.draw(g, letter, e.x, e.y + ascent + lineH * (dropLines - 1));
               String rest = s0.text.substring(letter.length());
               p.words.get(0).segs.set(0, new Seg(rest, s0.bold, s0.italic));
            }

            break;
         }
      }

      int lineNo = 0;

      outer:
      for (int pi = 0; pi < paras.size(); pi++) {
         Para p = paras.get(pi);
         Faces f = p.heading == 1 ? h1 : (p.heading == 2 ? h2 : body);
         double lh = p.heading == 1 ? lineH * 1.45 : (p.heading == 2 ? lineH * 1.18 : lineH);
         double asc = f.get(false, false).ascent();
         if (p.words.isEmpty()) {
            if (!first) {
               y += lineH * 0.5;
            }

            continue;
         }

         if (p.heading > 0 && !first) {
            y += lineH * 0.35;
         }

         List<Word> words = new ArrayList<>();

         for (Word w : p.words) {
            w.width = measure(w, f);
            if (w.width > colW * 0.98) {
               words.addAll(split(w, f, colW * 0.98));
            } else {
               words.add(w);
            }
         }

         double space = f.get(false, false).width(" ");
         int wi = 0;
         boolean firstLine = true;

         while (wi < words.size()) {
            if (y + lh > e.h + 0.5) {
               col++;
               y = 0.0;
               if (col >= cols) {
                  fits = false;
                  break outer;
               }
            }

            double left = (firstLine && p.heading == 0 && !(first && dropLines > 0) ? indent : 0.0);
            if (col == 0 && lineNo < dropLines) {
               left = dropW;
            }

            double avail = colW - left;
            List<Word> line = new ArrayList<>();
            double used = 0.0;

            while (wi < words.size()) {
               Word w = words.get(wi);
               double need = line.isEmpty() ? w.width : used + space + w.width;
               if (need > avail && !line.isEmpty()) {
                  break;
               }

               if (need > avail && line.isEmpty() && w.width > avail) {
                  line.add(w);
                  used = w.width;
                  wi++;
                  break;
               }

               line.add(w);
               used = need;
               wi++;
            }

            boolean last = wi >= words.size();
            double x0 = e.x + col * (colW + gutter) + left;
            double baseline = e.y + y + asc;
            int align = p.heading > 0 && e.align == PaperElement.ALIGN_JUSTIFY ? PaperElement.ALIGN_LEFT : e.align;
            double gap = space;
            double x = x0;
            if (align == PaperElement.ALIGN_JUSTIFY && !last && line.size() > 1) {
               gap = space + (avail - used) / (line.size() - 1);
            } else if (align == PaperElement.ALIGN_CENTER) {
               x = x0 + (avail - used) / 2.0;
            } else if (align == PaperElement.ALIGN_RIGHT) {
               x = x0 + avail - used;
            }

            for (Word w : line) {
               x = drawWord(g, w, f, x, baseline) + gap;
            }

            y += lh;
            lineNo++;
            firstLine = false;
            first = false;
         }

         if (p.heading > 0) {
            y += lineH * 0.2;
         }
      }

      if (e.has(PaperElement.F_RULES) && cols > 1) {
         g.setStroke(new BasicStroke(0.6F));
         Color c = new Color(e.color);
         g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 120));

         for (int c2 = 1; c2 < cols; c2++) {
            double rx = e.x + c2 * (colW + gutter) - gutter / 2.0;
            g.draw(new java.awt.geom.Line2D.Double(rx, e.y + 2, rx, e.y + e.h - 2));
         }
      }

      return fits;
   }

   /** Headline: auto-shrinks until every line fits the box; "|" or a new line breaks lines. */
   private static void title(Graphics2D g, PaperElement e, Map<String, String> tokens) {
      String text = expand(e.text, tokens);
      if (text.isBlank()) {
         return;
      }

      if (e.has(PaperElement.F_CAPS)) {
         text = text.toUpperCase(Locale.ROOT);
      }

      String[] lines = text.replace("\r", "").replace('|', '\n').split("\n");
      float size = Math.max(5, e.size);
      float track = e.tracking / 100.0F;
      Fonts.Face f;

      while (true) {
         f = Fonts.face(e.font, e.bold(), e.italic(), size, track);
         double widest = 0.0;

         for (String l : lines) {
            widest = Math.max(widest, f.width(l.trim()));
         }

         double total = size * (e.lead / 100.0) * (lines.length - 1) + f.ascent() + f.descent() * 0.6;
         if (widest <= e.w && total <= e.h + size * 0.2 || size <= 5.0F) {
            break;
         }

         size -= Math.max(0.5F, size * 0.04F);
      }

      g.setColor(new Color(e.color));
      double lineH = size * e.lead / 100.0;
      double total = lineH * (lines.length - 1) + f.ascent() + f.descent() * 0.6;
      double baseline = e.y + (e.h - total) / 2.0 + f.ascent();

      for (String raw : lines) {
         String l = raw.trim();
         double lw = f.width(l);
         double x = switch (e.align) {
            case PaperElement.ALIGN_CENTER -> e.x + (e.w - lw) / 2.0;
            case PaperElement.ALIGN_RIGHT -> e.x + e.w - lw;
            default -> e.x;
         };
         if (e.align == PaperElement.ALIGN_JUSTIFY && lines.length == 1) {
            String[] words = l.split(" +");
            if (words.length > 1) {
               double ww = 0.0;

               for (String w : words) {
                  ww += f.width(w);
               }

               double gap = (e.w - ww) / (words.length - 1);
               double cx = e.x;

               for (String w : words) {
                  f.draw(g, w, cx, baseline);
                  cx += f.width(w) + gap;
               }

               baseline += lineH;
               continue;
            }
         }

         f.draw(g, l, x, baseline);
         baseline += lineH;
      }
   }

   // ---------------------------------------------------------------- pictures

   private static void image(Graphics2D g, PaperElement e, Images images, double k, boolean editor) {
      BufferedImage src = e.asset.isEmpty() || images == null ? null : images.image(e.asset);
      Rectangle2D box = new Rectangle2D.Double(e.x, e.y, e.w, e.h);
      if (src == null) {
         if (editor || !e.asset.isEmpty()) {
            g.setColor(new Color(0, 0, 0, 22));
            g.fill(box);
            g.setColor(new Color(0, 0, 0, 90));
            g.setStroke(new BasicStroke(1.0F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0F, new float[]{5.0F, 4.0F}, 0.0F));
            g.draw(box);
            g.draw(new java.awt.geom.Line2D.Double(e.x, e.y, e.x + e.w, e.y + e.h));
            g.draw(new java.awt.geom.Line2D.Double(e.x + e.w, e.y, e.x, e.y + e.h));
            Fonts.Face f = Fonts.face(Fonts.SANS, false, false, 11.0F);
            String s = e.asset.isEmpty() ? L.t("картинка") : L.t("загрузка…");
            double sw = f.width(s);
            g.setColor(new Color(250, 248, 240, 220));
            g.fill(new Rectangle2D.Double(e.x + (e.w - sw) / 2.0 - 4.0, e.y + e.h / 2.0 - 10.0, sw + 8.0, 16.0));
            g.setColor(new Color(60, 60, 60));
            f.draw(g, s, e.x + (e.w - sw) / 2.0, e.y + e.h / 2.0 + 2.0);
         }

         return;
      }

      int pw = Math.max(1, (int)Math.round(e.w * k));
      int ph = Math.max(1, (int)Math.round(e.h * k));
      if ((long)pw * ph > 9000000L) {
         double s = Math.sqrt(9000000.0 / ((long)pw * ph));
         pw = Math.max(1, (int)(pw * s));
         ph = Math.max(1, (int)(ph * s));
      }

      BufferedImage tile = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
      Graphics2D tg = tile.createGraphics();
      tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      tg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      double sw = src.getWidth();
      double sh = src.getHeight();
      switch (e.fit) {
         case PaperElement.FIT_STRETCH:
            tg.drawImage(src, 0, 0, pw, ph, null);
            break;
         case PaperElement.FIT_CONTAIN: {
            double s = Math.min(pw / sw, ph / sh);
            int dw = (int)Math.round(sw * s);
            int dh = (int)Math.round(sh * s);
            tg.drawImage(src, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
            break;
         }
         default: {
            double s = Math.max(pw / sw, ph / sh);
            int dw = (int)Math.ceil(sw * s);
            int dh = (int)Math.ceil(sh * s);
            tg.drawImage(src, (pw - dw) / 2, (ph - dh) / 2, dw, dh, null);
         }
      }

      tg.dispose();
      BufferedImage shown = switch (e.filter) {
         case PaperElement.FILTER_GRAY -> gray(tile, false);
         case PaperElement.FILTER_SEPIA -> gray(tile, true);
         case PaperElement.FILTER_HALFTONE -> halftone(tile, Math.max(3.0, 4.2 * k));
         case PaperElement.FILTER_INK -> engrave(tile);
         default -> tile;
      };
      g.drawImage(shown, e.x, e.y, e.w, e.h, null);
      if (e.has(PaperElement.F_BORDER)) {
         g.setColor(new Color(30, 30, 30));
         g.setStroke(new BasicStroke(1.0F));
         g.draw(new Rectangle2D.Double(e.x + 0.5, e.y + 0.5, e.w - 1.0, e.h - 1.0));
      }
   }

   private static int lum(int argb) {
      return (int)(0.299 * (argb >> 16 & 0xFF) + 0.587 * (argb >> 8 & 0xFF) + 0.114 * (argb & 0xFF));
   }

   static BufferedImage gray(BufferedImage src, boolean sepia) {
      int w = src.getWidth();
      int h = src.getHeight();
      int[] px = src.getRGB(0, 0, w, h, null, 0, w);

      for (int i = 0; i < px.length; i++) {
         int a = px[i] >>> 24;
         int l = lum(px[i]);
         int r = l;
         int gg = l;
         int b = l;
         if (sepia) {
            r = Math.min(255, (int)(l * 1.07 + 18));
            gg = Math.min(255, (int)(l * 0.93 + 8));
            b = Math.min(255, (int)(l * 0.72));
         }

         px[i] = a << 24 | r << 16 | gg << 8 | b;
      }

      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      out.setRGB(0, 0, w, h, px, 0, w);
      return out;
   }

   /** Newspaper dot screen: ink dots on transparent paper, rotated 45°. */
   static BufferedImage halftone(BufferedImage src, double cell) {
      int w = src.getWidth();
      int h = src.getHeight();
      int[] px = src.getRGB(0, 0, w, h, null, 0, w);
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(new Color(22, 22, 24));
      double cos = Math.cos(Math.PI / 4);
      double sin = Math.sin(Math.PI / 4);
      double diag = Math.sqrt((double)w * w + (double)h * h);
      int n = (int)(diag / cell) + 2;

      for (int i = -n; i <= n; i++) {
         for (int j = -n; j <= n; j++) {
            double u = i * cell;
            double v = j * cell;
            double cx = w / 2.0 + u * cos - v * sin;
            double cy = h / 2.0 + u * sin + v * cos;
            if (cx >= -cell && cy >= -cell && cx < w + cell && cy < h + cell) {
               int sx = (int)Math.max(0, Math.min(w - 1, cx));
               int sy = (int)Math.max(0, Math.min(h - 1, cy));
               double sum = 0.0;
               double alpha = 0.0;
               int cnt = 0;
               int r = Math.max(1, (int)(cell / 2.5));

               for (int dy = -r; dy <= r; dy += Math.max(1, r / 2)) {
                  for (int dx = -r; dx <= r; dx += Math.max(1, r / 2)) {
                     int xx = Math.max(0, Math.min(w - 1, sx + dx));
                     int yy = Math.max(0, Math.min(h - 1, sy + dy));
                     int p = px[yy * w + xx];
                     sum += lum(p) / 255.0;
                     alpha += (p >>> 24) / 255.0;
                     cnt++;
                  }
               }

               double dark = 1.0 - sum / cnt;
               double a = alpha / cnt;
               if (a > 0.05 && dark > 0.02) {
                  double rad = cell * 0.72 * Math.sqrt(dark) * a;
                  g.fill(new Ellipse2D.Double(cx - rad, cy - rad, rad * 2.0, rad * 2.0));
               }
            }
         }
      }

      g.dispose();
      return out;
   }

   private static final int[][] BAYER = new int[][]{{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

   /** Old print look: ordered dithering in black ink, paper shows through. */
   static BufferedImage engrave(BufferedImage src) {
      int w = src.getWidth();
      int h = src.getHeight();
      int[] px = src.getRGB(0, 0, w, h, null, 0, w);
      int[] outPx = new int[px.length];

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            int p = px[y * w + x];
            int a = p >>> 24;
            double l = lum(p) / 255.0;
            double t = (BAYER[(y / 2) & 3][(x / 2) & 3] + 0.5) / 16.0;
            outPx[y * w + x] = a > 20 && l < t ? 0xFF1A1A1C : 0;
         }
      }

      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      out.setRGB(0, 0, w, h, outPx, 0, w);
      return out;
   }

   /** Picture prepared for upload: at most 1024 px and under the size limit. */
   public static BufferedImage limit(BufferedImage src, int maxSide) {
      int w = src.getWidth();
      int h = src.getHeight();
      double s = Math.min(1.0, (double)maxSide / Math.max(w, h));
      if (s >= 1.0 && src.getType() == BufferedImage.TYPE_INT_ARGB) {
         return src;
      } else {
         int nw = Math.max(1, (int)Math.round(w * s));
         int nh = Math.max(1, (int)Math.round(h * s));
         BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = out.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.drawImage(src, 0, 0, nw, nh, null);
         g.dispose();
         return out;
      }
   }
}
