package ru.realdocs.model;

import net.minecraft.nbt.CompoundTag;

/**
 * Look of a custom document type (2.4.0). A copy is stored inside every document issued from a
 * template, so the page keeps its look even if the template is edited or deleted later.
 */
public final class DocDesign {
   public static final int MAX_NAME = 40;
   public static final int MAX_TITLE = 40;
   public static final int MAX_HEADER = 80;
   public static final int MAX_FOOTER = 120;
   public static final String[] BORDERS = new String[]{"Тонкая рамка", "Двойная рамка", "Узорная рамка", "Широкая рамка", "Без рамки"};
   public static final String[] PAPERS = new String[]{"Кремовая", "Белая", "Голубая", "Зелёная", "Пергамент", "Розовая", "Серая"};
   public static final int[] PAPER_RGB = new int[]{0xFBF8F0, 0xFFFFFF, 0xEEF3FA, 0xEEF6EC, 0xF1E4C6, 0xFAEEEE, 0xEFEFEC};
   public static final String[] ACCENTS = new String[]{"Чёрный", "Синий", "Бордовый", "Зелёный", "Золотой", "Фиолетовый", "Коричневый"};
   public static final int[] ACCENT_RGB = new int[]{0x1C1C22, 0x1F3E8C, 0x7A1F2B, 0x2E6B35, 0x9A7322, 0x523A86, 0x5E4026};
   public static final String[] LOGO_POS = new String[]{"Без логотипа", "Сверху по центру", "Сверху слева", "Водяной знак"};
   public static final String[] BODY_STYLES = new String[]{"С абзацным отступом", "Без отступа", "По центру"};
   /** Document type shown in lists and messages, e.g. "Трудовой договор". */
   public String name = "";
   /** Big title printed on the page, e.g. "ТРУДОВОЙ ДОГОВОР". */
   public String title = "";
   public String header1 = "";
   public String header2 = "";
   public String footer = "";
   public String logo = "";
   public int logoPos;
   public int border;
   public int paper;
   public int accent;
   /** Index into the bundled fonts, -1 for the classic serif. */
   public int titleFont = -1;
   public int bodyFont = -1;
   public boolean titleLeft;
   public int bodyStyle;
   public boolean hideNumber;
   public boolean hideDate;

   public static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   public DocDesign normalize() {
      this.name = cut(this.name, MAX_NAME);
      this.title = cut(this.title, MAX_TITLE);
      this.header1 = cut(this.header1, MAX_HEADER);
      this.header2 = cut(this.header2, MAX_HEADER);
      this.footer = cut(this.footer, MAX_FOOTER);
      this.logo = cut(this.logo, 24);
      this.logoPos = clamp(this.logoPos, 0, LOGO_POS.length - 1);
      this.border = clamp(this.border, 0, BORDERS.length - 1);
      this.paper = clamp(this.paper, 0, PAPERS.length - 1);
      this.accent = clamp(this.accent, 0, ACCENTS.length - 1);
      this.titleFont = clamp(this.titleFont, -1, 31);
      this.bodyFont = clamp(this.bodyFont, -1, 31);
      this.bodyStyle = clamp(this.bodyStyle, 0, BODY_STYLES.length - 1);
      if (this.logo.isEmpty()) {
         this.logoPos = 0;
      }

      return this;
   }

   static String cut(String s, int max) {
      if (s == null) {
         return "";
      } else {
         s = s.replace('\n', ' ').replace('\r', ' ').trim();
         return s.length() <= max ? s : s.substring(0, max);
      }
   }

   public DocDesign copy() {
      return read(this.write());
   }

   public CompoundTag write() {
      CompoundTag t = new CompoundTag();
      t.putString("Name", this.name);
      t.putString("Title", this.title);
      t.putString("H1", this.header1);
      t.putString("H2", this.header2);
      t.putString("Foot", this.footer);
      t.putString("Logo", this.logo);
      t.putInt("LogoPos", this.logoPos);
      t.putInt("Border", this.border);
      t.putInt("Paper", this.paper);
      t.putInt("Accent", this.accent);
      t.putInt("TFont", this.titleFont);
      t.putInt("BFont", this.bodyFont);
      t.putBoolean("TLeft", this.titleLeft);
      t.putInt("BStyle", this.bodyStyle);
      t.putBoolean("NoNum", this.hideNumber);
      t.putBoolean("NoDate", this.hideDate);
      return t;
   }

   public static DocDesign read(CompoundTag t) {
      DocDesign d = new DocDesign();
      d.name = t.getString("Name");
      d.title = t.getString("Title");
      d.header1 = t.getString("H1");
      d.header2 = t.getString("H2");
      d.footer = t.getString("Foot");
      d.logo = t.getString("Logo");
      d.logoPos = t.getInt("LogoPos");
      d.border = t.getInt("Border");
      d.paper = t.getInt("Paper");
      d.accent = t.getInt("Accent");
      d.titleFont = t.contains("TFont") ? t.getInt("TFont") : -1;
      d.bodyFont = t.contains("BFont") ? t.getInt("BFont") : -1;
      d.titleLeft = t.getBoolean("TLeft");
      d.bodyStyle = t.getInt("BStyle");
      d.hideNumber = t.getBoolean("NoNum");
      d.hideDate = t.getBoolean("NoDate");
      return d.normalize();
   }

   /** Everything that changes how the page looks, for the document's authenticity hash. */
   public String canonical() {
      return String.join(
         "\u0001",
         this.name,
         this.title,
         this.header1,
         this.header2,
         this.footer,
         this.logo,
         Integer.toString(this.logoPos),
         Integer.toString(this.border),
         Integer.toString(this.paper),
         Integer.toString(this.accent),
         Integer.toString(this.titleFont),
         Integer.toString(this.bodyFont),
         this.titleLeft ? "L" : "C",
         Integer.toString(this.bodyStyle),
         this.hideNumber ? "1" : "0",
         this.hideDate ? "1" : "0"
      );
   }

   public int paperRgb() {
      return PAPER_RGB[clamp(this.paper, 0, PAPER_RGB.length - 1)];
   }

   public int accentRgb() {
      return ACCENT_RGB[clamp(this.accent, 0, ACCENT_RGB.length - 1)];
   }

   /** Built-in look of one of the four classic types, used as a starting point in the editor. */
   public static DocDesign preset(int template) {
      DocDesign d = new DocDesign();
      int t = DocModel.templateIndex(template);
      d.name = DocModel.TEMPLATE_NAMES[t];
      d.title = DocModel.TEMPLATE_TITLES[t];
      return d;
   }
}
