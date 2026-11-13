package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.OfficeData;
import ru.realdocs.model.DocDesign;
import ru.realdocs.model.Limits;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/** Custom document types: a look (header, logo, frame, paper, fonts) plus default subject and text. */
public final class TemplateService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   public static final int MAX_PER_OWNER = 20;
   public static final int LOGO_BYTES = 200000;
   public static final int LOGO_SIDE = 512;

   private TemplateService() {
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "templates":
            send(sp, "");
            break;
         case "tpl_save":
            save(sp, t);
            break;
         case "tpl_delete":
            delete(sp, t.getString("Id"));
      }
   }

   static boolean canUse(ServerPlayer sp, OfficeData od, OfficeData.Template t) {
      String me = sp.getUUID().toString();
      if (t.ownerUuid.equals(me) || BureauService.admin(sp)) {
         return true;
      } else if (!t.orgId.isEmpty()) {
         OfficeData.Org o = od.orgs.get(t.orgId);
         return o != null && o.isMember(me);
      } else {
         return false;
      }
   }

   static boolean canEdit(ServerPlayer sp, OfficeData od, OfficeData.Template t) {
      String me = sp.getUUID().toString();
      if (t.ownerUuid.equals(me) || BureauService.admin(sp)) {
         return true;
      } else if (!t.orgId.isEmpty()) {
         OfficeData.Org o = od.orgs.get(t.orgId);
         return o != null && o.isManager(me);
      } else {
         return false;
      }
   }

   /** The template if this player may issue documents from it. */
   public static OfficeData.Template usable(ServerPlayer sp, String id) {
      if (id != null && !id.isEmpty()) {
         OfficeData od = OfficeData.get(sp.server);
         OfficeData.Template t = od.templates.get(id);
         return t != null && canUse(sp, od, t) ? t : null;
      } else {
         return null;
      }
   }

   static void send(ServerPlayer sp, String select) {
      OfficeData od = OfficeData.get(sp.server);
      CompoundTag out = new CompoundTag();
      ListTag l = new ListTag();

      for (OfficeData.Template t : od.templates.values()) {
         if (canUse(sp, od, t)) {
            CompoundTag c = t.write();
            c.putBoolean("Edit", canEdit(sp, od, t));
            OfficeData.Org o = t.orgId.isEmpty() ? null : od.orgs.get(t.orgId);
            c.putString("OrgName", o == null ? "" : o.name);
            l.add(c);
         }
      }

      out.put("Templates", l);
      ListTag orgs = new ListTag();
      String me = sp.getUUID().toString();

      for (OfficeData.Org o : od.orgs.values()) {
         if (o.isManager(me)) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", o.id);
            c.putString("Name", o.name);
            orgs.add(c);
         }
      }

      out.put("Orgs", orgs);
      out.putBoolean("CanCreate", Perms.has(sp, Perms.TEMPLATE));
      out.putString("Select", select);
      BureauService.send(sp, "templates", out);
   }

   static void save(ServerPlayer sp, CompoundTag t) {
      OfficeData od = OfficeData.get(sp.server);
      String me = sp.getUUID().toString();
      if (!Perms.has(sp, Perms.TEMPLATE)) {
         DocService.err(sp, L.k("У вас нет права создавать шаблоны документов."));
         return;
      }

      DocDesign d = DocDesign.read(t.getCompound("Design"));
      if (d.name.isBlank() || d.title.isBlank()) {
         DocService.err(sp, L.k("Укажите название типа и заголовок документа."));
         return;
      }

      if (!d.logo.isEmpty()) {
         DocStore.AssetRec a = DocStore.get(sp.server).assets.get(d.logo);
         if (a == null || !"doc_logo".equals(a.kind)) {
            d.logo = "";
            d.logoPos = 0;
         }
      }

      String orgId = t.getString("Org");
      if (!orgId.isEmpty()) {
         OfficeData.Org o = od.orgs.get(orgId);
         if (o == null || !o.isManager(me) && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Шаблон организации может сохранить только её руководитель."));
            return;
         }
      }

      String id = t.getString("Id");
      OfficeData.Template tp = id.isEmpty() ? null : od.templates.get(id);
      if (tp != null && !canEdit(sp, od, tp)) {
         DocService.err(sp, L.k("Этот шаблон может менять только его владелец."));
         return;
      }

      if (tp == null) {
         int n = 0;

         for (OfficeData.Template x : od.templates.values()) {
            if (x.ownerUuid.equals(me)) {
               n++;
            }
         }

         if (n >= MAX_PER_OWNER && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Можно сохранить до ") + MAX_PER_OWNER + L.k(" шаблонов."));
            return;
         }

         od.templateCounter++;
         tp = new OfficeData.Template();
         tp.id = String.format("DT-%03d", od.templateCounter);
         tp.ownerUuid = me;
         tp.ownerName = sp.getGameProfile().getName();
         tp.created = LocalDate.now().format(DATE);
         od.templates.put(tp.id, tp);
      }

      tp.design = d;
      tp.orgId = orgId;
      tp.subject = DocService.clean(t.getString("Subject"), Limits.SUBJECT, false);
      tp.body = DocService.clean(t.getString("Body"), Limits.BODY, true);
      od.setDirty();
      DocService.ok(sp, L.k("Шаблон «") + d.name + L.k("» сохранён."));
      send(sp, tp.id);
   }

   static void delete(ServerPlayer sp, String id) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.Template tp = od.templates.get(id);
      if (tp != null && canEdit(sp, od, tp)) {
         od.templates.remove(id);
         od.setDirty();
         DocService.ok(sp, L.k("Шаблон «") + tp.design.name + L.k("» удалён. Уже выпущенные документы не изменятся."));
         send(sp, "");
      }
   }

   static void acceptLogo(ServerPlayer sp, int token, byte[] png) {
      if (!Perms.has(sp, Perms.TEMPLATE)) {
         Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("У вас нет права создавать шаблоны документов.")));
      } else if (!DocService.validPng(png, LOGO_BYTES, LOGO_SIDE)) {
         Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Логотип не подходит: нужен PNG до 512 px.")));
      } else {
         DocStore store = DocStore.get(sp.server);
         String aid = store.nextAssetId();

         try {
            DocStore.saveAsset(sp.server, aid, png);
         } catch (IOException var6) {
            LOG.error("[Signed & Sealed] could not save logo {}", aid, var6);
            Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Не удалось сохранить картинку на сервере.")));
            return;
         }

         DocStore.AssetRec ar = new DocStore.AssetRec();
         ar.ownerUuid = sp.getUUID().toString();
         ar.kind = "doc_logo";
         ar.created = LocalDate.now().format(DATE);
         store.assets.put(aid, ar);
         store.setDirty();
         Net.sendTo(sp, new MorePackets.UploadResult(token, true, aid, ""));
      }
   }
}
