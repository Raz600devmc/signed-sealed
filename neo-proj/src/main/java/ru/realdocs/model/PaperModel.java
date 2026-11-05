package ru.realdocs.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.realdocs.L;

/** A newspaper, leaflet or poster: up to 8 freely laid out pages. */
public final class PaperModel {
   public static final int W = 760;
   public static final int H = 1080;
   public static final int MAX_PAGES = 8;
   public static final int MAX_ELEMENTS = 48;
   public static final int MAX_IMAGES = 32;
   public static final int NAME = 48;
   public static final int PRICE = 24;
   public static final int FMT_PAPER = 0;
   public static final int FMT_LEAFLET = 1;
   public static final int FMT_POSTER = 2;
   public static final String[] FORMATS = new String[]{"Газета", "Листовка", "Плакат"};
   public static final String[] TONES = new String[]{"Газетная", "Белая", "Старая", "Розовая", "Голубая"};
   public static final int[] TONE_RGB = new int[]{0xF1ECDD, 0xFBFBF8, 0xEADBB8, 0xF6E1D6, 0xE3ECF1};
   public String id = "";
   public String name = "";
   public String price = "";
   public int format = FMT_PAPER;
   public int tone = 0;
   public final List<List<PaperElement>> pages = new ArrayList<>();
   public boolean published;
   public int issue;
   public String date = "";
   public String editor = "";
   public String editorUuid = "";

   public PaperModel() {
   }

   public int pageCount() {
      return Math.max(1, this.pages.size());
   }

   public List<PaperElement> page(int i) {
      while (this.pages.isEmpty()) {
         this.pages.add(new ArrayList<>());
      }

      return this.pages.get(Math.max(0, Math.min(this.pages.size() - 1, i)));
   }

   public String kindName() {
      return FORMATS[Math.max(0, Math.min(FORMATS.length - 1, this.format))];
   }

   public PaperModel copy() {
      PaperModel m = new PaperModel();
      m.id = this.id;
      m.name = this.name;
      m.price = this.price;
      m.format = this.format;
      m.tone = this.tone;
      m.published = this.published;
      m.issue = this.issue;
      m.date = this.date;
      m.editor = this.editor;
      m.editorUuid = this.editorUuid;

      for (List<PaperElement> p : this.pages) {
         List<PaperElement> c = new ArrayList<>(p.size());

         for (PaperElement e : p) {
            c.add(e.copy());
         }

         m.pages.add(c);
      }

      return m;
   }

   public Set<String> assets() {
      Set<String> out = new LinkedHashSet<>();

      for (List<PaperElement> p : this.pages) {
         for (PaperElement e : p) {
            if (e.type == PaperElement.IMAGE && !e.asset.isEmpty()) {
               out.add(e.asset);
            }
         }
      }

      return out;
   }

   public void sanitize() {
      this.name = clean(this.name, NAME);
      this.price = clean(this.price, PRICE);
      this.format = Math.max(0, Math.min(FORMATS.length - 1, this.format));
      this.tone = Math.max(0, Math.min(TONES.length - 1, this.tone));

      while (this.pages.size() > MAX_PAGES) {
         this.pages.remove(this.pages.size() - 1);
      }

      if (this.pages.isEmpty()) {
         this.pages.add(new ArrayList<>());
      }

      for (List<PaperElement> p : this.pages) {
         while (p.size() > MAX_ELEMENTS) {
            p.remove(p.size() - 1);
         }

         for (PaperElement e : p) {
            e.sanitize(W, H);
         }
      }
   }

