package ru.realdocs.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import ru.realdocs.client.screen.DocumentScreen;
import ru.realdocs.client.screen.MailListScreen;
import ru.realdocs.client.screen.OrgScreen;
import ru.realdocs.client.screen.SignRequestScreen;
import ru.realdocs.client.screen.TemplateEditorScreen;
import ru.realdocs.net.Net;
import ru.realdocs.net.OfficePackets;

/** Client side of the 2.4.0 office features: name lists for autocomplete and screen data. */
public final class ClientOffice {
   public static volatile List<String> names = List.of();
   public static volatile List<String> targets = List.of();
   public static volatile List<String> orgs = List.of();
   public static CompoundTag mailbox = new CompoundTag();
   public static CompoundTag lists = new CompoundTag();
   public static CompoundTag orgData = new CompoundTag();
   public static CompoundTag templates = new CompoundTag();
   public static int templatesVersion;

   private ClientOffice() {
   }

   public static void req(String action, CompoundTag t) {
      Net.sendToServer(new OfficePackets.Req(action, t));
   }

   public static void req(String action) {
      req(action, new CompoundTag());
   }

   static List<String> strings(ListTag l) {
      List<String> out = new ArrayList<>();

      for (int i = 0; i < l.size(); i++) {
         out.add(l.getString(i));
      }

      return out;
   }

   public static void handle(String kind, CompoundTag t) {
      Minecraft mc = Minecraft.getInstance();
      switch (kind) {
         case "names":
            names = strings(t.getList("Names", 8));
            targets = strings(t.getList("Targets", 8));
            orgs = strings(t.getList("Orgs", 8));
            break;
         case "mailbox":
            mailbox = t;
            break;
         case "lists":
            lists = t;
            if (mc.screen instanceof MailListScreen s) {
               s.reload();
            }
            break;
         case "orgs":
            orgData = t;
            if (mc.screen instanceof OrgScreen s) {
               s.reload();
            }
            break;
         case "templates":
            templates = t;
            templatesVersion++;
            if (mc.screen instanceof TemplateEditorScreen s) {
               s.reload();
            } else if (mc.screen instanceof DocumentScreen s) {
               s.templatesArrived();
            }
            break;
         case "signreq":
            if (mc.screen instanceof ru.realdocs.client.screen.ComputerScreen cs) {
               ru.realdocs.client.ui.UiScreen.returnTo = cs;
            }

            mc.setScreen(new SignRequestScreen(t));
            break;
         case "reader_cfg":
            mc.setScreen(new ru.realdocs.client.screen.ReaderConfigScreen(t));
            break;
         case "board":
            if (mc.screen instanceof ru.realdocs.client.screen.NoticeBoardScreen b && b.isFor(t)) {
               b.update(t);
            } else {
               mc.setScreen(new ru.realdocs.client.screen.NoticeBoardScreen(t));
            }
            break;
         case "inbox":
            if (mc.screen instanceof ru.realdocs.client.screen.ComputerScreen cs) {
               cs.onInbox(t);
            }
            break;
         case "reg_result":
            if (mc.screen instanceof ru.realdocs.client.screen.ComputerScreen cs) {
               cs.onRegistry(t);
            }
            break;
         case "open":
            String screen = t.getString("Screen");
            if ("orgs".equals(screen)) {
               mc.setScreen(new OrgScreen());
            } else if ("lists".equals(screen)) {
               mc.setScreen(new MailListScreen(mc.screen));
            } else if ("templates".equals(screen)) {
               mc.setScreen(new TemplateEditorScreen(null));
            }
      }
   }

   /** Templates the player may issue documents from, as sent by the server. */
   public static List<CompoundTag> templateList() {
      List<CompoundTag> out = new ArrayList<>();
      ListTag l = templates.getList("Templates", 10);

      for (int i = 0; i < l.size(); i++) {
         out.add(l.getCompound(i));
      }

      return out;
   }

   public static CompoundTag template(String id) {
      for (CompoundTag c : templateList()) {
         if (c.getString("Id").equals(id)) {
            return c;
         }
      }

      return null;
   }
}
