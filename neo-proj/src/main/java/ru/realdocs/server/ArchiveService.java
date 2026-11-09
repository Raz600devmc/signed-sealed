package ru.realdocs.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.ModSounds;
import ru.realdocs.Plat;
import ru.realdocs.blockentity.ArchiveBE;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

/** Archive shelves and filing cabinets. */
public final class ArchiveService {
   public static final int PUT = 0;
   public static final int PUT_ALL = 1;
   public static final int TAKE = 2;
   public static final int VIEW = 3;
   public static final int SET = 4;
   public static final int REFRESH = 5;

   private ArchiveService() {
   }

   static ArchiveBE be(ServerPlayer sp, BlockPos pos) {
      if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
         return null;
      } else {
         return sp.level().getBlockEntity(pos) instanceof ArchiveBE a ? a : null;
      }
   }

   public static void open(ServerPlayer sp, BlockPos pos) {
      ArchiveBE a = be(sp, pos);
      if (a != null) {
         if (!a.canUse(sp)) {
            DocService.err(sp, L.k("Заперто. Владелец: ") + a.ownerName);
            sp.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
         } else {
            send(sp, a);
            sp.level().playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6F, 1.2F);
         }
      }
   }

   static void send(ServerPlayer sp, ArchiveBE a) {
      List<ArchivePackets.Entry> es = new ArrayList<>();

      for (int i = 0; i < a.items.size(); i++) {
         ItemStack s = a.items.get(i);
         Describe.Meta m = Describe.of(null, s);
         int n = s.getItem() instanceof FolderItem ? MailCodec.count(s, MailCodec.FOLDER_TAG) : 0;
         es.add(new ArchivePackets.Entry(String.valueOf(i), m.type, s.getHoverName().getString(), Describe.line(m, n)));
      }

      Net.sendTo(sp, new ArchivePackets.ArchiveData(a.getBlockPos(), a.capacity(), a.label, a.ownerName, a.locked, a.isOwner(sp), a.membersText(), es));
   }

   public static void action(ServerPlayer sp, BlockPos pos, int action, int index, String label, String members, boolean locked) {
      ArchiveBE a = be(sp, pos);
      if (a != null && a.canUse(sp)) {
         switch (action) {
            case PUT: {
               ItemStack held = sp.getMainHandItem();
               if (!ArchiveBE.fits(held)) {
                  DocService.err(sp, L.k("В основной руке нужен документ, паспорт, газета, письмо или папка."));
               } else if (a.items.size() >= a.capacity()) {
                  DocService.err(sp, L.k("Места нет."));
               } else {
                  a.items.add(held.split(1));
                  sound(sp, pos);
               }
               break;
            }
            case PUT_ALL: {
               Inventory inv = sp.getInventory();
               int moved = 0;

               for (int i = 0; i < inv.getContainerSize() && a.items.size() < a.capacity(); i++) {
                  ItemStack st = inv.getItem(i);

                  while (ArchiveBE.fits(st) && !st.isEmpty() && a.items.size() < a.capacity()) {
                     a.items.add(st.split(1));
                     moved++;
                  }
               }

               if (moved == 0) {
                  DocService.err(sp, a.items.size() >= a.capacity() ? L.k("Места нет.") : L.k("В инвентаре нет документов."));
               } else {
                  sound(sp, pos);
               }
               break;
            }
            case TAKE:
               if (index >= 0 && index < a.items.size()) {
                  PaperService.give(sp, a.items.remove(index));
                  sound(sp, pos);
               }
               break;
            case VIEW:
               if (index >= 0 && index < a.items.size()) {
                  view(sp, a.items.get(index), a.label.isEmpty() ? L.k("архив") : "«" + a.label + "»");
               }

               return;
            case SET:
               if (!a.isOwner(sp)) {
                  DocService.err(sp, L.k("Настраивать может только владелец: ") + a.ownerName);
               } else {
                  a.label = DocService.clean(label, 40, false);
                  a.setMembers(members);
                  a.locked = locked;
                  DocService.ok(sp, L.k("Сохранено."));
               }
         }

         a.sync();
         send(sp, a);
      }
   }

   private static void sound(ServerPlayer sp, BlockPos pos) {
      sp.level().playSound(null, pos, (SoundEvent)ModSounds.PAGE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
   }

   /** Opens a stored item read-only: documents and passports with a check, the rest as they are. */
   public static void view(ServerPlayer sp, ItemStack s, String from) {
      if (s.getItem() instanceof DocumentItem) {
         DocService.VerifyResult vr = DocService.verify(sp.server, s);
         CompoundTag tag = NbtCodec.writeDoc(NbtCodec.readDoc(s));
         Net.sendTo(sp, new Packets.ViewRemote(false, tag, vr.status().ordinal(), vr.lines(), from));
      } else if (s.getItem() instanceof PassportItem) {
         DocService.VerifyResult vr = PassportService.verify(sp.server, s);
         CompoundTag tag = NbtCodec.writePass(NbtCodec.readPass(s));
         PassportService.grantAsset(sp, NbtCodec.readPass(s).photo);
         Net.sendTo(sp, new Packets.ViewRemote(true, tag, vr.status().ordinal(), vr.lines(), from));
      } else if (s.getItem() instanceof NewspaperItem || s.getItem() instanceof EnvelopeItem || s.getItem() instanceof FolderItem) {
         Net.sendTo(sp, new ArchivePackets.ViewItem(Plat.saveStack(s, sp.level())));
      }
   }
}
