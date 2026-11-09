package ru.realdocs.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.screen.AdminScreen;
import ru.realdocs.client.screen.DocumentScreen;
import ru.realdocs.client.screen.ForgeryScreen;
import ru.realdocs.client.screen.PassportScreen;
import ru.realdocs.client.screen.PassportTemplateScreen;
import ru.realdocs.client.screen.PrinterScreen;
import ru.realdocs.client.screen.RemoteViewScreen;
import ru.realdocs.client.screen.StampDesignerScreen;
import ru.realdocs.client.screen.ViewerScreen;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Packets;

public final class ClientHooks {
   private ClientHooks() {
   }

   public static void openDocument(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         ItemStack stack = mc.player.getItemInHand(hand);
         mc.setScreen(new DocumentScreen(hand, stack));
      }
   }

   public static void openDesigner() {
      Minecraft.getInstance().setScreen(new StampDesignerScreen());
   }

   public static void onStampData(String id, boolean exists, StampModel stamp, byte[] png) {
      StampCache.INSTANCE.put(id, exists, stamp, png);
   }

   public static void onVerifyResult(int status, List<String> lines) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof ViewerScreen vs) {
         vs.setVerify(status, lines);
      } else if (mc.screen instanceof ru.realdocs.client.screen.EnvelopeScreen es) {
         es.setVerify(status, lines);
      } else if (mc.player != null) {
         for (String l : lines) {
            mc.player.sendSystemMessage(Component.literal(L.t(l)));
         }
      }
   }

   public static void openForgery(InteractionHand kitHand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new ForgeryScreen(kitHand));
      }
   }

   public static void openPrinter(BlockPos pos, List<String[]> templates) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new PrinterScreen(pos, templates));
      }
   }

   public static void openPassport(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new PassportScreen(hand, mc.player.getItemInHand(hand)));
      }
   }

   public static void editTemplate(PassportTemplate t) {
      Minecraft.getInstance().setScreen(new PassportTemplateScreen(t));
   }

   public static void openTemplateEditor(String preset) {
      Minecraft.getInstance().setScreen(new PassportTemplateScreen(preset));
   }

   public static void onAssetData(String id, boolean exists, byte[] png) {
      PassportCache.INSTANCE.putAsset(id, exists, png);
   }

   public static void onTemplateData(String id, boolean exists, PassportTemplate t) {
      PassportCache.INSTANCE.putTemplate(id, exists, t);
   }

   public static void onAdminData(Packets.AdminData d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof AdminScreen as) {
         as.update(d);
      } else {
         mc.setScreen(new AdminScreen(d));
      }
   }

   public static void onViewRemote(boolean passport, CompoundTag tag, int status, List<String> lines, String from) {
      if (passport) {
         PassportCache.INSTANCE.forgetMissing(NbtCodec.readPass(tag).photo);
      }

      Minecraft.getInstance().setScreen(new RemoteViewScreen(passport, tag, status, lines, from));
   }

   public static void openNewspaper(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         ItemStack stack = mc.player.getItemInHand(hand);
         ru.realdocs.data.PaperCodec.Ref ref = ru.realdocs.data.PaperCodec.ref(stack);
         if (ref.published) {
            mc.setScreen(new ru.realdocs.client.screen.NewspaperScreen(hand, ref, true));
         } else {
            if (!ref.id.isEmpty()) {
               PaperCache.INSTANCE.refresh(ref.id);
            }

            mc.setScreen(new ru.realdocs.client.screen.NewspaperEditorScreen(hand, ref));
         }
      }
   }

   public static void openEnvelope(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new ru.realdocs.client.screen.EnvelopeScreen(hand, null));
      }
   }

   public static void openFolder(InteractionHand hand) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new ru.realdocs.client.screen.FolderScreen(hand));
      }
   }

   public static void openMailbox(BlockPos pos, int waiting, boolean holdsLetter, String to) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.setScreen(new ru.realdocs.client.screen.MailboxScreen(pos, waiting, holdsLetter, to));
      }
   }

   public static void onUploadResult(int token, boolean ok, String id, String msg) {
      ClientUploads.result(token, ok, id, msg);
   }

   public static void onPaperData(String id, boolean exists, byte[] data, boolean revoked) {
      PaperCache.INSTANCE.put(id, exists, data, revoked);
   }

   public static void readPoster(ItemStack stack) {
      Minecraft mc = Minecraft.getInstance();
      ru.realdocs.data.PaperCodec.Ref r = ru.realdocs.data.PaperCodec.ref(stack);
      if (r.published) {
         mc.setScreen(new ru.realdocs.client.screen.NewspaperScreen(InteractionHand.MAIN_HAND, r, false));
      }
   }

   public static void onArchiveData(ru.realdocs.net.ArchivePackets.ArchiveData d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof ru.realdocs.client.screen.ArchiveScreen as && as.isFor(d)) {
         as.update(d, true);
      } else {
         mc.setScreen(new ru.realdocs.client.screen.ArchiveScreen(d));
      }
   }

   public static void onComputerData(ru.realdocs.net.ArchivePackets.ComputerData d) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof ru.realdocs.client.screen.ComputerScreen cs && cs.isFor(d)) {
         cs.update(d);
      } else {
         mc.setScreen(new ru.realdocs.client.screen.ComputerScreen(d));
      }
   }

   public static void openBlockConfig(ru.realdocs.net.ArchivePackets.BlockConfig c) {
      Minecraft.getInstance().setScreen(new ru.realdocs.client.screen.BlockConfigScreen(c));
   }

   public static void viewItem(net.minecraft.nbt.CompoundTag tag) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         ItemStack s = ru.realdocs.Plat.loadStack(tag, mc.level);
         if (s.getItem() instanceof ru.realdocs.item.NewspaperItem) {
            ru.realdocs.data.PaperCodec.Ref r = ru.realdocs.data.PaperCodec.ref(s);
            if (r.published) {
               mc.setScreen(new ru.realdocs.client.screen.NewspaperScreen(InteractionHand.MAIN_HAND, r, false));
            }
         } else if (s.getItem() instanceof ru.realdocs.item.EnvelopeItem) {
            mc.setScreen(new ru.realdocs.client.screen.EnvelopeScreen(InteractionHand.MAIN_HAND, s));
         } else if (s.getItem() instanceof ru.realdocs.item.FolderItem) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(s.getHoverName().getString() + ":").withStyle(net.minecraft.ChatFormatting.GOLD), false);
            for (ItemStack in : ru.realdocs.data.MailCodec.items(s, ru.realdocs.data.MailCodec.FOLDER_TAG, mc.level)) {
               mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(" • " + in.getHoverName().getString()).withStyle(net.minecraft.ChatFormatting.GRAY), false);
            }
         }
      }
   }
}
