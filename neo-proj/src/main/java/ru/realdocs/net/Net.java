package ru.realdocs.net;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
   private static final String PROTOCOL = "8";
   private static final Map<Class<?>, Type<Net.Wrap>> TYPES = new HashMap<>();

   private Net() {
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar(PROTOCOL);
      toServer(r, Packets.SaveDoc.class, Packets.SaveDoc::decode);
      toServer(r, Packets.CoSign.class, Packets.CoSign::decode);
      toServer(r, Packets.Verify.class, Packets.Verify::decode);
      toServer(r, Packets.SubmitStamp.class, Packets.SubmitStamp::decode);
      toServer(r, Packets.StampRequest.class, Packets.StampRequest::decode);
      toServer(r, Packets.IssuePassport.class, Packets.IssuePassport::decode);
      toServer(r, Packets.SubmitTemplate.class, Packets.SubmitTemplate::decode);
      toServer(r, Packets.PlaceStamp.class, Packets.PlaceStamp::decode);
      toServer(r, Packets.AssetRequest.class, Packets.AssetRequest::decode);
      toServer(r, Packets.TemplateRequest.class, Packets.TemplateRequest::decode);
      toServer(r, Packets.AdminOpen.class, Packets.AdminOpen::decode);
      toServer(r, Packets.AdminAction.class, Packets.AdminAction::decode);
      toServer(r, Packets.ForgeCopy.class, Packets.ForgeCopy::decode);
      toServer(r, Packets.ForgeStamp.class, Packets.ForgeStamp::decode);
      toServer(r, Packets.PrinterAction.class, Packets.PrinterAction::decode);
      toClient(r, Packets.StampData.class, Packets.StampData::decode);
      toClient(r, Packets.VerifyResult.class, Packets.VerifyResult::decode);
      toClient(r, Packets.OpenDesigner.class, Packets.OpenDesigner::decode);
      toClient(r, Packets.AssetData.class, Packets.AssetData::decode);
      toClient(r, Packets.TemplateData.class, Packets.TemplateData::decode);
      toClient(r, Packets.OpenTemplateEditor.class, Packets.OpenTemplateEditor::decode);
      toClient(r, Packets.ViewRemote.class, Packets.ViewRemote::decode);
      toClient(r, Packets.AdminData.class, Packets.AdminData::decode);
      toClient(r, Packets.OpenPrinter.class, Packets.OpenPrinter::decode);
      toClient(r, Packets.EditTemplate.class, Packets.EditTemplate::decode);
      toServer(r, MorePackets.Upload.class, MorePackets.Upload::decode);
      toServer(r, MorePackets.PaperCommit.class, MorePackets.PaperCommit::decode);
      toServer(r, MorePackets.PaperRequest.class, MorePackets.PaperRequest::decode);
      toServer(r, MorePackets.PaperClone.class, MorePackets.PaperClone::decode);
      toServer(r, MorePackets.MailAction.class, MorePackets.MailAction::decode);
      toServer(r, MorePackets.MailboxAction.class, MorePackets.MailboxAction::decode);
      toServer(r, MorePackets.FolderAction.class, MorePackets.FolderAction::decode);
      toClient(r, MorePackets.UploadResult.class, MorePackets.UploadResult::decode);
      toClient(r, MorePackets.PaperData.class, MorePackets.PaperData::decode);
      toClient(r, MorePackets.OpenMailbox.class, MorePackets.OpenMailbox::decode);
      toServer(r, ArchivePackets.ArchiveAction.class, ArchivePackets.ArchiveAction::decode);
      toServer(r, ArchivePackets.BlockConfig.class, ArchivePackets.BlockConfig::decode);
      toServer(r, ArchivePackets.ComputerAction.class, ArchivePackets.ComputerAction::decode);
      toClient(r, ArchivePackets.ArchiveData.class, ArchivePackets.ArchiveData::decode);
      toClient(r, ArchivePackets.ViewItem.class, ArchivePackets.ViewItem::decode);
      toClient(r, ArchivePackets.OpenConfig.class, ArchivePackets.OpenConfig::decode);
      toClient(r, ArchivePackets.ComputerData.class, ArchivePackets.ComputerData::decode);
   }

   private static <T extends Packets.Packet> Type<Net.Wrap> type(Class<T> cls) {
      Type<Net.Wrap> t = new Type(ResourceLocation.fromNamespaceAndPath("signedsealed", cls.getSimpleName().toLowerCase(Locale.ROOT)));
      TYPES.put(cls, t);
      return t;
   }

   private static <T extends Packets.Packet> StreamCodec<RegistryFriendlyByteBuf, Net.Wrap> codec(Function<FriendlyByteBuf, T> decoder) {
      return StreamCodec.of((buf, w) -> w.packet().encode(buf), buf -> new Net.Wrap(decoder.apply(buf)));
   }

   private static void handle(Net.Wrap w, IPayloadContext ctx) {
      ServerPlayer sp = ctx.player() instanceof ServerPlayer s ? s : null;
      ctx.enqueueWork(() -> w.packet().handle(new Ctx(sp)));
   }

   private static <T extends Packets.Packet> void toServer(PayloadRegistrar r, Class<T> cls, Function<FriendlyByteBuf, T> decoder) {
      r.playToServer(type(cls), codec(decoder), Net::handle);
   }

   private static <T extends Packets.Packet> void toClient(PayloadRegistrar r, Class<T> cls, Function<FriendlyByteBuf, T> decoder) {
      r.playToClient(type(cls), codec(decoder), Net::handle);
   }

   public static void sendToServer(Packets.Packet packet) {
      PacketDistributor.sendToServer(new Net.Wrap(packet), new CustomPacketPayload[0]);
   }

   public static void sendTo(ServerPlayer player, Packets.Packet packet) {
      PacketDistributor.sendToPlayer(player, new Net.Wrap(packet), new CustomPacketPayload[0]);
   }

   public static record Wrap(Packets.Packet packet) implements CustomPacketPayload {
      public Type<? extends CustomPacketPayload> type() {
         return Net.TYPES.get(this.packet.getClass());
      }
   }
}
