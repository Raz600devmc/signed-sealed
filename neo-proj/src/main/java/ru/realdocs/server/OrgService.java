package ru.realdocs.server;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.L;
import ru.realdocs.Perms;
import ru.realdocs.data.OfficeData;

/**
 * Organizations: an owner, managers and members. Managers can run the organization's mailing
 * lists and templates and configure blocks that name the organization ("@Name") in their access
 * list; members can use them.
 */
public final class OrgService {
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   public static final int MAX_OWNED = 5;
   public static final int MAX_PEOPLE = 200;

   private OrgService() {
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "orgs":
            send(sp, "");
            break;
         case "org_create":
            create(sp, t.getString("Name"));
            break;
         case "org_save":
            save(sp, t);
            break;
         case "org_delete":
            delete(sp, t.getString("Id"));
            break;
         case "org_leave":
            leave(sp, t.getString("Id"));
      }
   }

   /** Is the player in the organization with this name? */
   public static boolean member(MinecraftServer server, String orgName, String uuid) {
      OfficeData.Org o = OfficeData.get(server).orgByName(orgName);
      return o != null && o.isMember(uuid);
   }

   public static boolean manager(MinecraftServer server, String orgName, String uuid) {
      OfficeData.Org o = OfficeData.get(server).orgByName(orgName);
      return o != null && o.isManager(uuid);
   }

   static String cleanName(String s) {
      return DocService.clean(s, 40, false).replace("@", "").replace(",", "").replace(";", "").trim();
   }

   static void send(ServerPlayer sp, String select) {
      OfficeData od = OfficeData.get(sp.server);
      String me = sp.getUUID().toString();
      boolean admin = BureauService.admin(sp);
      CompoundTag t = new CompoundTag();
      ListTag l = new ListTag();

      for (OfficeData.Org o : od.orgs.values()) {
         if (o.isMember(me) || admin) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", o.id);
            c.putString("Name", o.name);
            c.putString("Owner", o.ownerName);
            c.putString("Created", o.created);
            c.putString("Managers", String.join(", ", o.managers.values()));
            c.putString("Members", String.join(", ", o.members.values()));
            c.putInt("Role", o.isOwner(me) ? 0 : (o.isManager(me) ? 1 : (o.isMember(me) ? 2 : 3)));
            c.putInt("Count", o.everyone().size());
            l.add(c);
         }
      }

      t.put("Orgs", l);
      t.putBoolean("CanCreate", Perms.has(sp, Perms.ORG_CREATE));
      t.putBoolean("Admin", admin);
      t.putString("Select", select);
      BureauService.send(sp, "orgs", t);
   }

   static void create(ServerPlayer sp, String rawName) {
      OfficeData od = OfficeData.get(sp.server);
      String name = cleanName(rawName);
      String me = sp.getUUID().toString();
      if (!Perms.has(sp, Perms.ORG_CREATE)) {
         DocService.err(sp, L.k("У вас нет права создавать организации."));
      } else if (name.length() < 2) {
         DocService.err(sp, L.k("Название организации — от 2 символов."));
      } else if (od.orgByName(name) != null) {
         DocService.err(sp, L.k("Организация «") + name + L.k("» уже существует."));
      } else {
         int owned = 0;

         for (OfficeData.Org o : od.orgs.values()) {
            if (o.isOwner(me)) {
               owned++;
            }
         }

         if (owned >= MAX_OWNED && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Можно владеть не более чем ") + MAX_OWNED + L.k(" организациями."));
         } else {
            od.orgCounter++;
            OfficeData.Org o = new OfficeData.Org();
            o.id = "ORG-" + od.orgCounter;
            o.name = name;
            o.ownerUuid = me;
            o.ownerName = sp.getGameProfile().getName();
            o.created = LocalDate.now().format(DATE);
            od.orgs.put(o.id, o);
            od.setDirty();
            DocService.ok(sp, L.k("Организация «") + name + L.k("» создана. Добавьте руководителей и участников."));
            send(sp, o.id);
            BureauService.sendNames(sp);
         }
      }
   }

   static void save(ServerPlayer sp, CompoundTag t) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.Org o = od.orgs.get(t.getString("Id"));
      String me = sp.getUUID().toString();
      boolean admin = BureauService.admin(sp);
      if (o == null) {
         return;
      }

      if (!o.isManager(me) && !admin) {
         DocService.err(sp, L.k("Менять состав могут только руководители организации."));
         return;
      }

      List<String> unknown = new ArrayList<>();
      Map<String, String> members = ListService.players(sp, t.getString("Members"), unknown);
      Map<String, String> managers = null;
      boolean head = o.isOwner(me) || admin;
      if (head) {
         managers = ListService.players(sp, t.getString("Managers"), unknown);
      }

      if (!unknown.isEmpty()) {
         DocService.err(sp, L.k("Эти игроки ни разу не заходили на сервер: ") + String.join(", ", unknown));
         return;
      }

      if (head) {
         String name = cleanName(t.getString("Name"));
         if (name.length() >= 2 && !OfficeData.key(name).equals(OfficeData.key(o.name))) {
            if (od.orgByName(name) != null) {
               DocService.err(sp, L.k("Организация «") + name + L.k("» уже существует."));
               return;
            }

            o.name = name;
         }

         o.managers.clear();
         o.managers.putAll(managers);
         String newOwner = t.getString("NewOwner").trim();
         if (!newOwner.isEmpty() && !OfficeData.key(newOwner).equals(OfficeData.key(o.ownerName))) {
            String[] p = BureauService.findPlayer(sp.server, newOwner);
            if (p == null) {
               DocService.err(sp, L.k("Игрок «") + newOwner + L.k("» ни разу не заходил на сервер."));
               return;
            }

            o.managers.put(o.ownerUuid, o.ownerName);
            o.ownerUuid = p[0];
            o.ownerName = p[1];
            BureauService.tell(sp.server, p[0], L.k("Вам передана организация «") + o.name + "».");
         }

         o.managers.remove(o.ownerUuid);
      }

      members.keySet().removeAll(o.managers.keySet());
      members.remove(o.ownerUuid);
      if (members.size() + o.managers.size() > MAX_PEOPLE) {
         DocService.err(sp, L.k("В организации может быть до ") + MAX_PEOPLE + L.k(" человек."));
         return;
      }

      for (String u : members.keySet()) {
         if (!o.members.containsKey(u)) {
            BureauService.tell(sp.server, u, L.k("Вас добавили в организацию «") + o.name + "».");
         }
      }

      o.members.clear();
      o.members.putAll(members);
      od.setDirty();
      DocService.ok(sp, L.k("Организация «") + o.name + L.k("» сохранена."));
      send(sp, o.id);
      BureauService.sendNames(sp);
   }

   static void delete(ServerPlayer sp, String id) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.Org o = od.orgs.get(id);
      if (o != null) {
         if (!o.isOwner(sp.getUUID().toString()) && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Удалить организацию может только её владелец."));
         } else {
            od.orgs.remove(id);
            Iterator<OfficeData.MailList> it = od.lists.values().iterator();

            while (it.hasNext()) {
               if (it.next().owner.equals("o:" + id)) {
                  it.remove();
               }
            }

            for (OfficeData.Template tp : od.templates.values()) {
               if (tp.orgId.equals(id)) {
                  tp.orgId = "";
               }
            }

            od.setDirty();
            DocService.ok(sp, L.k("Организация «") + o.name + L.k("» удалена."));
            send(sp, "");
            BureauService.sendNames(sp);
         }
      }
   }

   static void leave(ServerPlayer sp, String id) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.Org o = od.orgs.get(id);
      String me = sp.getUUID().toString();
      if (o != null) {
         if (o.isOwner(me)) {
            DocService.err(sp, L.k("Владелец не может выйти: передайте организацию или удалите её."));
         } else {
            o.managers.remove(me);
            o.members.remove(me);
            od.setDirty();
            DocService.ok(sp, L.k("Вы вышли из организации «") + o.name + "».");
            send(sp, "");
            BureauService.sendNames(sp);
         }
      }
   }
}
