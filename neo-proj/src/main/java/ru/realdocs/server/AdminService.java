package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModItems;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.Tags;
import ru.realdocs.model.CountryPresets;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class AdminService {
   private static final Logger LOG = LogUtils.getLogger();
   public static final int STAMP_APPROVE = 0;
   public static final int STAMP_REVOKE = 1;
   public static final int STAMP_DELETE = 2;
   public static final int STAMP_GIVE = 3;
   public static final int TPL_APPROVE = 10;
   public static final int TPL_REVOKE = 11;
   public static final int TPL_DELETE = 12;
   public static final int TPL_GIVE = 13;
   public static final int TPL_PRESET = 14;
   public static final int TPL_EDIT = 15;
   public static final int REG_REVOKE = 20;
   public static final int REG_RESTORE = 21;
   public static final int MAX_BLANKS = 16;

   private AdminService() {
   }

   public static AdminService.Result approveStamp(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      StampModel m = store.stamps.get(id);
      if (m == null) {
         return AdminService.Result.fail(L.k("Печать ") + id + L.k(" не найдена."));
      } else {
         boolean wasRevoked = m.revoked();
         m.approved = true;
         m.everApproved = true;
         store.setDirty();
         notify(
            server,
            m.ownerUuid,
            (wasRevoked ? L.k("Ваша печать ") : L.k("Ваш макет печати "))
               + m.id
               + " «"
               + m.org
               + "» "
               + (wasRevoked ? L.k("восстановлена.") : L.k("утверждён."))
         );
         return AdminService.Result.ok(L.k("Печать ") + m.id + " «" + m.org + "» " + (wasRevoked ? L.k("восстановлена.") : L.k("утверждена.")));
      }
   }

   public static AdminService.Result revokeStamp(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      StampModel m = store.stamps.get(id);
      if (m == null) {
         return AdminService.Result.fail(L.k("Печать ") + id + L.k(" не найдена."));
      } else if (m.pending()) {
         return deleteStamp(server, id);
      } else {
         m.approved = false;
         store.setDirty();
         return AdminService.Result.ok(L.k("Печать ") + m.id + L.k(" отозвана: ставить её больше нельзя, на старых документах она помечена как отозванная."));
      }
   }

   public static AdminService.Result deleteStamp(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      StampModel m = store.stamps.remove(id);
      if (m == null) {
         return AdminService.Result.fail(L.k("Печать ") + id + L.k(" не найдена."));
      } else {
         store.setDirty();
         DocStore.deleteStampImage(server, id);
         if (!m.everApproved) {
            notify(server, m.ownerUuid, L.k("Ваш макет печати «") + m.org + L.k("» отклонён."));
         }

         return AdminService.Result.ok((m.everApproved ? L.k("Печать ") : L.k("Макет ")) + id + L.k(" удалён из реестра."));
      }
   }

   public static AdminService.Result giveStamp(MinecraftServer server, String id, Collection<ServerPlayer> targets) {
      StampModel m = DocStore.get(server).stamps.get(id);
      if (m == null) {
         return AdminService.Result.fail(L.k("Печать ") + id + L.k(" не найдена."));
      } else if (!m.approved) {
         return AdminService.Result.fail(L.k("Печать ") + m.id + L.k(" не утверждена."));
      } else if (targets.isEmpty()) {
         return AdminService.Result.fail(L.k("Выберите, кому выдать."));
      } else {
         for (ServerPlayer p : targets) {
            ItemStack stack = new ItemStack((ItemLike)ModItems.STAMP.get());
            Tags.update(stack, tag -> {
               tag.putString("StampId", m.id);
               tag.putString("StampOrg", m.org);
            });
            if (!p.getInventory().add(stack)) {
               p.drop(stack, false);
            }

            DocService.ok(p, L.k("Вам выдана печать «") + m.org + "».");
         }

         return AdminService.Result.ok(L.k("Печать ") + m.id + L.k(" выдана: ") + names(targets) + ".");
      }
   }

   public static AdminService.Result approveTpl(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      PassportTemplate t = store.templates.get(id);
      if (t == null) {
         return AdminService.Result.fail(L.k("Шаблон ") + id + L.k(" не найден."));
      } else {
         PassportTemplate target = t.replaces.isEmpty() ? null : store.templates.get(t.replaces);
         if (target != null) {
            PassportTemplate merged = t.copy();
            merged.id = target.id;
            merged.ownerUuid = target.ownerUuid;
            merged.ownerName = target.ownerName;
            merged.created = target.created;
            merged.autoStampId = target.autoStampId;
            merged.replaces = "";
            merged.approved = true;
            store.templates.put(target.id, merged);
            store.templates.remove(t.id);
            store.setDirty();
            PassportService.broadcastTemplate(server, merged);
            notify(server, merged.ownerUuid, L.k("Изменения шаблона ") + merged.id + " «" + L.k(merged.name) + L.k("» утверждены."));
            return AdminService.Result.ok(L.k("Изменения шаблона ") + merged.id + " «" + L.k(merged.name) + L.k("» утверждены."));
         } else {
            t.replaces = "";
            t.approved = true;
            store.setDirty();
            notify(server, t.ownerUuid, L.k("Ваш шаблон паспорта ") + t.id + " «" + L.k(t.name) + L.k("» утверждён."));
            return AdminService.Result.ok(L.k("Шаблон ") + t.id + " «" + L.k(t.name) + L.k("» утверждён."));
         }
      }
   }

   public static AdminService.Result revokeTpl(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      PassportTemplate t = store.templates.get(id);
      if (t == null) {
         return AdminService.Result.fail(L.k("Шаблон ") + id + L.k(" не найден."));
      } else {
         t.approved = false;
         store.setDirty();
         return AdminService.Result.ok(L.k("Шаблон ") + t.id + L.k(" отозван: новые бланки по нему не оформляются."));
      }
   }

   public static AdminService.Result deleteTpl(MinecraftServer server, String id) {
      DocStore store = DocStore.get(server);
      PassportTemplate t = store.templates.remove(id);
      if (t == null) {
         return AdminService.Result.fail(L.k("Шаблон ") + id + L.k(" не найден."));
      } else {
         store.setDirty();
         return AdminService.Result.ok(L.k("Шаблон ") + id + " «" + L.k(t.name) + L.k("» удалён."));
      }
   }

   public static AdminService.Result giveBlanks(MinecraftServer server, String id, String issuer, Collection<ServerPlayer> targets, int count) {
      PassportTemplate t = DocStore.get(server).templates.get(id);
      if (t == null) {
         return AdminService.Result.fail(L.k("Шаблон ") + id + L.k(" не найден."));
      } else if (!t.approved) {
         return AdminService.Result.fail(L.k("Шаблон ") + t.id + L.k(" ещё не утверждён."));
      } else if (targets.isEmpty()) {
         return AdminService.Result.fail(L.k("Выберите, кому выдать."));
      } else {
         int n = Math.max(1, Math.min(16, count));

         for (ServerPlayer p : targets) {
            for (int i = 0; i < n; i++) {
               ItemStack stack = PassportService.makeBlank(server, t, issuer);
               if (!p.getInventory().add(stack)) {
                  p.drop(stack, false);
               }
            }

            DocService.ok(
               p,
               (n == 1 ? L.k("Вам выдан бланк") : L.k("Вам выдано бланков: ") + n + ",")
                  + L.k(" паспорт «")
                  + t.name
                  + L.k("». Откройте его (ПКМ) и заполните.")
            );
         }

         return AdminService.Result.ok(L.k("Бланки «") + t.name + "» (" + n + L.k(" шт.) выданы: ") + names(targets) + ".");
      }
   }

   public static AdminService.Result installPreset(MinecraftServer server, String key, String ownerUuid, String ownerName) {
      PassportTemplate base = PassportTemplate.preset(key);
      if (base == null) {
         return AdminService.Result.fail(L.k("Такого готового шаблона нет."));
      } else {
         for (PassportTemplate x : DocStore.get(server).templates.values()) {
            if (x.name.equals(base.name)) {
               return AdminService.Result.fail(L.k("Шаблон «") + base.name + L.k("» уже установлен: ") + x.id);
            }
         }

         PassportTemplate t = PassportService.installPreset(server, key, ownerUuid, ownerName);
         return AdminService.Result.ok(L.k("Шаблон ") + t.id + " «" + L.k(t.name) + L.k("» установлен и утверждён."));
      }
   }

   public static AdminService.Result setRevoked(MinecraftServer server, boolean passport, String id, boolean revoked) {
      DocStore store = DocStore.get(server);
      DocStore.Rec r = (passport ? store.passes : store.docs).get(id);
      if (r == null) {
         return AdminService.Result.fail(L.k("Записи ") + id + L.k(" нет в реестре."));
      } else {
         r.revoked = revoked;
         store.setDirty();
         String what = passport ? L.k("Паспорт") : L.k("Документ");
         String num = r.number.isEmpty() ? id : r.number;
         return AdminService.Result.ok(what + " " + num + (revoked ? L.k(" аннулирован.") : L.k(" снова действителен.")));
      }
   }

   public static boolean isAdmin(ServerPlayer sp) {
      return Perms.has(sp, Perms.ISSUE) || Perms.has(sp, Perms.PASSPORT_ISSUE) || Perms.has(sp, Perms.ADMIN);
   }

   public static void sendPanel(ServerPlayer sp, String query, String message, boolean ok) {
      if (!isAdmin(sp)) {
         DocService.err(sp, L.k("Панель администрации доступна только администрации."));
      } else {
         Net.sendTo(sp, snapshot(sp, query, message, ok));
      }
   }

   static Packets.AdminData snapshot(ServerPlayer sp, String query, String message, boolean ok) {
      DocStore store = DocStore.get(sp.server);
      boolean st = Perms.has(sp, Perms.ISSUE);
      boolean pt = Perms.has(sp, Perms.PASSPORT_ISSUE);
      boolean ad = Perms.has(sp, Perms.ADMIN);
      Packets.AdminData d = new Packets.AdminData();
      d.flags = (st ? 1 : 0) | (pt ? 2 : 0) | (ad ? 4 : 0);
      d.message = message == null ? "" : message;
      d.ok = ok;
      if (st) {
         for (StampModel m : store.stamps.values()) {
            if (d.stamps.size() >= 300) {
               break;
            }

            d.stamps.add(m);
         }
      }

      if (pt) {
         for (PassportTemplate t : store.templates.values()) {
            if (d.templates.size() >= 300) {
               break;
            }

            d.templates
               .add(
                  new Packets.AdminData.Tpl(
                     t.id, t.replaces.isEmpty() ? t.name : t.name + " ✎ " + t.replaces, t.ownerName, t.created, t.approved, t.fields.size()
                  )
               );
         }

         for (String key : PassportTemplate.PRESET_KEYS) {
            d.presets.add(new String[]{key, L.k(CountryPresets.countryName(key))});
         }
      }

      if (ad || pt) {
         String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
         List<Packets.AdminData.Reg> all = new ArrayList<>();
         if (ad) {
            collect(all, store.docs, false, q, store);
         }

         collect(all, store.passes, true, q, store);
         all.sort((a, b) -> Long.compare(b.time(), a.time()));
         d.registry.addAll(all.subList(0, Math.min(all.size(), 300)));
      }

      for (ServerPlayer p : sp.server.getPlayerList().getPlayers()) {
         d.players.add(p.getGameProfile().getName());
      }

      return d;
   }

   private static void collect(List<Packets.AdminData.Reg> out, Map<String, DocStore.Rec> recs, boolean pass, String q, DocStore store) {
      for (Entry<String, DocStore.Rec> e : recs.entrySet()) {
         DocStore.Rec r = e.getValue();
         if (!pass || !"BLANK".equals(r.number) || !q.isEmpty()) {
            String kind = pass ? ("BLANK".equals(r.number) ? L.k("Бланк") : L.k("Паспорт")) : L.k(DocModel.TEMPLATE_NAMES[DocModel.templateIndex(r.template)]);
            String number = "BLANK".equals(r.number) ? "" : r.number;
            if (!q.isEmpty()) {
               String hay = (e.getKey() + " " + number + " " + r.author + " " + kind).toLowerCase(Locale.ROOT);
               if (!hay.contains(q)) {
                  continue;
               }
            }

            out.add(new Packets.AdminData.Reg(pass, e.getKey(), kind, number, r.author, r.date, r.revoked, r.time));
         }
      }
   }

   public static void handle(ServerPlayer sp, int action, String id, List<String> targetNames, int count, String query) {
      MinecraftServer server = sp.server;
      String actor = sp.getGameProfile().getName();
      if (action == 15) {
         PassportService.openTemplateEdit(sp, id);
      } else {
         AdminService.Result r;
         if (action >= 0 && action <= 3) {
            if (!Perms.has(sp, Perms.ISSUE)) {
               r = AdminService.Result.fail(L.k("Нет права управлять печатями."));
            } else {
               r = switch (action) {
                  case 0 -> approveStamp(server, id);
                  case 1 -> revokeStamp(server, id);
                  case 2 -> deleteStamp(server, id);
                  default -> giveStamp(server, id, resolve(server, targetNames));
               };
            }
         } else if (action >= 10 && action <= 14) {
            if (!Perms.has(sp, Perms.PASSPORT_ISSUE)) {
               r = AdminService.Result.fail(L.k("Нет права управлять паспортами."));
            } else {
               r = switch (action) {
                  case 10 -> approveTpl(server, id);
                  case 11 -> revokeTpl(server, id);
                  case 12 -> deleteTpl(server, id);
                  case 13 -> giveBlanks(server, id, actor, resolve(server, targetNames), count);
                  default -> installPreset(server, id, sp.getUUID().toString(), actor);
               };
            }
         } else if (action != 20 && action != 21) {
            r = AdminService.Result.fail(L.k("Неизвестное действие."));
         } else {
            boolean pass = count == 1;
            boolean allowed = pass ? Perms.has(sp, Perms.ADMIN) || Perms.has(sp, Perms.PASSPORT_ISSUE) : Perms.has(sp, Perms.ADMIN);
            r = allowed ? setRevoked(server, pass, id, action == 20) : AdminService.Result.fail(L.k("Нет права аннулировать."));
         }

         if (r.ok()) {
            LOG.info("[Signed & Sealed] admin panel: {} -> action {} on {}: {}", new Object[]{actor, action, id, L.plain(r.msg())});
         }

         sendPanel(sp, query, r.msg(), r.ok());
      }
   }

   private static List<ServerPlayer> resolve(MinecraftServer server, List<String> names) {
      List<ServerPlayer> out = new ArrayList<>();
      if (names == null) {
         return out;
      } else {
         for (String n : names) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(n.trim());
            if (p != null && !out.contains(p)) {
               out.add(p);
            }
         }

         return out;
      }
   }

   private static String names(Collection<ServerPlayer> ps) {
      List<String> n = new ArrayList<>();

      for (ServerPlayer p : ps) {
         n.add(p.getGameProfile().getName());
      }

      return String.join(", ", n);
   }

   private static void notify(MinecraftServer server, String uuid, String text) {
      try {
         ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(uuid));
         if (owner != null) {
            DocService.ok(owner, text);
         }
      } catch (IllegalArgumentException var4) {
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         PassportService.forgetGrants(sp);
      }
   }

   public static record Result(boolean ok, String msg) {
      static AdminService.Result ok(String m) {
         return new AdminService.Result(true, m);
      }

      static AdminService.Result fail(String m) {
         return new AdminService.Result(false, m);
      }
   }
}
