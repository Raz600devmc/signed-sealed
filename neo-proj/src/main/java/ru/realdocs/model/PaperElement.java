package ru.realdocs.model;

/** One block on a newspaper page: article text, headline, picture or decoration. */
public final class PaperElement {
   public static final int TEXT = 0;
   public static final int TITLE = 1;
   public static final int IMAGE = 2;
   public static final int LINE = 3;
   public static final int FRAME = 4;
   public static final int BAND = 5;
   public static final String[] TYPE_NAMES = new String[]{"Статья", "Заголовок", "Картинка", "Линия", "Рамка", "Плашка"};
   public static final int ALIGN_LEFT = 0;
   public static final int ALIGN_CENTER = 1;
   public static final int ALIGN_RIGHT = 2;
   public static final int ALIGN_JUSTIFY = 3;
   public static final int FIT_CONTAIN = 0;
   public static final int FIT_COVER = 1;
   public static final int FIT_STRETCH = 2;
   public static final String[] FIT_NAMES = new String[]{"Вписать", "Заполнить", "Растянуть"};
   public static final int FILTER_COLOR = 0;
   public static final int FILTER_GRAY = 1;
   public static final int FILTER_HALFTONE = 2;
   public static final int FILTER_SEPIA = 3;
   public static final int FILTER_INK = 4;
   public static final String[] FILTER_NAMES = new String[]{"Цвет", "Ч/б", "Растр", "Сепия", "Гравюра"};
   public static final int F_BOLD = 1;
   public static final int F_ITALIC = 2;
   public static final int F_DROPCAP = 4;
   public static final int F_INDENT = 8;
   public static final int F_BORDER = 16;
   public static final int F_CAPS = 32;
   public static final int F_RULES = 64;
   public static final int MAX_TEXT = 8000;
   public static final int MAX_TITLE = 300;
   public int type;
   public int x;
   public int y;
   public int w = 200;
   public int h = 100;
   public String text = "";
   public String asset = "";
   public int font = 0;
   public int size = 13;
   public int flags = 0;
   public int align = 3;
   public int cols = 1;
   public int lead = 125;
   public int color = 0x1E1E22;
   public int alpha = 100;
   public int fit = FIT_COVER;
   public int filter = FILTER_COLOR;
   public int thick = 2;
   public int tracking = 0;

   public PaperElement() {
   }

   public PaperElement(int type, int x, int y, int w, int h) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
   }

   public boolean has(int flag) {
      return (this.flags & flag) != 0;
   }

   public void set(int flag, boolean on) {
      this.flags = on ? this.flags | flag : this.flags & ~flag;
   }

   public boolean bold() {
      return this.has(F_BOLD);
   }

   public boolean italic() {
      return this.has(F_ITALIC);
   }

   public boolean textual() {
      return this.type == TEXT || this.type == TITLE;
   }

   public PaperElement copy() {
      PaperElement e = new PaperElement(this.type, this.x, this.y, this.w, this.h);
      e.text = this.text;
      e.asset = this.asset;
      e.font = this.font;
      e.size = this.size;
      e.flags = this.flags;
      e.align = this.align;
      e.cols = this.cols;
      e.lead = this.lead;
      e.color = this.color;
      e.alpha = this.alpha;
      e.fit = this.fit;
      e.filter = this.filter;
      e.thick = this.thick;
      e.tracking = this.tracking;
      return e;
   }

   static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   public void sanitize(int pageW, int pageH) {
      this.type = clamp(this.type, 0, TYPE_NAMES.length - 1);
      this.w = clamp(this.w, this.type == LINE ? 1 : 8, pageW);
      this.h = clamp(this.h, this.type == LINE ? 1 : 8, pageH);
      this.x = clamp(this.x, -this.w / 2, pageW - this.w / 2);
      this.y = clamp(this.y, -this.h / 2, pageH - this.h / 2);
      if (this.text == null) {
         this.text = "";
      }

      int max = this.type == TEXT ? MAX_TEXT : MAX_TITLE;
      if (this.text.length() > max) {
         this.text = this.text.substring(0, max);
      }

      if (this.asset == null || this.type != IMAGE) {
         this.asset = "";
      }

      if (this.asset.length() > 24) {
         this.asset = "";
      }

      this.font = clamp(this.font, 0, 31);
      this.size = clamp(this.size, 5, this.type == TITLE ? 200 : 60);
      this.flags &= 127;
      this.align = clamp(this.align, 0, 3);
      this.cols = clamp(this.cols, 1, 5);
      this.lead = clamp(this.lead, 80, 220);
      this.color &= 0xFFFFFF;
      this.alpha = clamp(this.alpha, 5, 100);
      this.fit = clamp(this.fit, 0, FIT_NAMES.length - 1);
      this.filter = clamp(this.filter, 0, FILTER_NAMES.length - 1);
      this.thick = clamp(this.thick, 1, 24);
      this.tracking = clamp(this.tracking, -10, 60);
   }

   public String label() {
      if (this.textual() && !this.text.isBlank()) {
         String s = this.text.replace('\n', ' ').replace('|', ' ').replace("**", "").replace("__", "").trim();
         return s.length() > 40 ? s.substring(0, 40) : s;
      } else {
         return TYPE_NAMES[clamp(this.type, 0, TYPE_NAMES.length - 1)];
      }
   }

   public String canonical() {
      return this.type + "," + this.x + "," + this.y + "," + this.w + "," + this.h + "," + this.font + "," + this.size + "," + this.flags + "," + this.align
         + "," + this.cols + "," + this.lead + "," + this.color + "," + this.alpha + "," + this.fit + "," + this.filter + "," + this.thick + "," + this.tracking
         + "," + this.asset + "," + this.text.length() + ":" + this.text;
   }
}
