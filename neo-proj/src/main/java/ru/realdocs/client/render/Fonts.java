package ru.realdocs.client.render;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bundled typefaces (SIL OFL, see assets/signedsealed/fonts/licenses). All of them cover
 * Latin (incl. German umlauts and ß) and, except Fraktur, Cyrillic. Missing glyphs fall back
 * per character to a matching bundled face, so text never turns into empty boxes and looks the
 * same on every OS (no system Cyrillic fonts needed on Linux).
 * <p>
 * The family index is stored in item/world data: append new families, never reorder.
 */
public final class Fonts {
   public static final int SERIF = 0;
   public static final int SANS = 1;
   public static final int OLD = 2;
   public static final int DIDONE = 3;
   public static final int POSTER = 4;
   public static final int TYPEWRITER = 5;
   public static final int HAND = 6;
   public static final int SCRIPT = 7;
   public static final int DECO = 8;
   public static final int PIXEL = 9;
   public static final int FRAKTUR = 10;

   /** Russian UI names (translated through lang files). */
   public static final String[] NAMES = new String[]{
      "Классический", "Гротеск", "Старая газета", "Дидона", "Плакатный", "Пишущая машинка", "Рукописный", "Каллиграфия", "Ар-деко", "Пиксельный", "Фрактура"
   };
   /** Short sample shown on font buttons. */
   public static final String SAMPLE = "Аа Ää";
   private static final String[][] FILES = new String[][]{
      {"ptserif_regular", "ptserif_bold", "ptserif_italic", "ptserif_bolditalic"},
      {"ptsans_regular", "ptsans_bold", "ptsans_italic", "ptsans_bolditalic"},
      {"oldstandard_regular", "oldstandard_bold", "oldstandard_italic", null},
      {"prata_regular", null, null, null},
      {"russoone_regular", null, null, null},
      {"plexmono_regular", "plexmono_bold", "plexmono_italic", "plexmono_bolditalic"},
      {"marckscript_regular", null, null, null},
      {"lobster_regular", null, null, null},
      {"poiretone_regular", null, null, null},
      {"pressstart2p_regular", null, null, null},
      {"unifrakturmaguntia_regular", null, null, null}
   };
   /** Which family supplies glyphs a face lacks (Fraktur has no Cyrillic: Old Standard matches it best). */
   private static final int[] FALLBACK = new int[]{SANS, SERIF, SERIF, SERIF, SANS, SANS, SERIF, SERIF, SANS, SANS, OLD};
   private static final Map<String, Font> BASE = new ConcurrentHashMap<>();
   private static final Map<String, Font> DERIVED = new ConcurrentHashMap<>();
   public static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), true, true);

   private Fonts() {
   }

   public static int count() {
      return FILES.length;
   }

   public static int clamp(int family) {
      return family >= 0 && family < FILES.length ? family : SERIF;
   }

   /** True if the family has real bold/italic faces (otherwise they are synthesized). */
   public static boolean hasStyle(int family, boolean bold, boolean italic) {
      return FILES[clamp(family)][(bold ? 1 : 0) + (italic ? 2 : 0)] != null;
   }

   private static Font base(String file) {
      Font f = BASE.get(file);
      if (f != null) {
         return f;
      } else {
         Font loaded = null;

         try (InputStream in = Fonts.class.getResourceAsStream("/assets/signedsealed/fonts/" + file + ".ttf")) {
            if (in != null) {
               loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            }
         } catch (IOException | FontFormatException var7) {
            loaded = null;
         }

         if (loaded == null) {
            loaded = new Font(file.startsWith("plexmono") ? "Monospaced" : (file.startsWith("ptsans") ? "SansSerif" : "Serif"), Font.PLAIN, 12);
         }

         BASE.put(file, loaded);
         return loaded;
      }
   }

   /** Font of a family in a style and size; missing styles are derived (synthetic bold/italic). */
   public static Font get(int family, boolean bold, boolean italic, float size) {
      family = clamp(family);
      String key = family + ":" + (bold ? 1 : 0) + (italic ? 1 : 0) + ":" + size;
      Font cached = DERIVED.get(key);
      if (cached != null) {
         return cached;
      } else {
         String[] files = FILES[family];
         int idx = (bold ? 1 : 0) + (italic ? 2 : 0);
         Font f;
         if (files[idx] != null) {
            f = base(files[idx]).deriveFont(size);
         } else if (italic && bold && files[2] != null) {
            f = base(files[2]).deriveFont(Font.BOLD, size);
         } else if (bold && files[1] != null) {
            f = base(files[1]).deriveFont(italic ? Font.ITALIC : Font.PLAIN, size);
         } else {
            int style = (bold ? Font.BOLD : 0) | (italic ? Font.ITALIC : 0);
            f = base(files[0]).deriveFont(style, size);
         }

         DERIVED.put(key, f);
         return f;
      }
   }

   public static Font tracked(Font f, float tracking) {
      if (Math.abs(tracking) < 0.001F) {
         return f;
      } else {
         Map<TextAttribute, Object> a = new HashMap<>();
         a.put(TextAttribute.TRACKING, tracking);
         return f.deriveFont(a);
      }
   }

   public static Face face(int family, boolean bold, boolean italic, float size) {
      return face(family, bold, italic, size, 0.0F);
   }

   public static Face face(int family, boolean bold, boolean italic, float size, float tracking) {
      family = clamp(family);
      Font main = tracked(get(family, bold, italic, size), tracking);
      Font fb = tracked(get(FALLBACK[family], bold, italic, size), tracking);
      return new Face(main, fb, family);
   }

   public static void hints(Graphics2D g) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
   }

   /** A font with a per-character fallback. Measuring uses a fixed antialiased, fractional context. */
   public static final class Face {
      public final Font font;
      public final Font fallback;
      public final int family;
      private final Font last;

      Face(Font font, Font fallback, int family) {
         this.font = font;
         this.fallback = fallback;
         this.family = family;
         this.last = new Font("Dialog", font.getStyle(), Math.max(1, Math.round(font.getSize2D()))).deriveFont(font.getSize2D());
      }

      public float size() {
         return this.font.getSize2D();
      }

      private Font pick(int cp) {
         if (this.font.canDisplay(cp)) {
            return this.font;
         } else {
            return this.fallback.canDisplay(cp) ? this.fallback : this.last;
         }
      }

      /** Splits text into runs that share one physical font. */
      private List<Object[]> runs(String s) {
         List<Object[]> out = new ArrayList<>(2);
         int i = 0;
         Font cur = null;
         int start = 0;

         while (i < s.length()) {
            int cp = s.codePointAt(i);
            Font f = this.pick(cp);
            if (cur == null) {
               cur = f;
            } else if (f != cur) {
               out.add(new Object[]{cur, s.substring(start, i)});
               cur = f;
               start = i;
            }

            i += Character.charCount(cp);
         }

         if (cur != null) {
            out.add(new Object[]{cur, s.substring(start)});
         }

         return out;
      }

      public double width(String s) {
         if (s != null && !s.isEmpty()) {
            double w = 0.0;

            for (Object[] r : this.runs(s)) {
               w += ((Font)r[0]).getStringBounds((String)r[1], FRC).getWidth();
            }

            return w;
         } else {
            return 0.0;
         }
      }

      public double ascent() {
         return this.font.getLineMetrics("Ág", FRC).getAscent();
      }

      public double descent() {
         return this.font.getLineMetrics("Ág", FRC).getDescent();
      }

      /** Height of capital letters, used to size drop caps and to centre single lines. */
      public double capHeight() {
         return this.font.createGlyphVector(FRC, "H").getVisualBounds().getHeight();
      }

      public void draw(Graphics2D g, String s, double x, double baseline) {
         if (s != null && !s.isEmpty()) {
            Font old = g.getFont();
            double cx = x;

            for (Object[] r : this.runs(s)) {
               Font f = (Font)r[0];
               String t = (String)r[1];
               g.setFont(f);
               g.drawString(t, (float)cx, (float)baseline);
               cx += f.getStringBounds(t, FRC).getWidth();
            }

            g.setFont(old);
         }
      }
   }
}
