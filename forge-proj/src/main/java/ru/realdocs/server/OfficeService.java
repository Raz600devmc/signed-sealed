package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModBlocks;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.item.StampItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class OfficeService {
   private static final Logger LOG = LogUtils.getLogger();
   public static final int MAX_PRINT = 16;

   private OfficeService() {
   }

   private static boolean near(ServerPlayer sp, BlockPos pos, Block block) {
      return sp.level().getBlockState(pos).is(block)
         && sp.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0;
   }

   static List<PassportTemplate> printable(ServerPlayer sp) {
      DocStore store = DocStore.get(sp.server);
      boolean admin = Perms.has(sp, Perms.PASSPORT_ISSUE);
      String me = sp.getUUID().toString();
      List<PassportTemplate> out = new ArrayList<>();

      for (PassportTemplate t : store.templates.values()) {
         if (t.approved && (admin || t.ownerUuid.equals(me))) {
            out.add(t);
         }
      }

      return out;
   }

   public static void openPrinter(ServerPlayer sp, BlockPos pos) {
      if (!Perms.has(sp, Perms.PRINT)) {
         DocService.err(sp, L.k("У вас нет права пользоваться принтером."));
      } else {
         List<String[]> list = new ArrayList<>();

         for (PassportTemplate t : printable(sp)) {
            list.add(new String[]{t.id, t.name, String.valueOf(t.format)});
         }

         Net.sendTo(sp, new Packets.OpenPrinter(pos, list));
      }
   }

   private static int count(ServerPlayer sp, Item what) {
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

   private static void take(ServerPlayer sp, Item what, int n) {
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

   private static void give(ServerPlayer sp, ItemStack out) {
      if (!sp.getInventory().add(out)) {
         sp.drop(out, false);
      }
   }

   public static void print(ServerPlayer sp, BlockPos pos, String templateId, int count) {
      if (near(sp, pos, (Block)ModBlocks.PRINTER.get())) {
         if (!Perms.has(sp, Perms.PRINT)) {
            DocService.err(sp, L.k("У вас нет права пользоваться принтером."));
         } else {
            boolean free = sp.getAbilities().instabuild;
            if ("@paper".equals(templateId)) {
               PaperService.printRun(sp, count);
            } else if (templateId.isEmpty()) {
               ItemStack held = sp.getMainHandItem();
               if (!(held.getItem() instanceof DocumentItem)) {
                  DocService.err(sp, L.k("Возьмите документ в основную руку, чтобы снять копию."));
               } else {
                  DocModel d = NbtCodec.readDoc(held);
                  if (!d.issued) {
                     DocService.err(sp, L.k("Копию можно снять только с выпущенного документа."));
                  } else if (!free && count(sp, Items.PAPER) < 1) {
                     DocService.err(sp, L.k("Нужен лист бумаги в инвентаре."));
                  } else {
                     if (!free) {
                        take(sp, Items.PAPER, 1);
                     }

                     d.copy = true;
                     ItemStack out = new ItemStack((ItemLike)ModItems.DOCUMENT.get());
                     NbtCodec.writeDoc(out, d);
                     give(sp, out);
                     sp.level().playSound(null, pos, (SoundEvent)ModSounds.PRINT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
                     DocService.ok(sp, L.k("Копия напечатана. На ней пометка «КОПИЯ»: оригиналом она не является."));
                  }
               }
            } else {
               PassportTemplate t = null;

               for (PassportTemplate x : printable(sp)) {
                  if (x.id.equals(templateId)) {
                     t = x;
                  }
               }

               if (t == null) {
                  DocService.err(sp, L.k("Печатать бланки этого шаблона может только его организация или администрация."));
               } else {
                  int n = Math.max(1, Math.min(16, count));
                  Item material = t.book() ? Items.BOOK : Items.PAPER;
                  if (!free && count(sp, material) < n) {
                     DocService.err(
                        sp,
                        t.book() ? L.k("Не хватает книг: на каждый бланк паспорта нужна одна.") : L.k("Не хватает бумаги: на каждый бланк нужен один лист.")
                     );
                  } else {
                     if (!free) {
                        take(sp, material, n);
                     }

                     for (int i = 0; i < n; i++) {
                        give(sp, PassportService.makeBlank(sp.server, t, sp.getGameProfile().getName()));
                     }

                     sp.level().playSound(null, pos, (SoundEvent)ModSounds.PRINT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
                     DocService.ok(sp, L.k("Напечатано бланков: ") + n + " (" + L.k(t.name) + ").");
                     LOG.info("[Signed & Sealed] {} напечатал {} бланков шаблона {}", new Object[]{sp.getGameProfile().getName(), n, t.id});
                  }
               }
            }
         }
      }
   }

   public static void shred(ServerPlayer sp, BlockPos pos) {
      if (near(sp, pos, (Block)ModBlocks.SHREDDER.get())) {
         ItemStack held = sp.getMainHandItem();
         boolean doc = held.getItem() instanceof DocumentItem;
         boolean pass = held.getItem() instanceof PassportItem;
         boolean stamp = held.getItem() instanceof StampItem;
         boolean paper = held.getItem() instanceof ru.realdocs.item.NewspaperItem || held.getItem() instanceof ru.realdocs.item.EnvelopeItem;
         if (!doc && !pass && !stamp && !paper) {
            DocService.err(sp, L.k("Поднесите к шредеру документ, паспорт, газету, письмо или печать в основной руке."));
         } else {
            DocStore store = DocStore.get(sp.server);
            String me = sp.getUUID().toString();
            boolean admin = Perms.has(sp, Perms.ADMIN);
            boolean annulled = false;
            if (doc) {
               DocModel d = NbtCodec.readDoc(held);
               DocStore.Rec r = d.issued && !d.copy && !d.forged ? store.docs.get(d.docId) : null;
               if (r != null && (admin || d.authorUuid.equals(me)) && DocService.check(store, d, false).status() == DocService.Status.OK) {
                  r.revoked = true;
                  annulled = true;
               }
            } else if (pass) {
               PassportModel p = NbtCodec.readPass(held);
               PassportTemplate t = store.templates.get(p.templateId);
               DocStore.Rec r = p.issued && !p.forged ? store.passes.get(p.passId) : null;
               boolean owner = t != null && t.ownerUuid.equals(me);
               if (r != null
                  && (admin || owner || Perms.has(sp, Perms.PASSPORT_ISSUE))
                  && PassportService.check(store, p, false).status() == DocService.Status.OK) {
                  r.revoked = true;
                  annulled = true;
               }
            }

            if (annulled) {
               store.setDirty();
            }

            ItemStack copy = held.copy();
            held.shrink(1);
            if (sp.level() instanceof ServerLevel sl) {
               sl.sendParticles(
                  new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)),
                  (double)pos.getX() + 0.5,
                  (double)pos.getY() + 0.95,
                  (double)pos.getZ() + 0.5,
                  24,
                  0.18,
                  0.05,
                  0.18,
                  0.06
               );
            }

            sp.level().playSound(null, pos, (SoundEvent)ModSounds.SHRED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            DocService.ok(sp, annulled ? L.k("Документ уничтожен и аннулирован в реестре.") : L.k("Документ уничтожен."));
            LOG.info("[Signed & Sealed] {} уничтожил {} (аннулирован: {})", new Object[]{sp.getGameProfile().getName(), copy.getHoverName().getString(), annulled});
         }
      }
   }
}
