package ru.realdocs.model;

import java.util.ArrayList;
import java.util.List;

public final class DocModel {
   public static final String[] TEMPLATE_TITLES = new String[]{"ПОСТАНОВЛЕНИЕ", "ПРИКАЗ", "СПРАВКА", "ДОГОВОР"};
   public static final String[] TEMPLATE_NAMES = new String[]{"Постановление", "Приказ", "Справка", "Договор"};
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
   public static final int[][] DEFAULT_SPOTS = new int[][]{{350, 880}, {770, 880}, {560, 915}, {250, 820}, {860, 820}, {560, 840}};

   public static DocModel.StampRef defaultPlacement(String id, int index) {
      int[] p = DEFAULT_SPOTS[Math.floorMod(index, DEFAULT_SPOTS.length)];
      return new DocModel.StampRef(id, 0, p[0], p[1], index * 37 % 31 - 15, 100);
   }

   public static int templateIndex(int t) {
      return Math.floorMod(t, TEMPLATE_TITLES.length);
   }

   public String title() {
      return TEMPLATE_TITLES[templateIndex(this.template)];
   }

   public String templateName() {
      return TEMPLATE_NAMES[templateIndex(this.template)];
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
