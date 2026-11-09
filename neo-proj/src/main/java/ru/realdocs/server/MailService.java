package ru.realdocs.server;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModBlocks;
import ru.realdocs.ModItems;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.MailModel;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/** Envelopes with wax seals and the mailbox network. */
public final class MailService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   public static final int SAVE = 0;
   public static final int ATTACH = 1;
   public static final int DETACH = 2;
   public static final int SEAL = 3;
   public static final int BREAK = 4;
   public static final int BOX_SEND = 0;
   public static final int BOX_RECEIVE = 1;
   public static final int MAX_BOX = 64;

   private MailService() {
   }

   public static boolean attachable(ItemStack s) {
      return s.getItem() instanceof DocumentItem || s.getItem() instanceof PassportItem || s.getItem() instanceof NewspaperItem;
   }

   static String canon(ItemStack s) {
      if (s.getItem() instanceof DocumentItem) {
         return "D" + NbtCodec.readDoc(s).canonical();
      } else if (s.getItem() instanceof PassportItem) {
         return "P" + NbtCodec.readPass(s).canonical();
      } else if (s.getItem() instanceof NewspaperItem) {
         PaperCodec.Ref r = PaperCodec.ref(s);
         return "N" + r.canonical() + "|" + r.sig + "|" + s.getCount();
      } else {
         return "?" + BuiltInRegistries.ITEM.getKey(s.getItem()) + "x" + s.getCount();
      }
   }

   static String hash(DocStore store, MailModel m, List<ItemStack> items) {
      List<String> c = new ArrayList<>();

      for (ItemStack s : items) {
         c.add(canon(s));
      }

      return store.hmac(m.canonical(c));
   }

   private static ItemStack single(ServerPlayer sp, InteractionHand hand) {
      ItemStack stack = sp.getItemInHand(hand);
      return stack.getCount() <= 1 ? stack : stack.split(1);
   }

   private static void done(ServerPlayer sp, ItemStack held, ItemStack target) {
      if (target != held) {
         PaperService.give(sp, target);
      }
   }

   public static void action(ServerPlayer sp, InteractionHand hand, int action, String to, String from, String text, String stamp, int n) {
      ItemStack held = sp.getItemInHand(hand);
      if (held.getItem() instanceof EnvelopeItem) {
         MailModel m = MailCodec.readMail(held);
         switch (action) {
            case SAVE:
               if (m.state != MailModel.OPEN) {
                  DocService.err(sp, L.k("Запечатанное письмо уже не изменить."));
               } else {
                  ItemStack t = single(sp, hand);
                  fill(sp, m, to, from, text, n);
                  MailCodec.writeMail(t, m);
                  done(sp, held, t);
               }
               break;
            case ATTACH:
               attach(sp, hand, held, m, to, from, text, n);
               break;
            case DETACH:
               detach(sp, held, m, n);
               break;
            case SEAL:
               seal(sp, hand, held, m, to, from, text, stamp, n);
               break;
            case BREAK:
               if (m.state != MailModel.SEALED) {
                  DocService.err(sp, L.k("Письмо не запечатано."));
               } else {
                  ItemStack t = single(sp, hand);
                  m.state = MailModel.BROKEN;
                  m.brokenDate = LocalDate.now().format(DATE);
                  MailCodec.writeMail(t, m);
                  done(sp, held, t);
                  sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.PLAYERS, 0.9F, 0.7F);
                  DocService.ok(sp, L.k("Сургуч сломан, письмо вскрыто."));
               }
         }
      }
   }

   private static void fill(ServerPlayer sp, MailModel m, String to, String from, String text, int font) {
      m.font = Math.max(0, Math.min(31, font));
      m.to = DocService.clean(to, MailModel.MAX_NAME, false);
      m.from = DocService.clean(from, MailModel.MAX_NAME, false);
      if (m.from.isEmpty()) {
         m.from = sp.getGameProfile().getName();
      }

      m.fromUuid = sp.getUUID().toString();
      m.text = DocService.clean(text, MailModel.MAX_TEXT, true);
      if (m.id.isEmpty()) {
         m.id = UUID.randomUUID().toString();
      }

      m.date = LocalDate.now().format(DATE);
   }

   private static void attach(ServerPlayer sp, InteractionHand hand, ItemStack held, MailModel m, String to, String from, String text, int font) {
      InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack other = sp.getItemInHand(otherHand);
      if (m.state != MailModel.OPEN) {
         DocService.err(sp, L.k("Запечатанное письмо уже не изменить."));
      } else if (!attachable(other)) {
         DocService.err(sp, L.k("Во второй руке нужен документ, паспорт или газета."));
      } else if (MailCodec.count(held, MailCodec.MAIL_TAG) >= MailModel.MAX_ITEMS) {
         DocService.err(sp, L.k("В конверт помещается до ") + MailModel.MAX_ITEMS + L.k(" вложений."));
      } else {
         ItemStack t = single(sp, hand);
         List<ItemStack> items = MailCodec.items(t, MailCodec.MAIL_TAG, sp.level());
         items.add(other.split(1));
         fill(sp, m, to, from, text, font);
         MailCodec.writeMail(t, m);
         MailCodec.setItems(t, MailCodec.MAIL_TAG, items, sp.level());
         done(sp, held, t);
         sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
      }
   }

   private static void detach(ServerPlayer sp, ItemStack held, MailModel m, int index) {
      if (m.state == MailModel.SEALED) {
         DocService.err(sp, L.k("Сначала вскройте письмо."));
      } else if (held.getCount() != 1) {
         DocService.err(sp, L.k("Возьмите в руку один конверт."));
      } else {
         List<ItemStack> items = MailCodec.items(held, MailCodec.MAIL_TAG, sp.level());
         if (index >= 0 && index < items.size()) {
            ItemStack out = items.remove(index);
            MailCodec.setItems(held, MailCodec.MAIL_TAG, items, sp.level());
            PaperService.give(sp, out);
            sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
         }
      }
   }

   private static void seal(ServerPlayer sp, InteractionHand hand, ItemStack held, MailModel m, String to, String from, String text, String stampId, int wax) {
      if (m.state != MailModel.OPEN) {
         DocService.err(sp, L.k("Письмо уже запечатано или вскрыто — возьмите новый конверт."));
         return;
      }

      if (DocService.clean(to, MailModel.MAX_NAME, false).isEmpty()) {
         DocService.err(sp, L.k("Укажите получателя."));
         return;
      }

      boolean free = sp.getAbilities().instabuild;
      if (!free && PaperService.count(sp, ModItems.SEALING_WAX.get()) < 1) {
         DocService.err(sp, L.k("Нужен сургуч в инвентаре."));
         return;
      }

      StampModel sm = null;
      boolean forged = false;
      if (stampId != null && !stampId.isEmpty()) {
         if (!Perms.has(sp, Perms.APPLY)) {
            DocService.err(sp, L.k("У вас нет права ставить печати."));
            return;
         }

         int heldKind = DocService.stampHeld(sp, stampId);
         sm = DocStore.get(sp.server).stamps.get(stampId);
         forged = heldKind == 1;
         if (heldKind == 0 || sm == null || !sm.approved && !forged) {
            DocService.err(sp, L.k("Эта печать недействительна или её нет в инвентаре."));
            return;
         }

         if (sm.kind != 0) {
            DocService.err(sp, L.k("Для сургуча подходит только печать организации."));
            return;
         }
      }

      ItemStack t = single(sp, hand);
      fill(sp, m, to, from, text, wax >> 8);
      wax &= 255;
      if (!free) {
         PaperService.take(sp, ModItems.SEALING_WAX.get(), 1);
      }

      DocStore store = DocStore.get(sp.server);
      m.state = MailModel.SEALED;
      m.wax = Math.max(0, Math.min(MailModel.WAX_RGB.length - 1, wax));
      m.sealId = sm == null ? "" : sm.id;
      m.sealOrg = sm == null ? "" : sm.org;
      m.sealForged = forged;
      m.sealer = sp.getGameProfile().getName();
      m.sealerUuid = sp.getUUID().toString();
      m.sealDate = LocalDate.now().format(DATE);
      m.hash = hash(store, m, MailCodec.items(t, MailCodec.MAIL_TAG, sp.level()));
      MailCodec.writeMail(t, m);
      done(sp, held, t);
      sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.STAMP.get(), SoundSource.PLAYERS, 0.9F, 0.8F);
      DocService.ok(sp, L.k("Письмо запечатано сургучом") + (sm == null ? "." : L.k(" с оттиском «") + sm.org + "»."));
   }

   public static DocService.VerifyResult verify(MinecraftServer server, ServerPlayer viewer, ItemStack stack) {
      List<String> lines = new ArrayList<>();
      MailModel m = MailCodec.readMail(stack);
      if (m.state == MailModel.OPEN) {
         lines.add(L.k("Конверт не запечатан: содержимое мог видеть кто угодно."));
         return new DocService.VerifyResult(DocService.Status.NOT_ISSUED, lines);
      } else if (m.state == MailModel.BROKEN) {
         lines.add(L.k("Сургуч сломан: письмо вскрыто") + (m.brokenDate.isEmpty() ? "." : " " + m.brokenDate + "."));
         if (!m.sealer.isEmpty()) {
            lines.add(L.k("Запечатал: ") + m.sealer + ", " + m.sealDate);
         }

         return new DocService.VerifyResult(DocService.Status.REVOKED, lines);
      } else {
         DocStore store = DocStore.get(server);
         String h = hash(store, m, MailCodec.items(stack, MailCodec.MAIL_TAG, viewer.level()));
         if (!DocStore.safeEquals(h, m.hash)) {
            lines.add(L.k("Сургуч не совпадает с содержимым: письмо вскрывали и запечатали заново или подменили вложение."));
            return new DocService.VerifyResult(DocService.Status.FORGED, lines);
         } else if (m.sealForged) {
            lines.add(L.k("Оттиск на сургуче поддельный: не совпадает с образцом печати «") + m.sealOrg + L.k("» в реестре."));
            return new DocService.VerifyResult(DocService.Status.FORGED, lines);
         } else {
            lines.add(L.k("Сургуч цел, письмо не вскрывали."));
            lines.add(L.k("Запечатал: ") + m.sealer + ", " + m.sealDate);
            if (m.sealId.isEmpty()) {
               lines.add(L.k("Без оттиска печати."));
            } else {
               StampModel sm = store.stamps.get(m.sealId);
               lines.add(
                  L.k("Оттиск «") + m.sealOrg + "» (" + m.sealId + ")" + (sm == null ? L.k(": печать удалена из реестра") : (sm.approved ? "" : L.k(": печать отозвана")))
               );
            }

            return new DocService.VerifyResult(DocService.Status.OK, lines);
         }
      }
   }

   private static boolean near(ServerPlayer sp, BlockPos pos) {
      return sp.level().getBlockState(pos).is((Block)ModBlocks.MAILBOX.get())
         && sp.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0;
   }

   static List<CompoundTag> box(DocStore store, String uuid) {
      return store.mail.computeIfAbsent(uuid, k -> new ArrayList<>());
   }

   public static int waiting(MinecraftServer server, String uuid) {
      List<CompoundTag> b = DocStore.get(server).mail.get(uuid);
      return b == null ? 0 : b.size();
   }

   public static void openMailbox(ServerPlayer sp, BlockPos pos) {
      ItemStack held = sp.getMainHandItem();
      String to = held.getItem() instanceof EnvelopeItem ? MailCodec.readMail(held).to : "";
      Net.sendTo(sp, new MorePackets.OpenMailbox(pos, waiting(sp.server, sp.getUUID().toString()), held.getItem() instanceof EnvelopeItem, to));
   }

   public static void mailbox(ServerPlayer sp, BlockPos pos, int action) {
      if (near(sp, pos)) {
         if (action == BOX_SEND) {
            send(sp);
         } else if (action == BOX_RECEIVE) {
            receive(sp);
         }
      }
   }

   private static void send(ServerPlayer sp) {
      ItemStack held = sp.getMainHandItem();
      if (!(held.getItem() instanceof EnvelopeItem)) {
         DocService.err(sp, L.k("Возьмите письмо в основную руку."));
      } else if (!Perms.has(sp, Perms.MAIL)) {
         DocService.err(sp, L.k("У вас нет права отправлять почту."));
      } else {
         MailModel m = MailCodec.readMail(held);
         if (m.to.isBlank()) {
            DocService.err(sp, L.k("На конверте не указан получатель."));
         } else {
            MinecraftServer server = sp.server;
            ServerPlayer online = server.getPlayerList().getPlayerByName(m.to);
            String uuid;
            String name;
            if (online != null) {
               uuid = online.getUUID().toString();
               name = online.getGameProfile().getName();
            } else {
               Optional<GameProfile> gp = Plat.profile(server, m.to);
               if (gp.isEmpty()) {
                  DocService.err(sp, L.k("Игрок «") + m.to + L.k("» ни разу не заходил на сервер — письмо не отправлено."));
                  return;
               }

               uuid = gp.get().getId().toString();
               name = gp.get().getName();
            }

            DocStore store = DocStore.get(server);
            List<CompoundTag> b = box(store, uuid);
            if (b.size() >= MAX_BOX) {
               DocService.err(sp, L.k("Почтовый ящик получателя переполнен."));
            } else {
               ItemStack one = held.split(1);
               MailModel pm = MailCodec.readMail(one);
               pm.postmark = LocalDate.now().format(DATE);
               MailCodec.writeMail(one, pm);
               CompoundTag entry = new CompoundTag();
               entry.put("Item", Plat.saveStack(one, sp.level()));
               entry.putString("From", m.from.isEmpty() ? sp.getGameProfile().getName() : m.from);
               entry.putLong("Time", System.currentTimeMillis());
               b.add(entry);
               store.setDirty();
               sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
               DocService.ok(sp, L.k("Письмо отправлено: ") + name + ".");
               if (online != null && online != sp) {
                  notify(online, 1, entry.getString("From"));
               }

               LOG.info("[Signed & Sealed] {} отправил письмо игроку {}", sp.getGameProfile().getName(), name);
            }
         }
      }
   }

   private static void notify(ServerPlayer p, int count, String from) {
      p.sendSystemMessage(
         L.c(L.k("[Почта] ")).withStyle(ChatFormatting.GOLD)
            .append(
               L.c(from == null ? L.k("Вас ждут письма: ") + count + L.k(". Заберите их в любом почтовом ящике.") : L.k("Новое письмо от ") + from + L.k(". Заберите его в любом почтовом ящике."))
                  .withStyle(ChatFormatting.YELLOW)
            )
      );
   }

   private static void receive(ServerPlayer sp) {
      DocStore store = DocStore.get(sp.server);
      List<CompoundTag> b = store.mail.remove(sp.getUUID().toString());
      if (b != null && !b.isEmpty()) {
         int n = 0;

         for (CompoundTag e : b) {
            ItemStack s = Plat.loadStack(e.getCompound("Item"), sp.level());
            if (!s.isEmpty()) {
               PaperService.give(sp, s);
               n++;
            }
         }

         store.setDirty();
         sp.level().playSound(null, sp.blockPosition(), (SoundEvent)ModSounds.PAPER.get(), SoundSource.BLOCKS, 0.9F, 1.2F);
         DocService.ok(sp, L.k("Получено писем: ") + n + ".");
      } else {
         DocService.msg(sp, L.k("Писем для вас нет."), ChatFormatting.GRAY);
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         int n = waiting(sp.server, sp.getUUID().toString());
         if (n > 0) {
            notify(sp, n, null);
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         Uploads.forget(sp);
      }
   }
}
