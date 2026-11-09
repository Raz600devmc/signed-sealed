package ru.realdocs.server;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.L;
import ru.realdocs.block.TicketGateBlock;
import ru.realdocs.blockentity.GateBE;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.Tags;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;

/** Ticket gates: check the ticket, punch or take it, give a redstone pulse. */
public final class GateService {
   public static final String PUNCH_TAG = "RealPunch";
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

   private GateService() {
   }

   public static void use(ServerPlayer sp, BlockPos pos) {
      if (!(sp.level().getBlockEntity(pos) instanceof GateBE g)) {
         return;
      }

      ItemStack held = sp.getMainHandItem();
      if (held.isEmpty() && sp.isSecondaryUseActive()) {
         if (g.isOwner(sp)) {
            Net.sendTo(sp, new ArchivePackets.OpenConfig(new ArchivePackets.BlockConfig(pos, 0, g.label, g.filter, g.membersText(), g.mode, g.pulse, g.locked, false)));
         } else {
            deny(sp, pos, L.k("Настраивать турникет может только владелец: ") + g.ownerName);
         }

         return;
      }

      if (!g.canUse(sp)) {
         deny(sp, pos, L.k("Заперто. Владелец: ") + g.ownerName);
         return;
      }

      if (!(held.getItem() instanceof PassportItem)) {
         sp.displayClientMessage(L.c(L.k("Приложите билет к валидатору.")).withStyle(ChatFormatting.YELLOW), true);
         return;
      }

      PassportModel p = NbtCodec.readPass(held);
      if (p.format != PassportTemplate.FORMAT_TICKET) {
         deny(sp, pos, L.k("Это не билет."));
         return;
      }

      DocService.VerifyResult vr = PassportService.verify(sp.server, held);
      if (vr.status() != DocService.Status.OK) {
         deny(sp, pos, L.k("Билет недействителен") + (vr.lines().isEmpty() ? "." : ": " + vr.lines().get(0)));
         return;
      }

      if (!g.filter.isBlank()) {
         PassportTemplate t = DocStore.get(sp.server).templates.get(p.templateId);
         String hay = t == null ? "" : (t.name + " " + t.org + " " + t.title + " " + t.id).toLowerCase(Locale.ROOT);
         boolean ok = false;

         for (String f : g.filter.toLowerCase(Locale.ROOT).split(",")) {
            if (!f.isBlank() && hay.contains(f.strip())) {
               ok = true;
            }
         }

         if (!ok) {
            deny(sp, pos, L.k("Этот билет не подходит для прохода здесь."));
            return;
         }
      }

      String where = g.label.isEmpty() ? L.k("турникет") : g.label;
      String now = java.time.LocalDateTime.now().format(DATE);
      if (g.mode == GateBE.MODE_PUNCH) {
         CompoundTag punch = Tags.element(held, PUNCH_TAG);
         if (punch != null) {
            deny(sp, pos, L.k("Билет уже использован: ") + punch.getString("Gate") + ", " + punch.getString("Date"));
            return;
         }

         Tags.update(held, t -> {
            CompoundTag c = new CompoundTag();
            c.putString("Gate", where);
            c.putString("Date", now);
            t.put(PUNCH_TAG, c);
         });
      } else if (g.mode == GateBE.MODE_TAKE && !sp.getAbilities().instabuild) {
         held.shrink(1);
      }

      BlockState state = sp.level().getBlockState(pos);
      if (state.getBlock() instanceof TicketGateBlock) {
         TicketGateBlock.pulse((ServerLevel)sp.level(), pos, state, g.pulse);
      }

      g.passes++;
      g.setChanged();
      sp.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.6F);
      sp.displayClientMessage(L.c(L.k("Проход разрешён") + (g.label.isEmpty() ? "" : " · " + g.label)).withStyle(ChatFormatting.GREEN), true);
   }

   private static void deny(ServerPlayer sp, BlockPos pos, String why) {
      sp.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.9F, 0.6F);
      sp.displayClientMessage(L.c(why).withStyle(ChatFormatting.RED), true);
   }

}
