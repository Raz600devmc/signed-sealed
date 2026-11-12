package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.block.TerminalBlock;
import ru.realdocs.blockentity.TerminalBE;
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
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;

/** Digital archive: scanners put copies of documents into a named archive, computers search them. */
public final class DigitalService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
   public static final int SEARCH = 0;
   public static final int VIEW = 1;
   public static final int PRINT = 2;
   public static final int DELETE = 3;
   public static final int MAX_RECORDS = 5000;
   public static final int MAX_RESULTS = 100;
   public static final String[] STATUS = new String[]{"не проверен", "подлинный", "недействителен", "подделка"};

   private DigitalService() {
   }

   static TerminalBE be(ServerPlayer sp, BlockPos pos) {
      if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
         return null;
      } else {
         return sp.level().getBlockEntity(pos) instanceof TerminalBE t ? t : null;
      }
   }

   /** Network of the computer named in a request (X, Y, Z), if the player may use it. */
   public static String networkAt(ServerPlayer sp, CompoundTag t) {
      if (!t.contains("X")) {
         return null;
      } else {
         TerminalBE b = be(sp, new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")));
         return b != null && b.canUse(sp) ? b.net() : null;
      }
   }

   static String key(String net) {
      return net.strip().toLowerCase(Locale.ROOT);
   }

   static List<CompoundTag> records(DocStore store, String net) {
      return store.digital.computeIfAbsent(key(net), k -> new ArrayList<>());
   }

   private static boolean config(ServerPlayer sp, BlockPos pos, TerminalBE t) {
      if (sp.getMainHandItem().isEmpty() && sp.isSecondaryUseActive()) {
         if (t.isOwner(sp)) {
            Net.sendTo(sp, new ArchivePackets.OpenConfig(new ArchivePackets.BlockConfig(pos, 1, t.label, t.network, t.membersText(), 0, 0, t.locked, false)));
         } else {
            DocService.err(sp, L.k("Настраивать может только владелец: ") + t.ownerName);
         }

         return true;
      } else {
         return false;
      }
   }

   // ------------------------------------------------------------------ scanner

   public static void useScanner(ServerPlayer sp, BlockPos pos) {
      TerminalBE t = be(sp, pos);
      if (t != null && !config(sp, pos, t)) {
         if (!t.canUse(sp)) {
            DocService.err(sp, L.k("Доступ закрыт. Владелец: ") + t.ownerName);
         } else if (!Perms.has(sp, Perms.ARCHIVE)) {
            DocService.err(sp, L.k("У вас нет права пользоваться архивом."));
         } else {
            ItemStack held = sp.getMainHandItem();
            List<ItemStack> batch = new ArrayList<>();
            if (held.getItem() instanceof FolderItem) {
               batch.addAll(MailCodec.items(held, MailCodec.FOLDER_TAG, sp.level()));
            } else if (!held.isEmpty()) {
               batch.add(held);
            }

            if (batch.isEmpty()) {
               DocService.msg(
                  sp, L.k("Сканер: положите документ, паспорт, газету, вскрытое письмо или папку в основную руку. Архив: ") + t.net(), net.minecraft.ChatFormatting.GRAY
               );
            } else {
               DocStore store = DocStore.get(sp.server);
               List<CompoundTag> recs = records(store, t.net());
               int done = 0;
               String lastId = "";
               String skipped = "";

               for (ItemStack s : batch) {
                  String why = scannable(s);
                  if (why != null) {
                     skipped = why;
                  } else if (recs.size() >= MAX_RECORDS) {
                     skipped = L.k("Архив переполнен.");
                  } else {
                     CompoundTag rec = record(sp, s, t.net());
                     recs.add(rec);
                     lastId = rec.getString("Id");
                     done++;
                  }
               }

               if (done > 0) {
                  store.setDirty();
                  sp.level().playSound(null, pos, (SoundEvent)ModSounds.PRINT.get(), SoundSource.BLOCKS, 0.6F, 1.5F);
                  DocService.ok(
                     sp, done == 1 ? L.k("Оцифровано в архив «") + t.net() + "»: " + lastId : L.k("Оцифровано документов: ") + done + L.k(" в архив «") + t.net() + "»"
                  );
                  LOG.info("[Signed & Sealed] {} оцифровал {} док. в архив {}", new Object[]{sp.getGameProfile().getName(), done, t.net()});
               }

               if (!skipped.isEmpty() && done < batch.size()) {
                  DocService.err(sp, skipped);
               }
            }
         }
      }
   }

   /** null if the item can be scanned, otherwise the reason. */
   static String scannable(ItemStack s) {
      if (s.getItem() instanceof DocumentItem || s.getItem() instanceof PassportItem) {
         return null;
      } else if (s.getItem() instanceof NewspaperItem) {
         return PaperCodec.ref(s).published ? null : L.k("Неопубликованный макет не сканируется.");
      } else if (s.getItem() instanceof EnvelopeItem) {
         MailModel m = MailCodec.readMail(s);
         return m.sealed() ? L.k("Запечатанное письмо сначала нужно вскрыть.") : null;
      } else {
         return L.k("Это нельзя отсканировать.");
      }
   }

   static CompoundTag record(ServerPlayer sp, ItemStack s, String net) {
      Describe.Meta m = Describe.of(sp.server, s);
      int status = 0;
      DocService.VerifyResult vr = null;
      if (s.getItem() instanceof DocumentItem) {
         vr = DocService.verify(sp.server, s);
      } else if (s.getItem() instanceof PassportItem) {
         vr = PassportService.verify(sp.server, s);
      } else if (s.getItem() instanceof NewspaperItem) {
         vr = PaperService.verify(sp.server, s);
      }

      if (vr != null) {
         status = switch (vr.status()) {
            case OK -> 1;
            case FORGED -> 3;
            default -> 2;
         };
      }

      CompoundTag r = new CompoundTag();
      r.putString("Id", newId(records(DocStore.get(sp.server), net)));
      r.putInt("Type", m.type);
      r.putString("Title", m.title);
      r.putString("Author", m.author);
      r.putString("Holder", m.holder);
      r.putString("Date", m.date);
      r.putString("Text", m.text.length() > 6000 ? m.text.substring(0, 6000) : m.text);
      r.putString("ScanDate", LocalDateTime.now().format(DATE));
      r.putString("By", sp.getGameProfile().getName());
      r.putInt("Status", status);
      r.putString("Net", net);
      r.put("Item", Plat.saveStack(s.copyWithCount(1), sp.level()));
      return r;
   }

   /** A record number not used yet in this archive (VIEW and DELETE find records by it). */
   static String newId(List<CompoundTag> recs) {
      java.util.Set<String> used = new java.util.HashSet<>();

      for (CompoundTag r : recs) {
         used.add(r.getString("Id"));
      }

      String id;
      do {
         id = "DA-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0x1000000)).toUpperCase(Locale.ROOT);
      } while (used.contains(id));

      return id;
   }

   // ------------------------------------------------------------------ computer

   public static void openComputer(ServerPlayer sp, BlockPos pos) {
      TerminalBE t = be(sp, pos);
      if (t != null && !config(sp, pos, t)) {
         if (!t.canUse(sp)) {
            DocService.err(sp, L.k("Доступ закрыт. Владелец: ") + t.ownerName);
         } else {
            sendResults(sp, t, "");
         }
      }
   }

   static boolean matches(CompoundTag r, String[] words) {
      String hay = (r.getString("Id") + " " + r.getString("Title") + " " + r.getString("Author") + " " + r.getString("Holder") + " " + r.getString("Date") + " "
            + r.getString("By") + " " + r.getString("Text"))
         .toLowerCase(Locale.ROOT);

      for (String w : words) {
         if (!w.isEmpty() && !hay.contains(w)) {
            return false;
         }
      }

      return true;
   }

   static void sendResults(ServerPlayer sp, TerminalBE t, String query) {
      DocStore store = DocStore.get(sp.server);
      List<CompoundTag> recs = records(store, t.net());
      String[] words = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
      List<ArchivePackets.Entry> out = new ArrayList<>();

      for (int i = recs.size() - 1; i >= 0 && out.size() < MAX_RESULTS; i--) {
         CompoundTag r = recs.get(i);
         if (matches(r, words)) {
            String meta = r.getString("Author") + "\u0001" + r.getString("Holder") + "\u0001" + r.getString("Date") + "\u0001" + r.getString("ScanDate") + "\u0001"
               + r.getString("By") + "\u0001" + r.getInt("Status");
            out.add(new ArchivePackets.Entry(r.getString("Id"), r.getInt("Type"), r.getString("Title"), meta));
         }
      }

      boolean printer = sp.level().getBlockState(t.getBlockPos()).getBlock() instanceof TerminalBlock tb && !tb.scanner;
      Net.sendTo(sp, new ArchivePackets.ComputerData(t.getBlockPos(), t.net(), query, recs.size(), t.isOwner(sp), printer, out));
   }

   static CompoundTag find(List<CompoundTag> recs, String id) {
      for (CompoundTag r : recs) {
         if (r.getString("Id").equals(id)) {
            return r;
         }
      }

      return null;
   }

   public static void computer(ServerPlayer sp, BlockPos pos, int action, String text) {
      TerminalBE t = be(sp, pos);
      if (t != null && t.canUse(sp)) {
         DocStore store = DocStore.get(sp.server);
         List<CompoundTag> recs = records(store, t.net());
         switch (action) {
            case SEARCH:
               sendResults(sp, t, text);
               return;
            case VIEW: {
               CompoundTag r = find(recs, text);
               if (r != null) {
                  ItemStack s = Plat.loadStack(r.getCompound("Item"), sp.level());
                  if (!s.isEmpty()) {
                     ArchiveService.view(sp, s, L.k("архив «") + t.net() + "»");
                  }
               }

               return;
            }
            case PRINT: {
               CompoundTag r = find(recs, text);
               if (r != null) {
                  print(sp, pos, Plat.loadStack(r.getCompound("Item"), sp.level()));
               }

               return;
            }
            case DELETE: {
               if (!t.isOwner(sp)) {
                  DocService.err(sp, L.k("Удалять записи может только владелец: ") + t.ownerName);
                  return;
               }

               Iterator<CompoundTag> it = recs.iterator();

               while (it.hasNext()) {
                  if (it.next().getString("Id").equals(text)) {
                     it.remove();
                     store.setDirty();
                     DocService.ok(sp, L.k("Запись удалена: ") + text);
                  }
               }

               sendResults(sp, t, "");
            }
         }
      }
   }

   /** Paper copy from the archive. Documents only (marked as a copy); passports and newspapers are never duplicated. */
   static void print(ServerPlayer sp, BlockPos pos, ItemStack s) {
      if (!(s.getItem() instanceof DocumentItem)) {
         DocService.err(sp, L.k("Распечатать копию можно только документ. Паспорта и газеты не копируются."));
      } else if (!Perms.has(sp, Perms.PRINT)) {
         DocService.err(sp, L.k("У вас нет права пользоваться принтером."));
      } else {
         boolean free = sp.getAbilities().instabuild;
         if (!free && PaperService.count(sp, Items.PAPER) < 1) {
            DocService.err(sp, L.k("Нужен лист бумаги в инвентаре."));
         } else {
            if (!free) {
               PaperService.take(sp, Items.PAPER, 1);
            }

            DocModel d = NbtCodec.readDoc(s);
            d.copy = true;
            ItemStack out = new ItemStack((ItemLike)ModItems.DOCUMENT.get());
            NbtCodec.writeDoc(out, d);
            PaperService.give(sp, out);
            sp.level().playSound(null, pos, (SoundEvent)ModSounds.PRINT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            DocService.ok(sp, L.k("Копия напечатана. На ней пометка «КОПИЯ»: оригиналом она не является."));
         }
      }
   }
}
