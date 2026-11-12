package ru.realdocs.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.OfficeData;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.model.DocModel;

/**
 * Signature requests: the author hands an issued document over for signing without giving the
 * item away. The document stays locked on the server until everyone has signed or declined; then
 * the original returns to the author and every signer gets an authentic printed copy.
 */
public final class SignService {
   public static final int MAX_OPEN_PER_PLAYER = 10;

   private SignService() {
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "sign_ask":
            ask(sp, t.getInt("Hand") == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, t.getString("To"));
            break;
         case "sign_open":
            open(sp, t.getString("Id"));
            break;
         case "sign_do":
            sign(sp, t.getString("Id"), t.getString("Pos"), t.getIntArray("Sig"));
            break;
         case "sign_decline":
            decline(sp, t.getString("Id"));
            break;
         case "sign_cancel":
            cancel(sp, t.getString("Id"));
            break;
         case "sign_list":
            list(sp);
      }
   }

   static String title(DocModel d) {
      return L.k(d.templateName()) + (d.number.isEmpty() ? "" : " № " + d.number) + (d.subject.isEmpty() ? "" : " «" + d.subject + "»");
   }

   static MutableComponent openButton(String id) {
      return BureauService.button(L.k("[Открыть]"), "/seal sign open " + id, L.k("Посмотреть документ и подписать"));
   }

   public static String ask(ServerPlayer sp, InteractionHand hand, String to) {
      ItemStack stack = sp.getItemInHand(hand);
      MinecraftServer server = sp.server;
      if (!(stack.getItem() instanceof DocumentItem)) {
         DocService.err(sp, L.k("Возьмите в руку выпущенный документ."));
         return null;
      }

      DocModel d = NbtCodec.readDoc(stack);
      DocStore store = DocStore.get(server);
      String me = sp.getUUID().toString();
      if (!d.issued || d.copy || DocService.check(store, d, false).status() != DocService.Status.OK) {
         DocService.err(sp, L.k("На подпись можно отправить только подлинный выпущенный документ, не копию."));
         return null;
      }

      if (!d.hasAuthorizedSignature(me) && !d.authorUuid.equals(me)) {
         DocService.err(sp, L.k("Запросить подписи может только тот, кто уже подписал документ."));
         return null;
      }

      List<String> unknown = new ArrayList<>();
      Map<String, String> who = ListService.players(sp, to, unknown);
      if (!unknown.isEmpty()) {
         DocService.err(sp, L.k("Эти игроки ни разу не заходили на сервер: ") + String.join(", ", unknown));
         return null;
      }

      who.remove(me);
      who.keySet().removeIf(d::hasAuthorizedSignature);
      if (who.isEmpty()) {
         DocService.err(sp, L.k("Укажите, кого попросить подписать (ники через запятую)."));
         return null;
      }

      if (d.sigs.size() + who.size() > 4) {
         DocService.err(sp, L.k("На документе может быть до 4 подписей: свободно мест — ") + Math.max(0, 4 - d.sigs.size()) + ".");
         return null;
      }

      OfficeData od = OfficeData.get(server);
      int open = 0;

      for (OfficeData.SignRequest r : od.requests.values()) {
         if (r.fromUuid.equals(me)) {
            open++;
         }
      }

      if (open >= MAX_OPEN_PER_PLAYER) {
         DocService.err(sp, L.k("У вас уже слишком много документов на подписи (до ") + MAX_OPEN_PER_PLAYER + ").");
         return null;
      }

      od.requestCounter++;
      OfficeData.SignRequest r = new OfficeData.SignRequest();
      r.id = "SR-" + od.requestCounter;
      r.fromUuid = me;
      r.fromName = sp.getGameProfile().getName();
      r.title = title(d);
      r.created = System.currentTimeMillis();
      r.item = Plat.saveStack(stack.split(1), sp.level());

      for (Entry<String, String> e : who.entrySet()) {
         r.names.put(e.getKey(), e.getValue());
         r.states.put(e.getKey(), OfficeData.SignRequest.PENDING);
      }

      od.requests.put(r.id, r);
      od.setDirty();
      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
      DocService.ok(
         sp,
         L.k("Документ передан на подпись: ")
            + String.join(", ", who.values())
            + L.k(". Пока его подписывают, он хранится в канцелярии и не меняется; потом вернётся к вам.")
      );

      for (Entry<String, String> e : who.entrySet()) {
         ServerPlayer p = BureauService.online(server, e.getKey());
         String text = r.fromName + L.k(" просит вас подписать: ") + r.title + ". ";
         if (p != null) {
            p.sendSystemMessage(L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD).append(L.c(text).withStyle(ChatFormatting.YELLOW)).append(openButton(r.id)));
         } else {
            od.notice(e.getKey(), text + L.k("Список: /seal sign list"));
         }
      }

      return r.id;
   }

   static boolean involved(OfficeData.SignRequest r, String uuid) {
      return r.fromUuid.equals(uuid) || r.names.containsKey(uuid);
   }

   static void open(ServerPlayer sp, String id) {
      OfficeData.SignRequest r = OfficeData.get(sp.server).requests.get(id);
      String me = sp.getUUID().toString();
      if (r == null) {
         DocService.err(sp, L.k("Запрос подписи не найден: возможно, он уже завершён."));
      } else if (!involved(r, me) && !BureauService.admin(sp)) {
         DocService.err(sp, L.k("Этот документ не для вас."));
      } else {
         ItemStack stack = Plat.loadStack(r.item, sp.level());
         DocModel d = NbtCodec.readDoc(stack);
         CompoundTag t = new CompoundTag();
         t.putString("Id", r.id);
         t.putString("From", r.fromName);
         t.putString("Title", r.title);
         t.put("Doc", NbtCodec.writeDoc(d));
         ListTag l = new ListTag();

         for (Entry<String, String> e : r.names.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("Name", e.getValue());
            c.putInt("State", r.states.getOrDefault(e.getKey(), 0));
            l.add(c);
         }

         t.put("Targets", l);
         t.putInt("Mine", r.names.containsKey(me) ? r.states.getOrDefault(me, 0) : -1);
         t.putBoolean("IsFrom", r.fromUuid.equals(me) || BureauService.admin(sp));
         t.putBoolean("CanSign", Perms.has(sp, Perms.SIGN));
         BureauService.send(sp, "signreq", t);
      }
   }

   static void sign(ServerPlayer sp, String id, String position, int[] sig) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.SignRequest r = od.requests.get(id);
      String me = sp.getUUID().toString();
      if (r == null || r.states.getOrDefault(me, -1) != OfficeData.SignRequest.PENDING) {
         DocService.err(sp, L.k("Запрос подписи не найден: возможно, он уже завершён."));
         return;
      }

      ItemStack stack = Plat.loadStack(r.item, sp.level());
      DocModel d = NbtCodec.readDoc(stack);
      String e = DocService.addSignature(sp, DocStore.get(sp.server), d, position, sig);
      if (e != null) {
         DocService.err(sp, e);
         return;
      }

      NbtCodec.writeDoc(stack, d);
      r.item = Plat.saveStack(stack, sp.level());
      r.states.put(me, OfficeData.SignRequest.SIGNED);
      od.setDirty();
      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PEN.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
      DocService.ok(sp, L.k("Вы подписали: ") + r.title + L.k(". Копию пришлют, когда подпишут все."));
      BureauService.tell(sp.server, r.fromUuid, sp.getGameProfile().getName() + L.k(" подписал(а): ") + r.title + ".");
      finishIfDone(sp.server, r);
   }

   static void decline(ServerPlayer sp, String id) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.SignRequest r = od.requests.get(id);
      String me = sp.getUUID().toString();
      if (r != null && r.states.getOrDefault(me, -1) == OfficeData.SignRequest.PENDING) {
         r.states.put(me, OfficeData.SignRequest.DECLINED);
         od.setDirty();
         DocService.msg(sp, L.k("Вы отказались подписывать: ") + r.title + ".", ChatFormatting.GRAY);
         BureauService.tell(sp.server, r.fromUuid, sp.getGameProfile().getName() + L.k(" отказался(ась) подписывать: ") + r.title + ".");
         finishIfDone(sp.server, r);
      }
   }

   static void cancel(ServerPlayer sp, String id) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.SignRequest r = od.requests.get(id);
      if (r != null) {
         if (!r.fromUuid.equals(sp.getUUID().toString()) && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Отозвать запрос может только тот, кто его отправил."));
         } else {
            for (Entry<String, Integer> e : r.states.entrySet()) {
               if (e.getValue() == OfficeData.SignRequest.PENDING) {
                  e.setValue(OfficeData.SignRequest.DECLINED);
                  BureauService.tell(sp.server, e.getKey(), r.fromName + L.k(" отозвал(а) запрос подписи: ") + r.title + ".");
               }
            }

            finishIfDone(sp.server, r);
         }
      }
   }

   /** Once nobody is left to answer: the original goes back to the author, copies to the signers. */
   static void finishIfDone(MinecraftServer server, OfficeData.SignRequest r) {
      if (!r.pending()) {
         OfficeData od = OfficeData.get(server);
         od.requests.remove(r.id);
         InboxService.dropRequest(od, r.id);
         od.setDirty();
         ItemStack original = Plat.loadStack(r.item, server.overworld());
         DocModel d = NbtCodec.readDoc(original);
         List<String> signed = new ArrayList<>();

         for (Entry<String, Integer> e : r.states.entrySet()) {
            if (e.getValue() == OfficeData.SignRequest.SIGNED) {
               signed.add(r.names.get(e.getKey()));
               ItemStack copy = original.copy();
               DocModel c = NbtCodec.readDoc(copy);
               c.copy = true;
               NbtCodec.writeDoc(copy, c);
               BureauService.deliver(server, e.getKey(), copy, L.k("Канцелярия"));
               BureauService.tell(server, e.getKey(), L.k("Все ответили по документу «") + r.title + L.k("». Вам выдана заверенная копия."));
            }
         }

         BureauService.deliver(server, r.fromUuid, original, L.k("Канцелярия"));
         String who = signed.isEmpty() ? L.k("никто не подписал") : L.k("подписали: ") + String.join(", ", signed);
         BureauService.tell(server, r.fromUuid, L.k("Документ вернулся к вам: ") + r.title + " (" + who + ").");
      }
   }

   static void list(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      String me = sp.getUUID().toString();
      int n = 0;

      for (OfficeData.SignRequest r : od.requests.values()) {
         if (involved(r, me)) {
            n++;
            MutableComponent line = L.c(r.id + " · " + r.title + " · ").withStyle(ChatFormatting.WHITE);
            if (r.fromUuid.equals(me)) {
               List<String> st = new ArrayList<>();

               for (Entry<String, String> e : r.names.entrySet()) {
                  int s = r.states.getOrDefault(e.getKey(), 0);
                  st.add(e.getValue() + (s == 1 ? " ✔" : (s == 2 ? " ✘" : " …")));
               }

               line.append(L.c(String.join(", ", st) + " ").withStyle(ChatFormatting.GRAY))
                  .append(openButton(r.id))
                  .append(L.c(" "))
                  .append(BureauService.button(L.k("[Отозвать]"), "/seal sign cancel " + r.id, L.k("Вернуть документ себе")));
            } else {
               line.append(L.c(L.k("от ") + r.fromName + " ").withStyle(ChatFormatting.GRAY)).append(openButton(r.id));
            }

            sp.sendSystemMessage(line);
         }
      }

      if (n == 0) {
         DocService.msg(sp, L.k("Документов на подписи нет."), ChatFormatting.GRAY);
      }
   }

   static void remind(ServerPlayer sp) {
      String me = sp.getUUID().toString();
      Iterator<OfficeData.SignRequest> it = OfficeData.get(sp.server).requests.values().iterator();
      int n = 0;

      while (it.hasNext()) {
         OfficeData.SignRequest r = it.next();
         if (r.states.getOrDefault(me, -1) == OfficeData.SignRequest.PENDING) {
            n++;
         }
      }

      if (n > 0) {
         sp.sendSystemMessage(
            L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD)
               .append(L.c(L.k("Документов ждут вашей подписи: ") + n + ". ").withStyle(ChatFormatting.YELLOW))
               .append(BureauService.button(L.k("[Список]"), "/seal sign list", L.k("Показать документы на подпись")))
         );
      }
   }
}
