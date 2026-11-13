package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModBlocks;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.OfficeData;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.MailModel;

/** Several recipients per letter, saved mailing lists, newspaper subscriptions and delivery receipts. */
public final class ListService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM HH:mm");
   /** Recipients of one letter for ordinary players; mail.mass lifts it to {@link #MAX_MASS}. */
   public static final int MAX_RECIPIENTS = 30;
   public static final int MAX_MASS = 500;
   public static final int MAX_LISTS = 20;
   public static final int MAX_LIST_MEMBERS = 200;
   public static final int MAX_RECEIPTS = 40;
   /** Pause between two mass mailings for players without mail.mass. */
   public static final long COOLDOWN_MS = 60000L;
   public static final String[] ALL = new String[]{"@all", "@все", "@alle"};
   public static final String[] SUBSCRIBERS = new String[]{"@subscribers", "@подписчики", "@abonnenten"};
   private static final Map<UUID, Long> LAST_MASS = new HashMap<>();

   private ListService() {
   }

   public static final class Resolved {
      public final Map<String, String> who = new LinkedHashMap<>();
      public String error;
   }

   static boolean is(String token, String[] aliases) {
      for (String a : aliases) {
         if (a.equalsIgnoreCase(token)) {
            return true;
         }
      }

      return false;
   }

   /** Mailing lists a player may send to: their own and those of their organizations. */
   static List<OfficeData.MailList> usableLists(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      String me = sp.getUUID().toString();
      List<OfficeData.MailList> out = new ArrayList<>();

      for (OfficeData.MailList l : od.lists.values()) {
         if (l.owner.equals("p:" + me)) {
            out.add(l);
         } else if (l.owner.startsWith("o:")) {
            OfficeData.Org o = od.orgs.get(l.owner.substring(2));
            if (o != null && o.isMember(me)) {
               out.add(l);
            }
         }
      }

      return out;
   }

   /** Mailing lists a player may change: their own and those of organizations they manage. */
   static boolean canEdit(ServerPlayer sp, OfficeData od, String owner) {
      String me = sp.getUUID().toString();
      if (owner.equals("p:" + me)) {
         return true;
      } else if (owner.startsWith("o:")) {
         OfficeData.Org o = od.orgs.get(owner.substring(2));
         return o != null && (o.isManager(me) || BureauService.admin(sp));
      } else {
         return false;
      }
   }

   /** "@" names offered by autocomplete. */
   public static List<String> targets(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      List<String> out = new ArrayList<>();
      if (Perms.has(sp, Perms.MAIL_MASS)) {
         out.add("@all");
      }

      out.add("@subscribers");

      for (OfficeData.MailList l : usableLists(sp)) {
         if (!out.contains("@" + l.name)) {
            out.add("@" + l.name);
         }
      }

      String me = sp.getUUID().toString();

      for (OfficeData.Org o : od.orgs.values()) {
         if (o.isMember(me) && !out.contains("@" + o.name)) {
            out.add("@" + o.name);
         }
      }

      return out;
   }

   /**
    * Turns "Alex, Sam, @list, @Organization" into players. {@code title} is the newspaper title
    * key for "@subscribers", or null when there is no newspaper.
    */
   public static Resolved resolve(ServerPlayer sp, String to, String title) {
      Resolved r = new Resolved();
      MinecraftServer server = sp.server;
      OfficeData od = OfficeData.get(server);
      String me = sp.getUUID().toString();
      boolean mass = Perms.has(sp, Perms.MAIL_MASS);

      for (String raw : (to == null ? "" : to).split("[,;]")) {
         String tok = raw.trim();
         if (!tok.isEmpty()) {
            if (tok.startsWith("@")) {
               if (is(tok, ALL)) {
                  if (!mass) {
                     r.error = L.k("Рассылка всем игрокам доступна только с правом mail.mass.");
                     return r;
                  }

                  r.who.putAll(od.known);
               } else if (is(tok, SUBSCRIBERS)) {
                  if (title == null) {
                     r.error = L.k("«@subscribers» работает только при рассылке газеты.");
                     return r;
                  }

                  DocStore.TitleRec tr = DocStore.get(server).titles.get(title);
                  if (tr == null || !tr.member(me) && !BureauService.admin(sp)) {
                     r.error = L.k("Рассылать подписчикам может только редакция газеты.");
                     return r;
                  }

                  r.who.putAll(od.subs.getOrDefault(title, Map.of()));
               } else {
                  String name = tok.substring(1).trim();
                  OfficeData.MailList found = null;

                  for (OfficeData.MailList l : usableLists(sp)) {
                     if (OfficeData.key(l.name).equals(OfficeData.key(name)) && (found == null || l.owner.startsWith("p:"))) {
                        found = l;
                     }
                  }

                  if (found != null) {
                     r.who.putAll(found.members);
                  } else {
                     OfficeData.Org o = od.orgByName(name);
                     if (o == null) {
                        r.error = L.k("Нет списка рассылки или организации «") + name + "».";
                        return r;
                     }

                     if (!o.isMember(me) && !mass) {
                        r.error = L.k("Писать всем участникам «") + o.name + L.k("» могут только её участники.");
                        return r;
                     }

                     r.who.putAll(o.everyone());
                  }
               }
            } else {
               String[] p = BureauService.findPlayer(server, tok);
               if (p == null) {
                  r.error = L.k("Игрок «") + tok + L.k("» ни разу не заходил на сервер — письмо не отправлено.");
                  return r;
               }

               r.who.put(p[0], p[1]);
            }
         }
      }

      r.who.remove("");
      int max = mass ? MAX_MASS : MAX_RECIPIENTS;
      if (r.who.isEmpty()) {
         r.error = L.k("Некому отправлять: список получателей пуст.");
      } else if (r.who.size() > max) {
         r.error = L.k("Слишком много получателей: ") + r.who.size() + L.k(" (можно до ") + max + ").";
      }

      return r;
   }

   static String cooldown(ServerPlayer sp, int recipients) {
      if (recipients > 1 && !Perms.has(sp, Perms.MAIL_MASS)) {
         Long last = LAST_MASS.get(sp.getUUID());
         long now = System.currentTimeMillis();
         if (last != null && now - last < COOLDOWN_MS) {
            return L.k("Следующую рассылку можно сделать через ") + (COOLDOWN_MS - (now - last) + 999L) / 1000L + L.k(" с.");
         }
      }

      return null;
   }

   static void used(ServerPlayer sp, int recipients) {
      if (recipients > 1) {
         LAST_MASS.put(sp.getUUID(), System.currentTimeMillis());
      }
   }

   /** Extra copies cost one sheet of paper each (free in creative). */
   static boolean pay(ServerPlayer sp, int sheets) {
      if (sheets <= 0 || sp.getAbilities().instabuild) {
         return true;
      } else if (PaperService.count(sp, Items.PAPER) < sheets) {
         DocService.err(sp, L.k("Для копий нужна бумага: ") + sheets + L.k(" шт."));
         return false;
      } else {
         PaperService.take(sp, Items.PAPER, sheets);
         return true;
      }
   }

   /** Sends the envelope in the main hand to everyone on its address line. Called by the mailbox. */
   static void sendEnvelope(ServerPlayer sp) {
      ItemStack held = sp.getMainHandItem();
      MailModel m = MailCodec.readMail(held);
      MinecraftServer server = sp.server;
      Resolved res = resolve(sp, m.to, null);
      if (res.error != null) {
         DocService.err(sp, res.error);
         return;
      }

      int n = res.who.size();
      String cd = cooldown(sp, n);
      if (cd != null) {
         DocService.err(sp, cd);
         return;
      }

      List<ItemStack> attached = MailCodec.items(held, MailCodec.MAIL_TAG, sp.level());
      if (n > 1) {
         for (ItemStack a : attached) {
            if (a.getItem() instanceof PassportItem) {
               DocService.err(sp, L.k("Паспорт нельзя разослать нескольким адресатам — у него нет копий."));
               return;
            }
         }
      }

      DocStore store = DocStore.get(server);

      for (Entry<String, String> e : res.who.entrySet()) {
         if (MailService.box(store, e.getKey()).size() >= MailService.MAX_BOX) {
            DocService.err(sp, L.k("Почтовый ящик получателя переполнен: ") + e.getValue() + ".");
            return;
         }
      }

      if (!pay(sp, n - 1)) {
         return;
      }

      ItemStack original = held.split(1);
      String from = m.from.isEmpty() ? sp.getGameProfile().getName() : m.from;
      String what = describe(m, attached);
      String stamp = LocalDate.now().format(DATE);
      boolean first = true;

      for (Entry<String, String> e : res.who.entrySet()) {
         ItemStack one = first ? original : original.copy();
         MailModel pm = MailCodec.readMail(one);
         pm.postmark = stamp;
         if (n > 1) {
            pm.to = e.getValue();
            if (!first) {
               List<ItemStack> items = MailCodec.items(one, MailCodec.MAIL_TAG, sp.level());

               for (ItemStack a : items) {
                  if (a.getItem() instanceof DocumentItem) {
                     DocModel d = NbtCodec.readDoc(a);
                     if (d.issued) {
                        d.copy = true;
                        NbtCodec.writeDoc(a, d);
                     }
                  }
               }

               MailCodec.setItems(one, MailCodec.MAIL_TAG, items, sp.level());
            }

            if (pm.state == MailModel.SEALED) {
               pm.hash = MailService.hash(store, pm, MailCodec.items(one, MailCodec.MAIL_TAG, sp.level()));
            }
         }

         MailCodec.writeMail(one, pm);
         CompoundTag entry = new CompoundTag();
         entry.put("Item", Plat.saveStack(one, sp.level()));
         entry.putString("From", from);
         entry.putLong("Time", System.currentTimeMillis());
         entry.putString("Rcpt", receipt(sp, e.getValue(), what));
         entry.putString("RcptFrom", sp.getUUID().toString());
         MailService.box(store, e.getKey()).add(entry);
         ServerPlayer online = BureauService.online(server, e.getKey());
         if (online != null && online != sp) {
            MailService.notifyNew(online, from);
         }

         first = false;
      }

      store.setDirty();
      used(sp, n);
      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
      if (n == 1) {
         DocService.ok(sp, L.k("Письмо отправлено: ") + res.who.values().iterator().next() + ".");
      } else {
         DocService.ok(sp, L.k("Писем разослано: ") + n + ".");
      }

      LOG.info("[Signed & Sealed] {} sent a letter to {} recipient(s)", sp.getGameProfile().getName(), n);
   }

   static String describe(MailModel m, List<ItemStack> attached) {
      String t = m.text == null ? "" : m.text.replace('\n', ' ').trim();
      if (t.length() > 40) {
         t = t.substring(0, 40) + "…";
      }

      if (!attached.isEmpty()) {
         ItemStack a = attached.get(0);
         String n;
         if (a.getItem() instanceof DocumentItem) {
            n = L.k(NbtCodec.readDoc(a).templateName());
         } else if (a.getItem() instanceof NewspaperItem) {
            n = PaperCodec.ref(a).name;
         } else {
            n = L.k("вложение");
         }

         t = (t.isEmpty() ? "" : t + " · ") + "+ " + n;
      }

      return t.isEmpty() ? L.k("письмо без текста") : t;
   }

   static String receipt(ServerPlayer sp, String toName, String what) {
      OfficeData od = OfficeData.get(sp.server);
      od.receiptCounter++;
      OfficeData.Receipt r = new OfficeData.Receipt();
      r.id = "MR-" + od.receiptCounter;
      r.toName = toName;
      r.what = what;
      r.sent = System.currentTimeMillis();
      List<OfficeData.Receipt> l = od.receipts.computeIfAbsent(sp.getUUID().toString(), k -> new ArrayList<>());
      l.add(r);

      while (l.size() > MAX_RECEIPTS) {
         l.remove(0);
      }

      od.setDirty();
      return r.id;
   }

   /** Marks a letter as collected and lets the sender know. */
   static void collected(MinecraftServer server, CompoundTag entry, String byName) {
      String id = entry.getString("Rcpt");
      String sender = entry.getString("RcptFrom");
      if (!id.isEmpty() && !sender.isEmpty()) {
         OfficeData od = OfficeData.get(server);
         List<OfficeData.Receipt> l = od.receipts.get(sender);
         if (l != null) {
            for (OfficeData.Receipt r : l) {
               if (r.id.equals(id) && r.collected == 0L) {
                  r.collected = System.currentTimeMillis();
                  od.setDirty();
                  BureauService.tell(server, sender, L.k("Письмо вручено: ") + byName + L.k(" забрал(а) ваше письмо") + " (" + r.what + ").");
               }
            }
         }
      }
   }

   static String when(long t) {
      return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), java.time.ZoneId.systemDefault()).format(STAMP);
   }

   /** Extra data for the mailbox screen: sent letters, the newspaper in hand. */
   static CompoundTag mailboxData(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      CompoundTag t = new CompoundTag();
      ListTag rl = new ListTag();
      List<OfficeData.Receipt> l = od.receipts.getOrDefault(sp.getUUID().toString(), List.of());

      for (int i = l.size() - 1; i >= 0 && rl.size() < 12; i--) {
         OfficeData.Receipt r = l.get(i);
         CompoundTag c = new CompoundTag();
         c.putString("To", r.toName);
         c.putString("What", r.what);
         c.putString("Sent", when(r.sent));
         c.putString("Got", r.collected == 0L ? "" : when(r.collected));
         rl.add(c);
      }

      t.put("Sent", rl);
      ItemStack held = sp.getMainHandItem();
      if (held.getItem() instanceof NewspaperItem) {
         PaperCodec.Ref ref = PaperCodec.ref(held);
         if (ref.published) {
            t.putString("Paper", ref.name);
            t.putInt("Copies", held.getCount());
            t.putInt("Subs", od.subs.getOrDefault(DocStore.titleKey(ref.name), Map.of()).size());
         }
      }

      return t;
   }

   /** Sends the published issue in hand to many players; copies beyond the stack cost paper. */
   static void mailPaper(ServerPlayer sp, BlockPos pos, String to) {
      if (!sp.level().getBlockState(pos).is((Block)ModBlocks.MAILBOX.get())
         || sp.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) > 64.0) {
         return;
      }

      ItemStack held = sp.getMainHandItem();
      if (!(held.getItem() instanceof NewspaperItem) || !PaperCodec.ref(held).published) {
         DocService.err(sp, L.k("Возьмите опубликованный выпуск в основную руку."));
      } else if (!Perms.has(sp, Perms.MAIL)) {
         DocService.err(sp, L.k("У вас нет права отправлять почту."));
      } else {
         PaperCodec.Ref ref = PaperCodec.ref(held);
         Resolved res = resolve(sp, to, DocStore.titleKey(ref.name));
         if (res.error != null) {
            DocService.err(sp, res.error);
            return;
         }

         int n = res.who.size();
         String cd = cooldown(sp, n);
         if (cd != null) {
            DocService.err(sp, cd);
            return;
         }

         int fromStack = Math.min(n, held.getCount());
         if (!pay(sp, n - fromStack)) {
            return;
         }

         ItemStack proto = held.copyWithCount(1);
         held.shrink(fromStack);
         DocStore store = DocStore.get(sp.server);
         int sent = 0;

         for (Entry<String, String> e : res.who.entrySet()) {
            List<CompoundTag> box = MailService.box(store, e.getKey());
            if (box.size() < MailService.MAX_BOX) {
               CompoundTag entry = new CompoundTag();
               entry.put("Item", Plat.saveStack(proto.copy(), sp.level()));
               entry.putString("From", ref.name);
               entry.putLong("Time", System.currentTimeMillis());
               box.add(entry);
               sent++;
               ServerPlayer online = BureauService.online(sp.server, e.getKey());
               if (online != null && online != sp) {
                  MailService.notifyNew(online, ref.name);
               }
            }
         }

         store.setDirty();
         used(sp, n);
         sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
         DocService.ok(sp, L.k("Выпуск «") + ref.name + L.k("» разослан: ") + sent + (sent < n ? L.k(" (у части получателей ящик переполнен)") : "") + ".");
         LOG.info("[Signed & Sealed] {} mailed newspaper {} ({} recipients)", sp.getGameProfile().getName(), ref.name, sent);
      }
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "lists":
            sendLists(sp);
            break;
         case "list_save":
            saveList(sp, t);
            break;
         case "list_delete":
            deleteList(sp, t.getString("Key"));
            break;
         case "paper_sub":
            subscribe(sp, t.getString("Name"), t.getBoolean("Toggle") ? !subscribed(sp, t.getString("Name")) : t.getBoolean("Sub"));
            break;
         case "mail_paper":
            mailPaper(sp, new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")), t.getString("To"));
      }
   }

   static String membersText(Map<String, String> m) {
      return String.join(", ", m.values());
   }

   /** Lists and subscriber lists this player can manage, for the mailing list screen. */
   static void sendLists(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      DocStore store = DocStore.get(sp.server);
      String me = sp.getUUID().toString();
      CompoundTag t = new CompoundTag();
      ListTag ls = new ListTag();

      for (OfficeData.MailList l : od.lists.values()) {
         if (canEdit(sp, od, l.owner)) {
            CompoundTag c = new CompoundTag();
            c.putString("Key", l.key());
            c.putString("Kind", "list");
            c.putString("Name", l.name);
            c.putString("Owner", l.owner.startsWith("o:") ? l.owner.substring(2) : "");
            c.putString("OwnerName", l.owner.startsWith("o:") && od.orgs.containsKey(l.owner.substring(2)) ? od.orgs.get(l.owner.substring(2)).name : "");
            c.putString("Members", membersText(l.members));
            c.putInt("Count", l.members.size());
            ls.add(c);
         }
      }

      for (DocStore.TitleRec tr : store.titles.values()) {
         if (tr.member(me) || BureauService.admin(sp)) {
            Map<String, String> s = od.subs.getOrDefault(DocStore.titleKey(tr.name), Map.of());
            CompoundTag c = new CompoundTag();
            c.putString("Key", "s|" + DocStore.titleKey(tr.name));
            c.putString("Kind", "subs");
            c.putString("Name", tr.name);
            c.putString("Members", membersText(s));
            c.putInt("Count", s.size());
            ls.add(c);
         }
      }

      t.put("Lists", ls);
      ListTag orgs = new ListTag();

      for (OfficeData.Org o : od.orgs.values()) {
         if (o.isManager(me)) {
            CompoundTag c = new CompoundTag();
            c.putString("Id", o.id);
            c.putString("Name", o.name);
            orgs.add(c);
         }
      }

      t.put("Orgs", orgs);
      List<String> mine = new ArrayList<>();

      for (Entry<String, Map<String, String>> e : od.subs.entrySet()) {
         if (e.getValue().containsKey(me)) {
            DocStore.TitleRec tr = store.titles.get(e.getKey());
            mine.add(tr == null ? e.getKey() : tr.name);
         }
      }

      t.put("MySubs", BureauService.strings(mine));
      BureauService.send(sp, "lists", t);
   }

   /** Parses "Alex, Sam" into known players; unknown names are reported back. */
   static Map<String, String> players(ServerPlayer sp, String text, List<String> unknown) {
      Map<String, String> out = new LinkedHashMap<>();

      for (String raw : (text == null ? "" : text).split("[,;\\s]+")) {
         String n = raw.trim();
         if (!n.isEmpty()) {
            String[] p = BureauService.findPlayer(sp.server, n);
            if (p == null) {
               unknown.add(n);
            } else {
               out.put(p[0], p[1]);
            }
         }
      }

      return out;
   }

   static void saveList(ServerPlayer sp, CompoundTag t) {
      OfficeData od = OfficeData.get(sp.server);
      List<String> unknown = new ArrayList<>();
      Map<String, String> members = players(sp, t.getString("Members"), unknown);
      if (!unknown.isEmpty()) {
         DocService.err(sp, L.k("Эти игроки ни разу не заходили на сервер: ") + String.join(", ", unknown));
         return;
      }

      if (members.size() > MAX_LIST_MEMBERS) {
         DocService.err(sp, L.k("В списке может быть до ") + MAX_LIST_MEMBERS + L.k(" человек."));
         return;
      }

      if ("subs".equals(t.getString("Kind"))) {
         String key = t.getString("Key");
         String title = key.startsWith("s|") ? key.substring(2) : key;
         DocStore.TitleRec tr = DocStore.get(sp.server).titles.get(title);
         if (tr == null || !tr.member(sp.getUUID().toString()) && !BureauService.admin(sp)) {
            DocService.err(sp, L.k("Подписчиков меняет только редакция газеты."));
            return;
         }

         od.subs.put(title, members);
         od.setDirty();
         DocService.ok(sp, L.k("Подписчики «") + tr.name + L.k("» сохранены: ") + members.size() + ".");
      } else {
         String name = DocService.clean(t.getString("Name"), 32, false).replace("@", "").replace(",", "").trim();
         if (name.isEmpty()) {
            DocService.err(sp, L.k("Назовите список рассылки."));
            return;
         }

         String org = t.getString("Owner");
         String owner = org.isEmpty() ? "p:" + sp.getUUID() : "o:" + org;
         if (!canEdit(sp, od, owner)) {
            DocService.err(sp, L.k("Списки организации меняют только её руководители."));
            return;
         }

         String old = t.getString("Key");
         if (!old.isEmpty()) {
            OfficeData.MailList prev = od.lists.get(old);
            if (prev != null && canEdit(sp, od, prev.owner)) {
               od.lists.remove(old);
            }
         }

         OfficeData.MailList l = new OfficeData.MailList();
         l.owner = owner;
         l.name = name;
         l.members.putAll(members);
         int count = 0;

         for (OfficeData.MailList x : od.lists.values()) {
            if (x.owner.equals(owner)) {
               count++;
            }
         }

         if (count >= MAX_LISTS && !od.lists.containsKey(l.key())) {
            DocService.err(sp, L.k("Слишком много списков рассылки (до ") + MAX_LISTS + ").");
            return;
         }

         od.lists.put(l.key(), l);
         od.setDirty();
         DocService.ok(sp, L.k("Список «@") + name + L.k("» сохранён: ") + members.size() + L.k(" адресат(ов)."));
      }

      sendLists(sp);
      BureauService.sendNames(sp);
   }

   static void deleteList(ServerPlayer sp, String key) {
      OfficeData od = OfficeData.get(sp.server);
      OfficeData.MailList l = od.lists.get(key);
      if (l != null && canEdit(sp, od, l.owner)) {
         od.lists.remove(key);
         od.setDirty();
         DocService.ok(sp, L.k("Список «@") + l.name + L.k("» удалён."));
         sendLists(sp);
         BureauService.sendNames(sp);
      }
   }

   public static String subscribe(ServerPlayer sp, String paperName, boolean on) {
      DocStore store = DocStore.get(sp.server);
      DocStore.TitleRec tr = store.titles.get(DocStore.titleKey(paperName));
      if (tr == null) {
         String e = L.k("Газета «") + paperName + L.k("» не найдена.");
         DocService.err(sp, e);
         return e;
      } else {
         OfficeData od = OfficeData.get(sp.server);
         Map<String, String> s = od.subs.computeIfAbsent(DocStore.titleKey(tr.name), k -> new LinkedHashMap<>());
         if (on) {
            s.put(sp.getUUID().toString(), sp.getGameProfile().getName());
            DocService.ok(sp, L.k("Вы подписались на «") + tr.name + L.k("». Новые выпуски будут приходить в почтовый ящик."));
         } else {
            s.remove(sp.getUUID().toString());
            DocService.msg(sp, L.k("Подписка на «") + tr.name + L.k("» отменена."), ChatFormatting.GRAY);
         }

         od.setDirty();
         return null;
      }
   }

   public static boolean subscribed(ServerPlayer sp, String paperName) {
      Map<String, String> s = OfficeData.get(sp.server).subs.get(DocStore.titleKey(paperName));
      return s != null && s.containsKey(sp.getUUID().toString());
   }

   static String uid() {
      return UUID.randomUUID().toString();
   }
}
