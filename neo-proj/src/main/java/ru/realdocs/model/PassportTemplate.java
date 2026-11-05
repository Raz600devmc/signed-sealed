package ru.realdocs.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PassportTemplate {
   public static final int W = 640;
   public static final int H = 460;
   public static final int FORMAT_BOOK = 0;
   public static final int FORMAT_CARD = 1;
   public static final int FORMAT_TICKET = 2;
   public static final int FORMAT_FINE = 3;
   public static final String[] FORMATS = new String[]{"Паспорт", "Удостоверение", "Билет", "Штраф"};
   public int format = 0;
   public static final int FIELD_TEXT = 0;
   public static final int FIELD_DATE = 1;
   public static final int FIELD_PHOTO = 2;
   public static final int FIELD_SERIAL = 3;
   public static final int FIELD_SIGNATURE = 4;
   public static final int FIELD_ISSUE_DATE = 5;
   public static final int FIELD_EXPIRY_DATE = 6;
   public static final String[] FIELD_TYPES = new String[]{"Текст", "Дата", "Фото", "Номер", "Подпись", "Дата выдачи", "Срок действия"};
   public static final int PATTERN_NONE = 0;
   public static final int PATTERN_GUILLOCHE = 1;
   public static final int PATTERN_STRIPES = 2;
   public static final int PATTERN_DOTS = 3;
   public static final String[] PATTERNS = new String[]{"Нет", "Гильоше", "Полосы", "Точки"};
   public static final int EMBLEM_NONE = 0;
   public static final int EMBLEM_MEDALLION = 1;
   public static final int EMBLEM_STAR = 2;
   public static final int EMBLEM_CROWN = 3;
   public static final String[] EMBLEM_NAMES = new String[]{"Нет", "Флаг", "Звезда", "Корона"};
   public static final int DEFAULT_VISA_SPREADS = 3;
   public static final int MAX_VISA_SPREADS = 8;
   public static final int MAX_FIELDS = 16;
   public String id = "";
   public String name = "";
   public String org = "";
   public String ownerUuid = "";
   public String ownerName = "";
   public boolean approved;
   public String replaces = "";
   public String created = "";
   public String title = "ПАСПОРТ";
   public String subtitle = "";
   public int bg = 15327948;
   public int accent = 2046611;
   public int pattern = 1;
   public int coverColor = 2046611;
   public String coverTitle = "ПАСПОРТ";
   public String coverText = "";
   public String emblem = "";
   public int emblemCode = 0;
   public String serialPrefix = "";
   public String flag = "";
   public int visaSpreads = 3;
   public String visaTitle = "ВИЗЫ · VISAS";
   public int validYears = 10;
   public static final int MAX_ELEMENTS = 24;
   public static final String[] TEXTURES = new String[]{"Кожа", "Гладкая", "Ткань"};
   public static final String[] FRAMES = new String[]{"Нет", "Одна", "Двойная", "Узорная"};
   public final List<DesignElement> cover = new ArrayList<>();
   public final List<DesignElement> decor = new ArrayList<>();
   public final List<DesignElement> back = new ArrayList<>();
   public int coverTexture = 0;
   public int coverFrame = 0;
   public boolean hideHeader;
   public String autoStampId = "";
   public final List<PassportTemplate.Field> fields = new ArrayList<>();
   public static final String[] PRESET_KEYS = CountryPresets.keys();

   public static int width(int format) {
      return switch (format) {
         case 3 -> 420;
         default -> 640;
      };
   }

   public static int height(int format) {
      return switch (format) {
         case 1 -> 404;
         case 2 -> 250;
         case 3 -> 600;
         default -> 460;
      };
   }

   public int w() {
      return width(this.format);
   }

   public int h() {
      return height(this.format);
   }

   public boolean book() {
      return this.format == 0;
   }

   public int extraSides() {
      return switch (this.format) {
         case 0 -> this.visaSpreads;
         case 1 -> 1;
         default -> 0;
      };
   }

   public static boolean playerInput(PassportTemplate.Field f) {
      return (f.type == 0 || f.type == 1) && !f.key.equals("issued");
   }

   public String expiry(String issued) {
      try {
         DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy");
         return LocalDate.parse(issued, f).plusYears((long)this.validYears).minusDays(1L).format(f);
      } catch (Exception var3) {
         return "";
      }
   }

   public static boolean issueDate(PassportTemplate.Field f) {
      return f.type == 5 || f.type == 1 && f.key.equals("issued");
   }

   public List<DesignElement> defaultCover() {
      List<DesignElement> out = new ArrayList<>();
      int y = 46;

      for (String line : this.coverText.split("\n")) {
         if (!line.isBlank()) {
            out.add(DesignElement.text(line.trim().toUpperCase(Locale.ROOT), 30, y, 240, 18, 13, 14));
            y += 17;
         }
      }

      int ey = Math.max(y + 12, 112);
      if (!this.emblem.isEmpty()) {
         out.add(new DesignElement(1, 88, ey, 124, 124));
      } else if (this.emblemCode != 0) {
         out.add(new DesignElement(2, 88, ey, 124, 124));
      }

      out.add(DesignElement.text(this.coverTitle.toUpperCase(Locale.ROOT), 25, 278, 250, 30, 26, 18));
      DesignElement linex = new DesignElement(3, 114, 312, 72, 1);
      out.add(linex);
      return out;
   }

   public static PassportTemplate sample() {
      PassportTemplate t = new PassportTemplate();
      t.name = "Паспорт гражданина";
      t.org = "Правительство";
      t.title = "ПАСПОРТ ГРАЖДАНИНА";
      t.subtitle = "внутриигровой документ";
      t.serialPrefix = "RP";
      t.coverTitle = "ПАСПОРТ";
      t.coverText = "ГРАЖДАНИН\nСЕРВЕРА";
      t.fields.add(field("photo", "Фото", 2, 34, 92, 150, 196));
      t.fields.add(field("serial", "Номер", 3, 34, 300, 150, 36));
      t.fields.add(field("sign", "Подпись владельца", 4, 34, 348, 150, 64));
      t.fields.add(field("surname", "Фамилия", 0, 338, 118, 282, 40));
      t.fields.add(field("name", "Имя", 0, 338, 166, 282, 40));
      t.fields.add(field("birth", "Дата рождения", 1, 338, 214, 132, 40));
      t.fields.add(field("sex", "Пол", 0, 480, 214, 140, 40));
      t.fields.add(field("city", "Город", 0, 338, 262, 282, 40));
      t.fields.add(field("issued", "Дата выдачи", 5, 338, 310, 282, 40));
      return t;
   }

   public static PassportTemplate sample(int format) {
      return switch (format) {
         case 1 -> sampleCard();
         case 2 -> sampleTicket();
         case 3 -> sampleFine();
         default -> sample();
      };
   }

   public static PassportTemplate sampleCard() {
      PassportTemplate t = new PassportTemplate();
      t.format = 1;
      t.name = "Служебное удостоверение";
      t.org = "Организация";
      t.title = "УДОСТОВЕРЕНИЕ";
      t.serialPrefix = "ID";
      t.bg = 15659252;
      t.accent = 2046611;
      t.emblemCode = 2;
      t.validYears = 5;
      t.fields.add(field("photo", "Фото", 2, 34, 92, 140, 186));
      t.fields.add(field("serial", "Номер", 3, 34, 290, 140, 36));
      t.fields.add(field("surname", "Фамилия", 0, 196, 96, 410, 40));
      t.fields.add(field("name", "Имя", 0, 196, 142, 410, 40));
      t.fields.add(field("position", "Должность", 0, 196, 188, 410, 40));
      t.fields.add(field("issued", "Дата выдачи", 5, 196, 234, 196, 40));
      t.fields.add(field("expiry", "Действительно до", 6, 410, 234, 196, 40));
      t.fields.add(field("sign", "Подпись владельца", 4, 196, 286, 230, 54));
      DesignElement band = new DesignElement(5, 8, 8, 624, 56);
      band.ink = 0;
      band.color = t.accent;
      band.alpha = 14;
      t.decor.add(band);
      DesignElement wm = new DesignElement(2, 236, 60, 300, 300);
      wm.ink = 0;
      wm.color = t.accent;
      wm.alpha = 12;
      t.back.add(wm);
      DesignElement txt = DesignElement.text("ПРЕДЪЯВЛЯТЬ ПО ТРЕБОВАНИЮ|ПРИ УТЕРЕ СООБЩИТЬ В ОРГАНИЗАЦИЮ", 60, 120, 520, 60, 14, 8);
      txt.ink = 0;
      txt.color = 2764602;
      t.back.add(txt);
      DesignElement num = DesignElement.text("№ {serial}", 60, 200, 520, 30, 18, 12);
      num.ink = 0;
      num.color = t.accent;
      t.back.add(num);
      return t;
   }

   public static PassportTemplate sampleTicket() {
      PassportTemplate t = new PassportTemplate();
      t.format = 2;
      t.name = "Проездной билет";
      t.org = "Транспортная компания";
      t.title = "БИЛЕТ";
      t.serialPrefix = "TK";
      t.bg = 15985625;
      t.accent = 9050907;
      t.pattern = 2;
      t.validYears = 1;
      t.fields.add(field("from", "Откуда", 0, 24, 66, 210, 38));
      t.fields.add(field("to", "Куда", 0, 244, 66, 210, 38));
      t.fields.add(field("date", "Дата поездки", 1, 24, 108, 140, 38));
      t.fields.add(field("time", "Время", 0, 174, 108, 110, 38));
      t.fields.add(field("seat", "Место", 0, 294, 108, 160, 38));
      t.fields.add(field("passenger", "Пассажир", 0, 24, 150, 430, 38));
      t.fields.add(field("serial", "Номер", 3, 492, 66, 128, 36));
      t.fields.add(field("issued", "Выдан", 5, 492, 108, 128, 38));
      t.fields.get(5).required = false;
      return t;
   }

   public static PassportTemplate sampleFine() {
      PassportTemplate t = new PassportTemplate();
      t.format = 3;
      t.name = "Постановление о штрафе";
      t.org = "Полиция";
      t.title = "ШТРАФ";
      t.serialPrefix = "FN";
      t.bg = 16250092;
      t.accent = 2764602;
      t.pattern = 0;
      t.validYears = 1;
      t.fields.add(field("serial", "Номер постановления", 3, 30, 86, 360, 36));
      t.fields.add(field("violator", "Нарушитель", 0, 30, 134, 360, 40));
      t.fields.add(field("reason", "Нарушение", 0, 30, 182, 360, 40));
      t.fields.add(field("place", "Место", 0, 30, 230, 360, 40));
      t.fields.add(field("amount", "Сумма", 0, 30, 278, 170, 40));
      t.fields.add(field("issued", "Дата", 5, 220, 278, 170, 40));
      t.fields.add(field("officer", "Инспектор", 0, 30, 326, 360, 40));
      t.fields.add(field("sign", "Подпись инспектора", 4, 30, 378, 360, 60));
      t.fields.get(3).required = false;
      DesignElement note = DesignElement.text("ОПЛАТИТЬ В ТЕЧЕНИЕ 7 ДНЕЙ", 30, 470, 360, 20, 11, 10);
      note.ink = 0;
      note.color = 9050907;
      t.decor.add(note);
      return t;
   }

   public static PassportTemplate preset(String key) {
      return key != null && !key.isEmpty() ? CountryPresets.build(key.toLowerCase(Locale.ROOT)) : null;
   }

   public static boolean computed(PassportTemplate.Field f) {
      return issueDate(f) || f.type == 6;
   }

   private static PassportTemplate.Field field(String key, String label, int type, int x, int y, int w, int h) {
      PassportTemplate.Field f = new PassportTemplate.Field();
      f.key = key;
      f.label = label;
      f.type = type;
      f.x = x;
      f.y = y;
      f.w = w;
      f.h = h;
      return f;
   }

   public PassportTemplate copy() {
      PassportTemplate t = new PassportTemplate();
      t.id = this.id;
      t.format = this.format;
      t.name = this.name;
      t.org = this.org;
      t.ownerUuid = this.ownerUuid;
      t.ownerName = this.ownerName;
      t.approved = this.approved;
      t.created = this.created;
      t.replaces = this.replaces;
      t.title = this.title;
      t.subtitle = this.subtitle;
      t.bg = this.bg;
      t.accent = this.accent;
      t.pattern = this.pattern;
      t.coverColor = this.coverColor;
      t.coverTitle = this.coverTitle;
      t.coverText = this.coverText;
      t.emblem = this.emblem;
      t.emblemCode = this.emblemCode;
      t.serialPrefix = this.serialPrefix;
      t.autoStampId = this.autoStampId;
      t.flag = this.flag;
      t.visaSpreads = this.visaSpreads;
      t.visaTitle = this.visaTitle;
      t.validYears = this.validYears;

      for (DesignElement e : this.cover) {
         t.cover.add(e.copy());
      }

      for (DesignElement e : this.decor) {
         t.decor.add(e.copy());
      }

      for (DesignElement e : this.back) {
         t.back.add(e.copy());
      }

      t.coverTexture = this.coverTexture;
      t.coverFrame = this.coverFrame;
      t.hideHeader = this.hideHeader;

      for (PassportTemplate.Field f : this.fields) {
         t.fields.add(f.copy());
      }

      return t;
   }

   public PassportTemplate.Field find(String key) {
      for (PassportTemplate.Field f : this.fields) {
         if (f.key.equals(key)) {
            return f;
         }
      }

      return null;
   }

   public void sanitize() {
      this.format = Math.max(0, Math.min(FORMATS.length - 1, this.format));
      int W = this.w();
      int H = this.h();

      while (this.fields.size() > 16) {
         this.fields.remove(this.fields.size() - 1);
      }

      Set<String> seen = new HashSet<>();

      for (PassportTemplate.Field f : this.fields) {
         f.type = Math.max(0, Math.min(FIELD_TYPES.length - 1, f.type));
         f.x = Math.max(0, Math.min(W - 20, f.x));
         f.y = Math.max(0, Math.min(H - 20, f.y));
         f.w = Math.max(20, Math.min(W - f.x, f.w));
         f.h = Math.max(16, Math.min(H - f.y, f.h));
         f.size = Math.max(8, Math.min(40, f.size));
         f.key = f.key.replaceAll("[^a-zA-Z0-9_]", "");
         if (f.key.isEmpty()) {
            f.key = "f" + seen.size();
         }

         String base = f.key;
         int n = 2;

         while (!seen.add(f.key)) {
            f.key = base + n++;
         }
      }

      this.pattern = Math.max(0, Math.min(PATTERNS.length - 1, this.pattern));
      this.visaSpreads = Math.max(0, Math.min(8, this.visaSpreads));
      this.validYears = Math.max(1, Math.min(30, this.validYears));

      while (this.cover.size() > 24) {
         this.cover.remove(this.cover.size() - 1);
      }

      while (this.decor.size() > 24) {
         this.decor.remove(this.decor.size() - 1);
      }

      while (this.back.size() > 24) {
         this.back.remove(this.back.size() - 1);
      }

      for (DesignElement e : this.cover) {
         e.sanitize(300, 420);
      }

      for (DesignElement e : this.decor) {
         e.sanitize(W, H);
      }

      for (DesignElement e : this.back) {
         e.sanitize(W, H);
      }

      this.coverTexture = Math.max(0, Math.min(TEXTURES.length - 1, this.coverTexture));
      this.coverFrame = Math.max(0, Math.min(FRAMES.length - 1, this.coverFrame));
      if (this.flag == null || this.flag.length() > 80 || !this.flag.matches("[a-z]*:?[0-9A-Fa-f,]*")) {
         this.flag = "";
      }

      if (this.visaTitle == null) {
         this.visaTitle = "";
      }

      if (this.visaTitle.length() > 40) {
         this.visaTitle = this.visaTitle.substring(0, 40);
      }

      this.emblemCode = Math.max(0, Math.min(EMBLEM_NAMES.length - 1, this.emblemCode));
      this.bg &= 16777215;
      this.accent &= 16777215;
      this.coverColor &= 16777215;
   }

   public String canonical() {
      StringBuilder sb = new StringBuilder(this.format == 0 ? "PT1;" : "PT2;" + this.format + ";");
      a(sb, this.id, this.title, this.subtitle, this.org, this.serialPrefix, this.coverTitle, this.coverText, this.emblem, this.autoStampId);
      a(sb, this.bg + "," + this.accent + "," + this.coverColor + "," + this.pattern + "," + this.emblemCode);

      for (PassportTemplate.Field f : this.fields) {
         a(sb, f.key, f.label, f.type + "," + f.x + "," + f.y + "," + f.w + "," + f.h + "," + f.size + "," + f.bold + "," + f.required, f.prefix, f.hint);
      }

      return sb.toString();
   }

   private static void a(StringBuilder sb, String... parts) {
      for (String p : parts) {
         String s = p == null ? "" : p;
         sb.append(s.length()).append(':').append(s).append(';');
      }
   }

   public static final class Field {
      public String key = "";
      public String label = "";
      public int type;
      public int x;
      public int y;
      public int w;
      public int h;
      public int size = 15;
      public boolean bold;
      public boolean required = true;
      public String prefix = "";
      public String hint = "";

      public PassportTemplate.Field copy() {
         PassportTemplate.Field f = new PassportTemplate.Field();
         f.key = this.key;
         f.label = this.label;
         f.type = this.type;
         f.x = this.x;
         f.y = this.y;
         f.w = this.w;
         f.h = this.h;
         f.size = this.size;
         f.bold = this.bold;
         f.required = this.required;
         f.prefix = this.prefix;
         f.hint = this.hint;
         return f;
      }
   }
}
