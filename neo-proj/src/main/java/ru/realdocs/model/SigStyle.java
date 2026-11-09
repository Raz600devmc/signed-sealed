package ru.realdocs.model;

public final class SigStyle {
   public static final int PEN = 0;
   public static final int FOUNTAIN = 1;
   public static final int NIB = 2;
   public static final int MARKER = 3;
   public static final int PENCIL = 4;
   public static final String[] BRUSHES = new String[]{"Ручка", "Перо (нажим)", "Каллиграфия", "Маркер", "Карандаш"};
   public static final String[] BRUSHES_SHORT = new String[]{"Ручка", "Нажим", "Каллигр.", "Маркер", "Карандаш"};
   public static final String[] COLOR_NAMES = new String[]{"Синие", "Чёрные", "Фиолетовые", "Зелёные"};
   public static final int[] COLORS = new int[]{1451146, 1644830, 5777548, 1333820};
   public static final int MIN_SIZE = 1;
   public static final int MAX_SIZE = 5;
   public static final int DEFAULT_SIZE = 2;
   public int brush = 0;
   public int color = 0;
   public int size = 2;

   public SigStyle() {
   }

   public SigStyle(int brush, int color, int size) {
      this.brush = Math.max(0, Math.min(BRUSHES.length - 1, brush));
      this.color = Math.max(0, Math.min(COLORS.length - 1, color));
      this.size = Math.max(1, Math.min(5, size));
   }

   public int code() {
      return this.brush | this.color << 4 | this.size << 8;
   }

   public int header() {
      return -(2 + this.code());
   }

   public static boolean isHeader(int v) {
      return v <= -2 && v >= -4097;
   }

   public static SigStyle of(int[] strokes) {
      if (strokes != null && strokes.length != 0 && isHeader(strokes[0])) {
         int c = -strokes[0] - 2;
         return new SigStyle(c & 15, c >> 4 & 15, c >> 8 & 15);
      } else {
         return new SigStyle();
      }
   }

   public static boolean hasHeader(int[] strokes) {
      return strokes != null && strokes.length > 0 && isHeader(strokes[0]);
   }
}
