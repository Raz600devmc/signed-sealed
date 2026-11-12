package ru.realdocs.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.L;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.OfficeData;
import ru.realdocs.model.DocModel;

/**
 * Registry numbers (RD-000042) for issued documents, references between documents and
 * replacement: a document can name older ones it supersedes, and those lose force.
 */
public final class RegistryService {
   private RegistryService() {
   }

   /** Copies the template look and the references from the editor into a new draft. */
   static String prepare(ServerPlayer sp, DocModel cur, DocModel in, DocModel d) {
      d.zones = DocService.clean(in.zones, 80, false);
      d.holder = DocService.clean(in.holder, 40, false);
      d.pub = in.pub;
      String exp = in.expires == null ? "" : in.expires.trim();
      if (!exp.isEmpty()) {
         java.time.LocalDate ld = DocModel.parseDate(exp);
         if (ld == null) {
            return L.k("Срок действия — дата в виде дд.мм.гггг.");
         }

         d.expires = DocModel.formatDate(ld);
      }

      d.refs.addAll(in.refs);
      d.supersedes.addAll(in.supersedes);
      d.supersedes.removeIf(d.refs::contains);
      if (in.templateId != null && !in.templateId.isEmpty()) {
         OfficeData.Template t = TemplateService.usable(sp, in.templateId);
         if (t == null) {
            if (cur.design != null && in.templateId.equals(cur.templateId)) {
               d.design = cur.design.copy();
               d.templateId = cur.templateId;
               return null;
            }

            return L.k("Этот шаблон документа вам недоступен.");
         }

         d.design = t.design.copy();
         d.templateId = t.id;
      }

      return null;
   }

   static String[] reg(OfficeData od, String regNo) {
      String v = od.regs.get(regNo);
      if (v == null) {
         return null;
      } else {
         int i = v.indexOf(1);
         return i < 0 ? new String[]{v, ""} : new String[]{v.substring(0, i), v.substring(i + 1)};
      }
   }

   /** Checks references before a document is issued. Returns an error or null. */
   static String checkSupersede(ServerPlayer sp, DocModel d) {
      if (d.expired()) {
         return L.k("Срок действия уже прошёл — укажите будущую дату.");
      }

      OfficeData od = OfficeData.get(sp.server);
      DocStore store = DocStore.get(sp.server);

      for (String r : d.refs) {
         if (reg(od, r) == null) {
            return L.k("Документа ") + r + L.k(" нет в реестре — проверьте номер ссылки.");
         }
      }

      String me = sp.getUUID().toString();

      for (String r : d.supersedes) {
         String[] x = reg(od, r);
         if (x == null) {
            return L.k("Документа ") + r + L.k(" нет в реестре — проверьте номер ссылки.");
         }

         if (od.superseded.containsKey(r)) {
            return L.k("Документ ") + r + L.k(" уже утратил силу.");
         }

         DocStore.Rec rec = store.docs.get(x[0]);
         boolean author = rec != null && rec.authorUuid.equals(me);
         boolean sameOrg = !x[1].isBlank()
            && OfficeData.key(x[1]).equals(OfficeData.key(d.org))
            && OrgService.manager(sp.server, x[1], me);
         if (!author && !sameOrg && !BureauService.admin(sp)) {
            return L.k("Отменить документ ")
               + r
               + L.k(" может только его автор или руководитель организации «")
               + (x[1].isBlank() ? "?" : x[1])
               + L.k("», выпуская документ от её имени.");
         }
      }

      return null;
   }

   static String assign(MinecraftServer server, DocModel d) {
      OfficeData od = OfficeData.get(server);
      String reg = od.nextReg();
      od.regs.put(reg, d.docId + "\u0001" + d.org);
      od.setDirty();
      return reg;
   }

   static void supersede(MinecraftServer server, DocModel d) {
      if (!d.supersedes.isEmpty()) {
         OfficeData od = OfficeData.get(server);

         for (String r : d.supersedes) {
            od.superseded.put(r, d.regNo + "\u0001" + d.date);
         }

         od.setDirty();
      }
   }

   static String state(OfficeData od, String r) {
      if (reg(od, r) == null) {
         return r + L.k(" (нет в реестре)");
      } else {
         String s = od.superseded.get(r);
         return s == null ? r + L.k(" (действует)") : r + L.k(" (утратил силу)");
      }
   }

   /** Remembers the latest state of an issued document for registry searches. */
   static void snapshot(MinecraftServer server, DocModel d) {
      if (!d.regNo.isEmpty() && !d.copy) {
         OfficeData od = OfficeData.get(server);
         od.regDocs.put(d.regNo, ru.realdocs.data.NbtCodec.writeDoc(d));
         od.setDirty();
      }
   }

