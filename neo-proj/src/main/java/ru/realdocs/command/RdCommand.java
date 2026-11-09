package ru.realdocs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import ru.realdocs.L;
import ru.realdocs.Perms;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;
import ru.realdocs.server.AdminService;
import ru.realdocs.server.DocService;
import ru.realdocs.server.PassportService;

public final class RdCommand {
   private static final SuggestionProvider<CommandSourceStack> STAMP_IDS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         DocStore.get(((CommandSourceStack)ctx.getSource()).getServer()).stamps.keySet(), builder
      );
   private static final SuggestionProvider<CommandSourceStack> TEMPLATE_IDS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         DocStore.get(((CommandSourceStack)ctx.getSource()).getServer()).templates.keySet(), builder
      );
   private static final SuggestionProvider<CommandSourceStack> PRESETS = (ctx, builder) -> SharedSuggestionProvider.suggest(
         PassportTemplate.PRESET_KEYS, builder
      );

   private RdCommand() {
   }

   @SubscribeEvent
   public static void onRegister(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
      LiteralCommandNode<CommandSourceStack> root = d.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                              "seal"
                           )
                           .then(Commands.literal("admin").executes(RdCommand::admin)))
                        .then(Commands.literal("verify").executes(RdCommand::verify)))
                     .then(Commands.literal("revoke").executes(RdCommand::revokeDoc)))
                  .then(Commands.literal("show").then(Commands.argument("target", EntityArgument.player()).executes(RdCommand::show))))
               .then(
                  ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("passport")
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("designer").executes(RdCommand::passDesigner))
                                    .then(Commands.argument("preset", StringArgumentType.word()).suggests(PRESETS).executes(RdCommand::passDesignerPreset))
                              ))
                           .then(Commands.literal("revoke").executes(RdCommand::passRevoke)))
                        .then(
                           Commands.literal("give")
                              .then(
                                 Commands.argument("template", StringArgumentType.word())
                                    .suggests(TEMPLATE_IDS)
                                    .then(Commands.argument("targets", EntityArgument.players()).executes(RdCommand::passGive))
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                             "template"
                                          )
                                          .then(
                                             ((LiteralArgumentBuilder)Commands.literal("list").executes(c -> tplList(c, false)))
                                                .then(Commands.literal("pending").executes(c -> tplList(c, true)))
                                          ))
                                       .then(
                                          Commands.literal("approve")
                                             .then(
                                                Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATE_IDS).executes(RdCommand::tplApprove)
                                             )
                                       ))
                                    .then(
                                       Commands.literal("edit")
                                          .then(Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATE_IDS).executes(RdCommand::tplEdit))
                                    ))
                                 .then(
                                    Commands.literal("preset")
                                       .then(Commands.argument("country", StringArgumentType.word()).suggests(PRESETS).executes(RdCommand::tplPreset))
                                 ))
                              .then(
                                 Commands.literal("revoke")
                                    .then(Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATE_IDS).executes(RdCommand::tplRevoke))
                              ))
                           .then(
                              Commands.literal("delete")
                                 .then(Commands.argument("template", StringArgumentType.word()).suggests(TEMPLATE_IDS).executes(RdCommand::tplDelete))
                           )
                     )
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                    "stamp"
                                 )
                                 .then(Commands.literal("designer").executes(RdCommand::designer)))
                              .then(
                                 ((LiteralArgumentBuilder)Commands.literal("list").executes(c -> list(c, false)))
                                    .then(Commands.literal("pending").executes(c -> list(c, true)))
                              ))
                           .then(
                              Commands.literal("approve")
                                 .then(Commands.argument("id", StringArgumentType.word()).suggests(STAMP_IDS).executes(RdCommand::approve))
                           ))
                        .then(
                           Commands.literal("give")
                              .then(
                                 Commands.argument("id", StringArgumentType.word())
                                    .suggests(STAMP_IDS)
                                    .then(Commands.argument("targets", EntityArgument.players()).executes(RdCommand::give))
                              )
                        ))
                     .then(
                        Commands.literal("revoke")
                           .then(Commands.argument("id", StringArgumentType.word()).suggests(STAMP_IDS).executes(RdCommand::revokeStamp))
                     ))
                  .then(
                     Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).suggests(STAMP_IDS).executes(RdCommand::deleteStamp))
                  )
            )
      );
      d.register(
         Commands.literal("seal")
            .then(
               Commands.literal("paper")
                  .then(Commands.literal("titles").executes(RdCommand::paperTitles))
                  .then(
                     Commands.literal("staff")
                        .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player()).executes(RdCommand::paperStaffAdd)))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).executes(RdCommand::paperStaffRemove)))
                  )
                  .then(Commands.literal("revoke").executes(c -> paperRevoke(c, true)))
                  .then(Commands.literal("restore").executes(c -> paperRevoke(c, false)))
            )
      );
      d.register((LiteralArgumentBuilder)Commands.literal("signedsealed").redirect(root));
   }

   private static int paperTitles(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      java.util.List<String> lines = ru.realdocs.server.PaperService.describeTitles(src.getServer());
      if (lines.isEmpty()) {
         ok(src, L.k("Изданий пока нет."));
      } else {
         ok(src, L.k("Издания: ") + lines.size());

         for (String l : lines) {
            src.sendSuccess(() -> L.c(" • " + l).withStyle(ChatFormatting.GRAY), false);
         }
      }

      return lines.size();
   }

   private static int paperStaffAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         ServerPlayer target = EntityArgument.getPlayer(c, "player");
         String err = ru.realdocs.server.PaperService.staffEdit(sp, target.getGameProfile().getName(), target.getUUID().toString(), true);
         if (err != null) {
            src.sendFailure(L.c(err));
            return 0;
         } else {
            ok(src, target.getGameProfile().getName() + L.k(" добавлен в редакцию."));
            return 1;
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static int paperStaffRemove(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         String name = StringArgumentType.getString(c, "name");
         String err = ru.realdocs.server.PaperService.staffEdit(sp, name, null, false);
         if (err != null) {
            src.sendFailure(L.c(err));
            return 0;
         } else {
            ok(src, name + L.k(" исключён из редакции."));
            return 1;
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static int paperRevoke(CommandContext<CommandSourceStack> c, boolean revoke) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.ADMIN)) {
         return deny(src);
      } else if (src.getEntity() instanceof ServerPlayer sp) {
         ItemStack stack = sp.getMainHandItem();
         if (!(stack.getItem() instanceof ru.realdocs.item.NewspaperItem)) {
            src.sendFailure(L.c(L.k("Возьмите выпуск газеты в основную руку.")));
            return 0;
         } else {
            String err = ru.realdocs.server.PaperService.setRevoked(sp.server, ru.realdocs.data.PaperCodec.ref(stack).id, revoke);
            if (err != null) {
               src.sendFailure(L.c(err));
               return 0;
            } else {
               ok(src, revoke ? L.k("Выпуск отозван: проверка покажет «Отозван».") : L.k("Выпуск снова действителен."));
               return 1;
            }
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static boolean allowed(CommandSourceStack src, PermissionNode<Boolean> node) {
      return src.getEntity() instanceof ServerPlayer sp ? Perms.has(sp, node) : true;
   }

   private static int deny(CommandSourceStack src) {
      src.sendFailure(L.c(L.k("Недостаточно прав.")));
      return 0;
   }

   private static void ok(CommandSourceStack src, String text) {
      src.sendSuccess(() -> L.c(text).withStyle(ChatFormatting.GREEN), false);
   }

   private static int result(CommandSourceStack src, AdminService.Result r) {
      if (r.ok()) {
         ok(src, r.msg());
      } else {
         src.sendFailure(L.c(r.msg()));
      }

      return r.ok() ? 1 : 0;
   }

   private static int admin(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         if (!AdminService.isAdmin(sp)) {
            return deny(src);
         } else {
            AdminService.sendPanel(sp, "", "", true);
            return 1;
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static int verify(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!(src.getEntity() instanceof ServerPlayer sp)) {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      } else if (!Perms.has(sp, Perms.VERIFY)) {
         return deny(src);
      } else {
         ItemStack stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
         if (!(stack.getItem() instanceof DocumentItem)) {
            stack = sp.getItemInHand(InteractionHand.OFF_HAND);
         }

         DocService.VerifyResult vr = DocService.verify(sp.server, stack);

         ChatFormatting color = switch (vr.status()) {
            case OK -> ChatFormatting.GREEN;
            case FORGED, UNKNOWN, REVOKED -> ChatFormatting.RED;
            case OUTDATED -> ChatFormatting.GOLD;
            default -> ChatFormatting.YELLOW;
         };

         String head = switch (vr.status()) {
            case OK -> L.k("ПОДЛИННЫЙ ДОКУМЕНТ");
            case FORGED -> L.k("ПОДДЕЛКА");
            case UNKNOWN -> L.k("НЕ НАЙДЕН В РЕЕСТРЕ");
            case REVOKED -> L.k("ДОКУМЕНТ ОТОЗВАН");
            case OUTDATED -> L.k("УСТАРЕВШАЯ КОПИЯ");
            case NOT_ISSUED -> L.k("НЕ ВЫПУЩЕН");
            default -> L.k("НЕ ДОКУМЕНТ");
         };
         sp.sendSystemMessage(L.c(L.k("[Документы] ") + head).withStyle(new ChatFormatting[]{color, ChatFormatting.BOLD}));

         for (String line : vr.lines()) {
            sp.sendSystemMessage(L.c(" " + line).withStyle(ChatFormatting.GRAY));
         }

         return 1;
      }
   }

   private static int revokeDoc(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.ADMIN)) {
         return deny(src);
      } else if (src.getEntity() instanceof ServerPlayer sp) {
         ItemStack stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
         DocModel d = NbtCodec.readDoc(stack);
         if (stack.getItem() instanceof DocumentItem && d.issued) {
            return result(src, AdminService.setRevoked(sp.server, false, d.docId, true));
         } else {
            src.sendFailure(L.c(L.k("В основной руке нет выпущенного документа.")));
            return 0;
         }
      } else {
         src.sendFailure(L.c(L.k("Возьмите документ в руку (команда для игрока).")));
         return 0;
      }
   }

   private static int designer(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         if (!Perms.has(sp, Perms.DESIGN)) {
            return deny(src);
         } else {
            Net.sendTo(sp, new Packets.OpenDesigner());
            return 1;
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static int list(CommandContext<CommandSourceStack> c, boolean pendingOnly) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.ISSUE)) {
         return deny(src);
      } else {
         DocStore store = DocStore.get(src.getServer());
         int n = 0;

         for (StampModel m : store.stamps.values()) {
            if (!pendingOnly || !m.approved) {
               n++;
               String state = m.approved ? L.k("утверждена") : L.k("ожидает");
               ChatFormatting col = m.approved ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
               src.sendSuccess(
                  () -> L.c(m.id + " «" + m.org + "» — " + m.ownerName + ", " + m.created + ", " + state + (m.hasImage ? L.k(", с изображением") : ""))
                        .withStyle(col),
                  false
               );
            }
         }

         if (n == 0) {
            ok(src, pendingOnly ? L.k("Нет макетов, ожидающих утверждения.") : L.k("Печатей пока нет."));
         }

         return n;
      }
   }

   private static String stampArg(CommandContext<CommandSourceStack> c) {
      return StringArgumentType.getString(c, "id").toUpperCase(Locale.ROOT);
   }

   private static int approve(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.ISSUE) ? deny(src) : result(src, AdminService.approveStamp(src.getServer(), stampArg(c)));
   }

   private static int give(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.ISSUE)
         ? deny(src)
         : result(src, AdminService.giveStamp(src.getServer(), stampArg(c), EntityArgument.getPlayers(c, "targets")));
   }

   private static int revokeStamp(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.ISSUE) ? deny(src) : result(src, AdminService.revokeStamp(src.getServer(), stampArg(c)));
   }

   private static int deleteStamp(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.ISSUE) ? deny(src) : result(src, AdminService.deleteStamp(src.getServer(), stampArg(c)));
   }

   private static int passDesigner(CommandContext<CommandSourceStack> c) {
      return openDesigner(c, "");
   }

   private static int passDesignerPreset(CommandContext<CommandSourceStack> c) {
      return openDesigner(c, StringArgumentType.getString(c, "preset"));
   }

   private static int openDesigner(CommandContext<CommandSourceStack> c, String preset) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         if (!Perms.has(sp, Perms.DESIGN)) {
            return deny(src);
         } else if (!preset.isEmpty() && PassportTemplate.preset(preset) == null) {
            src.sendFailure(L.c(L.k("Готовые шаблоны: ") + String.join(", ", PassportTemplate.PRESET_KEYS) + "."));
            return 0;
         } else {
            Net.sendTo(sp, new Packets.OpenTemplateEditor(preset));
            return 1;
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }

   private static String tplArg(CommandContext<CommandSourceStack> c) {
      return StringArgumentType.getString(c, "template").toUpperCase(Locale.ROOT);
   }

   private static int tplList(CommandContext<CommandSourceStack> c, boolean pendingOnly) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.PASSPORT_ISSUE)) {
         return deny(src);
      } else {
         int n = 0;

         for (PassportTemplate t : DocStore.get(src.getServer()).templates.values()) {
            if (!pendingOnly || !t.approved) {
               n++;
               ChatFormatting col = t.approved ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
               src.sendSuccess(
                  () -> L.c(
                           t.id
                              + " «"
                              + L.k(t.name)
                              + "» — "
                              + t.ownerName
                              + ", "
                              + t.created
                              + ", "
                              + (t.approved ? L.k("утверждён") : L.k("ожидает"))
                              + L.k(", полей: ")
                              + t.fields.size()
                        )
                        .withStyle(col),
                  false
               );
            }
         }

         if (n == 0) {
            ok(src, pendingOnly ? L.k("Нет шаблонов на утверждении.") : L.k("Шаблонов пока нет."));
         }

         return n;
      }
   }

   private static int tplEdit(CommandContext<CommandSourceStack> c) {
      if (((CommandSourceStack)c.getSource()).getEntity() instanceof ServerPlayer sp) {
         PassportService.openTemplateEdit(sp, tplArg(c));
         return 1;
      } else {
         return 0;
      }
   }

   private static int tplApprove(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.PASSPORT_ISSUE) ? deny(src) : result(src, AdminService.approveTpl(src.getServer(), tplArg(c)));
   }

   private static int tplPreset(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.PASSPORT_ISSUE)) {
         return deny(src);
      } else {
         ServerPlayer sp = src.getEntity() instanceof ServerPlayer p ? p : null;
         return result(
            src,
            AdminService.installPreset(
               src.getServer(),
               StringArgumentType.getString(c, "country"),
               sp == null ? "" : sp.getUUID().toString(),
               sp == null ? "Администрация" : sp.getGameProfile().getName()
            )
         );
      }
   }

   private static int tplRevoke(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.PASSPORT_ISSUE) ? deny(src) : result(src, AdminService.revokeTpl(src.getServer(), tplArg(c)));
   }

   private static int tplDelete(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      return !allowed(src, Perms.PASSPORT_ISSUE) ? deny(src) : result(src, AdminService.deleteTpl(src.getServer(), tplArg(c)));
   }

   private static int passGive(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.PASSPORT_ISSUE)) {
         return deny(src);
      } else {
         String issuer = src.getEntity() instanceof ServerPlayer sp ? sp.getGameProfile().getName() : "Администрация";
         return result(src, AdminService.giveBlanks(src.getServer(), tplArg(c), issuer, EntityArgument.getPlayers(c, "targets"), 1));
      }
   }

   private static int passRevoke(CommandContext<CommandSourceStack> c) {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (!allowed(src, Perms.ADMIN)) {
         return deny(src);
      } else if (src.getEntity() instanceof ServerPlayer sp) {
         ItemStack stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
         if (!(stack.getItem() instanceof PassportItem)) {
            src.sendFailure(L.c(L.k("В основной руке нет паспорта.")));
            return 0;
         } else {
            return result(src, AdminService.setRevoked(sp.server, true, NbtCodec.readPass(stack).passId, true));
         }
      } else {
         src.sendFailure(L.c(L.k("Возьмите паспорт в руку (команда для игрока).")));
         return 0;
      }
   }

   private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
      CommandSourceStack src = (CommandSourceStack)c.getSource();
      if (src.getEntity() instanceof ServerPlayer sp) {
         if (!Perms.has(sp, Perms.SHOW)) {
            return deny(src);
         } else {
            ServerPlayer target = EntityArgument.getPlayer(c, "target");
            ItemStack stack = sp.getItemInHand(InteractionHand.MAIN_HAND);
            boolean pass = stack.getItem() instanceof PassportItem;
            boolean doc = stack.getItem() instanceof DocumentItem;
            if (!pass && !doc) {
               src.sendFailure(L.c(L.k("Возьмите документ или паспорт в основную руку.")));
               return 0;
            } else {
               boolean issued = pass ? NbtCodec.readPass(stack).issued : NbtCodec.readDoc(stack).issued;
               if (!issued) {
                  src.sendFailure(L.c(L.k("Показывать можно только выпущенные документы.")));
                  return 0;
               } else if (target.distanceToSqr(sp) > 144.0) {
                  src.sendFailure(L.c(L.k("Игрок слишком далеко (до 12 блоков).")));
                  return 0;
               } else {
                  DocService.VerifyResult vr = pass ? PassportService.verify(sp.server, stack) : DocService.verify(sp.server, stack);
                  CompoundTag tag = pass ? NbtCodec.writePass(NbtCodec.readPass(stack)) : NbtCodec.writeDoc(NbtCodec.readDoc(stack));
                  if (pass) {
                     PassportService.grantAsset(target, NbtCodec.readPass(stack).photo);
                  }

                  Net.sendTo(target, new Packets.ViewRemote(pass, tag, vr.status().ordinal(), vr.lines(), sp.getGameProfile().getName()));
                  ok(src, L.k("Вы показали ") + (pass ? L.k("паспорт") : L.k("документ")) + L.k(" игроку ") + target.getGameProfile().getName() + ".");
                  return 1;
               }
            }
         }
      } else {
         src.sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return 0;
      }
   }
}
