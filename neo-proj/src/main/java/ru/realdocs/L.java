package ru.realdocs;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class L {
   public static final char OPEN = '\u0001';
   public static final char CLOSE = '\u0002';

   private L() {
   }

   public static String k(String ru) {
      return ru != null && !ru.isEmpty()
         ? OPEN + ru + CLOSE
         : ru;
   }

   private static String lookup(String s) {
      if (s.isEmpty()) {
         return s;
      } else {
         Language lang = Language.getInstance();
         return lang.has(s) ? lang.getOrDefault(s) : s;
      }
   }

   public static String t(String s) {
      if (s == null || s.isEmpty()) {
         return s;
      } else if (s.indexOf(1) < 0) {
         return lookup(s);
      } else {
         StringBuilder sb = new StringBuilder(s.length());
         int i = 0;

         while (i < s.length()) {
            int a = s.indexOf(1, i);
            if (a < 0) {
               sb.append(s, i, s.length());
               break;
            }

            sb.append(s, i, a);
            int b = s.indexOf(2, a + 1);
            if (b < 0) {
               sb.append(s, a + 1, s.length());
               break;
            }

            sb.append(lookup(s.substring(a + 1, b)));
            i = b + 1;
         }

         return sb.toString();
      }
   }

   public static String f(String ru, Object... args) {
      String s = t(ru);

      for (Object a : args) {
         int p = s.indexOf("%s");
         if (p < 0) {
            break;
         }

         s = s.substring(0, p) + a + s.substring(p + 2);
      }

      return s;
   }

   public static String plain(String s) {
      if (s == null) {
         return null;
      } else {
         return s.indexOf(1) < 0 ? s : s.replace(String.valueOf('\u0001'), "").replace(String.valueOf('\u0002'), "");
      }
   }

   /** Turns a component back into a marked string, so a dedicated server can pass names on untranslated. */
   public static String s(Component c) {
      StringBuilder out = new StringBuilder();
      if (c != null) {
         mark(c, out);
      }

      return out.toString();
   }

   private static void mark(Component c, StringBuilder out) {
      if (c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getArgs().length == 0) {
         out.append(k(tc.getKey()));
      } else {
         c.getContents().visit(text -> {
            out.append(text);
            return java.util.Optional.empty();
         });
      }

      for (Component p : c.getSiblings()) {
         mark(p, out);
      }
   }

   public static MutableComponent c(String s) {
      if (s == null) {
         return Component.empty();
      } else if (s.indexOf(1) < 0) {
         return Component.literal(s);
      } else {
         MutableComponent root = Component.empty();
         int i = 0;

         while (i < s.length()) {
            int a = s.indexOf(1, i);
            if (a < 0) {
               root.append(Component.literal(s.substring(i)));
               break;
            }

            if (a > i) {
               root.append(Component.literal(s.substring(i, a)));
            }

            int b = s.indexOf(2, a + 1);
            if (b < 0) {
               root.append(Component.literal(s.substring(a + 1)));
               break;
            }

            String key = s.substring(a + 1, b);
            root.append(Component.translatableWithFallback(key, key.replace("%", "%%")));
            i = b + 1;
         }

         return root;
      }
   }
}
