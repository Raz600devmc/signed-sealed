package ru.realdocs.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.OfficeData;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;

/**
 * Computer inbox: documents sent to a player or an organization, as the original or as a copy,
 * and signature requests waiting for an answer. Opened on any computer.
 */
public final class InboxService {
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM HH:mm");
   public static final int MAX_BOX = 60;

   private InboxService() {
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "inbox":
            send(sp);
            break;
         case "inbox_send":
            sendDoc(sp, t);
            break;
         case "inbox_take":
            take(sp, t.getString("Id"));
            break;
         case "inbox_view":
            view(sp, t.getString("Id"));
            break;
         case "inbox_delete":
            delete(sp, t.getString("Id"));
            break;
         case "inbox_return":
            giveBack(sp, t.getString("Id"));
      }
   }

   /** Boxes this player reads: their own and those of their organizations. */
   static Map<String, String> boxes(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      String me = sp.getUUID().toString();
      Map<String, String> out = new LinkedHashMap<>();
      out.put("p:" + me, "");

      for (OfficeData.Org o : od.orgs.values()) {
         if (o.isMember(me)) {
            out.put("o:" + o.id, o.name);
         }
      }

      return out;
   }

   static List<CompoundTag> box(OfficeData od, String key) {
      return od.inbox.computeIfAbsent(key, k -> new ArrayList<>());
   }

   static CompoundTag find(ServerPlayer sp, String id, String[] whereOut) {
      OfficeData od = OfficeData.get(sp.server);

      for (String k : boxes(sp).keySet()) {
         for (CompoundTag e : od.inbox.getOrDefault(k, List.of())) {
            if (e.getString("Id").equals(id)) {
               whereOut[0] = k;
               return e;
            }
         }
      }

      return null;
   }

   static void remove(OfficeData od, String key, String id) {
      List<CompoundTag> l = od.inbox.get(key);
      if (l != null) {
         l.removeIf(e -> e.getString("Id").equals(id));
         od.setDirty();
      }
   }

   static void dropRequest(OfficeData od, String reqId) {
      for (List<CompoundTag> l : od.inbox.values()) {
         l.removeIf(e -> reqId.equals(e.getString("Req")));
      }
   }

   public static int unread(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      int n = 0;

      for (String k : boxes(sp).keySet()) {
         for (CompoundTag e : od.inbox.getOrDefault(k, List.of())) {
            if (!e.getBoolean("Read")) {
               n++;
            }
         }
      }

      return n;
   }

   static void send(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      CompoundTag out = new CompoundTag();
      ListTag l = new ListTag();

      for (Entry<String, String> b : boxes(sp).entrySet()) {
         List<CompoundTag> box = od.inbox.getOrDefault(b.getKey(), List.of());

         for (int i = box.size() - 1; i >= 0; i--) {
            CompoundTag e = box.get(i);
            CompoundTag c = new CompoundTag();
            c.putString("Id", e.getString("Id"));
            c.putString("From", e.getString("From"));
            c.putString("Kind", e.getString("Kind"));
            c.putString("Title", e.getString("Title"));
            c.putString("Note", e.getString("Note"));
            c.putString("Time", e.getString("Time"));
            c.putBoolean("Original", e.getBoolean("Original"));
            c.putBoolean("Read", e.getBoolean("Read"));
            c.putString("Org", b.getValue());
            l.add(c);
         }
      }

      out.put("Entries", l);
      BureauService.send(sp, "inbox", out);
   }

   static String title(ItemStack s) {
      if (s.getItem() instanceof DocumentItem) {
         DocModel d = NbtCodec.readDoc(s);
         return L.k(d.templateName()) + (d.number.isEmpty() ? "" : " № " + d.number) + (d.subject.isEmpty() ? "" : " «" + d.subject + "»");
      } else if (s.getItem() instanceof NewspaperItem) {
         return "«" + PaperCodec.ref(s).name + "»";
      } else {
         return L.s(s.getHoverName());
      }
   }

   static void sendDoc(ServerPlayer sp, CompoundTag t) {
      InteractionHand hand = t.getInt("Hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack held = sp.getItemInHand(hand);
      String to = t.getString("To");
      if (held.isEmpty()) {
         DocService.err(sp, L.k("Возьмите в руку то, что хотите отправить."));
         return;
      }

      if (t.getBoolean("Sign")) {
         String id = SignService.ask(sp, hand, to);
         if (id != null) {
            OfficeData od = OfficeData.get(sp.server);
            OfficeData.SignRequest r = od.requests.get(id);
            if (r != null) {
               for (String u : r.names.keySet()) {
                  CompoundTag e = entry(od, sp, "sign", r.title, null, false, t.getString("Note"));
                  e.putString("Req", id);
                  box(od, "p:" + u).add(e);
               }

               od.setDirty();
            }

            send(sp);
         }

         return;
      }

      boolean doc = held.getItem() instanceof DocumentItem;
      boolean paper = held.getItem() instanceof NewspaperItem;
      boolean other = held.getItem() instanceof PassportItem || held.getItem() instanceof EnvelopeItem || held.getItem() instanceof FolderItem;
      if (!doc && !paper && !other) {
         DocService.err(sp, L.k("Отправить можно документ, паспорт, газету, письмо или папку."));
         return;
      }

      boolean copy = t.getBoolean("Copy");
      if (copy && !doc && !paper) {
         DocService.err(sp, L.k("С паспортов, писем и папок копий не бывает — отправьте оригинал."));
         return;
      }

      if (copy && doc && !NbtCodec.readDoc(held).issued) {
         DocService.err(sp, L.k("Копию можно снять только с выпущенного документа."));
         return;
      }

      if (paper && !PaperCodec.ref(held).published) {
         DocService.err(sp, L.k("Отправить можно только опубликованный выпуск."));
         return;
      }

      OfficeData od = OfficeData.get(sp.server);
      Map<String, String> targets = new LinkedHashMap<>();
      List<String> unknown = new ArrayList<>();

      for (String raw : to.split("[,;]")) {
         String tok = raw.trim();
         if (!tok.isEmpty()) {
            if (tok.startsWith("@")) {
               OfficeData.Org o = od.orgByName(tok.substring(1));
               if (o == null) {
                  unknown.add(tok);
               } else {
                  targets.put("o:" + o.id, "@" + o.name);
               }
            } else {
               String[] p = BureauService.findPlayer(sp.server, tok);
               if (p == null) {
                  unknown.add(tok);
               } else {
                  targets.put("p:" + p[0], p[1]);
               }
            }
         }
      }

      if (!unknown.isEmpty()) {
         DocService.err(sp, L.k("Не найдены: ") + String.join(", ", unknown));
         return;
      }

      if (targets.isEmpty()) {
         DocService.err(sp, L.k("Укажите получателя: ник или @организацию."));
         return;
      }

      if (!copy && targets.size() > 1) {
         DocService.err(sp, L.k("Оригинал можно отправить только одному получателю. Остальным — копии."));
         return;
      }

      int max = Perms.has(sp, Perms.MAIL_MASS) ? ListService.MAX_MASS : ListService.MAX_RECIPIENTS;
      if (targets.size() > max) {
         DocService.err(sp, L.k("Слишком много получателей: ") + targets.size() + L.k(" (можно до ") + max + ").");
         return;
      }

      for (Entry<String, String> e : targets.entrySet()) {
         if (box(od, e.getKey()).size() >= MAX_BOX) {
            DocService.err(sp, L.k("Входящие получателя переполнены: ") + e.getValue() + ".");
            return;
         }
      }

      if (copy && !ListService.pay(sp, targets.size())) {
         return;
      }

      ItemStack one = copy ? held.copyWithCount(1) : held.split(1);
      if (copy && doc) {
         DocModel d = NbtCodec.readDoc(one);
         d.copy = true;
         NbtCodec.writeDoc(one, d);
      }

      String title = title(one);
      String note = DocService.clean(t.getString("Note"), 120, false);

      for (Entry<String, String> e : targets.entrySet()) {
         CompoundTag en = entry(od, sp, "doc", title, one, !copy, note);
         box(od, e.getKey()).add(en);
         notify(sp.server, e.getKey(), sp.getGameProfile().getName(), title);
      }

      od.setDirty();
      DocService.ok(sp, (copy ? L.k("Копия отправлена: ") : L.k("Оригинал отправлен: ")) + String.join(", ", targets.values()) + ".");
   }

   static CompoundTag entry(OfficeData od, ServerPlayer sp, String kind, String title, ItemStack item, boolean original, String note) {
      od.inboxCounter++;
      CompoundTag e = new CompoundTag();
      e.putString("Id", "IN-" + od.inboxCounter);
      e.putString("From", sp.getGameProfile().getName());
      e.putString("FromUuid", sp.getUUID().toString());
      e.putString("Kind", kind);
      e.putString("Title", title);
      e.putString("Note", note == null ? "" : note);
      e.putString("Time", LocalDateTime.now().format(STAMP));
      e.putBoolean("Original", original);
      if (item != null) {
         e.put("Item", Plat.saveStack(item, sp.level()));
      }

      return e;
   }

   static void notify(MinecraftServer server, String key, String from, String title) {
      String text = L.k("Во входящих новый документ от ") + from + ": " + title + L.k(". Откройте любой компьютер.");
      if (key.startsWith("p:")) {
         BureauService.tell(server, key.substring(2), text);
      } else {
         OfficeData.Org o = OfficeData.get(server).orgs.get(key.substring(2));
         if (o != null) {
            for (String u : o.everyone().keySet()) {
               ServerPlayer p = BureauService.online(server, u);
               if (p != null) {
                  p.sendSystemMessage(L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD).append(L.c("@" + o.name + ": " + text).withStyle(ChatFormatting.YELLOW)));
               }
            }
         }
      }
   }

   static void take(ServerPlayer sp, String id) {
      String[] where = new String[1];
      CompoundTag e = find(sp, id, where);
      if (e != null && e.contains("Item")) {
         ItemStack s = Plat.loadStack(e.getCompound("Item"), sp.level());
         OfficeData od = OfficeData.get(sp.server);
         remove(od, where[0], id);
         if (!s.isEmpty()) {
            PaperService.give(sp, s);
            DocService.ok(sp, L.k("Получено: ") + e.getString("Title"));
         }

         send(sp);
      }
   }

   static void view(ServerPlayer sp, String id) {
      String[] where = new String[1];
      CompoundTag e = find(sp, id, where);
      if (e != null) {
         e.putBoolean("Read", true);
         OfficeData.get(sp.server).setDirty();
         if ("sign".equals(e.getString("Kind"))) {
            SignService.open(sp, e.getString("Req"));
         } else {
            ItemStack s = Plat.loadStack(e.getCompound("Item"), sp.level());
            if (!s.isEmpty()) {
               ArchiveService.view(sp, s, L.k("входящие · от ") + e.getString("From"));
            }
         }
      }
   }

   static void delete(ServerPlayer sp, String id) {
      String[] where = new String[1];
      CompoundTag e = find(sp, id, where);
      if (e != null) {
         remove(OfficeData.get(sp.server), where[0], id);
         if ("sign".equals(e.getString("Kind"))) {
            SignService.decline(sp, e.getString("Req"));
         }

         send(sp);
      }
   }

   static void giveBack(ServerPlayer sp, String id) {
      String[] where = new String[1];
      CompoundTag e = find(sp, id, where);
      if (e != null && e.contains("Item") && !e.getString("FromUuid").isEmpty()) {
         OfficeData od = OfficeData.get(sp.server);
         remove(od, where[0], id);
         CompoundTag back = e.copy();
         od.inboxCounter++;
         back.putString("Id", "IN-" + od.inboxCounter);
         back.putString("From", sp.getGameProfile().getName());
         back.putString("FromUuid", sp.getUUID().toString());
         back.putString("Note", L.k("Возвращено без ответа"));
         back.putString("Time", LocalDateTime.now().format(STAMP));
         back.putBoolean("Read", false);
         box(od, "p:" + e.getString("FromUuid")).add(back);
         od.setDirty();
         notify(sp.server, "p:" + e.getString("FromUuid"), sp.getGameProfile().getName(), e.getString("Title"));
         DocService.ok(sp, L.k("Документ возвращён отправителю."));
         send(sp);
      }
   }

   /** Unread reminder on login. */
   static void remind(ServerPlayer sp) {
      int n = unread(sp);
      if (n > 0) {
         sp.sendSystemMessage(
            L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD)
               .append(L.c(L.k("Новых документов во входящих: ") + n + L.k(". Откройте любой компьютер.")).withStyle(ChatFormatting.YELLOW))
         );
      }
   }
}
