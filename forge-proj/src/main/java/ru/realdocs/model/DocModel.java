package ru.realdocs.model;

import java.util.ArrayList;
import java.util.List;

public final class DocModel {
   public static final String[] TEMPLATE_TITLES = new String[]{"ПОСТАНОВЛЕНИЕ", "ПРИКАЗ", "СПРАВКА", "ДОГОВОР", "ПРОПУСК"};
   public static final String[] TEMPLATE_NAMES = new String[]{"Постановление", "Приказ", "Справка", "Договор", "Пропуск"};
   public static final int TEMPLATE_PASS = 4;
   public int template;
   public String number = "";
   public String org = "";
   public String position = "";
   public String subject = "";
   public String body = "";
   public boolean issued;
   public String sec = "";
   public String docId = "";
   public String date = "";
   public String author = "";
   public String authorUuid = "";
   public boolean forged;
   public boolean copy;
   public final List<DocModel.Sig> sigs = new ArrayList<>();
   public final List<DocModel.StampRef> stamps = new ArrayList<>();
   /** Custom look and type name from a document template (2.4.0); null for the classic types. */
   public DocDesign design;
   /** Template the document was made from (informational only). */
   public String templateId = "";
   /** Registry number assigned when the document is issued, e.g. RD-000042. */
   public String regNo = "";
   /** Registry numbers this document refers to. */
   public final List<String> refs = new ArrayList<>();
   /** Registry numbers this document replaces: they lose force once it is issued. */
   public final List<String> supersedes = new ArrayList<>();
   /** Last day the document is valid (dd.MM.yyyy, real date); empty = no limit. */
   public String expires = "";
   /** Zones for readers, comma-separated (e.g. "Депо, Стройка-1"); empty = any reader without a zone. */
   public String zones = "";
   /** Who the document (a pass) was issued to; empty = bearer. */
   public String holder = "";
   /** Public documents show their details to anyone searching the registry. */
   public boolean pub;
   public static final int[][] DEFAULT_SPOTS = new int[][]{{350, 880}, {770, 880}, {560, 915}, {250, 820}, {860, 820}, {560, 840}};

   public static DocModel.StampRef defaultPlacement(String id, int index) {
      int[] p = DEFAULT_SPOTS[Math.floorMod(index, DEFAULT_SPOTS.length)];
      return new DocModel.StampRef(id, 0, p[0], p[1], index * 37 % 31 - 15, 100);
   }

   public static int templateIndex(int t) {
      return Math.floorMod(t, TEMPLATE_TITLES.length);
   }

   public String title() {
      return this.design != null && !this.design.title.isBlank() ? this.design.title : TEMPLATE_TITLES[templateIndex(this.template)];
   }

   public String templateName() {
      return this.design != null && !this.design.name.isBlank() ? this.design.name : TEMPLATE_NAMES[templateIndex(this.template)];
   }

   public static String refsText(List<String> l) {
      return String.join(", ", l);
   }

   /** Parses "RD-1, RD-000002 ..." into normalized registry numbers (at most six). */
   public static List<String> parseRefs(String s) {
      List<String> out = new ArrayList<>();
      if (s == null) {
         return out;
      } else {
         for (String part : s.split("[,;\\s]+")) {
            String n = normRef(part);
            if (!n.isEmpty() && !out.contains(n) && out.size() < 6) {
               out.add(n);
            }
         }

         return out;
      }
   }

   public static String normRef(String s) {
      if (s == null) {
         return "";
      } else {
         String t = s.trim().toUpperCase(java.util.Locale.ROOT).replace("№", "").replace("РД", "RD");
         if (t.startsWith("RD-")) {
            t = t.substring(3);
         } else if (t.startsWith("RD")) {
            t = t.substring(2);
         }

         if (t.isEmpty() || t.length() > 9 || !t.chars().allMatch(Character::isDigit)) {
            return "";
         } else {
            long v = Long.parseLong(t);
            return v <= 0L ? "" : regFormat(v);
         }
      }
   }

   private static final java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

   /** Parses dd.MM.yyyy (also d.M.yyyy); null if it is not a date. */
   public static java.time.LocalDate parseDate(String s) {
      if (s == null) {
         return null;
      } else {
         String[] p = s.trim().split("[./-]");
         if (p.length != 3) {
            return null;
         } else {
            try {
               int d = Integer.parseInt(p[0]);
               int m = Integer.parseInt(p[1]);
               int y = Integer.parseInt(p[2]);
               if (y < 100) {
                  y += 2000;
               }

               return java.time.LocalDate.of(y, m, d);
            } catch (RuntimeException var5) {
               return null;
            }
         }
      }
   }

   public static String formatDate(java.time.LocalDate d) {
      return d.format(DMY);
   }

   /** True when the document has a validity date and it has passed. */
   public boolean expired() {
      java.time.LocalDate d = parseDate(this.expires);
      return d != null && java.time.LocalDate.now().isAfter(d);
   }

   public List<String> zoneList() {
      List<String> out = new ArrayList<>();

      for (String z : this.zones.split(",")) {
         if (!z.isBlank()) {
            out.add(z.trim());
         }
      }

      return out;
   }

