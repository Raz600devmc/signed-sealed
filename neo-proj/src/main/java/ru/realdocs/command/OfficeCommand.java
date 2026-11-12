package ru.realdocs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.realdocs.L;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.OfficeData;
import ru.realdocs.server.ListService;
import ru.realdocs.server.BureauService;

/** Commands for 2.4.0: /seal sign, /seal org, /seal mail, /seal template, /seal paper subscribe. */
public final class OfficeCommand {
   private static final SuggestionProvider<CommandSourceStack> REQUESTS = (ctx, b) -> SharedSuggestionProvider.suggest(
         OfficeData.get(ctx.getSource().getServer()).requests.keySet(), b
      );
   private static final SuggestionProvider<CommandSourceStack> TITLES = (ctx, b) -> {
      List<String> names = new ArrayList<>();

      for (DocStore.TitleRec r : DocStore.get(ctx.getSource().getServer()).titles.values()) {
         names.add(r.name.contains(" ") ? "\"" + r.name + "\"" : r.name);
      }

      return SharedSuggestionProvider.suggest(names, b);
   };
   private static final SuggestionProvider<CommandSourceStack> PLAYERS = (ctx, b) -> SharedSuggestionProvider.suggest(
         OfficeData.get(ctx.getSource().getServer()).known.values(), b
      );

   private OfficeCommand() {
   }

   @SubscribeEvent
   public static void onRegister(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
      d.register(
         Commands.literal("seal")
            .then(
               Commands.literal("sign")
                  .then(Commands.literal("list").executes(c -> run(c, "sign_list", new CompoundTag())))
                  .then(Commands.literal("open").then(Commands.argument("id", StringArgumentType.word()).suggests(REQUESTS).executes(c -> id(c, "sign_open"))))
                  .then(Commands.literal("cancel").then(Commands.argument("id", StringArgumentType.word()).suggests(REQUESTS).executes(c -> id(c, "sign_cancel"))))
                  .then(Commands.literal("decline").then(Commands.argument("id", StringArgumentType.word()).suggests(REQUESTS).executes(c -> id(c, "sign_decline"))))
                  .then(
                     Commands.literal("ask")
                        .then(
                           Commands.argument("players", StringArgumentType.greedyString())
                              .suggests(PLAYERS)
                              .executes(c -> {
                                 CompoundTag t = new CompoundTag();
                                 t.putString("To", StringArgumentType.getString(c, "players"));
                                 return run(c, "sign_ask", t);
                              })
                        )
                  )
            )
            .then(
               Commands.literal("org")
                  .executes(c -> open(c, "orgs", "orgs"))
                  .then(
                     Commands.literal("create")
                        .then(
                           Commands.argument("name", StringArgumentType.greedyString())
                              .executes(c -> {
                                 CompoundTag t = new CompoundTag();
                                 t.putString("Name", StringArgumentType.getString(c, "name"));
                                 run(c, "org_create", t);
                                 return open(c, null, "orgs");
                              })
                        )
                  )
            )
            .then(Commands.literal("mail").executes(c -> open(c, "lists", "lists")).then(Commands.literal("lists").executes(c -> open(c, "lists", "lists"))))
            .then(Commands.literal("template").executes(c -> open(c, "templates", "templates")))
            .then(
               Commands.literal("paper")
                  .then(Commands.literal("subscribe").then(Commands.argument("title", StringArgumentType.string()).suggests(TITLES).executes(c -> sub(c, true))))
                  .then(Commands.literal("unsubscribe").then(Commands.argument("title", StringArgumentType.string()).suggests(TITLES).executes(c -> sub(c, false))))
            )
      );
   }

   static ServerPlayer player(CommandContext<CommandSourceStack> c) {
      if (c.getSource().getEntity() instanceof ServerPlayer sp) {
         return sp;
      } else {
         c.getSource().sendFailure(L.c(L.k("Команда доступна только игроку.")));
         return null;
      }
   }

   static int run(CommandContext<CommandSourceStack> c, String action, CompoundTag t) {
      ServerPlayer sp = player(c);
      if (sp == null) {
         return 0;
      } else {
         BureauService.handle(sp, action, t);
         return 1;
      }
   }

   static int id(CommandContext<CommandSourceStack> c, String action) {
      CompoundTag t = new CompoundTag();
      t.putString("Id", StringArgumentType.getString(c, "id"));
      return run(c, action, t);
   }

   /** Sends the data for a screen and asks the client to open it. */
   static int open(CommandContext<CommandSourceStack> c, String action, String screen) {
      ServerPlayer sp = player(c);
      if (sp == null) {
         return 0;
      } else {
         if (action != null) {
            BureauService.handle(sp, action, new CompoundTag());
         }

         CompoundTag t = new CompoundTag();
         t.putString("Screen", screen);
         BureauService.send(sp, "open", t);
         return 1;
      }
   }

   static int sub(CommandContext<CommandSourceStack> c, boolean on) {
      ServerPlayer sp = player(c);
      if (sp == null) {
         return 0;
      } else {
         return ListService.subscribe(sp, StringArgumentType.getString(c, "title"), on) == null ? 1 : 0;
      }
   }
}
