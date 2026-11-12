package ru.realdocs.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import ru.realdocs.L;
import ru.realdocs.Perms;
import ru.realdocs.Plat;
import ru.realdocs.data.DocStore;
import ru.realdocs.data.OfficeData;
import ru.realdocs.net.Net;
import ru.realdocs.net.OfficePackets;

/** Entry point for the 2.4.0 office features: routes requests, keeps the player list, delivers items. */
public final class BureauService {
   private BureauService() {
   }

   public static void handle(ServerPlayer sp, String action, CompoundTag t) {
      switch (action) {
         case "names":
            sendNames(sp);
            break;
         case "lists":
         case "list_save":
         case "list_delete":
         case "paper_sub":
         case "mail_paper":
            ListService.handle(sp, action, t);
            break;
         case "orgs":
         case "org_create":
         case "org_save":
         case "org_delete":
         case "org_leave":
            OrgService.handle(sp, action, t);
            break;
         case "templates":
         case "tpl_save":
         case "tpl_delete":
            TemplateService.handle(sp, action, t);
            break;
         case "board_view":
         case "board_take":
         case "board_save":
            BoardService.handle(sp, action, t);
            break;
         case "reader_save":
            ReaderService.save(sp, t);
            break;
         case "reg_find":
            RegistryService.find(sp, t.getString("Query"), DigitalService.networkAt(sp, t));
            break;
         case "inbox":
         case "inbox_send":
         case "inbox_take":
         case "inbox_view":
         case "inbox_delete":
         case "inbox_return":
            InboxService.handle(sp, action, t);
            break;
         case "sign_ask":
         case "sign_open":
         case "sign_do":
         case "sign_decline":
         case "sign_cancel":
         case "sign_list":
            SignService.handle(sp, action, t);
      }
   }

   public static void send(ServerPlayer sp, String kind, CompoundTag t) {
      Net.sendTo(sp, new OfficePackets.Data(kind, t));
   }

   /** Every name that has joined the server, plus the "@" targets this player may mail to. */
   public static void sendNames(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      remember(sp);
      TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      names.addAll(od.known.values());

      for (ServerPlayer p : sp.server.getPlayerList().getPlayers()) {
         names.add(p.getGameProfile().getName());
      }

      CompoundTag t = new CompoundTag();
      t.put("Names", strings(names));
      t.put("Targets", strings(ListService.targets(sp)));
      List<String> orgs = new ArrayList<>();

      for (OfficeData.Org o : od.orgs.values()) {
         orgs.add(o.name);
      }

      t.put("Orgs", strings(orgs));
      send(sp, "names", t);
   }

   public static ListTag strings(Iterable<String> it) {
      ListTag l = new ListTag();

      for (String s : it) {
         l.add(StringTag.valueOf(s));
      }

      return l;
   }

   public static List<String> strings(ListTag l) {
      List<String> out = new ArrayList<>();

      for (int i = 0; i < l.size(); i++) {
         out.add(l.getString(i));
      }

      return out;
   }

   static void remember(ServerPlayer sp) {
      OfficeData od = OfficeData.get(sp.server);
      String uuid = sp.getUUID().toString();
      String name = sp.getGameProfile().getName();
      if (!name.equals(od.known.get(uuid))) {
         od.known.put(uuid, name);
         od.setDirty();
      }
   }

   /** Looks a player up by name among everyone who has ever joined. Returns {uuid, name} or null. */
   public static String[] findPlayer(MinecraftServer server, String name) {
      if (name != null && !name.isBlank()) {
         ServerPlayer online = server.getPlayerList().getPlayerByName(name.trim());
         if (online != null) {
            return new String[]{online.getUUID().toString(), online.getGameProfile().getName()};
         } else {
            OfficeData od = OfficeData.get(server);
            String u = od.uuidOf(name);
            if (u != null) {
               return new String[]{u, od.known.get(u)};
            } else {
               return Plat.profile(server, name.trim()).map(gp -> new String[]{gp.getId().toString(), gp.getName()}).orElse(null);
            }
         }
      } else {
         return null;
      }
   }

   public static ServerPlayer online(MinecraftServer server, String uuid) {
      try {
         return server.getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
      } catch (IllegalArgumentException var3) {
         return null;
      }
   }

   /** Tells a player something now, or the next time they join. */
   public static void tell(MinecraftServer server, String uuid, String text) {
      ServerPlayer p = online(server, uuid);
      if (p != null) {
         p.sendSystemMessage(L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD).append(L.c(text).withStyle(ChatFormatting.YELLOW)));
      } else {
         OfficeData.get(server).notice(uuid, text);
      }
   }

   /** A chat line with a clickable command button. */
   public static MutableComponent button(String label, String command, String hover) {
      return L.c(label)
         .withStyle(
            Style.EMPTY
               .withColor(ChatFormatting.AQUA)
               .withUnderlined(true)
               .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
               .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, L.c(hover)))
         );
   }

   /**
    * Hands an item to a player: straight into the inventory when they are online, otherwise into
    * their mailbox.
    */
   public static void deliver(MinecraftServer server, String uuid, ItemStack stack, String from) {
      ServerPlayer p = online(server, uuid);
      if (p != null) {
         PaperService.give(p, stack);
      } else {
         DocStore store = DocStore.get(server);
         CompoundTag entry = new CompoundTag();
         entry.put("Item", Plat.saveStack(stack, server.overworld()));
         entry.putString("From", from);
         entry.putLong("Time", System.currentTimeMillis());
         MailService.box(store, uuid).add(entry);
         store.setDirty();
      }
   }

   public static boolean admin(ServerPlayer sp) {
      return Perms.has(sp, Perms.ADMIN);
   }

   private static MinecraftServer seeded;

   /** Fills the player list from the server's name cache once, so long-time players are suggested too. */
   static void seed(MinecraftServer server) {
      if (seeded != server) {
         seeded = server;
         java.nio.file.Path p = java.nio.file.Path.of("usercache.json");

         try {
            if (java.nio.file.Files.exists(p)) {
               com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p)).getAsJsonArray();
               OfficeData od = OfficeData.get(server);
               boolean changed = false;

               for (com.google.gson.JsonElement e : arr) {
                  com.google.gson.JsonObject o = e.getAsJsonObject();
                  if (o.has("uuid") && o.has("name")) {
                     String u = o.get("uuid").getAsString();
                     if (!od.known.containsKey(u)) {
                        od.known.put(u, o.get("name").getAsString());
                        changed = true;
                     }
                  }
               }

               if (changed) {
                  od.setDirty();
               }
            }
         } catch (Exception var8) {
         }
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         seed(sp.server);
         OfficeData od = OfficeData.get(sp.server);
         boolean fresh = !od.known.containsKey(sp.getUUID().toString());
         remember(sp);
         sendNames(sp);
         if (fresh) {
            for (ServerPlayer p : sp.server.getPlayerList().getPlayers()) {
               if (p != sp) {
                  sendNames(p);
               }
            }
         }

         List<String> n = od.notices.remove(sp.getUUID().toString());
         if (n != null && !n.isEmpty()) {
            od.setDirty();

            for (String s : n) {
               sp.sendSystemMessage(L.c(L.k("[Канцелярия] ")).withStyle(ChatFormatting.GOLD).append(L.c(s).withStyle(ChatFormatting.YELLOW)));
            }
         }

         SignService.remind(sp);
         InboxService.remind(sp);
      }
   }

   static Component plain(String s) {
      return L.c(s);
   }

   static Map<String, String> copyOf(Map<String, String> m) {
      return new java.util.LinkedHashMap<>(m);
   }
}
