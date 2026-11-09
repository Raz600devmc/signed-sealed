package ru.realdocs.net;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.Side;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.server.ArchiveService;
import ru.realdocs.server.BlockConfigService;
import ru.realdocs.server.DigitalService;

/** Packets for archives, ticket gates, scanners and computers (2.3.0). */
public final class ArchivePackets {
   private ArchivePackets() {
   }

   static String cut(String s, int max) {
      return MorePackets.cut(s, max);
   }

   /** One row in an archive or search listing. */
   public static final class Entry {
      public final String id;
      public final int type;
      public final String title;
      public final String meta;

      public Entry(String id, int type, String title, String meta) {
         this.id = id == null ? "" : id;
         this.type = type;
         this.title = title == null ? "" : title;
         this.meta = meta == null ? "" : meta;
      }

      void write(FriendlyByteBuf b) {
         b.writeUtf(cut(this.id, 24), 24);
         b.writeVarInt(this.type);
         b.writeUtf(cut(this.title, 120), 120);
         b.writeUtf(cut(this.meta, 400), 400);
      }

      static Entry read(FriendlyByteBuf b) {
         return new Entry(b.readUtf(24), b.readVarInt(), b.readUtf(120), b.readUtf(400));
      }
   }

   static void writeEntries(FriendlyByteBuf b, List<Entry> es) {
      b.writeVarInt(Math.min(es.size(), 200));

      for (int i = 0; i < es.size() && i < 200; i++) {
         es.get(i).write(b);
      }
   }

   static List<Entry> readEntries(FriendlyByteBuf b) {
      int n = Math.min(200, b.readVarInt());
      List<Entry> out = new ArrayList<>(n);

      for (int i = 0; i < n; i++) {
         out.add(Entry.read(b));
      }

      return out;
   }

   public static final class ArchiveData implements Packets.Packet {
      public final BlockPos pos;
      public final int capacity;
      public final String label;
      public final String owner;
      public final boolean locked;
      public final boolean manage;
      public final String members;
      public final List<Entry> entries;

      public ArchiveData(BlockPos pos, int capacity, String label, String owner, boolean locked, boolean manage, String members, List<Entry> entries) {
         this.pos = pos;
         this.capacity = capacity;
         this.label = label;
         this.owner = owner;
         this.locked = locked;
         this.manage = manage;
         this.members = members;
         this.entries = entries;
      }

      public static ArchiveData decode(FriendlyByteBuf b) {
         return new ArchiveData(b.readBlockPos(), b.readVarInt(), b.readUtf(48), b.readUtf(32), b.readBoolean(), b.readBoolean(), b.readUtf(600), readEntries(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.capacity);
         b.writeUtf(cut(this.label, 48), 48);
         b.writeUtf(cut(this.owner, 32), 32);
         b.writeBoolean(this.locked);
         b.writeBoolean(this.manage);
         b.writeUtf(cut(this.members, 600), 600);
         writeEntries(b, this.entries);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.onArchiveData(this));
      }
   }

   public static final class ArchiveAction implements Packets.Packet {
      final BlockPos pos;
      final int action;
      final int index;
      final String label;
      final String members;
      final boolean locked;

      public ArchiveAction(BlockPos pos, int action, int index, String label, String members, boolean locked) {
         this.pos = pos;
         this.action = action;
         this.index = index;
         this.label = label == null ? "" : label;
         this.members = members == null ? "" : members;
         this.locked = locked;
      }