   private static String clean(String s, int max) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            sb.append(c >= ' ' && c != 127 ? c : ' ');
         }

         return sb.toString().strip();
      }
   }

   /** Values for {placeholders} in text. */
   public Map<String, String> tokens(int page) {
      Map<String, String> t = new HashMap<>();
      t.put("name", this.name.isEmpty() ? "" : this.name);
      t.put("issue", this.issue > 0 ? String.valueOf(this.issue) : "—");
      t.put("date", this.date.isEmpty() ? "__.__.____" : this.date);
      t.put("price", this.price);
      t.put("editor", this.editor);
      t.put("page", String.valueOf(page + 1));
      t.put("pages", String.valueOf(this.pageCount()));
      return t;
   }

   /** Layout fingerprint for the registry; includes everything that is printed. */
   public String canonical() {
      StringBuilder sb = new StringBuilder("NP1;");
      f(sb, this.id);
      f(sb, this.name);
      f(sb, this.price);
      f(sb, String.valueOf(this.format));
      f(sb, String.valueOf(this.tone));
      f(sb, String.valueOf(this.issue));
      f(sb, this.date);
      f(sb, this.editorUuid);
      sb.append('P').append(this.pages.size()).append(';');

      for (List<PaperElement> p : this.pages) {
         sb.append('E').append(p.size()).append(';');

         for (PaperElement e : p) {
            f(sb, e.canonical());
         }
      }

      return sb.toString();
   }

   private static void f(StringBuilder sb, String s) {
      if (s == null) {
         s = "";
      }

      sb.append(s.length()).append(':').append(s).append(';');
   }

   static PaperElement t(int type, int x, int y, int w, int h, String text, int font, int size, int flags, int align) {
      PaperElement e = new PaperElement(type, x, y, w, h);
      e.text = text;
      e.font = font;
      e.size = size;
      e.flags = flags;
      e.align = align;
      return e;
   }

   /** Starting layout for a new blank: masthead with placeholders and a few sample blocks. */
   public static PaperModel blank(int format, String ruLorem) {
      PaperModel m = new PaperModel();
      m.format = Math.max(0, Math.min(FORMATS.length - 1, format));
      List<PaperElement> p = new ArrayList<>();
      m.pages.add(p);
      if (m.format == FMT_PAPER) {
         p.add(t(PaperElement.TITLE, 30, 26, 700, 96, "{name}", 2, 84, PaperElement.F_BOLD, 1));
         p.add(new PaperElement(PaperElement.LINE, 30, 128, 700, 3));
         p.add(t(PaperElement.TITLE, 30, 134, 230, 20, "№ {issue}", 0, 13, 0, 0));
         p.add(t(PaperElement.TITLE, 265, 134, 230, 20, "{date}", 0, 13, 0, 1));
         p.add(t(PaperElement.TITLE, 500, 134, 230, 20, "{price}", 0, 13, 0, 2));
         p.add(new PaperElement(PaperElement.LINE, 30, 158, 700, 1));
         p.add(t(PaperElement.TITLE, 30, 176, 700, 70, L.t("ГЛАВНАЯ НОВОСТЬ"), 0, 46, PaperElement.F_BOLD, 0));
         PaperElement img = new PaperElement(PaperElement.IMAGE, 30, 256, 440, 300);
         img.filter = PaperElement.FILTER_HALFTONE;
         img.set(PaperElement.F_BORDER, true);
         p.add(img);
         p.add(t(PaperElement.TITLE, 30, 560, 440, 18, L.t("Подпись к фотографии"), 0, 11, PaperElement.F_ITALIC, 0));
         PaperElement side = t(PaperElement.TEXT, 490, 256, 240, 330, ruLorem, 0, 12, PaperElement.F_INDENT, 3);
         side.set(PaperElement.F_DROPCAP, true);
         p.add(side);
         p.add(new PaperElement(PaperElement.LINE, 30, 600, 700, 1));
         p.add(t(PaperElement.TITLE, 30, 612, 700, 40, L.t("Второй заголовок"), 0, 28, PaperElement.F_BOLD, 0));
         PaperElement body = t(PaperElement.TEXT, 30, 660, 700, 380, ruLorem + "\n" + ruLorem, 0, 12, PaperElement.F_INDENT, 3);
         body.cols = 3;
         body.set(PaperElement.F_RULES, true);
         p.add(body);
         p.add(t(PaperElement.TITLE, 30, 1046, 700, 16, L.t("{name} · {date} · стр. {page}/{pages}"), 1, 10, 0, 1));
      } else if (m.format == FMT_LEAFLET) {
         m.tone = 1;
         PaperElement band = new PaperElement(PaperElement.BAND, 0, 0, 760, 150);
         band.color = 0x8E1B1B;
         p.add(band);
         PaperElement head = t(PaperElement.TITLE, 40, 30, 680, 90, L.t("ВНИМАНИЕ!"), 4, 70, 0, 1);
         head.color = 0xFFFFFF;
         p.add(head);
         PaperElement img = new PaperElement(PaperElement.IMAGE, 130, 190, 500, 330);
         img.fit = PaperElement.FIT_CONTAIN;
         p.add(img);
         p.add(t(PaperElement.TITLE, 40, 550, 680, 60, L.t("Заголовок листовки"), 1, 40, PaperElement.F_BOLD, 1));
         p.add(t(PaperElement.TEXT, 80, 630, 600, 330, ruLorem, 1, 18, 0, 1));
         p.add(t(PaperElement.TITLE, 40, 1000, 680, 40, "{name}", 1, 20, PaperElement.F_BOLD, 1));
      } else {
         m.tone = 2;
         PaperElement frame = new PaperElement(PaperElement.FRAME, 24, 24, 712, 1032);
         frame.thick = 6;
         p.add(frame);
         p.add(t(PaperElement.TITLE, 60, 70, 640, 160, L.t("РАЗЫСКИВАЕТСЯ"), 2, 92, PaperElement.F_BOLD, 1));
         PaperElement img = new PaperElement(PaperElement.IMAGE, 180, 260, 400, 440);
         img.filter = PaperElement.FILTER_SEPIA;
         img.set(PaperElement.F_BORDER, true);
         p.add(img);
         p.add(t(PaperElement.TITLE, 60, 730, 640, 80, L.t("Имя Фамилия"), 2, 54, 0, 1));
         p.add(t(PaperElement.TEXT, 100, 830, 560, 130, L.t("Описание, приметы, за что разыскивается."), 2, 20, 0, 1));
         p.add(t(PaperElement.TITLE, 60, 970, 640, 60, L.t("НАГРАДА: {price}"), 2, 40, PaperElement.F_BOLD, 1));
      }

      return m;
   }
}
