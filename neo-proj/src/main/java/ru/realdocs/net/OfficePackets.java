package ru.realdocs.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.Side;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.server.BureauService;

/**
 * Two general-purpose packets for the 2.4.0 features (names, mailing lists, organizations,
 * templates, signature requests, receipts): an action name plus an NBT payload.
 */
public final class OfficePackets {
   private OfficePackets() {
   }

   static CompoundTag readTag(FriendlyByteBuf b) {
      CompoundTag t = b.readNbt();
      return t == null ? new CompoundTag() : t;
   }

   /** Client to server. */
   public static final class Req implements Packets.Packet {
      final String action;
      final CompoundTag data;

      public Req(String action, CompoundTag data) {
         this.action = action;
         this.data = data == null ? new CompoundTag() : data;
      }

      public static Req decode(FriendlyByteBuf b) {
         return new Req(b.readUtf(40), readTag(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(MorePackets.cut(this.action, 40), 40);
         b.writeNbt(this.data);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            BureauService.handle(sp, this.action, this.data);
         }
      }
   }

   /** Server to client. */
   public static final class Data implements Packets.Packet {
      final String kind;
      final CompoundTag data;

      public Data(String kind, CompoundTag data) {
         this.kind = kind;
         this.data = data == null ? new CompoundTag() : data;
      }

      public static Data decode(FriendlyByteBuf b) {
         return new Data(b.readUtf(40), readTag(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(MorePackets.cut(this.kind, 40), 40);
         b.writeNbt(this.data);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientOffice.handle(this.kind, this.data));
      }
   }
}
