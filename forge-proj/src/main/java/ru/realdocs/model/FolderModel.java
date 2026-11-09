package ru.realdocs.model;

/** Document folder: a label and a cover colour. The contents live next to it in the item data. */
public final class FolderModel {
   public static final int MAX_ITEMS = 16;
   public static final int MAX_LABEL = 40;
   public static final String[] COLOR_NAMES = new String[]{"Картон", "Красная", "Синяя", "Зелёная", "Чёрная", "Серая", "Жёлтая", "Коричневая"};
   public static final int[] COLOR_RGB = new int[]{0xC7A77A, 0x9C2B2B, 0x2F4C8A, 0x3E6E44, 0x2B2B2E, 0x7D8189, 0xD6B23E, 0x6B4428};
   public String label = "";
   public int color = 0;

   public int rgb() {
      return COLOR_RGB[Math.max(0, Math.min(COLOR_RGB.length - 1, this.color))];
   }
}
