package ru.realdocs.net;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
   /** Network version = mod MAJOR.MINOR: patch releases stay compatible, and a mismatch shows which versions clash. */
   private static final String PROTOCOL = "2.5";
   public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation("signedsealed", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
   private static int nextId = 0;

   private Net() {
   }

   public static void register() {
      toServer(Packets.SaveDoc.class, Packets.SaveDoc::decode);
      toServer(Packets.CoSign.class, Packets.CoSign::decode);
      toServer(Packets.Verify.class, Packets.Verify::decode);
      toServer(Packets.SubmitStamp.class, Packets.SubmitStamp::decode);
      toServer(Packets.StampRequest.class, Packets.StampRequest::decode);
      toServer(Packets.IssuePassport.class, Packets.IssuePassport::decode);
      toServer(Packets.SubmitTemplate.class, Packets.SubmitTemplate::decode);
      toServer(Packets.PlaceStamp.class, Packets.PlaceStamp::decode);
      toServer(Packets.AssetRequest.class, Packets.AssetRequest::decode);
      toServer(Packets.TemplateRequest.class, Packets.TemplateRequest::decode);
      toServer(Packets.AdminOpen.class, Packets.AdminOpen::decode);
      toServer(Packets.AdminAction.class, Packets.AdminAction::decode);
      toServer(Packets.ForgeCopy.class, Packets.ForgeCopy::decode);
      toServer(Packets.ForgeStamp.class, Packets.ForgeStamp::decode);
      toServer(Packets.PrinterAction.class, Packets.PrinterAction::decode);
      toClient(Packets.StampData.class, Packets.StampData::decode);
      toClient(Packets.VerifyResult.class, Packets.VerifyResult::decode);
      toClient(Packets.OpenDesigner.class, Packets.OpenDesigner::decode);
      toClient(Packets.AssetData.class, Packets.AssetData::decode);
      toClient(Packets.TemplateData.class, Packets.TemplateData::decode);
      toClient(Packets.OpenTemplateEditor.class, Packets.OpenTemplateEditor::decode);
      toClient(Packets.ViewRemote.class, Packets.ViewRemote::decode);
      toClient(Packets.AdminData.class, Packets.AdminData::decode);
      toClient(Packets.OpenPrinter.class, Packets.OpenPrinter::decode);
      toClient(Packets.EditTemplate.class, Packets.EditTemplate::decode);
      toServer(MorePackets.Upload.class, MorePackets.Upload::decode);
      toServer(MorePackets.PaperCommit.class, MorePackets.PaperCommit::decode);
      toServer(MorePackets.PaperRequest.class, MorePackets.PaperRequest::decode);
      toServer(MorePackets.PaperClone.class, MorePackets.PaperClone::decode);
      toServer(MorePackets.MailAction.class, MorePackets.MailAction::decode);
      toServer(MorePackets.MailboxAction.class, MorePackets.MailboxAction::decode);
      toServer(MorePackets.FolderAction.class, MorePackets.FolderAction::decode);
      toClient(MorePackets.UploadResult.class, MorePackets.UploadResult::decode);
      toClient(MorePackets.PaperData.class, MorePackets.PaperData::decode);
      toClient(MorePackets.OpenMailbox.class, MorePackets.OpenMailbox::decode);
      toServer(ArchivePackets.ArchiveAction.class, ArchivePackets.ArchiveAction::decode);
      toServer(ArchivePackets.BlockConfig.class, ArchivePackets.BlockConfig::decode);
      toServer(ArchivePackets.ComputerAction.class, ArchivePackets.ComputerAction::decode);
      toClient(ArchivePackets.ArchiveData.class, ArchivePackets.ArchiveData::decode);
      toClient(ArchivePackets.ViewItem.class, ArchivePackets.ViewItem::decode);
      toClient(ArchivePackets.OpenConfig.class, ArchivePackets.OpenConfig::decode);
      toClient(ArchivePackets.ComputerData.class, ArchivePackets.ComputerData::decode);
      toServer(OfficePackets.Req.class, OfficePackets.Req::decode);
      toClient(OfficePackets.Data.class, OfficePackets.Data::decode);
   }

   private static <T extends Packets.Packet> void toServer(Class<T> cls, Function<FriendlyByteBuf, T> decoder) {
      reg(cls, decoder, NetworkDirection.PLAY_TO_SERVER);
   }

   private static <T extends Packets.Packet> void toClient(Class<T> cls, Function<FriendlyByteBuf, T> decoder) {
      reg(cls, decoder, NetworkDirection.PLAY_TO_CLIENT);
   }

   private static <T extends Packets.Packet> void reg(Class<T> cls, Function<FriendlyByteBuf, T> decoder, NetworkDirection dir) {
      CH.registerMessage(nextId++, cls, (p, buf) -> p.encode(buf), decoder, (p, ctxSupplier) -> {
         Context ctx = (Context)ctxSupplier.get();
         ctx.enqueueWork(() -> p.handle(ctx));
         ctx.setPacketHandled(true);
      }, Optional.of(dir));
   }

   public static void sendToServer(Packets.Packet packet) {
      CH.sendToServer(packet);
   }

   public static void sendTo(ServerPlayer player, Packets.Packet packet) {
      CH.send(PacketDistributor.PLAYER.with(() -> player), packet);
   }
}
