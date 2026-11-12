package ru.realdocs.server;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.L;
import ru.realdocs.block.ReaderBlock;
import ru.realdocs.blockentity.ReaderBE;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.Tags;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;

/** Document readers: check what the player holds against the reader's settings and give a redstone pulse. */
public final class ReaderService {
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM HH:mm");
   private static final DateTimeFormatter PUNCH = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

   private ReaderService() {
   }

   static ReaderBE be(ServerPlayer sp, BlockPos pos) {
      return sp.level().getBlockEntity(pos) instanceof ReaderBE r
            && sp.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0
         ? r
         : null;
   }

   public static void use(ServerPlayer sp, BlockPos pos) {
      ReaderBE r = be(sp, pos);
      if (r == null) {
         return;
      }

      ItemStack held = sp.getMainHandItem();
      if (held.isEmpty() && sp.isSecondaryUseActive()) {
         if (r.isOwner(sp)) {
            sendConfig(sp, r);
         } else {
            deny(sp, pos, r, L.k("Настраивать считыватель может только владелец: ") + r.ownerName, false);
         }

         return;
      }

      if (!r.canUse(sp)) {
         deny(sp, pos, r, L.k("Заперто. Владелец: ") + r.ownerName, false);
         return;
      }

      if (held.isEmpty()) {
         sp.displayClientMessage(L.c(L.k("Приложите документ к считывателю.")).withStyle(ChatFormatting.YELLOW), true);
         return;
      }

      String me = sp.getGameProfile().getName();
      String why;
      String what;
      if (held.getItem() instanceof PassportItem) {
         PassportModel p = NbtCodec.readPass(held);
         int kind = p.format == PassportTemplate.FORMAT_BOOK ? ReaderBE.K_PASSPORT : (p.format == PassportTemplate.FORMAT_CARD ? ReaderBE.K_CARD : (p.format == PassportTemplate.FORMAT_TICKET ? ReaderBE.K_TICKET : 0));
         what = p.holderName.isEmpty() ? L.k("документ") : p.holderName;
         why = checkPassport(sp, r, held, p, kind);
      } else if (held.getItem() instanceof DocumentItem) {
         DocModel d = NbtCodec.readDoc(held);
         what = L.k(d.templateName()) + (d.regNo.isEmpty() ? "" : " " + d.regNo);
         why = checkDocument(sp, r, held, d);
      } else {
         deny(sp, pos, r, L.k("Считыватель принимает паспорта, удостоверения, билеты и документы."), false);
         return;
      }

      if (why != null) {
         deny(sp, pos, r, why, true);
         r.addLog(LocalDateTime.now().format(STAMP) + " ✘ " + me + " · " + what + " · " + why);
         return;
      }

      String where = r.label.isEmpty() ? L.k("считыватель") : r.label;
      if (r.mode == ReaderBE.MODE_PUNCH) {
         CompoundTag punch = Tags.element(held, GateService.PUNCH_TAG);
         if (punch != null) {
            String msg = L.k("Документ уже использован: ") + punch.getString("Gate") + ", " + punch.getString("Date");
            deny(sp, pos, r, msg, true);
            r.addLog(LocalDateTime.now().format(STAMP) + " ✘ " + me + " · " + what + " · " + L.k("уже использован"));
            return;
         }

         String now = LocalDateTime.now().format(PUNCH);
         Tags.update(held, t -> {
            CompoundTag c = new CompoundTag();
            c.putString("Gate", where);
            c.putString("Date", now);
            t.put(GateService.PUNCH_TAG, c);
         });
      } else if (r.mode == ReaderBE.MODE_TAKE && !sp.getAbilities().instabuild) {
         held.shrink(1);
      }

      BlockState state = sp.level().getBlockState(pos);
      if (state.getBlock() instanceof ReaderBlock) {
         ReaderBlock.show((ServerLevel)sp.level(), pos, state, 1, r.pulse * 20);
      }

      r.passes++;
      r.addLog(LocalDateTime.now().format(STAMP) + " ✔ " + me + " · " + what);
      sp.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.8F);
      sp.displayClientMessage(L.c(L.k("Доступ разрешён") + (r.label.isEmpty() ? "" : " · " + r.label)).withStyle(ChatFormatting.GREEN), true);
   }

   static boolean matchesFilter(ReaderBE r, String hay) {
      if (r.filter.isBlank()) {
         return true;
      } else {
         String h = hay.toLowerCase(Locale.ROOT);

         for (String f : r.filter.toLowerCase(Locale.ROOT).split(",")) {
            if (!f.isBlank() && h.contains(f.strip())) {
               return true;
            }
         }

         return false;
      }
   }

   static String checkPassport(ServerPlayer sp, ReaderBE r, ItemStack held, PassportModel p, int kind) {
      if (kind == 0 || (r.kinds & kind) == 0) {
         return L.k("Этот вид документа здесь не принимается.");
      } else {
         DocService.VerifyResult vr = PassportService.verify(sp.server, held);
         if (vr.status() != DocService.Status.OK) {
            return L.k("Документ недействителен") + (vr.lines().isEmpty() ? "." : ": " + vr.lines().get(0));
         } else {
            LocalDate exp = DocModel.parseDate(p.values.getOrDefault("expiry", ""));
            if (exp != null && LocalDate.now().isAfter(exp)) {
               return L.k("Срок действия истёк ") + DocModel.formatDate(exp) + ".";
            } else if (r.holderOnly && kind != ReaderBE.K_TICKET && !p.holderUuid.isEmpty() && !p.holderUuid.equals(sp.getUUID().toString())) {
               return L.k("Документ выдан другому человеку.");
            } else if (!r.zone.isBlank() && kind != ReaderBE.K_TICKET) {
               return L.k("Нужен пропуск в зону «") + r.zone + "».";
            } else {
               PassportTemplate t = DocStore.get(sp.server).templates.get(p.templateId);
               String hay = (t == null ? "" : t.name + " " + t.org + " " + t.title + " " + t.id) + " " + p.holderName;
               return matchesFilter(r, hay) ? null : L.k("Документ не подходит для этого считывателя.");
            }
         }
      }
   }

   static String checkDocument(ServerPlayer sp, ReaderBE r, ItemStack held, DocModel d) {
      if ((r.kinds & ReaderBE.K_DOCUMENT) == 0) {
         return L.k("Этот вид документа здесь не принимается.");
      } else if (!d.issued) {
         return L.k("Черновик — документ не выпущен.");
      } else if (d.copy) {
         return L.k("Копия не подходит — нужен оригинал.");
      } else {
         DocService.VerifyResult vr = DocService.verify(sp.server, held);
         if (vr.status() != DocService.Status.OK) {
            return L.k("Документ недействителен") + (vr.lines().isEmpty() ? "." : ": " + vr.lines().get(0));
         } else if (r.holderOnly && !d.holder.isEmpty() && !d.holder.equalsIgnoreCase(sp.getGameProfile().getName())) {
            return L.k("Документ выдан другому человеку.");
         } else {
            if (!r.zone.isBlank()) {
               boolean ok = false;

               for (String z : d.zoneList()) {
                  if (z.equalsIgnoreCase(r.zone.trim())) {
                     ok = true;
                  }
               }

               if (!ok) {
                  return L.k("Нет допуска в зону «") + r.zone + "».";
               }
            }

            String hay = d.org + " " + d.templateName() + " " + d.regNo + " " + d.holder + " " + d.subject + " " + d.number;
            return matchesFilter(r, hay) ? null : L.k("Документ не подходит для этого считывателя.");
         }
      }
   }

   static void deny(ServerPlayer sp, BlockPos pos, ReaderBE r, String why, boolean flash) {
      sp.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.9F, 0.6F);
      sp.displayClientMessage(L.c(why).withStyle(ChatFormatting.RED), true);
      if (flash) {
         BlockState state = sp.level().getBlockState(pos);
         if (state.getBlock() instanceof ReaderBlock && state.getValue(ReaderBlock.RESULT) != 1) {
            ReaderBlock.show((ServerLevel)sp.level(), pos, state, 2, 20);
         }
      }
   }

   static void sendConfig(ServerPlayer sp, ReaderBE r) {
      CompoundTag t = new CompoundTag();
      BlockPos p = r.getBlockPos();
      t.putInt("X", p.getX());
      t.putInt("Y", p.getY());
      t.putInt("Z", p.getZ());
      t.putString("Label", r.label);
      t.putString("Filter", r.filter);
      t.putString("Zone", r.zone);
      t.putString("Members", r.membersText());
      t.putInt("Kinds", r.kinds);
      t.putInt("Mode", r.mode);
      t.putInt("Pulse", r.pulse);
      t.putBoolean("Holder", r.holderOnly);
      t.putBoolean("Locked", r.locked);
      t.putInt("Passes", r.passes);
      t.putString("Owner", r.ownerName);
      ListTag l = new ListTag();

      for (int i = r.log.size() - 1; i >= 0; i--) {
         l.add(StringTag.valueOf(r.log.get(i)));
      }

      t.put("Log", l);
      BureauService.send(sp, "reader_cfg", t);
   }

   static void save(ServerPlayer sp, CompoundTag t) {
      ReaderBE r = be(sp, new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z")));
      if (r != null && r.isOwner(sp)) {
         r.label = DocService.clean(t.getString("Label"), 40, false);
         r.filter = DocService.clean(t.getString("Filter"), 120, false);
         r.zone = DocService.clean(t.getString("Zone"), 32, false);
         r.setMembers(t.getString("Members"));
         r.kinds = t.getInt("Kinds") & 15;
         r.mode = Math.max(0, Math.min(2, t.getInt("Mode")));
         r.pulse = Math.max(1, Math.min(30, t.getInt("Pulse")));
         r.holderOnly = t.getBoolean("Holder");
         r.locked = t.getBoolean("Locked");
         if (t.getBoolean("ClearLog")) {
            r.log.clear();
         }

         r.setChanged();
         r.sync();
         DocService.ok(sp, L.k("Настройки считывателя сохранены."));
      }
   }
}
