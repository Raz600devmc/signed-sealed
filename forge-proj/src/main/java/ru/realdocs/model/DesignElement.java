package ru.realdocs.model;

public final class DesignElement {
   public static final int TEXT = 0;
   public static final int IMAGE = 1;
   public static final int MEDALLION = 2;
   public static final int LINE = 3;
   public static final int FRAME = 4;
   public static final int BAND = 5;
   public static final String[] TYPE_NAMES = new String[]{"Текст", "Эмблема", "Медальон", "Линия", "Рамка", "Плашка"};
   public static final int INK_COLOR = 0;
   public static final int INK_GOLD = 1;
   public static final int INK_SILVER = 2;
   public static final int INK_BLIND = 3;
   public static final String[] INK_NAMES = new String[]{"Цвет", "Золото", "Серебро", "Тиснение"};
   public static final int MAX_TEXT = 60;
   public int type = 0;
   public int x;
   public int y;
   public int w = 120;
   public int h = 24;
   public String text = "";
   public int size = 14;
   public boolean bold = true;
   public int align = 1;
   public int spacing = 10;
   public int ink = 1;
   public int color = 14860400;
   public int alpha = 100;

   public DesignElement() {
   }

   public DesignElement(int type, int x, int y, int w, int h) {
      this.type = type;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
   }

   public static DesignElement text(String text, int x, int y, int w, int h, int size, int spacing) {
      DesignElement e = new DesignElement(0, x, y, w, h);
      e.text = text;
      e.size = size;
      e.spacing = spacing;
      return e;
   }

   public DesignElement copy() {
      DesignElement e = new DesignElement(this.type, this.x, this.y, this.w, this.h);
      e.text = this.text;
      e.size = this.size;
      e.bold = this.bold;
      e.align = this.align;
      e.spacing = this.spacing;
      e.ink = this.ink;
      e.color = this.color;
      e.alpha = this.alpha;
      return e;
   }

   public void sanitize(int maxW, int maxH) {
      this.type = Math.max(0, Math.min(TYPE_NAMES.length - 1, this.type));
      this.w = Math.max(4, Math.min(maxW, this.w));
      this.h = Math.max(this.type == 3 ? 1 : 4, Math.min(maxH, this.h));
      this.x = Math.max(-this.w / 2, Math.min(maxW - this.w / 2, this.x));
      this.y = Math.max(-this.h / 2, Math.min(maxH - this.h / 2, this.y));
      if (this.text == null) {
         this.text = "";
      }

      if (this.text.length() > 60) {
         this.text = this.text.substring(0, 60);
      }

      this.size = Math.max(5, Math.min(60, this.size));
      this.align = Math.max(0, Math.min(2, this.align));
      this.spacing = Math.max(0, Math.min(40, this.spacing));
      this.ink = Math.max(0, Math.min(INK_NAMES.length - 1, this.ink));
      this.color &= 16777215;
      this.alpha = Math.max(10, Math.min(100, this.alpha));
   }

   public String label() {
      if (this.type == 0) {
         return this.text.isBlank() ? "Текст" : this.text.replace('|', ' ');
      } else {
         return TYPE_NAMES[this.type];
      }
   }
}