   public static String regFormat(long v) {
      return String.format(java.util.Locale.ROOT, "RD-%06d", v);
   }

   public boolean hasAuthorizedSignature(String uuid) {
      for (DocModel.Sig s : this.sigs) {
         if (s.uuid.equals(uuid)) {
            return true;
         }
      }

      return false;
   }

   public String canonical() {
      StringBuilder sb = new StringBuilder("RD1;");
      f(sb, this.docId);
      f(sb, Integer.toString(templateIndex(this.template)));
      f(sb, this.number);
      f(sb, this.org);
      f(sb, this.position);
      f(sb, this.subject);
      f(sb, this.body);
      f(sb, this.date);
      f(sb, this.author);
      f(sb, this.authorUuid);
      sb.append("S").append(this.sigs.size()).append(';');

      for (DocModel.Sig s : this.sigs) {
         f(sb, s.name);
         f(sb, s.uuid);
         f(sb, s.position);
         StringBuilder st = new StringBuilder();

         for (int v : s.strokes) {
            st.append(v).append(',');
         }

         f(sb, st.toString());
      }

      sb.append("T").append(this.stamps.size()).append(';');

      for (DocModel.StampRef r : this.stamps) {
         f(sb, r.canonical());
      }

      if (!this.sec.isEmpty()) {
         f(sb, "SEC:" + this.sec);
      }

      if (this.forged) {
         f(sb, "FORGED");
      }

      if (this.copy) {
         f(sb, "COPY");
      }

      if (this.design != null) {
         f(sb, "DS:" + this.design.canonical());
      }

      if (!this.regNo.isEmpty()) {
         f(sb, "REG:" + this.regNo);
      }

      if (!this.refs.isEmpty()) {
         f(sb, "REF:" + String.join(",", this.refs));
      }

      if (!this.supersedes.isEmpty()) {
         f(sb, "SUP:" + String.join(",", this.supersedes));
      }

      if (!this.expires.isEmpty()) {
         f(sb, "EXP:" + this.expires);
      }

      if (!this.zones.isEmpty()) {
         f(sb, "ZONE:" + this.zones);
      }

      if (!this.holder.isEmpty()) {
         f(sb, "HOLD:" + this.holder);
      }

      if (this.pub) {
         f(sb, "PUB");
      }

      return sb.toString();
   }

   private static void f(StringBuilder sb, String s) {
      if (s == null) {
         s = "";
      }

      sb.append(s.length()).append(':').append(s).append(';');
   }

   public static final class Sig {
      public String name = "";
      public String uuid = "";
      public String position = "";
      public int[] strokes = new int[0];
   }

   public static final class StampRef {
      public String id = "";
      public int page;
      public int x = 500;
      public int y = 900;
      public int rot;
      public int scale = 100;
      public int dir;
      public int cat;
      public String date = "";
      public String until = "";
      public String num = "";
      public boolean forged;
      public static final String[] CATEGORIES = new String[]{"Туристическая", "Деловая", "Рабочая", "Транзитная", "Учебная"};
      public static final String[] CATEGORIES_SHORT = new String[]{"ТУРИСТ", "ДЕЛОВАЯ", "РАБОЧАЯ", "ТРАНЗИТ", "УЧЁБА"};
      public static final String[] CATEGORIES_UI = new String[]{"Турист", "Деловая", "Рабочая", "Транзит", "Учёба"};
      public static final int[] VISA_DAYS = new int[]{7, 30, 90, 180, 365};

      public StampRef() {
      }

      public boolean hasExtra() {
         return this.dir != 0 || this.cat != 0 || !this.date.isEmpty() || !this.until.isEmpty() || !this.num.isEmpty();
      }

      public DocModel.StampRef extra(int dir, int cat, String date, String until, String num) {
         this.dir = clamp(dir, 0, 2);
         this.cat = clamp(cat, 0, CATEGORIES.length - 1);
         this.date = date == null ? "" : date;
         this.until = until == null ? "" : until;
         this.num = num == null ? "" : num;
         return this;
      }

      public StampRef(String id, int page, int x, int y, int rot, int scale) {
         this.id = id;
         this.page = Math.max(0, Math.min(16, page));
         this.x = clamp(x, 0, 1000);
         this.y = clamp(y, 0, 1000);
         this.rot = clamp(rot, -180, 180);
         this.scale = clamp(scale, 60, 160);
      }

      public DocModel.StampRef copy() {
         DocModel.StampRef r = new DocModel.StampRef(this.id, this.page, this.x, this.y, this.rot, this.scale)
            .extra(this.dir, this.cat, this.date, this.until, this.num);
         r.forged = this.forged;
         return r;
      }

      public String canonical() {
         String base = this.id + "@" + this.page + "," + this.x + "," + this.y + "," + this.rot + "," + this.scale;
         if (this.hasExtra()) {
            base = base + "|" + this.dir + "," + this.cat + "," + this.date + "," + this.until + "," + this.num;
         }

         return this.forged ? base + "|F" : base;
      }

      static int clamp(int v, int lo, int hi) {
         return Math.max(lo, Math.min(hi, v));
      }
   }
}
