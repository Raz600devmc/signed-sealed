package ru.realdocs.server;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;
import ru.realdocs.L;
import ru.realdocs.ModItems;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class PassportService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   private static final Pattern DATE_RE = Pattern.compile("^\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}$");
   public static final int MAX_DATA_STAMPS = 4;
   public static final int MAX_PAGE_STAMPS = 8;
   private static final int PHOTOS_PER_PLAYER = 20;
   private static final int PENDING_TEMPLATES = 4;
   private static final SecureRandom RNG = new SecureRandom();
   private static final Map<UUID, Map<String, Long>> GRANTS = new ConcurrentHashMap<>();
   private static final long GRANT_MS = 600000L;

   private PassportService() {
   }

   public static boolean canEditTemplate(ServerPlayer sp, PassportTemplate t) {
      return Perms.has(sp, Perms.PASSPORT_ISSUE) || t.ownerUuid.equals(sp.getUUID().toString());
   }

   public static void openTemplateEdit(ServerPlayer sp, String id) {
      PassportTemplate t = DocStore.get(sp.server).templates.get(id);
      if (t == null) {
         DocService.err(sp, L.k("Шаблон ") + id + L.k(" не найден."));
      } else if (!canEditTemplate(sp, t)) {
         DocService.err(sp, L.k("Изменять шаблон может только его автор или администрация."));
      } else {
         Net.sendTo(sp, new Packets.EditTemplate(t));
      }
   }

   public static void broadcastTemplate(MinecraftServer server, PassportTemplate t) {
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         Net.sendTo(p, new Packets.TemplateData(t.id, true, t));
      }
   }

   public static void submitTemplate(ServerPlayer sp, PassportTemplate in, byte[] emblem) {
      if (!Perms.has(sp, Perms.DESIGN)) {
         DocService.err(sp, L.k("У вас нет права создавать шаблоны."));
      } else {
         PassportTemplate t = in.copy();
         t.name = DocService.clean(t.name, 60, false);
         t.org = DocService.clean(t.org, 80, false);
         t.title = DocService.clean(t.title, 60, false);
         t.subtitle = DocService.clean(t.subtitle, 80, false);
         t.serialPrefix = DocService.clean(t.serialPrefix, 8, false);
         t.coverTitle = DocService.clean(t.coverTitle, 40, false);
         t.coverText = DocService.clean(t.coverText, 120, true);

         for (DesignElement e : t.cover) {
            e.text = DocService.clean(e.text, 60, false);
         }

         for (DesignElement e : t.decor) {
            e.text = DocService.clean(e.text, 60, false);
         }

         for (PassportTemplate.Field f : t.fields) {
            f.label = DocService.clean(f.label, 40, false);
            f.prefix = DocService.clean(f.prefix, 12, false);
            f.hint = DocService.clean(f.hint, 40, false);
            f.key = DocService.clean(f.key, 24, false);
         }

         t.sanitize();
         if (t.name.isEmpty()) {
            DocService.err(sp, L.k("Укажите название шаблона."));
         } else if (t.fields.isEmpty()) {
            DocService.err(sp, L.k("Добавьте хотя бы одно поле."));
         } else {
            DocStore store = DocStore.get(sp.server);
            boolean auto = Perms.has(sp, Perms.PASSPORT_ISSUE);
            String owner = sp.getUUID().toString();
            PassportTemplate old = in.id != null && !in.id.isEmpty() ? store.templates.get(in.id) : null;
            if (in.id != null && !in.id.isEmpty()) {
               if (old == null) {
                  DocService.err(sp, L.k("Шаблон ") + in.id + L.k(" не найден."));
                  return;
               }

               if (!canEditTemplate(sp, old)) {
                  DocService.err(sp, L.k("Изменять шаблон может только его автор или администрация."));
                  return;
               }

               if (old.format != t.format) {
                  DocService.err(sp, L.k("Формат существующего шаблона менять нельзя."));
                  return;
               }
            }

            boolean inPlace = old != null && (auto || !old.approved);
            if (!auto && !inPlace) {
               int pending = 0;

               for (PassportTemplate x : store.templates.values()) {
                  if (!x.approved && x.ownerUuid.equals(owner)) {
                     pending++;
                  }
               }

               if (pending >= 4) {
                  DocService.err(sp, L.k("У вас уже ") + pending + L.k(" шаблонов на утверждении."));
                  return;
               }
            }

            StampModel autoStamp = store.stamps.get(t.autoStampId);
            if (!auto || autoStamp == null || !autoStamp.approved) {
               t.autoStampId = old != null && !auto ? old.autoStampId : "";
            }

            t.id = store.nextTemplateId();
            t.ownerUuid = owner;
            t.ownerName = sp.getGameProfile().getName();
            t.created = LocalDate.now().format(DATE);
            t.approved = auto;
            String keepEmblem = old != null && (emblem == null || emblem.length == 0) && !old.emblem.isEmpty() && old.emblem.equals(in.emblem)
               ? old.emblem
               : "";
            t.emblem = keepEmblem;
            if (emblem != null && emblem.length > 0) {
               if (!DocService.validPng(emblem, 16000, 512)) {
                  DocService.err(sp, L.k("Эмблема не подходит: PNG до ") + "16" + L.k(" КБ."));
                  return;
               }

               String aid = store.nextAssetId();

               try {
                  DocStore.saveAsset(sp.server, aid, emblem);
               } catch (IOException var13) {
                  LOG.error("[Signed & Sealed] не удалось сохранить эмблему {}", aid, var13);
                  DocService.err(sp, L.k("Не удалось сохранить эмблему."));
                  return;
               }

               DocStore.AssetRec ar = new DocStore.AssetRec();
               ar.ownerUuid = owner;
               ar.kind = "emblem";
               ar.created = t.created;
               store.assets.put(aid, ar);
               t.emblem = aid;
            }

            if (old != null) {
               if (!inPlace) {
                  store.templates.values().removeIf(xx -> old.id.equals(xx.replaces) && !xx.approved);
                  t.replaces = old.id;
                  t.ownerUuid = old.ownerUuid;
                  t.ownerName = old.ownerName;
                  t.approved = false;
                  store.templates.put(t.id, t);
                  store.setDirty();
                  DocService.ok(sp, L.k("Изменения шаблона ") + old.id + L.k(" отправлены на утверждение. Пока их не утвердят, действует прежний вариант."));

                  for (ServerPlayer admin : sp.server.getPlayerList().getPlayers()) {
                     if (admin != sp && Perms.has(admin, Perms.PASSPORT_ISSUE)) {
                        DocService.msg(
                           admin,
                           t.ownerName
                              + L.k(" предлагает изменения шаблона ")
                              + old.id
                              + " «"
                              + t.name
                              + L.k("». Утвердить: /seal passport template approve ")
                              + t.id,
                           ChatFormatting.YELLOW
                        );
                     }
                  }
               } else {
                  t.id = old.id;
                  t.ownerUuid = old.ownerUuid;
                  t.ownerName = old.ownerName;
                  t.created = old.created;
                  t.replaces = old.replaces;
                  t.approved = old.replaces.isEmpty() && (auto || old.approved);
                  store.templates.put(t.id, t);
                  store.setDirty();
                  broadcastTemplate(sp.server, t);
                  DocService.ok(sp, L.k("Шаблон ") + t.id + " «" + L.k(t.name) + L.k("» изменён. Уже выданные документы показываются по-новому."));
                  LOG.info("[Signed & Sealed] {} изменил шаблон {}", sp.getGameProfile().getName(), t.id);
               }
            } else {
               store.templates.put(t.id, t);
               store.setDirty();
               if (auto) {
                  DocService.ok(sp, L.k("Шаблон ") + t.id + " «" + L.k(t.name) + L.k("» создан. Выдать бланк: /seal passport give ") + t.id + L.k(" <игрок>"));
                  DocService.msg(sp, L.k("Изменить шаблон позже: /seal passport template edit ") + t.id, ChatFormatting.GRAY);
               } else {
                  DocService.ok(sp, L.k("Шаблон ") + t.id + " «" + L.k(t.name) + L.k("» отправлен на утверждение."));
                  DocService.msg(sp, L.k("Изменить шаблон позже: /seal passport template edit ") + t.id, ChatFormatting.GRAY);

                  for (ServerPlayer adminx : sp.server.getPlayerList().getPlayers()) {
                     if (adminx != sp && Perms.has(adminx, Perms.PASSPORT_ISSUE)) {
                        DocService.msg(
                           adminx,
                           t.ownerName
                              + L.k(" предлагает шаблон паспорта ")
                              + t.id
                              + " «"
                              + t.name
                              + L.k("». Утвердить: /seal passport template approve ")
                              + t.id,
                           ChatFormatting.YELLOW
                        );
                     }
                  }
               }

               LOG.info("[Signed & Sealed] {} создал шаблон паспорта {} ({})", new Object[]{t.ownerName, t.id, t.name});
            }
         }
      }
   }

   public static PassportTemplate installPreset(MinecraftServer server, String key, String ownerUuid, String ownerName) {
      PassportTemplate t = PassportTemplate.preset(key);
      if (t == null) {
         return null;
      } else {
         t.sanitize();
         DocStore store = DocStore.get(server);
         t.id = store.nextTemplateId();
         t.ownerUuid = ownerUuid;
         t.ownerName = ownerName;
         t.created = LocalDate.now().format(DATE);
         t.approved = true;
         store.templates.put(t.id, t);
         store.setDirty();
         LOG.info("[Signed & Sealed] {} установил готовый шаблон паспорта {} ({})", new Object[]{ownerName, t.id, t.name});
         return t;
      }
   }

   private static String blankCanonical(String id, String templateId, String issuer) {
      return "BL1;" + id + ";" + templateId + ";" + issuer;
   }

   public static ItemStack makeBlank(MinecraftServer server, PassportTemplate t, String issuerName) {
      DocStore store = DocStore.get(server);
      PassportModel p = new PassportModel();
      p.templateId = t.id;
      p.format = t.format;
      p.passId = UUID.randomUUID().toString();
      p.issuer = issuerName;
      DocStore.Rec r = new DocStore.Rec();
      r.hash = store.hmac(blankCanonical(p.passId, t.id, issuerName));
      r.author = issuerName;
      r.number = "BLANK";
      r.date = LocalDate.now().format(DATE);
      r.time = System.currentTimeMillis();
      store.passes.put(p.passId, r);
      store.setDirty();
      ItemStack stack = new ItemStack((ItemLike)ModItems.PASSPORT.get());
      NbtCodec.writePass(stack, p);
      return stack;
   }

   public static void issue(ServerPlayer sp, InteractionHand hand, Map<String, String> in, byte[] photo, int[] sig) {
      ItemStack stack = sp.getItemInHand(hand);
      if (stack.getItem() instanceof PassportItem) {
         PassportModel p = NbtCodec.readPass(stack);
         if (p.issued) {
            DocService.err(sp, L.k("Паспорт уже оформлен."));
         } else if (!Perms.has(sp, Perms.PASSPORT_FILL)) {
            DocService.err(sp, L.k("У вас нет права оформлять паспорт."));
         } else {
            DocStore store = DocStore.get(sp.server);
            PassportTemplate t = store.templates.get(p.templateId);
            DocStore.Rec rec = store.passes.get(p.passId);
            if (t != null && t.approved) {
               if (rec == null || !"BLANK".equals(rec.number) || !DocStore.safeEquals(store.hmac(blankCanonical(p.passId, p.templateId, p.issuer)), rec.hash)) {
                  DocService.err(sp, L.k("Бланк недействителен (подделан, использован или из другого мира)."));
               } else if (rec.revoked) {
                  DocService.err(sp, L.k("Бланк отозван администрацией."));
               } else {
                  PassportModel np = new PassportModel();
                  np.templateId = p.templateId;
                  np.format = t.format;
                  np.passId = p.passId;
                  np.issuer = p.issuer;
                  List<String> missing = new ArrayList<>();
                  boolean needPhoto = false;
                  boolean needSig = false;

                  for (PassportTemplate.Field f : t.fields) {
                     switch (f.type) {
                        case 2:
                           needPhoto |= f.required;
                        case 3:
                        case 5:
                        case 6:
                           break;
                        case 4:
                           needSig |= f.required;
                           break;
                        default:
                           if (!f.key.equals("issued")) {
                              String v = DocService.clean(in.getOrDefault(f.key, ""), 60, false);
                              if (f.type == 1 && !v.isEmpty() && !DATE_RE.matcher(v).matches()) {
                                 DocService.err(sp, L.k("Поле «") + f.label + L.k("»: дата в формате ДД.ММ.ГГГГ."));
                                 return;
                              }

                              if (v.isEmpty() && f.required) {
                                 missing.add(f.label);
                              }

                              if (!v.isEmpty()) {
                                 np.values.put(f.key, v);
                              }
                           }
                     }
                  }

                  if (!missing.isEmpty()) {
                     DocService.err(sp, L.k("Заполните поля: ") + String.join(", ", missing) + ".");
                  } else {
                     boolean hasPhotoField = t.fields.stream().anyMatch(f -> f.type == 2);
                     boolean hasSigField = t.fields.stream().anyMatch(f -> f.type == 4);
                     boolean gotPhoto = photo != null && photo.length > 0;
                     if (hasPhotoField && needPhoto && !gotPhoto) {
                        DocService.err(sp, L.k("Добавьте фото."));
                     } else if (!gotPhoto || hasPhotoField && DocService.validPng(photo, 16000, 512)) {
                        boolean gotSig = DocService.validSignature(sig);
                        if (hasSigField && needSig && !gotSig) {
                           DocService.err(sp, L.k("Нарисуйте подпись."));
                        } else {
                           String owner = sp.getUUID().toString();
                           if (gotPhoto) {
                              int mine = 0;

                              for (DocStore.AssetRec a : store.assets.values()) {
                                 if (a.ownerUuid.equals(owner) && a.kind.equals("photo")) {
                                    mine++;
                                 }
                              }

                              if (mine >= 20) {
                                 DocService.err(sp, L.k("Слишком много загруженных фото. Обратитесь к администрации."));
                                 return;
                              }

                              String aid = store.nextAssetId();

                              try {
                                 DocStore.saveAsset(sp.server, aid, photo);
                              } catch (IOException var22) {
                                 LOG.error("[Signed & Sealed] не удалось сохранить фото {}", aid, var22);
                                 DocService.err(sp, L.k("Не удалось сохранить фото."));
                                 return;
                              }

                              DocStore.AssetRec ar = new DocStore.AssetRec();
                              ar.ownerUuid = owner;
                              ar.kind = "photo";
                              ar.created = LocalDate.now().format(DATE);
                              store.assets.put(aid, ar);
                              np.photo = aid;
                           }

                           if (hasSigField && gotSig) {
                              np.signature = (int[])sig.clone();
                           }

                           np.issued = true;
                           np.holderName = sp.getGameProfile().getName();
                           np.holderUuid = owner;
                           np.date = LocalDate.now().format(DATE);
                           np.serial = (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + String.format("%06d", store.nextSerial());
                           np.sec = store.secCode(np.passId);
                           StampModel auto = store.stamps.get(t.autoStampId);
                           if (auto != null && auto.approved) {
                              np.stamps.add(new DocModel.StampRef(auto.id, 0, 800, 780, -10, 100));
                           }

                           rec.rehash(store.hmac(np.canonical()));
                           rec.old.clear();
                           rec.number = np.serial;
                           rec.author = np.issuer;
                           rec.authorUuid = np.holderUuid;
                           rec.date = np.date;
                           store.passes.put(np.passId, rec);
                           store.setDirty();
                           NbtCodec.writePass(stack, np);
                           DocService.ok(sp, (t.book() ? L.k("Паспорт оформлен. Номер: ") : L.k("Документ оформлен. Номер: ")) + np.serial + ".");
                           LOG.info("[Signed & Sealed] {} оформил паспорт {} ({})", new Object[]{np.holderName, np.passId, np.serial});
                        }
                     } else {
                        DocService.err(sp, L.k("Фото не подходит (PNG до ") + "16" + L.k(" КБ)."));
                     }
                  }
               }
            } else {
               DocService.err(sp, L.k("Шаблон этого бланка недоступен."));
            }
         }
      }
   }

   public static boolean canEdit(ServerPlayer sp, PassportTemplate t) {
      return t == null ? false : t.ownerUuid.equals(sp.getUUID().toString()) || Perms.has(sp, Perms.PASSPORT_ISSUE) || Perms.has(sp, Perms.ADMIN);
   }

   public static void edit(ServerPlayer sp, InteractionHand hand, Map<String, String> in, byte[] photo, int[] sig) {
      ItemStack stack = sp.getItemInHand(hand);
      if (stack.getItem() instanceof PassportItem) {
         PassportModel p = NbtCodec.readPass(stack);
         DocStore store = DocStore.get(sp.server);
         PassportTemplate t = store.templates.get(p.templateId);
         if (p.issued && t != null) {
            if (!canEdit(sp, t)) {
               DocService.err(sp, L.k("Менять данные может только организация, выдавшая документ, или администрация."));
            } else if (check(store, p, false).status() != DocService.Status.OK) {
               DocService.err(sp, L.k("Документ не прошёл проверку подлинности — изменить его нельзя."));
            } else {
               List<String> missing = new ArrayList<>();
               Map<String, String> vals = new LinkedHashMap<>();

               for (PassportTemplate.Field f : t.fields) {
                  if (PassportTemplate.playerInput(f)) {
                     String v = DocService.clean(in.getOrDefault(f.key, ""), 60, false);
                     if (f.type == 1 && !v.isEmpty() && !DATE_RE.matcher(v).matches()) {
                        DocService.err(sp, L.k("Поле «") + f.label + L.k("»: дата в формате ДД.ММ.ГГГГ."));
                        return;
                     }

                     if (v.isEmpty() && f.required) {
                        missing.add(f.label);
                     }

                     if (!v.isEmpty()) {
                        vals.put(f.key, v);
                     }
                  }
               }

               if (!missing.isEmpty()) {
                  DocService.err(sp, L.k("Заполните поля: ") + String.join(", ", missing) + ".");
               } else {
                  for (Entry<String, String> e : p.values.entrySet()) {
                     PassportTemplate.Field fx = t.find(e.getKey());
                     if (fx != null && !PassportTemplate.playerInput(fx)) {
                        vals.put(e.getKey(), e.getValue());
                     }
                  }

                  boolean hasPhotoField = t.fields.stream().anyMatch(fx -> fx.type == 2);
                  if (photo != null && photo.length > 0) {
                     if (!hasPhotoField || !DocService.validPng(photo, 16000, 512)) {
                        DocService.err(sp, L.k("Фото не подходит (PNG до ") + "16" + L.k(" КБ)."));
                        return;
                     }

                     String aid = store.nextAssetId();

                     try {
                        DocStore.saveAsset(sp.server, aid, photo);
                     } catch (IOException var14) {
                        DocService.err(sp, L.k("Не удалось сохранить фото."));
                        return;
                     }

                     DocStore.AssetRec ar = new DocStore.AssetRec();
                     ar.ownerUuid = p.holderUuid.isEmpty() ? sp.getUUID().toString() : p.holderUuid;
                     ar.kind = "photo";
                     ar.created = LocalDate.now().format(DATE);
                     store.assets.put(aid, ar);
                     p.photo = aid;
                  }

                  if (DocService.validSignature(sig)) {
                     p.signature = (int[])sig.clone();
                  }

                  p.values.clear();
                  p.values.putAll(vals);
                  DocStore.Rec r = store.passes.get(p.passId);
                  r.rehash(store.hmac(p.canonical()));
                  store.setDirty();
                  NbtCodec.writePass(stack, p);
                  DocService.ok(sp, L.k("Данные документа изменены. Старые копии теперь недействительны."));
                  LOG.info("[Signed & Sealed] {} изменил данные документа {} ({})", new Object[]{sp.getGameProfile().getName(), p.passId, p.serial});
               }
            }
         }
      }
   }

   public static DocService.VerifyResult verify(MinecraftServer server, ItemStack stack) {
      List<String> lines = new ArrayList<>();
      if (!(stack.getItem() instanceof PassportItem)) {
         return new DocService.VerifyResult(DocService.Status.NOT_DOC, List.of(L.k("Это не паспорт.")));
      } else {
         DocStore store = DocStore.get(server);
         PassportModel p = NbtCodec.readPass(stack);
         if (!p.issued) {
            lines.add(L.k("Бланк паспорта: силы не имеет."));
            return new DocService.VerifyResult(DocService.Status.NOT_ISSUED, lines);
         } else {
            return check(store, p, true);
         }
      }
   }

   static DocService.VerifyResult check(DocStore store, PassportModel p, boolean detailed) {
      List<String> lines = new ArrayList<>();
      DocStore.Rec r = store.passes.get(p.passId);
      if (r == null) {
         lines.add(L.k("Паспорта нет в реестре этого сервера."));
         lines.add(L.k("Вероятно, подделка или паспорт из другого мира."));
         return new DocService.VerifyResult(DocService.Status.UNKNOWN, lines);
      } else {
         String h = store.hmac(p.canonical());
         if (!DocStore.safeEquals(h, r.hash)) {
            if (r.isOld(h)) {
               lines.add(L.k("Это устаревшая копия: позже в паспорт поставили печать или отметку."));
               lines.add(L.k("Действителен только актуальный экземпляр паспорта."));
               return new DocService.VerifyResult(DocService.Status.OUTDATED, lines);
            } else {
               lines.add(L.k("Данные паспорта не совпадают с оформленными."));
               lines.add(L.k("Паспорт изменён вручную: ПОДДЕЛКА."));
               return new DocService.VerifyResult(DocService.Status.FORGED, lines);
            }
         } else if (r.revoked) {
            lines.add(L.k("Паспорт аннулирован администрацией."));
            return new DocService.VerifyResult(DocService.Status.REVOKED, lines);
         } else {
            for (DocModel.StampRef ref : p.stamps) {
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
               PassportTemplate t = store.templates.get(p.templateId);
               lines.add((t == null ? L.k("Паспорт") : L.k(t.name)) + " № " + p.serial + L.k(", выдан ") + p.date);
               boolean personal = t == null || t.format == 0 || t.format == 1;
               lines.add((personal ? L.k("Владелец: ") : L.k("Оформил: ")) + p.holderName);
               if (!p.sec.isEmpty()) {
                  lines.add(L.k("Код защиты: ") + p.sec);
               }

               for (Entry<String, String> e : p.values.entrySet()) {
                  PassportTemplate.Field f = t == null ? null : t.find(e.getKey());
                  lines.add((f == null ? e.getKey() : f.label) + ": " + e.getValue());
               }

               for (DocModel.StampRef refx : p.stamps) {
                  StampModel sm = store.stamps.get(refx.id);
                  if (sm == null) {
                     lines.add(L.k("Отметка ") + refx.id + L.k(": удалена из реестра!"));
                  } else {
                     String st = sm.approved ? "" : L.k(" — ОТОЗВАНА");
                     if (sm.kind == 1) {
                        lines.add((refx.dir == 2 ? L.k("Выезд ") : L.k("Въезд ")) + refx.date + ", " + sm.org + st);
                     } else if (sm.kind == 2) {
                        lines.add(
                           L.k("Виза ")
                              + refx.num
                              + " ("
                              + DocModel.StampRef.CATEGORIES[refx.cat].toLowerCase()
                              + L.k(") до ")
                              + refx.until
                              + ", "
                              + sm.org
                              + visaState(refx)
                              + st
                        );
                     } else {
                        lines.add(L.k("Печать «") + sm.org + "»" + (sm.approved ? "" : L.k(": ОТОЗВАНА")));
                     }
                  }
               }
            }

            return new DocService.VerifyResult(DocService.Status.OK, lines);
         }
      }
   }

   private static String visaState(DocModel.StampRef ref) {
      try {
         LocalDate until = LocalDate.parse(ref.until, DATE);
         return until.isBefore(LocalDate.now()) ? L.k(" — ИСТЕКЛА") : "";
      } catch (Exception var2) {
         return "";
      }
   }

   static boolean addStamp(ServerPlayer sp, ItemStack stack, DocStore store, StampModel sm, DocModel.StampRef want, int days, boolean forged) {
      PassportModel p = NbtCodec.readPass(stack);
      if (!p.issued) {
         DocService.err(sp, L.k("Сначала оформите паспорт."));
         return false;
      } else if (!p.forged && check(store, p, false).status() != DocService.Status.OK) {
         DocService.err(sp, L.k("Паспорт не прошёл проверку подлинности — отметка не поставлена."));
         return false;
      } else {
         PassportTemplate t = store.templates.get(p.templateId);
         int spreads = t == null ? 3 : t.extraSides();
         boolean mark = sm.kind != 0;
         if (mark && t != null && !t.book()) {
            DocService.err(sp, L.k("Пограничные штампы и визы ставятся только в паспорт."));
            return false;
         } else {
            int page;
            if (want == null) {
               page = mark ? firstFreeVisaPage(p, spreads) : 0;
            } else {
               page = want.page;
            }

            if (page >= 0 && page <= spreads) {
               if (!mark || page != 0 || t != null && !t.book()) {
                  int onPage = 0;

                  for (DocModel.StampRef r : p.stamps) {
                     if (r.page == page) {
                        onPage++;
                     }
                  }

                  int limit = page != 0 && (t == null || t.book()) ? 8 : 4;
                  if (onPage >= limit) {
                     DocService.err(sp, L.k("На этой странице уже нет места (") + limit + L.k(" отметок)."));
                     return false;
                  } else {
                     DocModel.StampRef ref;
                     if (want == null) {
                        ref = page != 0 && (t == null || t.book())
                           ? new DocModel.StampRef(sm.id, page, onPage % 2 == 0 ? 250 : 750, 220 + onPage / 2 * 190, onPage * 37 % 21 - 10, 100)
                           : new DocModel.StampRef(sm.id, 0, 760, 760 - onPage % 3 * 60, onPage * 37 % 31 - 15, 100);
                     } else {
                        ref = new DocModel.StampRef(sm.id, page, want.x, want.y, want.rot, want.scale);
                     }

                     String today = LocalDate.now().format(DATE);
                     if (sm.kind == 1) {
                        int dir = want != null && want.dir != 0 ? want.dir : 1;
                        ref.extra(dir, want == null ? 0 : want.cat, today, "", "");
                     } else if (sm.kind == 2) {
                        int d = 30;

                        for (int v : DocModel.StampRef.VISA_DAYS) {
                           if (v == days) {
                              d = v;
                           }
                        }

                        String num = String.format("V%08d", RNG.nextInt(100000000));
                        ref.extra(0, want == null ? 0 : want.cat, today, LocalDate.now().plusDays((long)d).format(DATE), num);
                        if (want != null) {
                           ref.scale = Math.max(ref.scale, 90);
                        }
                     }

                     ref.forged = forged;
                     p.stamps.add(ref);
                     if (!p.forged) {
                        DocStore.Rec rx = store.passes.get(p.passId);
                        rx.rehash(store.hmac(p.canonical()));
                        store.setDirty();
                     }

                     NbtCodec.writePass(stack, p);
                     return true;
                  }
               } else {
                  DocService.err(sp, L.k("Пограничные штампы и визы ставятся на страницы для виз — перелистните паспорт."));
                  return false;
               }
            } else {
               DocService.err(sp, L.k("В паспорте нет такой страницы."));
               return false;
            }
         }
      }
   }

   private static int firstFreeVisaPage(PassportModel p, int spreads) {
      for (int page = 1; page <= spreads; page++) {
         int n = 0;

         for (DocModel.StampRef r : p.stamps) {
            if (r.page == page) {
               n++;
            }
         }

         if (n < 8) {
            return page;
         }
      }

      return spreads >= 1 ? spreads : 0;
   }

   public static void sendTemplate(ServerPlayer sp, String id) {
      PassportTemplate t = DocStore.get(sp.server).templates.get(id);
      Net.sendTo(sp, new Packets.TemplateData(id, t != null, t == null ? new PassportTemplate() : t));
   }

   public static void sendAsset(ServerPlayer sp, String id) {
      DocStore store = DocStore.get(sp.server);
      DocStore.AssetRec a = store.assets.get(id);
      byte[] png = a != null && canSeeAsset(sp, store, id, a) ? DocStore.loadAsset(sp.server, id) : new byte[0];
      Net.sendTo(sp, new Packets.AssetData(id, png.length > 0, png));
   }

   public static void grantAsset(ServerPlayer viewer, String assetId) {
      if (assetId != null && !assetId.isEmpty()) {
         GRANTS.computeIfAbsent(viewer.getUUID(), k -> new HashMap<>()).put(assetId, System.currentTimeMillis() + 600000L);
      }
   }

   static boolean canSeeAsset(ServerPlayer sp, DocStore store, String id, DocStore.AssetRec a) {
      if (a.ownerUuid.equals(sp.getUUID().toString()) || "paper_pub".equals(a.kind)) {
         return true;
      } else if ("paper".equals(a.kind)) {
         return false;
      } else if (!Perms.has(sp, Perms.PASSPORT_ISSUE) && !Perms.has(sp, Perms.ADMIN)) {
         if ("emblem".equals(a.kind)) {
            for (PassportTemplate t : store.templates.values()) {
               if (t.approved && id.equals(t.emblem)) {
                  return true;
               }
            }

            return false;
         } else {
            Map<String, Long> g = GRANTS.get(sp.getUUID());
            if (g != null) {
               Long until = g.get(id);
               if (until != null && until > System.currentTimeMillis()) {
                  return true;
               }
            }

            Inventory inv = sp.getInventory();

            for (int i = 0; i < inv.getContainerSize(); i++) {
               ItemStack st = inv.getItem(i);
               if (st.getItem() instanceof PassportItem && id.equals(NbtCodec.readPass(st).photo)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }

   public static void forgetGrants(ServerPlayer sp) {
      GRANTS.remove(sp.getUUID());
   }
}
