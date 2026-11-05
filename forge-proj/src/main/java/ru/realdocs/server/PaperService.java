package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.model.PaperElement;
import ru.realdocs.model.PaperModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/** Newspapers: drafts, publication, the title registry, print runs and verification. */
public final class PaperService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   public static final int MAX_RUN = 64;
   private static final long LAYOUT_QUOTA = 6000000L;

   private PaperService() {
   }

   static boolean admin(ServerPlayer sp) {
      return Perms.has(sp, Perms.ADMIN);
   }

   static void give(ServerPlayer sp, ItemStack out) {
      if (!sp.getInventory().add(out)) {
         sp.drop(out, false);
      }
   }

   static int count(ServerPlayer sp, Item what) {
      int n = 0;
      Inventory inv = sp.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.is(what)) {
            n += st.getCount();
         }
      }

      return n;
   }

   static void take(ServerPlayer sp, Item what, int n) {
      Inventory inv = sp.getInventory();

      for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
         ItemStack st = inv.getItem(i);
         if (st.is(what)) {
            int k = Math.min(n, st.getCount());
            st.shrink(k);
            n -= k;
         }
      }
   }

   /** Picture upload finished: store it as a world asset owned by the uploader. */
   static void acceptImage(ServerPlayer sp, int token, byte[] png) {
      if (!Perms.has(sp, Perms.NEWSPAPER)) {
         Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("У вас нет права выпускать газеты.")));
      } else if (!DocService.validPng(png, Uploads.MAX_IMAGE, 1024)) {
         Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Картинка не подходит: нужен PNG до 1024 px.")));
      } else {
         DocStore store = DocStore.get(sp.server);
         String aid = store.nextAssetId();

         try {
            DocStore.saveAsset(sp.server, aid, png);
         } catch (IOException var7) {
            LOG.error("[Signed & Sealed] не удалось сохранить картинку газеты {}", aid, var7);
            Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Не удалось сохранить картинку на сервере.")));
            return;
         }

         DocStore.AssetRec ar = new DocStore.AssetRec();
         ar.ownerUuid = sp.getUUID().toString();
         ar.kind = "paper";
         ar.created = LocalDate.now().format(DATE);
         store.assets.put(aid, ar);
         store.setDirty();
         Net.sendTo(sp, new MorePackets.UploadResult(token, true, aid, ""));
      }
   }

   private static boolean canUseAsset(DocStore store, String id, String me) {
      DocStore.AssetRec a = store.assets.get(id);
      return a != null && ("paper_pub".equals(a.kind) || a.ownerUuid.equals(me) && "paper".equals(a.kind));
   }

   /** Item in hand split off a stack so that only one blank turns into a layout. */
   private static ItemStack single(ServerPlayer sp, InteractionHand hand) {
      ItemStack stack = sp.getItemInHand(hand);
      if (stack.getCount() <= 1) {
         return stack;
      } else {
         return stack.split(1);
      }
   }

   public static void commit(ServerPlayer sp, InteractionHand hand, int token, boolean publish) {
      ItemStack held = sp.getItemInHand(hand);
      byte[] data = Uploads.takeLayout(sp, token);
      if (!(held.getItem() instanceof NewspaperItem)) {
         reply(sp, token, false, "", L.k("Возьмите газету в руку."));
      } else if (data == null) {
         reply(sp, token, false, "", L.k("Вёрстка не дошла до сервера, попробуйте ещё раз."));
      } else if (!Perms.has(sp, Perms.NEWSPAPER)) {
         reply(sp, token, false, "", L.k("У вас нет права выпускать газеты."));
      } else {
         PaperCodec.Ref ref = PaperCodec.ref(held);
         if (ref.published) {
            reply(sp, token, false, "", L.k("Этот выпуск уже опубликован и не меняется. Сделайте макет следующего номера."));
            return;
         }

         PaperModel m;
         try {
            m = PaperCodec.read(Plat.unpack(data, LAYOUT_QUOTA));
         } catch (Exception var18) {
            reply(sp, token, false, "", L.k("Повреждённая вёрстка."));
            return;
         }

         m.sanitize();
         DocStore store = DocStore.get(sp.server);
         String me = sp.getUUID().toString();
         for (List<PaperElement> p : m.pages) {
            for (PaperElement e : p) {
               if (e.type == PaperElement.IMAGE && !e.asset.isEmpty()) {
                  if (!canUseAsset(store, e.asset, me)) {
                     e.asset = "";
                  }
               }
            }
         }

         if (m.assets().size() > PaperModel.MAX_IMAGES) {
            reply(sp, token, false, "", L.k("Слишком много картинок (до ") + PaperModel.MAX_IMAGES + ").");
            return;
         }

         if (publish && m.name.isBlank()) {
            reply(sp, token, false, "", L.k("Укажите название издания."));
            return;
         }

         String id = "";
         DocStore.PaperRec rec = null;
         if (!ref.id.isEmpty()) {
            DocStore.PaperRec old = store.papers.get(ref.id);
            if (old != null && !old.published && (old.ownerUuid.equals(me) || admin(sp))) {
               id = ref.id;
               rec = old;
            }
         }

         if (rec == null) {
            id = store.nextPaperId();
            rec = new DocStore.PaperRec();
            rec.ownerUuid = me;
            rec.ownerName = sp.getGameProfile().getName();
         }

         m.id = id;
         m.published = false;
         m.issue = 0;
         m.date = "";
         m.editor = "";
         m.editorUuid = "";
         if (publish) {
            String key = DocStore.titleKey(m.name);
            DocStore.TitleRec t = store.titles.get(key);
            if (t == null) {
               t = new DocStore.TitleRec();
               t.name = m.name;
               t.ownerUuid = me;
               t.ownerName = sp.getGameProfile().getName();
               store.titles.put(key, t);
               DocService.ok(sp, L.k("Издание «") + m.name + L.k("» зарегистрировано за вами. Добавить сотрудников: /seal paper staff add <игрок>"));
            } else if (!t.member(me) && !admin(sp)) {
               reply(sp, token, false, "", L.k("Издание «") + t.name + L.k("» принадлежит ") + t.ownerName + L.k(". Попросите добавить вас в редакцию."));
               return;
            }

            t.counter++;
            m.name = t.name;
            m.issue = t.counter;
            m.date = LocalDate.now().format(DATE);
            m.editor = sp.getGameProfile().getName();
            m.editorUuid = me;
            m.published = true;
            rec.published = true;
            rec.hash = store.hmac(m.canonical());

            for (String a : m.assets()) {
               DocStore.AssetRec ar = store.assets.get(a);
               if (ar != null) {
                  ar.kind = "paper_pub";
               }
            }
         }

         try {
            DocStore.savePaper(sp.server, id, Plat.pack(PaperCodec.write(m)));
         } catch (IOException var17) {
            LOG.error("[Signed & Sealed] не удалось сохранить газету {}", id, var17);
            reply(sp, token, false, "", L.k("Не удалось сохранить вёрстку на сервере."));
            return;
         }

         rec.name = m.name;
         rec.issue = m.issue;
         rec.date = m.date;
         rec.format = m.format;
         rec.pages = m.pageCount();
         rec.time = System.currentTimeMillis();
         store.papers.put(id, rec);
         store.setDirty();
         ItemStack target = single(sp, hand);
         PaperCodec.Ref r = new PaperCodec.Ref();
         r.id = id;
         r.name = m.name;
         r.issue = m.issue;
         r.date = m.date;
         r.format = m.format;
         r.pages = m.pageCount();
         r.published = m.published;
         r.editor = m.editor;
         r.sig = m.published ? store.hmac(r.canonical()) : "";
         PaperCodec.writeRef(target, r);
         if (target != held) {
            give(sp, target);
         }

         if (publish) {
            sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PRINT.get(), SoundSource.PLAYERS, 0.7F, 1.1F);
            DocService.ok(sp, L.k(NewspaperItem.kind(m.format)) + " «" + m.name + "» № " + m.issue + L.k(" опубликован. Тираж печатается на принтере."));
            LOG.info("[Signed & Sealed] {} опубликовал «{}» №{} ({})", new Object[]{sp.getGameProfile().getName(), m.name, m.issue, id});
         }

         reply(sp, token, true, id, publish ? L.k("Опубликовано.") : L.k("Черновик сохранён."));
      }
   }

   private static void reply(ServerPlayer sp, int token, boolean ok, String id, String msg) {
      Net.sendTo(sp, new MorePackets.UploadResult(token, ok, id, msg));
      if (!ok) {
         DocService.err(sp, msg);
      }
   }

   static boolean canSeeLayout(ServerPlayer sp, DocStore.PaperRec rec) {
      return rec.published || rec.ownerUuid.equals(sp.getUUID().toString()) || admin(sp);
   }

   public static void sendLayout(ServerPlayer sp, String id) {
      DocStore store = DocStore.get(sp.server);
      DocStore.PaperRec rec = store.papers.get(id);
      byte[] data = rec != null && canSeeLayout(sp, rec) ? DocStore.loadPaper(sp.server, id) : new byte[0];
      Net.sendTo(sp, new MorePackets.PaperData(id, data.length > 0, data, rec != null && rec.revoked));
   }

   public static PaperModel loadModel(MinecraftServer server, String id) {
      byte[] data = DocStore.loadPaper(server, id);
      if (data.length == 0) {
         return null;
      } else {
         try {
            return PaperCodec.read(Plat.unpack(data, LAYOUT_QUOTA));
         } catch (Exception var4) {
            return null;
         }
      }
   }

   static boolean staff(ServerPlayer sp, DocStore store, String name) {
      DocStore.TitleRec t = store.titles.get(DocStore.titleKey(name));
      return admin(sp) || t != null && t.member(sp.getUUID().toString());
   }

   /** Print run of the issue held in the main hand. */
   public static void printRun(ServerPlayer sp, int count) {
      ItemStack held = sp.getMainHandItem();
      if (!(held.getItem() instanceof NewspaperItem)) {
         DocService.err(sp, L.k("Возьмите опубликованную газету в основную руку."));
      } else {
         PaperCodec.Ref r = PaperCodec.ref(held);
         DocStore store = DocStore.get(sp.server);
         DocStore.PaperRec rec = store.papers.get(r.id);
         if (!r.published || rec == null || !rec.published) {
            DocService.err(sp, L.k("Сначала опубликуйте выпуск в редакторе."));
         } else if (rec.revoked) {
            DocService.err(sp, L.k("Этот выпуск отозван администрацией."));
         } else if (!DocStore.safeEquals(store.hmac(r.canonical()), r.sig)) {
            DocService.err(sp, L.k("Экземпляр не совпадает с реестром — печать невозможна."));
         } else if (!staff(sp, store, r.name) && !rec.ownerUuid.equals(sp.getUUID().toString())) {
            DocService.err(sp, L.k("Тираж может печатать только редакция издания."));
         } else {
            int n = Math.max(1, Math.min(MAX_RUN, count));
            int per = Math.max(1, (r.pages + 1) / 2);
            boolean free = sp.getAbilities().instabuild;
            if (!free && count(sp, Items.PAPER) < n * per) {
               DocService.err(sp, L.k("Не хватает бумаги: нужно ") + n * per + L.k(" (по ") + per + L.k(" на экземпляр)."));
            } else {
               if (!free) {
                  take(sp, Items.PAPER, n * per);
               }

               int left = n;

               while (left > 0) {
                  int k = Math.min(16, left);
                  ItemStack out = new ItemStack((ItemLike)ModItems.NEWSPAPER.get(), k);
                  PaperCodec.writeRef(out, r);
                  give(sp, out);
                  left -= k;
               }

               sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PRINT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
               DocService.ok(sp, L.k("Напечатан тираж: ") + n + L.k(" экз. «") + r.name + "» № " + r.issue + ".");
               LOG.info("[Signed & Sealed] {} напечатал {} экз. {}", new Object[]{sp.getGameProfile().getName(), n, r.id});
            }
         }
      }
   }

   /** New draft based on a published issue (keeps the masthead and layout). */
   public static void cloneNext(ServerPlayer sp, InteractionHand hand) {
      ItemStack held = sp.getItemInHand(hand);
      PaperCodec.Ref r = PaperCodec.ref(held);
      DocStore store = DocStore.get(sp.server);
      DocStore.PaperRec rec = store.papers.get(r.id);
      if (!(held.getItem() instanceof NewspaperItem) || !r.published || rec == null) {
         DocService.err(sp, L.k("Возьмите опубликованный выпуск в руку."));
      } else if (!staff(sp, store, r.name) && !rec.ownerUuid.equals(sp.getUUID().toString())) {
         DocService.err(sp, L.k("Новый номер может готовить только редакция издания."));
      } else if (!Perms.has(sp, Perms.NEWSPAPER)) {
         DocService.err(sp, L.k("У вас нет права выпускать газеты."));
      } else {
         boolean free = sp.getAbilities().instabuild;
         if (!free && count(sp, Items.PAPER) < 1) {
            DocService.err(sp, L.k("Нужен лист бумаги в инвентаре."));
         } else {
            PaperModel m = loadModel(sp.server, r.id);
            if (m == null) {
               DocService.err(sp, L.k("Вёрстка выпуска не найдена на сервере."));
            } else {
               String id = store.nextPaperId();
               m.id = id;
               m.published = false;
               m.issue = 0;
               m.date = "";
               m.editor = "";
               m.editorUuid = "";

               try {
                  DocStore.savePaper(sp.server, id, Plat.pack(PaperCodec.write(m)));
               } catch (IOException var11) {
                  DocService.err(sp, L.k("Не удалось сохранить вёрстку на сервере."));
                  return;
               }

               if (!free) {
                  take(sp, Items.PAPER, 1);
               }

               DocStore.PaperRec nr = new DocStore.PaperRec();
               nr.ownerUuid = sp.getUUID().toString();
               nr.ownerName = sp.getGameProfile().getName();
               nr.name = m.name;
               nr.format = m.format;
               nr.pages = m.pageCount();
               nr.time = System.currentTimeMillis();
               store.papers.put(id, nr);
               store.setDirty();
               PaperCodec.Ref d = new PaperCodec.Ref();
               d.id = id;
               d.name = m.name;
               d.format = m.format;
               d.pages = m.pageCount();
               ItemStack out = new ItemStack((ItemLike)ModItems.NEWSPAPER.get());
               PaperCodec.writeRef(out, d);
               give(sp, out);
               DocService.ok(sp, L.k("Готов макет следующего номера «") + m.name + L.k("». Откройте его, чтобы сверстать."));
            }
         }
      }
   }

   public static DocService.VerifyResult verify(MinecraftServer server, ItemStack stack) {
      List<String> lines = new ArrayList<>();
      PaperCodec.Ref r = PaperCodec.ref(stack);
      if (!r.published) {
         lines.add(L.k("Макет, не опубликован: силы не имеет."));
         return new DocService.VerifyResult(DocService.Status.NOT_ISSUED, lines);
      } else {
         DocStore store = DocStore.get(server);
         DocStore.PaperRec rec = store.papers.get(r.id);
         if (rec == null || !rec.published) {
            lines.add(L.k("Выпуска нет в реестре этого сервера."));
            return new DocService.VerifyResult(DocService.Status.UNKNOWN, lines);
         } else if (!DocStore.safeEquals(store.hmac(r.canonical()), r.sig) || !rec.name.equals(r.name) || rec.issue != r.issue) {
            lines.add(L.k("Выходные данные не совпадают с реестром: ПОДДЕЛКА."));
            return new DocService.VerifyResult(DocService.Status.FORGED, lines);
         } else if (rec.revoked) {
            lines.add(L.k("Выпуск отозван администрацией."));
            return new DocService.VerifyResult(DocService.Status.REVOKED, lines);
         } else {
            lines.add(L.k(NewspaperItem.kind(r.format)) + " «" + r.name + "» № " + r.issue + L.k(" от ") + r.date);
            DocStore.TitleRec t = store.titles.get(DocStore.titleKey(r.name));
            if (t != null) {
               lines.add(L.k("Издатель: ") + t.ownerName);
            }

            if (!r.editor.isEmpty()) {
               lines.add(L.k("Выпускающий редактор: ") + r.editor);
            }

            lines.add(L.k("Подлинный экземпляр из реестра."));
            return new DocService.VerifyResult(DocService.Status.OK, lines);
         }
      }
   }

   public static String setRevoked(MinecraftServer server, String id, boolean revoked) {
      DocStore store = DocStore.get(server);
      DocStore.PaperRec rec = store.papers.get(id);
      if (rec == null || !rec.published) {
         return L.k("Выпуск не найден в реестре.");
      } else {
         rec.revoked = revoked;
         store.setDirty();
         return null;
      }
   }

   /** /seal paper staff add|remove: by the newspaper held in the main hand. */
   public static String staffEdit(ServerPlayer sp, String targetName, String targetUuid, boolean add) {
      ItemStack held = sp.getMainHandItem();
      if (!(held.getItem() instanceof NewspaperItem)) {
         return L.k("Возьмите в основную руку выпуск или макет этого издания.");
      } else {
         PaperCodec.Ref r = PaperCodec.ref(held);
         DocStore store = DocStore.get(sp.server);
         DocStore.TitleRec t = store.titles.get(DocStore.titleKey(r.name));
         if (t == null) {
            return L.k("Издание ещё не зарегистрировано: опубликуйте первый номер.");
         } else if (!t.ownerUuid.equals(sp.getUUID().toString()) && !admin(sp)) {
            return L.k("Управлять редакцией может только владелец издания: ") + t.ownerName;
         } else {
            if (add) {
               t.staff.put(targetUuid, targetName);
            } else {
               String found = null;

               for (java.util.Map.Entry<String, String> e : t.staff.entrySet()) {
                  if (e.getValue().equalsIgnoreCase(targetName)) {
                     found = e.getKey();
                  }
               }

               if (found == null) {
                  return targetName + L.k(" не состоит в редакции.");
               }

               t.staff.remove(found);
            }

            store.setDirty();
            return null;
         }
      }
   }

   public static List<String> describeTitles(MinecraftServer server) {
      List<String> out = new ArrayList<>();

      for (DocStore.TitleRec t : DocStore.get(server).titles.values()) {
         StringBuilder sb = new StringBuilder("«" + t.name + "» — " + t.ownerName + L.k(", номеров: ") + t.counter);
         if (!t.staff.isEmpty()) {
            sb.append(L.k(", редакция: ")).append(String.join(", ", t.staff.values()));
         }

         out.add(sb.toString());
      }

      return out;
   }

   public static Set<String> titleNames(MinecraftServer server) {
      return DocStore.get(server).titles.keySet();
   }
}
