package ru.realdocs.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class PassportModel {
   public String templateId = "";
   public int format;
   public boolean forged;
   public final Map<String, String> values = new LinkedHashMap<>();
   public String photo = "";
   public int[] signature = new int[0];
   public boolean issued;
   public String passId = "";
   public String serial = "";
   public String date = "";
   public String holderName = "";
   public String holderUuid = "";
   public String issuer = "";
   public String issuerUuid = "";
   public String sec = "";
   public final List<DocModel.StampRef> stamps = new ArrayList<>();

   public String canonical() {
      StringBuilder sb = new StringBuilder("PP1;");
      f(sb, this.passId);
      f(sb, this.templateId);
      f(sb, this.serial);
      f(sb, this.date);
      f(sb, this.holderName);
      f(sb, this.holderUuid);
      f(sb, this.issuer);
      f(sb, this.issuerUuid);
      f(sb, this.photo);
      StringBuilder st = new StringBuilder();

      for (int v : this.signature) {
         st.append(v).append(',');
      }

      f(sb, st.toString());
      sb.append("V").append(this.values.size()).append(';');

      for (Entry<String, String> e : this.values.entrySet()) {
         f(sb, e.getKey());
         f(sb, e.getValue());
      }

      sb.append("T").append(this.stamps.size()).append(';');

      for (DocModel.StampRef r : this.stamps) {
         f(sb, r.canonical());
      }

      if (!this.sec.isEmpty()) {
         f(sb, "SEC:" + this.sec);
      }

      if (this.format != 0) {
         f(sb, "FMT:" + this.format);
      }

      if (this.forged) {
         f(sb, "FORGED");
      }

      return sb.toString();
   }

   private static void f(StringBuilder sb, String s) {
      if (s == null) {
         s = "";
      }

      sb.append(s.length()).append(':').append(s).append(';');
   }
}