   /** Adds registry information to a verification result; superseded or expired documents lose force. */
   static DocService.VerifyResult annotate(MinecraftServer server, DocModel d, DocService.VerifyResult vr) {
      if (vr.status() != DocService.Status.OK && vr.status() != DocService.Status.COPY) {
         return vr;
      } else {
         OfficeData od = OfficeData.get(server);
         List<String> lines = new ArrayList<>();
         DocService.Status st = vr.status();
         String sup = d.regNo.isEmpty() ? null : od.superseded.get(d.regNo);
         if (sup != null) {
            int i = sup.indexOf(1);
            String by = i < 0 ? sup : sup.substring(0, i);
            String date = i < 0 ? "" : sup.substring(i + 1);
            lines.add(L.k("Документ утратил силу: его заменил документ ") + by + (date.isEmpty() ? "" : L.k(" от ") + date) + ".");
            st = DocService.Status.REVOKED;
         }

         if (d.expired()) {
            lines.add(L.k("Срок действия истёк ") + d.expires + ".");
            st = DocService.Status.REVOKED;
         }

         lines.addAll(vr.lines());
         if (!d.expires.isEmpty() && !d.expired()) {
            lines.add(L.k("Действует до ") + d.expires);
         }

         if (!d.holder.isEmpty()) {
            lines.add(L.k("Выдан: ") + d.holder);
         }

         if (!d.zones.isEmpty()) {
            lines.add(L.k("Зоны: ") + d.zones);
         }

         if (!d.regNo.isEmpty()) {
            lines.add(L.k("Рег. № ") + d.regNo);
         }

         if (!d.refs.isEmpty()) {
            List<String> l = new ArrayList<>();

            for (String r : d.refs) {
               l.add(state(od, r));
            }

            lines.add(L.k("Ссылается на: ") + String.join(", ", l));
         }

         if (!d.supersedes.isEmpty()) {
            lines.add(L.k("Отменяет: ") + String.join(", ", d.supersedes));
         }

         return new DocService.VerifyResult(st, lines);
      }
   }

   /** May this player see the full text of a registered document? */
   static boolean canSee(ServerPlayer sp, DocModel d, String network) {
      String me = sp.getUUID().toString();
      if (d.pub || BureauService.admin(sp) || d.authorUuid.equals(me) || d.hasAuthorizedSignature(me)) {
         return true;
      } else if (!d.holder.isEmpty() && d.holder.equalsIgnoreCase(sp.getGameProfile().getName())) {
         return true;
      } else if (!d.org.isBlank() && OrgService.member(sp.server, d.org.trim(), me)) {
         return true;
      } else {
         if (network != null) {
            for (net.minecraft.nbt.CompoundTag r : ru.realdocs.data.DocStore.get(sp.server).digital.getOrDefault(network.toLowerCase(java.util.Locale.ROOT), List.of())) {
               if (r.getCompound("Item").toString().contains(d.docId)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   /** Registry search from a computer: status by number, details for those allowed to see them. */
   static void find(ServerPlayer sp, String query, String network) {
      OfficeData od = OfficeData.get(sp.server);
      String reg = DocModel.normRef(query);
      net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
      t.putString("Query", query == null ? "" : query.trim());
      t.putString("Reg", reg);
      String[] x = reg.isEmpty() ? null : reg(od, reg);
      if (x == null) {
         t.putInt("State", -1);
      } else {
         DocStore store = DocStore.get(sp.server);
         DocStore.Rec rec = store.docs.get(x[0]);
         net.minecraft.nbt.CompoundTag snap = od.regDocs.get(reg);
         DocModel d = snap == null ? null : ru.realdocs.data.NbtCodec.readDoc(snap);
         String sup = od.superseded.get(reg);
         int state = 0;
         if (rec == null || rec.revoked) {
            state = 3;
         } else if (sup != null) {
            state = 1;
            int i = sup.indexOf(1);
            t.putString("By", i < 0 ? sup : sup.substring(0, i));
            t.putString("ByDate", i < 0 ? "" : sup.substring(i + 1));
         } else if (d != null && d.expired()) {
            state = 2;
         }

         t.putInt("State", state);
         t.putString("Org", x[1]);
         if (d != null) {
            boolean see = canSee(sp, d, network);
            t.putBoolean("See", see);
            t.putString("Type", L.k(d.templateName()));
            t.putString("Date", d.date);
            if (!d.expires.isEmpty()) {
               t.putString("Expires", d.expires);
            }

            if (see) {
               t.putString("Number", d.number);
               t.putString("Subject", d.subject);
               t.putString("Author", d.author);
               t.putString("Holder", d.holder);
               t.putString("Zones", d.zones);
               t.putString("Refs", String.join(", ", d.refs));
               t.putString("Supersedes", String.join(", ", d.supersedes));
               List<String> signers = new ArrayList<>();

               for (DocModel.Sig sg : d.sigs) {
                  signers.add(sg.name + (sg.position.isEmpty() ? "" : " (" + sg.position + ")"));
               }

               t.putString("Signers", String.join(", ", signers));
               DocModel c = ru.realdocs.data.NbtCodec.readDoc(snap);
               t.put("Doc", ru.realdocs.data.NbtCodec.writeDoc(c));
            }
         }
      }

      BureauService.send(sp, "reg_result", t);
   }
}
