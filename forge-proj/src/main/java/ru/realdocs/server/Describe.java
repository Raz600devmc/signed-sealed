package ru.realdocs.server;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.MailModel;
import ru.realdocs.model.PaperElement;
import ru.realdocs.model.PaperModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;

/** Searchable metadata of stored documents (archives and digital archives). */
public final class Describe {
   public static final int T_DOC = 0;
   public static final int T_PASS = 1;
   public static final int T_PAPER = 2;
   public static final int T_MAIL = 3;
   public static final int T_FOLDER = 4;
   public static final String[] TYPE_NAMES = new String[]{"Документ", "Паспорт", "Газета", "Письмо", "Папка"};

   private Describe() {
   }

   public static final class Meta {
      public int type;
      public String title = "";
      public String author = "";
      public String holder = "";
      public String date = "";
      public String text = "";
   }

   public static int type(ItemStack s) {
      if (s.getItem() instanceof PassportItem) {
         return T_PASS;
      } else if (s.getItem() instanceof NewspaperItem) {
         return T_PAPER;
      } else if (s.getItem() instanceof EnvelopeItem) {
         return T_MAIL;
      } else {
         return s.getItem() instanceof FolderItem ? T_FOLDER : T_DOC;
      }
   }

   public static Meta of(MinecraftServer server, ItemStack s) {
      Meta m = new Meta();
      m.type = type(s);
      if (s.getItem() instanceof DocumentItem) {
         DocModel d = NbtCodec.readDoc(s);
         m.title = (d.title() + (d.number.isEmpty() ? "" : " № " + d.number) + (d.subject.isEmpty() ? "" : " — " + d.subject)).strip();
         m.author = d.author;
         m.holder = d.org;
         m.date = d.date;
         m.text = d.subject + "\n" + d.body;
         if (d.copy) {
            m.title = m.title + L.k(" (копия)");
         }
      } else if (s.getItem() instanceof PassportItem) {
         PassportModel p = NbtCodec.readPass(s);
         PassportTemplate t = server == null ? null : DocStore.get(server).templates.get(p.templateId);
         String kind = PassportTemplate.FORMATS[Math.max(0, Math.min(3, p.format))];
         m.title = (t != null && !t.name.isEmpty() ? t.name : L.k(kind)) + (p.serial.isEmpty() ? "" : " " + p.serial);
         m.author = p.issuer;
         m.holder = p.holderName;
         m.date = p.date;
         StringBuilder sb = new StringBuilder();

         for (String v : p.values.values()) {
            sb.append(v).append(' ');
         }

         m.text = sb.toString();
      } else if (s.getItem() instanceof NewspaperItem) {
         PaperCodec.Ref r = PaperCodec.ref(s);
         m.title = L.k(NewspaperItem.kind(r.format)) + " «" + r.name + "»" + (r.issue > 0 ? " № " + r.issue : "");
         m.author = r.editor;
         m.date = r.date;
         if (server != null && r.published) {
            PaperModel pm = PaperService.loadModel(server, r.id);
            if (pm != null) {
               StringBuilder sb = new StringBuilder();

               for (List<PaperElement> page : pm.pages) {
                  for (PaperElement e : page) {
                     if (e.textual() && sb.length() < 6000) {
                        sb.append(e.text.replace("**", "").replace("__", "")).append('\n');
                     }
                  }
               }

               m.text = sb.toString();
            }
         }
      } else if (s.getItem() instanceof EnvelopeItem) {
         MailModel mm = MailCodec.readMail(s);
         m.title = L.k("Письмо") + (mm.to.isEmpty() ? "" : L.k(" для ") + mm.to);
         m.author = mm.from;
         m.holder = mm.to;
         m.date = mm.date;
         m.text = mm.sealed() ? "" : mm.text;
      } else if (s.getItem() instanceof FolderItem) {
         m.title = L.s(s.getHoverName());
         m.text = "";
      }

      if (m.title.isBlank()) {
         m.title = L.s(s.getHoverName());
      }

      return m;
   }

   /** One-line description for listings. */
   public static String line(Meta m, int count) {
      StringBuilder sb = new StringBuilder(L.k(TYPE_NAMES[m.type]));
      if (!m.holder.isEmpty()) {
         sb.append(" · ").append(m.holder);
      }

      if (!m.author.isEmpty() && !m.author.equals(m.holder)) {
         sb.append(" · ").append(m.author);
      }

      if (!m.date.isEmpty()) {
         sb.append(" · ").append(m.date);
      }

      if (m.type == T_FOLDER) {
         sb.append(" · ").append(L.k("документов: ")).append(count);
      }

      return sb.toString();
   }
}
