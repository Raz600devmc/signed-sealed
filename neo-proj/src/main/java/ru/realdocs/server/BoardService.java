package ru.realdocs.server;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.entity.NoticeBoardEntity;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;

/** Notice boards: pinning, taking, reading and settings. */
public final class BoardService {
   private BoardService() {
   }

   static boolean pinnable(ItemStack s) {
      return s.getItem() instanceof DocumentItem
         || s.getItem() instanceof NewspaperItem && PaperCodec.ref(s).published
         || s.getItem() instanceof EnvelopeItem
         || s.getItem() instanceof PassportItem;
   }

   /** Right click on a cell: pin what is in the hand, or open the board. Sneak with an empty hand opens settings. */
   public static void interact(ServerPlayer sp, NoticeBoardEntity b, int slot) {
      ItemStack held = sp.getMainHandItem();
      if (!held.isEmpty() && pinnable(held)) {
         if (!b.canPin(sp)) {
            sp.displayClientMessage(L.c(L.k("Вешать объявления на эту доску могут только допущенные. Владелец: ") + b.ownerName).withStyle(ChatFormatting.RED), true);
            return;
         }

         int target = -1;
         if (b.item(slot).isEmpty() && !(slot == 0 && !b.feed.isEmpty())) {
            target = slot;
         } else {
            for (int i = 0; i < NoticeBoardEntity.SLOTS; i++) {
               if (b.item(i).isEmpty() && !(i == 0 && !b.feed.isEmpty())) {
                  target = i;
                  break;
               }
            }
         }

         if (target < 0) {
            sp.displayClientMessage(L.c(L.k("На доске нет свободного места.")).withStyle(ChatFormatting.RED), true);
            return;
         }

         b.setItem(target, held.split(1));
         b.pinnedBy[target] = sp.getUUID().toString();
         sp.level().playSound(null, b.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
         sp.displayClientMessage(L.c(L.k("Приколото к доске.")).withStyle(ChatFormatting.GREEN), true);
      } else if (held.isEmpty()) {
         send(sp, b, slot);
      } else {
         sp.displayClientMessage(L.c(L.k("На доску можно приколоть документ, газету, письмо или паспорт.")).withStyle(ChatFormatting.YELLOW), true);
      }
   }

   static void send(ServerPlayer sp, NoticeBoardEntity b, int focus) {
      CompoundTag t = new CompoundTag();
      t.putInt("Entity", b.getId());
      t.putInt("Focus", focus);
      t.putString("Label", b.label());
      t.putString("Owner", b.ownerName);
      t.putString("Members", String.join(", ", b.members));
      t.putBoolean("Locked", b.locked);
      t.putString("Feed", b.feed);
      t.putBoolean("Manage", b.isOwner(sp));
      t.putBoolean("CanPin", b.canPin(sp));
      ListTag l = new ListTag();

      for (int i = 0; i < NoticeBoardEntity.SLOTS; i++) {
         CompoundTag c = new CompoundTag();
         ItemStack s = b.item(i);
         if (!s.isEmpty()) {
            c.put("Item", Plat.saveStack(s, sp.level()));
            c.putString("Title", InboxService.title(s));
         }

         c.putBoolean("Take", !s.isEmpty() && b.canTake(sp, i) && !(i == 0 && !b.feed.isEmpty()));
         l.add(c);
      }

      t.put("Slots", l);
      BureauService.send(sp, "board", t);
   }

   static NoticeBoardEntity board(ServerPlayer sp, CompoundTag t) {
      Entity e = sp.level().getEntity(t.getInt("Entity"));
      return e instanceof NoticeBoardEntity b && sp.distanceToSqr(b) <= 100.0 ? b : null;
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      NoticeBoardEntity b = board(sp, t);
      if (b != null) {
         int slot = Math.max(0, Math.min(NoticeBoardEntity.SLOTS - 1, t.getInt("Slot")));
         switch (action) {
            case "board_view": {
               ItemStack s = b.item(slot);
               if (!s.isEmpty()) {
                  ArchiveService.view(sp, s, L.k("доска объявлений") + (b.label().isEmpty() ? "" : " «" + b.label() + "»"));
               }
               break;
            }
            case "board_take": {
               ItemStack s = b.item(slot);
               if (!s.isEmpty() && b.canTake(sp, slot) && !(slot == 0 && !b.feed.isEmpty())) {
                  b.setItem(slot, ItemStack.EMPTY);
                  b.pinnedBy[slot] = "";
                  PaperService.give(sp, s.copy());
                  sp.level().playSound(null, b.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.8F, 0.9F);
                  send(sp, b, slot);
               }
               break;
            }
            case "board_save":
               if (b.isOwner(sp)) {
                  b.setLabel(DocService.clean(t.getString("Label"), 40, false));
                  b.setMembers(t.getString("Members"));
                  b.locked = t.getBoolean("Locked");
                  String feed = DocService.clean(t.getString("Feed"), 40, false);
                  if (!feed.isEmpty() && DocStore.get(sp.server).titles.get(DocStore.titleKey(feed)) == null) {
                     DocService.err(sp, L.k("Газета «") + feed + L.k("» не найдена."));
                     return;
                  }

                  if (!feed.isEmpty() && b.feed.isEmpty() && !b.item(0).isEmpty()) {
                     PaperService.give(sp, b.item(0).copy());
                     b.setItem(0, ItemStack.EMPTY);
                  }

                  if (feed.isEmpty() && !b.feed.isEmpty()) {
                     b.setItem(0, ItemStack.EMPTY);
                  }

                  b.feed = feed;
                  refreshFeed(b);
                  DocService.ok(sp, L.k("Доска сохранена."));
                  send(sp, b, 0);
               }
         }
      }
   }

   /** Keeps the newest published issue of the board's newspaper in the first slot. */
   public static void refreshFeed(NoticeBoardEntity b) {
      if (!b.feed.isEmpty() && b.level().getServer() != null) {
         DocStore store = DocStore.get(b.level().getServer());
         String key = DocStore.titleKey(b.feed);
         String bestId = null;
         DocStore.PaperRec best = null;

         for (java.util.Map.Entry<String, DocStore.PaperRec> e : store.papers.entrySet()) {
            DocStore.PaperRec r = e.getValue();
            if (r.published && !r.revoked && DocStore.titleKey(r.name).equals(key) && (best == null || r.time > best.time)) {
               best = r;
               bestId = e.getKey();
            }
         }

         if (best != null && !bestId.equals(PaperCodec.ref(b.item(0)).id)) {
            ItemStack s = new ItemStack(ModItems.NEWSPAPER.get());
            PaperCodec.Ref ref = new PaperCodec.Ref();
            ref.id = bestId;
            ref.name = best.name;
            ref.issue = best.issue;
            ref.date = best.date;
            ref.format = best.format;
            ref.pages = best.pages;
            ref.published = true;
            ref.editor = best.ownerName;
            ref.sig = store.hmac(ref.canonical());
            PaperCodec.writeRef(s, ref);
            b.setItem(0, s);
            b.pinnedBy[0] = "";
         }
      }
   }
}
