package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModSounds;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.item.StampItem;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.SigStyle;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class DocService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

   private DocService() {
   }

   public static void msg(ServerPlayer p, String text, ChatFormatting color) {
      p.sendSystemMessage(L.c(L.k("[Документы] ")).withStyle(ChatFormatting.GRAY).append(L.c(text).withStyle(color)));
   }

   public static void ok(ServerPlayer p, String text) {
      msg(p, text, ChatFormatting.GREEN);
   }

   public static void err(ServerPlayer p, String text) {
      msg(p, text, ChatFormatting.RED);
   }

   public static String clean(String s, int max, boolean multiline) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(Math.min(s.length(), max));

         for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            if (c != '\r') {
               if (c == '\n') {
                  sb.append((char)(multiline ? '\n' : ' '));
               } else if (c >= ' ' && c != 127 && (c < 128 || c > 159)) {
                  sb.append(c);
               } else {
                  sb.append(' ');
               }
            }
         }

         String out = sb.toString();
         return multiline ? out.stripTrailing() : out.strip();
      }
   }

   static boolean validSignature(int[] sig) {
      if (sig != null && sig.length != 0 && sig.length <= 900) {
         int points = 0;

         for (int i = 0; i < sig.length; i++) {
            int v = sig[i];
            if (v != -1) {
               if (i == 0 && SigStyle.isHeader(v)) {
                  SigStyle st = SigStyle.of(sig);
                  if (st.header() != v) {
                     return false;
                  }
               } else {
                  if (v < 0 || v >> 16 != 0) {
                     return false;
                  }

                  int x = v & 0xFF;
                  int y = v >> 8 & 0xFF;
                  if (x > 255 || y > 95) {
                     return false;
                  }

                  points++;
               }
            }
         }

         return points >= 8;
      } else {
         return false;
      }
   }

   public static void saveOrIssue(ServerPlayer sp, InteractionHand hand, DocModel in, int[] sig, boolean issue) {
      ItemStack stack = sp.getItemInHand(hand);
      if (stack.getItem() instanceof DocumentItem) {
         DocModel cur = NbtCodec.readDoc(stack);
         if (cur.issued) {
            err(sp, L.k("Этот документ уже выпущен и не может быть изменён."));
         } else if (!Perms.has(sp, Perms.CREATE)) {
            err(sp, L.k("У вас нет права создавать документы."));
         } else {
            DocModel d = new DocModel();
            d.template = DocModel.templateIndex(in.template);
            d.number = clean(in.number, 20, false);
            d.org = clean(in.org, 80, false);
            d.position = clean(in.position, 60, false);
            d.subject = clean(in.subject, 140, false);
            d.body = clean(in.body, 2400, true);
            String rerr = RegistryService.prepare(sp, cur, in, d);
            if (rerr != null) {
               err(sp, rerr);
               NbtCodec.writeDoc(stack, d);
               return;
            }

            if (!issue) {
               NbtCodec.writeDoc(stack, d);
            } else if (!Perms.has(sp, Perms.SIGN)) {
               err(sp, L.k("У вас нет права подписывать документы."));
            } else if (d.org.isEmpty() || d.body.isBlank()) {
               err(sp, L.k("Укажите организацию и текст документа."));
               NbtCodec.writeDoc(stack, d);
            } else if (!validSignature(sig)) {
               err(sp, L.k("Нарисуйте подпись перед выпуском документа."));
               NbtCodec.writeDoc(stack, d);
            } else {
               String name = sp.getGameProfile().getName();
               d.issued = true;
               d.docId = UUID.randomUUID().toString();
               d.date = LocalDate.now().format(DATE);
               d.author = name;
               d.authorUuid = sp.getUUID().toString();
               DocStore store0 = DocStore.get(sp.server);
               d.sec = store0.secCode(d.docId);
               String serr = RegistryService.checkSupersede(sp, d);
               if (serr != null) {
                  err(sp, serr);
                  d.issued = false;
                  d.docId = "";
                  d.date = "";
                  d.author = "";
                  d.authorUuid = "";
                  d.sec = "";
                  NbtCodec.writeDoc(stack, d);
                  return;
               }

               d.regNo = RegistryService.assign(sp.server, d);
               DocModel.Sig s = new DocModel.Sig();
               s.name = name;
               s.uuid = d.authorUuid;
               s.position = d.position;
               s.strokes = (int[])sig.clone();
               d.sigs.add(s);
               DocStore store = DocStore.get(sp.server);
               DocStore.Rec r = new DocStore.Rec();
               r.hash = store.hmac(d.canonical());
               r.author = name;
               r.authorUuid = d.authorUuid;
               r.date = d.date;
               r.number = d.number;
               r.template = d.template;
               r.time = System.currentTimeMillis();
               store.docs.put(d.docId, r);
               store.setDirty();
               NbtCodec.writeDoc(stack, d);
               RegistryService.supersede(sp.server, d);
               RegistryService.snapshot(sp.server, d);
               ok(sp, L.k(d.templateName()) + (d.number.isEmpty() ? "" : " № " + d.number) + L.k(" выпущен и подписан.") + L.k(" Рег. № ") + d.regNo);
               LOG.info("[Signed & Sealed] {} выпустил документ {} ({} №{})", new Object[]{name, d.docId, d.templateName(), d.number});
            }
         }
      }
   }

   public static void coSign(ServerPlayer sp, InteractionHand hand, String position, int[] sig) {
      ItemStack stack = sp.getItemInHand(hand);
      if (stack.getItem() instanceof DocumentItem) {
         DocStore store = DocStore.get(sp.server);
         DocModel d = NbtCodec.readDoc(stack);
         String e = addSignature(sp, store, d, position, sig);
         if (e != null) {
            err(sp, e);
         } else {
            NbtCodec.writeDoc(stack, d);
            ok(sp, L.k("Ваша подпись добавлена к документу."));
         }
      }
   }

   /** Adds the player's signature to an issued document and updates the registry. Returns an error or null. */
   static String addSignature(ServerPlayer sp, DocStore store, DocModel d, String position, int[] sig) {
      if (!Perms.has(sp, Perms.SIGN)) {
         return L.k("У вас нет права подписывать документы.");
      } else {
         DocService.VerifyResult vr = check(store, d, false);
         if (vr.status() != DocService.Status.OK || d.copy) {
            return L.k("Подпись невозможна: документ не прошёл проверку подлинности.");
         } else {
            String uuid = sp.getUUID().toString();
            if (d.hasAuthorizedSignature(uuid)) {
               return L.k("Вы уже подписали этот документ.");
            } else if (d.sigs.size() >= 4) {
               return L.k("На документе уже максимум подписей (") + "4).";
            } else {
               String pos = clean(position, 60, false);
               if (pos.isEmpty()) {
                  return L.k("Укажите вашу должность.");
               } else if (!validSignature(sig)) {
                  return L.k("Нарисуйте подпись.");
               } else {
                  DocModel.Sig s = new DocModel.Sig();
                  s.name = sp.getGameProfile().getName();
                  s.uuid = uuid;
                  s.position = pos;
                  s.strokes = (int[])sig.clone();
                  d.sigs.add(s);
                  DocStore.Rec r = store.docs.get(d.docId);
                  r.rehash(store.hmac(d.canonical()));
                  store.setDirty();
                  RegistryService.snapshot(sp.server, d);
                  LOG.info("[Signed & Sealed] {} подписал документ {}", s.name, d.docId);
                  return null;
               }
            }
         }
      }
   }

   public static void applyStamp(ServerPlayer sp, InteractionHand stampHand) {
      ItemStack stampStack = sp.getItemInHand(stampHand);
      InteractionHand other = stampHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      placeStamp(sp, other, stampId(stampStack), null, 0, isForgedStamp(stampStack));
   }

   public static boolean isForgedStamp(ItemStack s) {
      return s.hasTag() && s.getTag().getBoolean("StampForged");
   }

   public static int stampHeld(ServerPlayer sp, String id) {
      if (id != null && !id.isEmpty()) {
         int best = 0;
         Inventory inv = sp.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof StampItem && id.equals(stampId(st))) {
               best = Math.max(best, isForgedStamp(st) ? 1 : 2);
            }
         }

         return best;
      } else {
         return 0;
      }
   }

   public static String stampId(ItemStack s) {
      return s.hasTag() ? s.getTag().getString("StampId") : "";
   }

   public static boolean holdsStamp(ServerPlayer sp, String id) {
      if (id != null && !id.isEmpty()) {
         Inventory inv = sp.getInventory();

         for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof StampItem && id.equals(stampId(st))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void placeStamp(ServerPlayer sp, InteractionHand targetHand, String id, DocModel.StampRef want, int days) {
      placeStamp(sp, targetHand, id, want, days, stampHeld(sp, id) == 1);
   }

   public static void placeStamp(ServerPlayer sp, InteractionHand targetHand, String id, DocModel.StampRef want, int days, boolean forged) {
      ItemStack target = sp.getItemInHand(targetHand);
      boolean isDoc = target.getItem() instanceof DocumentItem;
      boolean isPass = target.getItem() instanceof PassportItem;
      if (!isDoc && !isPass) {
         err(sp, L.k("Возьмите документ или паспорт в руку, а печать держите в инвентаре."));
      } else if (!Perms.has(sp, Perms.APPLY)) {
         err(sp, L.k("У вас нет права ставить печати."));
      } else {
         int held = stampHeld(sp, id);
         if (held == 0) {
            err(sp, L.k("Эта печать должна лежать у вас в инвентаре."));
         } else {
            if (held == 2) {
               forged = false;
            }

            DocStore store = DocStore.get(sp.server);
            StampModel sm = store.stamps.get(id);
            if (sm != null && (sm.approved || forged)) {
               boolean done;
               if (isPass) {
                  done = PassportService.addStamp(sp, target, store, sm, want, days, forged);
               } else if (sm.kind != 0) {
                  err(sp, (sm.kind == 2 ? L.k("Визу") : L.k("Пограничный штамп")) + L.k(" можно поставить только в паспорт, на страницу для виз."));
                  done = false;
               } else {
                  done = addDocStamp(sp, target, store, sm, want, forged);
               }

               if (done) {
                  sp.level().playSound(sp, sp.blockPosition(), (SoundEvent)ModSounds.STAMP.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
                  ok(sp, L.k("Печать «") + sm.org + L.k("» поставлена."));
                  LOG.info("[Signed & Sealed] {} поставил печать {}", sp.getGameProfile().getName(), id);
               }
            } else {
               err(sp, L.k("Эта печать недействительна или отозвана."));
            }
         }
      }
   }

   private static boolean addDocStamp(ServerPlayer sp, ItemStack docStack, DocStore store, StampModel sm, DocModel.StampRef want, boolean forged) {
      DocModel d = NbtCodec.readDoc(docStack);
      if (!d.issued) {
         err(sp, L.k("Сначала выпустите и подпишите документ."));
         return false;
      } else if (d.forged) {
         if (d.stamps.size() >= 6) {
            return false;
         } else {
            DocModel.StampRef ref = want == null
               ? DocModel.defaultPlacement(sm.id, d.stamps.size())
               : new DocModel.StampRef(sm.id, 0, want.x, want.y, want.rot, want.scale);
            ref.forged = forged;
            d.stamps.add(ref);
            NbtCodec.writeDoc(docStack, d);
            return true;
         }
      } else if (check(store, d, false).status() != DocService.Status.OK) {
         err(sp, L.k("Документ не прошёл проверку подлинности — печать не поставлена."));
         return false;
      } else if (d.stamps.size() >= 6) {
         err(sp, L.k("На документе уже максимум печатей (") + "6).");
         return false;
      } else {
         DocModel.StampRef ref = want == null
            ? DocModel.defaultPlacement(sm.id, d.stamps.size())
            : new DocModel.StampRef(sm.id, 0, want.x, want.y, want.rot, want.scale);
         ref.forged = forged;
         d.stamps.add(ref);
         DocStore.Rec r = store.docs.get(d.docId);
         r.rehash(store.hmac(d.canonical()));
         RegistryService.snapshot(sp.server, d);
         store.setDirty();
         NbtCodec.writeDoc(docStack, d);
         return true;
      }
   }

   public static DocService.VerifyResult verify(MinecraftServer server, ItemStack stack) {
      if (!(stack.getItem() instanceof DocumentItem)) {
         return new DocService.VerifyResult(DocService.Status.NOT_DOC, List.of(L.k("Это не документ.")));
      } else {
         DocModel d = NbtCodec.readDoc(stack);
         DocService.VerifyResult vr = check(DocStore.get(server), d, true);
         return RegistryService.annotate(server, d, vr);
      }
   }

   static DocService.VerifyResult check(DocStore store, DocModel d, boolean detailed) {
      List<String> lines = new ArrayList<>();
      if (!d.issued) {
         lines.add(L.k("Бланк или черновик: силы не имеет."));
         return new DocService.VerifyResult(DocService.Status.NOT_ISSUED, lines);
      } else {
         DocStore.Rec r = store.docs.get(d.docId);
         if (r == null) {
            lines.add(L.k("Документа нет в реестре этого сервера."));
            lines.add(L.k("Вероятно, подделка или документ из другого мира."));
            return new DocService.VerifyResult(DocService.Status.UNKNOWN, lines);
         } else if (d.copy) {
            d.copy = false;
            String oh = store.hmac(d.canonical());
            d.copy = true;
            if (!d.forged && (DocStore.safeEquals(oh, r.hash) || r.isOld(oh))) {
               lines.add(L.k("Это копия подлинного документа, снятая на принтере."));
               lines.add(
                  r.revoked
                     ? L.k("Оригинал был отозван администрацией.")
                     : (
                        DocStore.safeEquals(oh, r.hash)
                           ? L.k("Содержимое совпадает с оригиналом, но копия не заменяет его.")
                           : L.k("Копия снята с устаревшей версии документа.")
                     )
               );
               return new DocService.VerifyResult(DocService.Status.COPY, lines);
            } else {
               lines.add(L.k("Содержимое копии не совпадает с выпущенным документом."));
               lines.add(L.k("Документ изменён вручную: ПОДДЕЛКА."));
               return new DocService.VerifyResult(DocService.Status.FORGED, lines);
            }
         } else {
            String h = store.hmac(d.canonical());
            if (!DocStore.safeEquals(h, r.hash)) {
               if (r.isOld(h)) {
                  lines.add(L.k("Это устаревшая копия: позже на документ добавили подпись или печать."));
                  lines.add(L.k("Действительна только актуальная версия документа."));
                  return new DocService.VerifyResult(DocService.Status.OUTDATED, lines);
               } else {
                  lines.add(L.k("Содержимое документа не совпадает с выпущенным."));
                  lines.add(L.k("Документ изменён вручную: ПОДДЕЛКА."));
                  return new DocService.VerifyResult(DocService.Status.FORGED, lines);
               }
            } else if (r.revoked) {
               lines.add(L.k("Документ был отозван администрацией."));
               return new DocService.VerifyResult(DocService.Status.REVOKED, lines);
            } else {
               for (DocModel.StampRef ref : d.stamps) {
                  if (ref.forged) {
                     StampModel sm = store.stamps.get(ref.id);
                     lines.add(
                        L.k("Документ подлинный, но печать «")
                           + (sm == null ? ref.id : sm.org)
                           + L.k("» на нём поддельная: оттиск не совпадает с образцом в реестре.")
                     );
                     return new DocService.VerifyResult(DocService.Status.FORGED, lines);
                  }
               }

               if (detailed) {
                  lines.add(L.k(d.templateName()) + (d.number.isEmpty() ? "" : " № " + d.number) + L.k(" от ") + d.date);
                  lines.add(L.k("Автор: ") + d.author);
                  if (!d.sec.isEmpty()) {
                     lines.add(L.k("Код защиты: ") + d.sec);
                  }

                  for (DocModel.Sig s : d.sigs) {
                     lines.add(L.k("Подпись: ") + s.name + (s.position.isEmpty() ? "" : " (" + s.position + ")"));
                  }

                  for (DocModel.StampRef refx : d.stamps) {
                     StampModel sm = store.stamps.get(refx.id);
                     if (sm == null) {
                        lines.add(L.k("Печать ") + refx.id + L.k(": удалена из реестра!"));
                     } else if (!sm.approved) {
                        lines.add(L.k("Печать «") + sm.org + "» (" + refx.id + L.k("): ОТОЗВАНА."));
                     } else {
                        lines.add(L.k("Печать «") + sm.org + "» (" + refx.id + L.k("): действительна."));
                     }
                  }
               }

               return new DocService.VerifyResult(DocService.Status.OK, lines);
            }
         }
      }
   }

   public static void verifyAndSend(ServerPlayer sp, InteractionHand hand) {
      if (!Perms.has(sp, Perms.VERIFY)) {
         err(sp, L.k("У вас нет права проверять документы."));
      } else {
         ItemStack held = sp.getItemInHand(hand);
         DocService.VerifyResult vr;
         if (held.getItem() instanceof PassportItem) {
            vr = PassportService.verify(sp.server, held);
         } else if (held.getItem() instanceof ru.realdocs.item.NewspaperItem) {
            vr = PaperService.verify(sp.server, held);
         } else if (held.getItem() instanceof ru.realdocs.item.EnvelopeItem) {
            vr = MailService.verify(sp.server, sp, held);
         } else {
            vr = verify(sp.server, held);
         }

         Net.sendTo(sp, new Packets.VerifyResult(vr.status().ordinal(), vr.lines()));
      }
   }

   public static void submitStamp(ServerPlayer sp, StampModel in, byte[] png) {
      if (!Perms.has(sp, Perms.DESIGN)) {
         err(sp, L.k("У вас нет права создавать макеты печатей."));
      } else {
         StampModel m = new StampModel();
         m.org = clean(in.org, 60, false);
         if (m.org.isEmpty()) {
            err(sp, L.k("Укажите название организации."));
         } else {
            m.top = clean(in.top, 44, false);
            m.bottom = clean(in.bottom, 44, false);
            m.center = clean(in.center, 88, false);
            m.shape = Math.max(0, Math.min(3, in.shape));
            m.kind = Math.max(0, Math.min(2, in.kind));
            m.color = in.color & 16777215;
            if (m.kind == 2) {
               for (DesignElement e : in.visa) {
                  if (m.visa.size() >= 24) {
                     break;
                  }

                  DesignElement c = e.copy();
                  c.text = clean(c.text, 60, false);
                  c.sanitize(420, 260);
                  m.visa.add(c);
               }
            }

            boolean hasImage = png != null && png.length > 0;
            if (m.shape == 3 && !hasImage) {
               err(sp, L.k("Для печати «Только картинка» нужно изображение."));
            } else if (hasImage && !validPng(png)) {
               err(sp, L.k("Файл PNG не подходит (размер до ") + "24" + L.k(" КБ, сторона до ") + "256 px).");
            } else {
               DocStore store = DocStore.get(sp.server);
               boolean auto = Perms.has(sp, Perms.ISSUE);
               String owner = sp.getUUID().toString();
               if (!auto) {
                  int pending = 0;

                  for (StampModel s : store.stamps.values()) {
                     if (!s.approved && s.ownerUuid.equals(owner)) {
                        pending++;
                     }
                  }

                  if (pending >= 5) {
                     err(sp, L.k("У вас уже ") + pending + L.k(" неутверждённых макетов. Дождитесь решения администрации."));
                     return;
                  }
               }

               m.id = store.nextStampId();
               m.ownerUuid = owner;
               m.ownerName = sp.getGameProfile().getName();
               m.hasImage = hasImage;
               m.approved = auto;
               m.everApproved = auto;
               m.created = LocalDate.now().format(DATE);
               if (hasImage) {
                  try {
                     DocStore.saveStampImage(sp.server, m.id, png);
                  } catch (IOException var11) {
                     LOG.error("[Signed & Sealed] не удалось сохранить PNG печати {}", m.id, var11);
                     err(sp, L.k("Не удалось сохранить изображение печати."));
                     return;
                  }
               }

               store.stamps.put(m.id, m);
               store.setDirty();
               if (auto) {
                  ok(sp, L.k("Печать ") + m.id + " «" + m.org + L.k("» создана и утверждена. Выдать: /seal stamp give ") + m.id + L.k(" <игрок>"));
               } else {
                  ok(sp, L.k("Макет ") + m.id + " «" + m.org + L.k("» отправлен на утверждение администрации."));

                  for (ServerPlayer admin : sp.server.getPlayerList().getPlayers()) {
                     if (admin != sp && Perms.has(admin, Perms.ISSUE)) {
                        msg(
                           admin,
                           m.ownerName + L.k(" отправил макет печати ") + m.id + " «" + m.org + L.k("». Утвердить: /seal stamp approve ") + m.id,
                           ChatFormatting.YELLOW
                        );
                     }
                  }
               }

               LOG.info("[Signed & Sealed] {} создал макет печати {} ({})", new Object[]{m.ownerName, m.id, m.org});
            }
         }
      }
   }

   static boolean validPng(byte[] b) {
      return validPng(b, 24000, 256);
   }

   static boolean validPng(byte[] b, int maxBytes, int maxSide) {
      if (b != null && b.length >= 33 && b.length <= maxBytes) {
         int[] sig = new int[]{137, 80, 78, 71, 13, 10, 26, 10};

         for (int i = 0; i < 8; i++) {
            if ((b[i] & 255) != sig[i]) {
               return false;
            }
         }

         if (b[12] == 73 && b[13] == 72 && b[14] == 68 && b[15] == 82) {
            int w = (b[16] & 255) << 24 | (b[17] & 255) << 16 | (b[18] & 255) << 8 | b[19] & 255;
            int h = (b[20] & 255) << 24 | (b[21] & 255) << 16 | (b[22] & 255) << 8 | b[23] & 255;
            return w > 0 && h > 0 && w <= maxSide && h <= maxSide;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void sendStamp(ServerPlayer sp, String id) {
      DocStore store = DocStore.get(sp.server);
      StampModel m = store.stamps.get(id);
      boolean visible = m != null && (!m.pending() || m.ownerUuid.equals(sp.getUUID().toString()) || Perms.has(sp, Perms.ISSUE));
      if (!visible) {
         Net.sendTo(sp, new Packets.StampData(id, false, new StampModel(), new byte[0]));
      } else {
         byte[] png = m.hasImage ? DocStore.loadStampImage(sp.server, id) : new byte[0];
         Net.sendTo(sp, new Packets.StampData(id, true, m, png));
      }
   }

   public static enum Status {
      NOT_DOC,
      NOT_ISSUED,
      OK,
      FORGED,
      UNKNOWN,
      REVOKED,
      OUTDATED,
      COPY;
   }

   public static record VerifyResult(DocService.Status status, List<String> lines) {
   }
}
