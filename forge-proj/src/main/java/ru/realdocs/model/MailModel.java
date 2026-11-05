package ru.realdocs.model;

import java.util.List;

/** Envelope: an address, a letter and up to three attachments, optionally closed with a wax seal. */
public final class MailModel {
   public static final int OPEN = 0;
   public static final int SEALED = 1;
   public static final int BROKEN = 2;
   public static final int MAX_TEXT = 1500;
   public static final int MAX_NAME = 40;
   public static final int MAX_ITEMS = 3;
   public static final String[] WAX_NAMES = new String[]{"Красный", "Бордовый", "Синий", "Зелёный", "Чёрный", "Золотой"};
   public static final int[] WAX_RGB = new int[]{0xB3261E, 0x6E1423, 0x1F3E8C, 0x2E6B35, 0x2A2626, 0xC9A13B};
   public String id = "";
   public String to = "";
   public String from = "";
   public String fromUuid = "";
   public String text = "";
   public String date = "";
   public int state = OPEN;
   public int wax = 0;
   public String sealId = "";
   public String sealOrg = "";
   public boolean sealForged;
   public String sealer = "";
   public String sealerUuid = "";
   public String sealDate = "";
   public String hash = "";
   public String brokenDate = "";
   /** Letter typeface (index into the bundled fonts), handwriting by default. */
   public int font = 6;
   /** Date stamped by the mail when sent (not covered by the seal). */
   public String postmark = "";

   public boolean sealed() {
      return this.state == SEALED;
   }

   public boolean broken() {
      return this.state == BROKEN;
   }

   public boolean blank() {
      return this.to.isEmpty() && this.from.isEmpty() && this.text.isEmpty() && this.state == OPEN;
   }

   public int waxRgb() {
      return WAX_RGB[Math.max(0, Math.min(WAX_RGB.length - 1, this.wax))];
   }

   /** What the wax seal protects; attachments are passed as their own canonical strings. */
   public String canonical(List<String> attachments) {
      StringBuilder sb = new StringBuilder("ML1;");
      f(sb, this.id);
      f(sb, this.to);
      f(sb, this.from);
      f(sb, this.fromUuid);
      f(sb, this.text);
      f(sb, this.date);
      f(sb, String.valueOf(this.wax));
      f(sb, this.sealId);
      f(sb, this.sealForged ? "F" : "");
      f(sb, this.sealer);
      f(sb, this.sealerUuid);
      f(sb, this.sealDate);
      f(sb, String.valueOf(this.font));
      sb.append('A').append(attachments.size()).append(';');

      for (String a : attachments) {
         f(sb, a);
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
