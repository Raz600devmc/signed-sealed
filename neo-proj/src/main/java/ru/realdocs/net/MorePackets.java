package ru.realdocs.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import ru.realdocs.Side;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.model.MailModel;
import ru.realdocs.server.FolderService;
import ru.realdocs.server.MailService;
import ru.realdocs.server.PaperService;
import ru.realdocs.server.Uploads;

/** Packets for newspapers, letters, mailboxes and folders (2.2.0). */
public final class MorePackets {
   private MorePackets() {
   }

   static String cut(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, max);
      }
   }

   public static final class Upload implements Packets.Packet {
      final int token;
      final int kind;
      final int index;
      final int total;
      final byte[] data;

      public Upload(int token, int kind, int index, int total, byte[] data) {
         this.token = token;
         this.kind = kind;
         this.index = index;
         this.total = total;
         this.data = data;
      }

      public static Upload decode(FriendlyByteBuf b) {
         return new Upload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(Uploads.CHUNK));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeVarInt(this.token);
         b.writeVarInt(this.kind);
         b.writeVarInt(this.index);
         b.writeVarInt(this.total);
         b.writeByteArray(this.data);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            Uploads.chunk(sp, this.token, this.kind, this.index, this.total, this.data);
         }
      }
   }

   public static final class UploadResult implements Packets.Packet {
      final int token;
      final boolean ok;
      final String id;
      final String msg;

      public UploadResult(int token, boolean ok, String id, String msg) {
         this.token = token;
         this.ok = ok;
         this.id = id == null ? "" : id;
         this.msg = msg == null ? "" : msg;
      }

      public static UploadResult decode(FriendlyByteBuf b) {
         return new UploadResult(b.readVarInt(), b.readBoolean(), b.readUtf(24), b.readUtf(400));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeVarInt(this.token);
         b.writeBoolean(this.ok);
         b.writeUtf(cut(this.id, 24), 24);
         b.writeUtf(cut(this.msg, 400), 400);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.onUploadResult(this.token, this.ok, this.id, this.msg));
      }
   }

   public static final class PaperCommit implements Packets.Packet {
      final InteractionHand hand;
      final int token;
      final boolean publish;

      public PaperCommit(InteractionHand hand, int token, boolean publish) {
         this.hand = hand;
         this.token = token;
         this.publish = publish;
      }

      public static PaperCommit decode(FriendlyByteBuf b) {
         return new PaperCommit((InteractionHand)b.readEnum(InteractionHand.class), b.readVarInt(), b.readBoolean());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeVarInt(this.token);
         b.writeBoolean(this.publish);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PaperService.commit(sp, this.hand, this.token, this.publish);
         }
      }
   }

   public static final class PaperRequest implements Packets.Packet {
      final String id;

      public PaperRequest(String id) {
         this.id = id;
      }

      public static PaperRequest decode(FriendlyByteBuf b) {
         return new PaperRequest(b.readUtf(24));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(cut(this.id, 24), 24);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PaperService.sendLayout(sp, this.id);
         }
      }
   }

   public static final class PaperData implements Packets.Packet {
      public static final int MAX = 1000000;
      final String id;
      final boolean exists;
      final byte[] data;
      final boolean revoked;

      public PaperData(String id, boolean exists, byte[] data, boolean revoked) {
         this.id = id;
         this.exists = exists;
         this.data = data;
         this.revoked = revoked;
      }

      public static PaperData decode(FriendlyByteBuf b) {
         String id = b.readUtf(24);
         boolean ex = b.readBoolean();
         byte[] d = ex ? b.readByteArray(MAX) : new byte[0];
         return new PaperData(id, ex, d, b.readBoolean());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(cut(this.id, 24), 24);
         boolean ex = this.exists && this.data.length <= MAX;
         b.writeBoolean(ex);
         if (ex) {
            b.writeByteArray(this.data);
         }

         b.writeBoolean(this.revoked);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.onPaperData(this.id, this.exists, this.data, this.revoked));
      }
   }

   public static final class PaperClone implements Packets.Packet {
      final InteractionHand hand;

      public PaperClone(InteractionHand hand) {
         this.hand = hand;
      }

      public static PaperClone decode(FriendlyByteBuf b) {
         return new PaperClone((InteractionHand)b.readEnum(InteractionHand.class));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PaperService.cloneNext(sp, this.hand);
         }
      }
   }

   public static final class MailAction implements Packets.Packet {
      final InteractionHand hand;
      final int action;
      final String to;
      final String from;
      final String text;
      final String stamp;
      final int n;

      public MailAction(InteractionHand hand, int action, String to, String from, String text, String stamp, int n) {
         this.hand = hand;
         this.action = action;
         this.to = to == null ? "" : to;
         this.from = from == null ? "" : from;
         this.text = text == null ? "" : text;
         this.stamp = stamp == null ? "" : stamp;
         this.n = n;
      }

      public static MailAction decode(FriendlyByteBuf b) {
         return new MailAction(
            (InteractionHand)b.readEnum(InteractionHand.class),
            b.readVarInt(),
            b.readUtf(MailModel.MAX_NAME),
            b.readUtf(MailModel.MAX_NAME),
            b.readUtf(MailModel.MAX_TEXT),
            b.readUtf(24),
            b.readVarInt()
         );
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeVarInt(this.action);
         b.writeUtf(cut(this.to, MailModel.MAX_NAME), MailModel.MAX_NAME);
         b.writeUtf(cut(this.from, MailModel.MAX_NAME), MailModel.MAX_NAME);
         b.writeUtf(cut(this.text, MailModel.MAX_TEXT), MailModel.MAX_TEXT);
         b.writeUtf(cut(this.stamp, 24), 24);
         b.writeVarInt(this.n);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            MailService.action(sp, this.hand, this.action, this.to, this.from, this.text, this.stamp, this.n);
         }
      }
   }

   public static final class MailboxAction implements Packets.Packet {
      final BlockPos pos;
      final int action;

      public MailboxAction(BlockPos pos, int action) {
         this.pos = pos;
         this.action = action;
      }

      public static MailboxAction decode(FriendlyByteBuf b) {
         return new MailboxAction(b.readBlockPos(), b.readVarInt());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.action);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            MailService.mailbox(sp, this.pos, this.action);
         }
      }
   }

   public static final class OpenMailbox implements Packets.Packet {
      final BlockPos pos;
      final int waiting;
      final boolean holdsLetter;
      final String to;

      public OpenMailbox(BlockPos pos, int waiting, boolean holdsLetter, String to) {
         this.pos = pos;
         this.waiting = waiting;
         this.holdsLetter = holdsLetter;
         this.to = to == null ? "" : to;
      }

      public static OpenMailbox decode(FriendlyByteBuf b) {
         return new OpenMailbox(b.readBlockPos(), b.readVarInt(), b.readBoolean(), b.readUtf(MailModel.MAX_NAME));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.waiting);
         b.writeBoolean(this.holdsLetter);
         b.writeUtf(cut(this.to, MailModel.MAX_NAME), MailModel.MAX_NAME);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.openMailbox(this.pos, this.waiting, this.holdsLetter, this.to));
      }
   }

   public static final class FolderAction implements Packets.Packet {
      final InteractionHand hand;
      final int action;
      final int index;
      final String label;
      final int color;

      public FolderAction(InteractionHand hand, int action, int index, String label, int color) {
         this.hand = hand;
         this.action = action;
         this.index = index;
         this.label = label == null ? "" : label;
         this.color = color;
      }

      public static FolderAction decode(FriendlyByteBuf b) {
         return new FolderAction((InteractionHand)b.readEnum(InteractionHand.class), b.readVarInt(), b.readVarInt(), b.readUtf(48), b.readVarInt());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeVarInt(this.action);
         b.writeVarInt(this.index);
         b.writeUtf(cut(this.label, 48), 48);
         b.writeVarInt(this.color);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            FolderService.action(sp, this.hand, this.action, this.index, this.label, this.color);
         }
      }
   }
}