      public static ArchiveAction decode(FriendlyByteBuf b) {
         return new ArchiveAction(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readUtf(48), b.readUtf(600), b.readBoolean());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.action);
         b.writeVarInt(this.index);
         b.writeUtf(cut(this.label, 48), 48);
         b.writeUtf(cut(this.members, 600), 600);
         b.writeBoolean(this.locked);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            ArchiveService.action(sp, this.pos, this.action, this.index, this.label, this.members, this.locked);
         }
      }
   }

   /** Shows a newspaper or letter that is not in the player's hand. */
   public static final class ViewItem implements Packets.Packet {
      final CompoundTag stack;

      public ViewItem(CompoundTag stack) {
         this.stack = stack;
      }

      public static ViewItem decode(FriendlyByteBuf b) {
         return new ViewItem(b.readNbt());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeNbt(this.stack);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.viewItem(this.stack));
      }
   }

   /** Settings of a gate (kind 0) or a scanner/computer (kind 1), both ways. */
   public static final class BlockConfig implements Packets.Packet {
      public final BlockPos pos;
      public final int kind;
      public final String label;
      public final String text;
      public final String members;
      public final int mode;
      public final int n;
      public final boolean locked;
      public final boolean toServer;

      public BlockConfig(BlockPos pos, int kind, String label, String text, String members, int mode, int n, boolean locked, boolean toServer) {
         this.pos = pos;
         this.kind = kind;
         this.label = label == null ? "" : label;
         this.text = text == null ? "" : text;
         this.members = members == null ? "" : members;
         this.mode = mode;
         this.n = n;
         this.locked = locked;
         this.toServer = toServer;
      }

      public static BlockConfig decode(FriendlyByteBuf b) {
         return new BlockConfig(
            b.readBlockPos(), b.readVarInt(), b.readUtf(48), b.readUtf(80), b.readUtf(600), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()
         );
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.kind);
         b.writeUtf(cut(this.label, 48), 48);
         b.writeUtf(cut(this.text, 80), 80);
         b.writeUtf(cut(this.members, 600), 600);
         b.writeVarInt(this.mode);
         b.writeVarInt(this.n);
         b.writeBoolean(this.locked);
         b.writeBoolean(this.toServer);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            BlockConfigService.apply(sp, this);
         }
      }
   }

   /** Server -> client: open the settings screen with the current values. */
   public static final class OpenConfig implements Packets.Packet {
      final BlockConfig cfg;

      public OpenConfig(BlockConfig cfg) {
         this.cfg = cfg;
      }

      public static OpenConfig decode(FriendlyByteBuf b) {
         return new OpenConfig(BlockConfig.decode(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         this.cfg.encode(b);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.openBlockConfig(this.cfg));
      }
   }

   public static final class ComputerData implements Packets.Packet {
      public final BlockPos pos;
      public final String network;
      public final String query;
      public final int total;
      public final boolean manage;
      public final boolean canPrint;
      public final List<Entry> entries;

      public ComputerData(BlockPos pos, String network, String query, int total, boolean manage, boolean canPrint, List<Entry> entries) {
         this.pos = pos;
         this.network = network;
         this.query = query;
         this.total = total;
         this.manage = manage;
         this.canPrint = canPrint;
         this.entries = entries;
      }

      public static ComputerData decode(FriendlyByteBuf b) {
         return new ComputerData(b.readBlockPos(), b.readUtf(48), b.readUtf(80), b.readVarInt(), b.readBoolean(), b.readBoolean(), readEntries(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeUtf(cut(this.network, 48), 48);
         b.writeUtf(cut(this.query, 80), 80);
         b.writeVarInt(this.total);
         b.writeBoolean(this.manage);
         b.writeBoolean(this.canPrint);
         writeEntries(b, this.entries);
      }

      @Override
      public void handle(Ctx ctx) {
         Side.client(() -> () -> ClientHooks.onComputerData(this));
      }
   }

   public static final class ComputerAction implements Packets.Packet {
      final BlockPos pos;
      final int action;
      final String text;

      public ComputerAction(BlockPos pos, int action, String text) {
         this.pos = pos;
         this.action = action;
         this.text = text == null ? "" : text;
      }

      public static ComputerAction decode(FriendlyByteBuf b) {
         return new ComputerAction(b.readBlockPos(), b.readVarInt(), b.readUtf(80));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeVarInt(this.action);
         b.writeUtf(cut(this.text, 80), 80);
      }

      @Override
      public void handle(Ctx ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DigitalService.computer(sp, this.pos, this.action, this.text);
         }
      }
   }
}
