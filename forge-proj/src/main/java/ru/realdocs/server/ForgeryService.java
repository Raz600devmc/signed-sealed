package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.ForgeryKitItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;

public final class ForgeryService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   public static final String F_NUMBER = "#number";
   public static final String F_ORG = "#org";
   public static final String F_POSITION = "#position";
   public static final String F_SUBJECT = "#subject";
   public static final String F_BODY = "#body";

   private ForgeryService() {
   }

   private static InteractionHand other(InteractionHand h) {
      return h == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
   }

   private static boolean ready(ServerPlayer sp, InteractionHand kitHand) {
      if (!(sp.getItemInHand(kitHand).getItem() instanceof ForgeryKitItem)) {
         return false;
      } else if (!Perms.has(sp, Perms.FORGE)) {
         DocService.err(sp, L.k("У вас нет права подделывать документы."));
         return false;
      } else {
         return true;
      }
   }

   private static boolean take(ServerPlayer sp, Item what) {
      if (sp.getAbilities().instabuild) {
         return true;
      } else {
         Inventory inv = sp.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(what)) {
               st.shrink(1);
               return true;
            }
         }

         return false;
      }
   }

   private static void wear(ServerPlayer sp, InteractionHand kitHand, int amount) {
      ItemStack kit = sp.getItemInHand(kitHand);
      kit.hurtAndBreak(amount, sp, p -> p.broadcastBreakEvent(kitHand));
   }

   private static void give(ServerPlayer sp, ItemStack out) {
      if (!sp.getInventory().add(out)) {
         sp.drop(out, false);
      }

      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PEN.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
   }

   public static void forgeCopy(ServerPlayer sp, InteractionHand kitHand, Map<String, String> in, byte[] photo) {
      if (ready(sp, kitHand)) {
         ItemStack target = sp.getItemInHand(other(kitHand));
         if (target.getItem() instanceof DocumentItem) {
            DocModel d = NbtCodec.readDoc(target);
            if (!d.issued) {
               DocService.err(sp, L.k("Подделать можно только выпущенный документ."));
            } else if (!take(sp, Items.PAPER)) {
               DocService.err(sp, L.k("Нужен лист бумаги в инвентаре."));
            } else {
               d.number = DocService.clean(in.getOrDefault("#number", d.number), 20, false);
               d.org = DocService.clean(in.getOrDefault("#org", d.org), 80, false);
               d.position = DocService.clean(in.getOrDefault("#position", d.position), 60, false);
               d.subject = DocService.clean(in.getOrDefault("#subject", d.subject), 140, false);
               d.body = DocService.clean(in.getOrDefault("#body", d.body), 2400, true);
               d.forged = true;
               d.copy = false;
               ItemStack out = new ItemStack((ItemLike)ModItems.DOCUMENT.get());
               NbtCodec.writeDoc(out, d);
               wear(sp, kitHand, 1);
               give(sp, out);
               DocService.ok(sp, L.k("Подделка готова. Внешне её не отличить, но проверка по реестру её выдаст."));
               LOG.info("[Signed & Sealed] {} forged document {}", sp.getGameProfile().getName(), d.docId);
            }
         } else if (target.getItem() instanceof PassportItem) {
            PassportModel p = NbtCodec.readPass(target);
            DocStore store = DocStore.get(sp.server);
            PassportTemplate t = store.templates.get(p.templateId);
            if (t == null) {
               DocService.err(sp, L.k("Шаблон этого документа недоступен."));
            } else {
               boolean book = t.book();
               if (!take(sp, book ? Items.BOOK : Items.PAPER)) {
                  DocService.err(sp, book ? L.k("Нужна книга в инвентаре.") : L.k("Нужен лист бумаги в инвентаре."));
               } else {
                  for (PassportTemplate.Field f : t.fields) {
                     if (PassportTemplate.playerInput(f) && in.containsKey(f.key)) {
                        String v = DocService.clean(in.get(f.key), 60, false);
                        if (v.isEmpty()) {
                           p.values.remove(f.key);
                        } else {
                           p.values.put(f.key, v);
                        }
                     }
                  }

                  if (photo != null && photo.length > 0 && DocService.validPng(photo, 16000, 512)) {
                     String aid = store.nextAssetId();

                     try {
                        DocStore.saveAsset(sp.server, aid, photo);
                        DocStore.AssetRec ar = new DocStore.AssetRec();
                        ar.ownerUuid = sp.getUUID().toString();
                        ar.kind = "photo";
                        ar.created = LocalDate.now().format(DATE);
                        store.assets.put(aid, ar);
                        store.setDirty();
                        p.photo = aid;
                     } catch (IOException var12) {
                        DocService.err(sp, L.k("Не удалось сохранить фото."));
                     }
                  }

                  if (!p.issued) {
                     p.issued = true;
                     p.date = LocalDate.now().format(DATE);
                     p.serial = (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + String.format("%06d", 100000 + sp.getRandom().nextInt(899999));
                     p.holderName = sp.getGameProfile().getName();
                     p.holderUuid = sp.getUUID().toString();
                  }

                  p.forged = true;
                  ItemStack out = new ItemStack((ItemLike)ModItems.PASSPORT.get());
                  NbtCodec.writePass(out, p);
                  wear(sp, kitHand, 1);
                  give(sp, out);
                  DocService.ok(sp, L.k("Подделка готова. Внешне её не отличить, но проверка по реестру её выдаст."));
                  LOG.info("[Signed & Sealed] {} forged document {} ({})", new Object[]{sp.getGameProfile().getName(), p.passId, p.serial});
               }
            }
         } else {
            DocService.err(sp, L.k("Возьмите в другую руку документ, паспорт, удостоверение, билет или штраф."));
         }
      }
   }

   public static void forgeStamp(ServerPlayer sp, InteractionHand kitHand, String stampId) {
      if (ready(sp, kitHand)) {
         ItemStack target = sp.getItemInHand(other(kitHand));
         List<DocModel.StampRef> refs = target.getItem() instanceof DocumentItem
            ? NbtCodec.readDoc(target).stamps
            : (target.getItem() instanceof PassportItem ? NbtCodec.readPass(target).stamps : List.of());
         boolean seen = refs.stream().anyMatch(r -> r.id.equals(stampId));
         StampModel sm = DocStore.get(sp.server).stamps.get(stampId);
         if (!seen || sm == null) {
            DocService.err(sp, L.k("Такого оттиска на документе нет."));
         } else if (!take(sp, Items.IRON_INGOT)) {
            DocService.err(sp, L.k("Для поддельной печати нужен железный слиток."));
         } else {
            ItemStack out = new ItemStack((ItemLike)ModItems.STAMP.get());
            out.getOrCreateTag().putString("StampId", sm.id);
            out.getOrCreateTag().putString("StampOrg", sm.org);
            out.getOrCreateTag().putBoolean("StampForged", true);
            wear(sp, kitHand, 2);
            give(sp, out);
            DocService.ok(sp, L.k("Поддельная печать «") + sm.org + L.k("» готова. Её оттиск проверка распознает как фальшивый."));
            LOG.info("[Signed & Sealed] {} made a fake stamp {}", sp.getGameProfile().getName(), sm.id);
         }
      }
   }
}
