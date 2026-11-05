package ru.realdocs.server;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.ModSounds;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.FolderModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

/** Folders keep up to 16 documents, passports, newspapers and letters together. */
public final class FolderService {
   public static final int PUT = 0;
   public static final int PUT_ALL = 1;
   public static final int TAKE = 2;
   public static final int VIEW = 3;
   public static final int SET = 4;

   private FolderService() {
   }

   public static boolean fits(ItemStack s) {
      return MailService.attachable(s) || s.getItem() instanceof EnvelopeItem;
   }

   public static void action(ServerPlayer sp, InteractionHand hand, int action, int index, String label, int color) {
      ItemStack folder = sp.getItemInHand(hand);
      if (folder.getItem() instanceof FolderItem) {
         List<ItemStack> items = MailCodec.items(folder, MailCodec.FOLDER_TAG, sp.level());
         switch (action) {
            case PUT: {
               InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
               ItemStack other = sp.getItemInHand(otherHand);
               if (!fits(other)) {
                  DocService.err(sp, L.k("Во второй руке нужен документ, паспорт, газета или письмо."));
               } else if (items.size() >= FolderModel.MAX_ITEMS) {
                  DocService.err(sp, L.k("Папка заполнена."));
               } else {
                  items.add(other.split(1));
                  save(sp, folder, items);
               }
               break;
            }
            case PUT_ALL: {
               Inventory inv = sp.getInventory();
               int moved = 0;

               for (int i = 0; i < inv.getContainerSize() && items.size() < FolderModel.MAX_ITEMS; i++) {
                  ItemStack st = inv.getItem(i);
                  if (st != folder && fits(st)) {
                     while (!st.isEmpty() && items.size() < FolderModel.MAX_ITEMS) {
                        items.add(st.split(1));
                        moved++;
                     }
                  }
               }

               if (moved == 0) {
                  DocService.err(sp, items.size() >= FolderModel.MAX_ITEMS ? L.k("Папка заполнена.") : L.k("В инвентаре нет документов."));
               } else {
                  save(sp, folder, items);
                  DocService.ok(sp, L.k("Вложено в папку: ") + moved + ".");
               }
               break;
            }
            case TAKE:
               if (index >= 0 && index < items.size()) {
                  ItemStack out = items.remove(index);
                  save(sp, folder, items);
                  PaperService.give(sp, out);
               }
               break;
            case VIEW:
               if (index >= 0 && index < items.size()) {
                  view(sp, items.get(index), MailCodec.readFolder(folder).label);
               }
               break;
            case SET: {
               FolderModel f = MailCodec.readFolder(folder);
               f.label = DocService.clean(label, FolderModel.MAX_LABEL, false);
               f.color = Math.max(0, Math.min(FolderModel.COLOR_RGB.length - 1, color));
               MailCodec.writeFolder(folder, f);
            }
         }
      }
   }

   private static void save(ServerPlayer sp, ItemStack folder, List<ItemStack> items) {
      MailCodec.setItems(folder, MailCodec.FOLDER_TAG, items, sp.level());
      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAGE.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
   }

   private static void view(ServerPlayer sp, ItemStack s, String label) {
      String from = label.isEmpty() ? L.k("папка") : "«" + label + "»";
      if (s.getItem() instanceof DocumentItem) {
         DocService.VerifyResult vr = DocService.verify(sp.server, s);
         CompoundTag tag = NbtCodec.writeDoc(NbtCodec.readDoc(s));
         Net.sendTo(sp, new Packets.ViewRemote(false, tag, vr.status().ordinal(), vr.lines(), from));
      } else if (s.getItem() instanceof PassportItem) {
         DocService.VerifyResult vr = PassportService.verify(sp.server, s);
         CompoundTag tag = NbtCodec.writePass(NbtCodec.readPass(s));
         PassportService.grantAsset(sp, NbtCodec.readPass(s).photo);
         Net.sendTo(sp, new Packets.ViewRemote(true, tag, vr.status().ordinal(), vr.lines(), from));
      }
   }
}
