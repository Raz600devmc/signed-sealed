package ru.realdocs.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.server.AdminService;
import ru.realdocs.server.DocService;
import ru.realdocs.server.ForgeryService;
import ru.realdocs.server.OfficeService;
import ru.realdocs.server.PassportService;

public final class Packets {
   private Packets() {
   }

   private static String cut(String s, int max) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= max ? s : s.substring(0, max);
      }
   }

   private static void writeInts(FriendlyByteBuf b, int[] a) {
      b.writeVarIntArray(a == null ? new int[0] : a);
   }

   private static int[] readInts(FriendlyByteBuf b) {
      return b.readVarIntArray(908);
   }

   static void writeTemplate(FriendlyByteBuf b, PassportTemplate t) {
      b.writeUtf(cut(t.id, 24), 24);
      b.writeVarInt(t.format);
      b.writeUtf(cut(t.name, 60), 60);
      b.writeUtf(cut(t.org, 80), 80);
      b.writeUtf(cut(t.ownerName, 40), 40);
      b.writeUtf(cut(t.ownerUuid, 40), 40);
      b.writeBoolean(t.approved);
      b.writeUtf(cut(t.title, 60), 60);
      b.writeUtf(cut(t.subtitle, 80), 80);
      b.writeInt(t.bg);
      b.writeInt(t.accent);
      b.writeVarInt(t.pattern);
      b.writeInt(t.coverColor);
      b.writeUtf(cut(t.coverTitle, 40), 40);
      b.writeUtf(cut(t.coverText, 120), 120);
      b.writeUtf(cut(t.emblem, 24), 24);
      b.writeVarInt(t.emblemCode);
      b.writeUtf(cut(t.serialPrefix, 8), 8);
      b.writeUtf(cut(t.autoStampId, 24), 24);
      b.writeUtf(cut(t.flag, 80), 80);
      b.writeVarInt(t.visaSpreads);
      b.writeUtf(cut(t.visaTitle, 40), 40);
      b.writeVarInt(t.validYears);
      writeElements(b, t.cover);
      writeElements(b, t.decor);
      writeElements(b, t.back);
      b.writeVarInt(t.coverTexture);
      b.writeVarInt(t.coverFrame);
      b.writeBoolean(t.hideHeader);
      int n = Math.min(16, t.fields.size());
      b.writeVarInt(n);

      for (int i = 0; i < n; i++) {
         PassportTemplate.Field f = t.fields.get(i);
         b.writeUtf(cut(f.key, 24), 24);
         b.writeUtf(cut(f.label, 40), 40);
         b.writeVarInt(f.type);
         b.writeVarInt(f.x);
         b.writeVarInt(f.y);
         b.writeVarInt(f.w);
         b.writeVarInt(f.h);
         b.writeVarInt(f.size);
         b.writeBoolean(f.bold);
         b.writeBoolean(f.required);
         b.writeUtf(cut(f.prefix, 12), 12);
         b.writeUtf(cut(f.hint, 40), 40);
      }
   }

   static void writeElements(FriendlyByteBuf b, List<DesignElement> list) {
      int n = Math.min(24, list.size());
      b.writeVarInt(n);

      for (int i = 0; i < n; i++) {
         DesignElement e = list.get(i);
         b.writeVarInt(e.type);
         b.writeVarInt(e.x);
         b.writeVarInt(e.y);
         b.writeVarInt(e.w);
         b.writeVarInt(e.h);
         b.writeUtf(cut(e.text, 60), 60);
         b.writeVarInt(e.size);
         b.writeBoolean(e.bold);
         b.writeVarInt(e.align);
         b.writeVarInt(e.spacing);
         b.writeVarInt(e.ink);
         b.writeInt(e.color);
         b.writeVarInt(e.alpha);
      }
   }

   static void readElements(FriendlyByteBuf b, List<DesignElement> out) {
      int n = Math.min(24, b.readVarInt());

      for (int i = 0; i < n; i++) {
         DesignElement e = new DesignElement(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
         e.text = b.readUtf(60);
         e.size = b.readVarInt();
         e.bold = b.readBoolean();
         e.align = b.readVarInt();
         e.spacing = b.readVarInt();
         e.ink = b.readVarInt();
         e.color = b.readInt();
         e.alpha = b.readVarInt();
         out.add(e);
      }
   }

   static PassportTemplate readTemplate(FriendlyByteBuf b) {
      PassportTemplate t = new PassportTemplate();
      t.id = b.readUtf(24);
      t.format = b.readVarInt();
      t.name = b.readUtf(60);
      t.org = b.readUtf(80);
      t.ownerName = b.readUtf(40);
      t.ownerUuid = b.readUtf(40);
      t.approved = b.readBoolean();
      t.title = b.readUtf(60);
      t.subtitle = b.readUtf(80);
      t.bg = b.readInt();
      t.accent = b.readInt();
      t.pattern = b.readVarInt();
      t.coverColor = b.readInt();
      t.coverTitle = b.readUtf(40);
      t.coverText = b.readUtf(120);
      t.emblem = b.readUtf(24);
      t.emblemCode = b.readVarInt();
      t.serialPrefix = b.readUtf(8);
      t.autoStampId = b.readUtf(24);
      t.flag = b.readUtf(80);
      t.visaSpreads = b.readVarInt();
      t.visaTitle = b.readUtf(40);
      t.validYears = b.readVarInt();
      readElements(b, t.cover);
      readElements(b, t.decor);
      readElements(b, t.back);
      t.coverTexture = b.readVarInt();
      t.coverFrame = b.readVarInt();
      t.hideHeader = b.readBoolean();
      int n = Math.min(16, b.readVarInt());

      for (int i = 0; i < n; i++) {
         PassportTemplate.Field f = new PassportTemplate.Field();
         f.key = b.readUtf(24);
         f.label = b.readUtf(40);
         f.type = b.readVarInt();
         f.x = b.readVarInt();
         f.y = b.readVarInt();
         f.w = b.readVarInt();
         f.h = b.readVarInt();
         f.size = b.readVarInt();
         f.bold = b.readBoolean();
         f.required = b.readBoolean();
         f.prefix = b.readUtf(12);
         f.hint = b.readUtf(40);
         t.fields.add(f);
      }

      return t;
   }

   public static final class AdminAction implements Packets.Packet {
      final int action;
      final String id;
      final List<String> targets;
      final int count;
      final String query;

      public AdminAction(int action, String id, List<String> targets, int count, String query) {
         this.action = action;
         this.id = id == null ? "" : id;
         this.targets = targets == null ? List.of() : targets;
         this.count = count;
         this.query = query == null ? "" : query;
      }

      public static Packets.AdminAction decode(FriendlyByteBuf b) {
         int action = b.readVarInt();
         String id = b.readUtf(64);
         int n = Math.min(32, b.readVarInt());
         List<String> t = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            t.add(b.readUtf(16));
         }

         return new Packets.AdminAction(action, id, t, b.readVarInt(), b.readUtf(64));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeVarInt(this.action);
         b.writeUtf(Packets.cut(this.id, 64), 64);
         int n = Math.min(32, this.targets.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            b.writeUtf(Packets.cut(this.targets.get(i), 16), 16);
         }

         b.writeVarInt(this.count);
         b.writeUtf(Packets.cut(this.query, 64), 64);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            AdminService.handle(sp, this.action, this.id, this.targets, this.count, this.query);
         }
      }
   }

   public static final class AdminData implements Packets.Packet {
      public static final int MAX_ROWS = 300;
      public int flags;
      public String message = "";
      public boolean ok = true;
      public final List<StampModel> stamps = new ArrayList<>();
      public final List<Packets.AdminData.Tpl> templates = new ArrayList<>();
      public final List<String[]> presets = new ArrayList<>();
      public final List<Packets.AdminData.Reg> registry = new ArrayList<>();
      public final List<String> players = new ArrayList<>();

      public static Packets.AdminData decode(FriendlyByteBuf b) {
         Packets.AdminData d = new Packets.AdminData();
         d.flags = b.readVarInt();
         d.message = b.readUtf(300);
         d.ok = b.readBoolean();
         int n = Math.min(300, b.readVarInt());

         for (int i = 0; i < n; i++) {
            StampModel m = new StampModel();
            m.id = b.readUtf(32);
            m.org = b.readUtf(60);
            m.ownerName = b.readUtf(40);
            m.created = b.readUtf(16);
            m.approved = b.readBoolean();
            m.everApproved = b.readBoolean();
            m.hasImage = b.readBoolean();
            m.kind = b.readVarInt();
            d.stamps.add(m);
         }

         n = Math.min(300, b.readVarInt());

         for (int i = 0; i < n; i++) {
            d.templates.add(new Packets.AdminData.Tpl(b.readUtf(24), b.readUtf(60), b.readUtf(40), b.readUtf(16), b.readBoolean(), b.readVarInt()));
         }

         n = Math.min(64, b.readVarInt());

         for (int i = 0; i < n; i++) {
            d.presets.add(new String[]{b.readUtf(16), b.readUtf(60)});
         }

         n = Math.min(300, b.readVarInt());

         for (int i = 0; i < n; i++) {
            d.registry
               .add(
                  new Packets.AdminData.Reg(
                     b.readBoolean(), b.readUtf(64), b.readUtf(24), b.readUtf(32), b.readUtf(40), b.readUtf(16), b.readBoolean(), b.readLong()
                  )
               );
         }

         n = Math.min(500, b.readVarInt());

         for (int i = 0; i < n; i++) {
            d.players.add(b.readUtf(16));
         }

         return d;
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeVarInt(this.flags);
         b.writeUtf(Packets.cut(this.message, 300), 300);
         b.writeBoolean(this.ok);
         int n = Math.min(300, this.stamps.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            StampModel m = this.stamps.get(i);
            b.writeUtf(Packets.cut(m.id, 32), 32);
            b.writeUtf(Packets.cut(m.org, 60), 60);
            b.writeUtf(Packets.cut(m.ownerName, 40), 40);
            b.writeUtf(Packets.cut(m.created, 16), 16);
            b.writeBoolean(m.approved);
            b.writeBoolean(m.everApproved);
            b.writeBoolean(m.hasImage);
            b.writeVarInt(m.kind);
         }

         n = Math.min(300, this.templates.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            Packets.AdminData.Tpl t = this.templates.get(i);
            b.writeUtf(Packets.cut(t.id(), 24), 24);
            b.writeUtf(Packets.cut(t.name(), 60), 60);
            b.writeUtf(Packets.cut(t.owner(), 40), 40);
            b.writeUtf(Packets.cut(t.created(), 16), 16);
            b.writeBoolean(t.approved());
            b.writeVarInt(t.fields());
         }

         n = Math.min(64, this.presets.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            b.writeUtf(Packets.cut(this.presets.get(i)[0], 16), 16);
            b.writeUtf(Packets.cut(this.presets.get(i)[1], 60), 60);
         }

         n = Math.min(300, this.registry.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            Packets.AdminData.Reg r = this.registry.get(i);
            b.writeBoolean(r.passport());
            b.writeUtf(Packets.cut(r.id(), 64), 64);
            b.writeUtf(Packets.cut(r.kind(), 24), 24);
            b.writeUtf(Packets.cut(r.number(), 32), 32);
            b.writeUtf(Packets.cut(r.author(), 40), 40);
            b.writeUtf(Packets.cut(r.date(), 16), 16);
            b.writeBoolean(r.revoked());
            b.writeLong(r.time());
         }

         n = Math.min(500, this.players.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            b.writeUtf(Packets.cut(this.players.get(i), 16), 16);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onAdminData(this));
      }

      public static record Reg(boolean passport, String id, String kind, String number, String author, String date, boolean revoked, long time) {
      }

      public static record Tpl(String id, String name, String owner, String created, boolean approved, int fields) {
      }
   }

   public static final class AdminOpen implements Packets.Packet {
      final String query;

      public AdminOpen(String query) {
         this.query = query == null ? "" : query;
      }

      public static Packets.AdminOpen decode(FriendlyByteBuf b) {
         return new Packets.AdminOpen(b.readUtf(64));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.query, 64), 64);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            AdminService.sendPanel(sp, this.query, "", true);
         }
      }
   }

   public static final class AssetData implements Packets.Packet {
      final String id;
      final boolean exists;
      final byte[] png;

      public AssetData(String id, boolean exists, byte[] png) {
         this.id = id;
         this.exists = exists;
         this.png = png;
      }

      public static Packets.AssetData decode(FriendlyByteBuf b) {
         String id = b.readUtf(24);
         boolean ex = b.readBoolean();
         return new Packets.AssetData(id, ex, ex ? b.readByteArray(700000) : new byte[0]);
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 24), 24);
         b.writeBoolean(this.exists);
         if (this.exists) {
            b.writeByteArray(this.png);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onAssetData(this.id, this.exists, this.png));
      }
   }

   public static final class AssetRequest implements Packets.Packet {
      final String id;

      public AssetRequest(String id) {
         this.id = id;
      }

      public static Packets.AssetRequest decode(FriendlyByteBuf b) {
         return new Packets.AssetRequest(b.readUtf(24));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 24), 24);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PassportService.sendAsset(sp, this.id);
         }
      }
   }

   public static final class CoSign implements Packets.Packet {
      final InteractionHand hand;
      final String position;
      final int[] sig;

      public CoSign(InteractionHand hand, String position, int[] sig) {
         this.hand = hand;
         this.position = position;
         this.sig = sig;
      }

      public static Packets.CoSign decode(FriendlyByteBuf b) {
         return new Packets.CoSign((InteractionHand)b.readEnum(InteractionHand.class), b.readUtf(60), Packets.readInts(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeUtf(Packets.cut(this.position, 60), 60);
         Packets.writeInts(b, this.sig);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.coSign(sp, this.hand, this.position, this.sig);
         }
      }
   }

   public static final class EditTemplate implements Packets.Packet {
      final PassportTemplate template;

      public EditTemplate(PassportTemplate template) {
         this.template = template;
      }

      public static Packets.EditTemplate decode(FriendlyByteBuf b) {
         return new Packets.EditTemplate(Packets.readTemplate(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         Packets.writeTemplate(b, this.template);
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.editTemplate(this.template));
      }
   }

   public static final class ForgeCopy implements Packets.Packet {
      final InteractionHand kitHand;
      final Map<String, String> values;
      final byte[] photo;

      public ForgeCopy(InteractionHand kitHand, Map<String, String> values, byte[] photo) {
         this.kitHand = kitHand;
         this.values = values;
         this.photo = photo == null ? new byte[0] : photo;
      }

      public static Packets.ForgeCopy decode(FriendlyByteBuf b) {
         InteractionHand h = (InteractionHand)b.readEnum(InteractionHand.class);
         int n = Math.min(22, b.readVarInt());
         Map<String, String> v = new LinkedHashMap<>();

         for (int i = 0; i < n; i++) {
            v.put(b.readUtf(24), b.readUtf(2400));
         }

         return new Packets.ForgeCopy(h, v, b.readByteArray(700000));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.kitHand);
         int n = Math.min(22, this.values.size());
         b.writeVarInt(n);
         int i = 0;

         for (Entry<String, String> e : this.values.entrySet()) {
            if (i++ >= n) {
               break;
            }

            b.writeUtf(Packets.cut(e.getKey(), 24), 24);
            b.writeUtf(Packets.cut(e.getValue(), 2400), 2400);
         }

         b.writeByteArray(this.photo);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            ForgeryService.forgeCopy(sp, this.kitHand, this.values, this.photo);
         }
      }
   }

   public static final class ForgeStamp implements Packets.Packet {
      final InteractionHand kitHand;
      final String stampId;

      public ForgeStamp(InteractionHand kitHand, String stampId) {
         this.kitHand = kitHand;
         this.stampId = stampId;
      }

      public static Packets.ForgeStamp decode(FriendlyByteBuf b) {
         return new Packets.ForgeStamp((InteractionHand)b.readEnum(InteractionHand.class), b.readUtf(32));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.kitHand);
         b.writeUtf(Packets.cut(this.stampId, 32), 32);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            ForgeryService.forgeStamp(sp, this.kitHand, this.stampId);
         }
      }
   }

   public static final class IssuePassport implements Packets.Packet {
      final InteractionHand hand;
      final Map<String, String> values;
      final byte[] photo;
      final int[] sig;
      final boolean edit;

      public IssuePassport(InteractionHand hand, Map<String, String> values, byte[] photo, int[] sig) {
         this(hand, values, photo, sig, false);
      }

      public IssuePassport(InteractionHand hand, Map<String, String> values, byte[] photo, int[] sig, boolean edit) {
         this.hand = hand;
         this.values = values;
         this.photo = photo == null ? new byte[0] : photo;
         this.sig = sig;
         this.edit = edit;
      }

      public static Packets.IssuePassport decode(FriendlyByteBuf b) {
         InteractionHand hand = (InteractionHand)b.readEnum(InteractionHand.class);
         boolean edit = b.readBoolean();
         int n = Math.min(16, b.readVarInt());
         Map<String, String> v = new LinkedHashMap<>();

         for (int i = 0; i < n; i++) {
            v.put(b.readUtf(24), b.readUtf(60));
         }

         return new Packets.IssuePassport(hand, v, b.readByteArray(700000), Packets.readInts(b), edit);
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeBoolean(this.edit);
         int n = Math.min(16, this.values.size());
         b.writeVarInt(n);
         int i = 0;

         for (Entry<String, String> e : this.values.entrySet()) {
            if (i++ >= n) {
               break;
            }

            b.writeUtf(Packets.cut(e.getKey(), 24), 24);
            b.writeUtf(Packets.cut(e.getValue(), 60), 60);
         }

         b.writeByteArray(this.photo);
         Packets.writeInts(b, this.sig);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            if (this.edit) {
               PassportService.edit(sp, this.hand, this.values, this.photo, this.sig);
            } else {
               PassportService.issue(sp, this.hand, this.values, this.photo, this.sig);
            }
         }
      }
   }

   public static final class OpenDesigner implements Packets.Packet {
      public static Packets.OpenDesigner decode(FriendlyByteBuf b) {
         return new Packets.OpenDesigner();
      }

      @Override
      public void encode(FriendlyByteBuf b) {
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openDesigner);
      }
   }

   public static final class OpenPrinter implements Packets.Packet {
      final BlockPos pos;
      final List<String[]> templates;

      public OpenPrinter(BlockPos pos, List<String[]> templates) {
         this.pos = pos;
         this.templates = templates;
      }

      public static Packets.OpenPrinter decode(FriendlyByteBuf b) {
         BlockPos pos = b.readBlockPos();
         int n = Math.min(200, b.readVarInt());
         List<String[]> t = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            t.add(new String[]{b.readUtf(24), b.readUtf(60), b.readUtf(4)});
         }

         return new Packets.OpenPrinter(pos, t);
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         int n = Math.min(200, this.templates.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            String[] t = this.templates.get(i);
            b.writeUtf(Packets.cut(t[0], 24), 24);
            b.writeUtf(Packets.cut(t[1], 60), 60);
            b.writeUtf(Packets.cut(t[2], 4), 4);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPrinter(this.pos, this.templates));
      }
   }

   public static final class OpenTemplateEditor implements Packets.Packet {
      public final String preset;

      public OpenTemplateEditor() {
         this("");
      }

      public OpenTemplateEditor(String preset) {
         this.preset = preset == null ? "" : preset;
      }

      public static Packets.OpenTemplateEditor decode(FriendlyByteBuf b) {
         return new Packets.OpenTemplateEditor(b.readUtf(16));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(this.preset, 16);
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openTemplateEditor(this.preset));
      }
   }

   public interface Packet {
      void encode(FriendlyByteBuf var1);

      void handle(Context var1);
   }

   public static final class PlaceStamp implements Packets.Packet {
      final InteractionHand hand;
      final String stampId;
      final int x;
      final int y;
      final int rot;
      final int scale;
      final int page;
      final int dir;
      final int cat;
      final int days;

      public PlaceStamp(InteractionHand hand, String stampId, int x, int y, int rot, int scale, int page, int dir, int cat, int days) {
         this.hand = hand;
         this.stampId = stampId;
         this.x = x;
         this.y = y;
         this.rot = rot;
         this.scale = scale;
         this.page = page;
         this.dir = dir;
         this.cat = cat;
         this.days = days;
      }

      public static Packets.PlaceStamp decode(FriendlyByteBuf b) {
         return new Packets.PlaceStamp(
            (InteractionHand)b.readEnum(InteractionHand.class),
            b.readUtf(32),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt()
         );
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeUtf(Packets.cut(this.stampId, 32), 32);
         b.writeVarInt(this.x);
         b.writeVarInt(this.y);
         b.writeVarInt(this.rot);
         b.writeVarInt(this.scale);
         b.writeVarInt(this.page);
         b.writeVarInt(this.dir);
         b.writeVarInt(this.cat);
         b.writeVarInt(this.days);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.placeStamp(
               sp,
               this.hand,
               this.stampId,
               new DocModel.StampRef(this.stampId, this.page, this.x, this.y, this.rot, this.scale).extra(this.dir, this.cat, "", "", ""),
               this.days
            );
         }
      }
   }

   public static final class PrinterAction implements Packets.Packet {
      final BlockPos pos;
      final String templateId;
      final int count;

      public PrinterAction(BlockPos pos, String templateId, int count) {
         this.pos = pos;
         this.templateId = templateId == null ? "" : templateId;
         this.count = count;
      }

      public static Packets.PrinterAction decode(FriendlyByteBuf b) {
         return new Packets.PrinterAction(b.readBlockPos(), b.readUtf(24), b.readVarInt());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBlockPos(this.pos);
         b.writeUtf(Packets.cut(this.templateId, 24), 24);
         b.writeVarInt(this.count);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            OfficeService.print(sp, this.pos, this.templateId, this.count);
         }
      }
   }

   public static final class SaveDoc implements Packets.Packet {
      final InteractionHand hand;
      final boolean issue;
      final DocModel doc;
      final int[] sig;

      public SaveDoc(InteractionHand hand, boolean issue, DocModel doc, int[] sig) {
         this.hand = hand;
         this.issue = issue;
         this.doc = doc;
         this.sig = sig;
      }

      public static Packets.SaveDoc decode(FriendlyByteBuf b) {
         InteractionHand hand = (InteractionHand)b.readEnum(InteractionHand.class);
         boolean issue = b.readBoolean();
         DocModel d = new DocModel();
         d.template = b.readVarInt();
         d.number = b.readUtf(20);
         d.org = b.readUtf(80);
         d.position = b.readUtf(60);
         d.subject = b.readUtf(140);
         d.body = b.readUtf(2400);
         return new Packets.SaveDoc(hand, issue, d, Packets.readInts(b));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
         b.writeBoolean(this.issue);
         b.writeVarInt(this.doc.template);
         b.writeUtf(Packets.cut(this.doc.number, 20), 20);
         b.writeUtf(Packets.cut(this.doc.org, 80), 80);
         b.writeUtf(Packets.cut(this.doc.position, 60), 60);
         b.writeUtf(Packets.cut(this.doc.subject, 140), 140);
         b.writeUtf(Packets.cut(this.doc.body, 2400), 2400);
         Packets.writeInts(b, this.sig);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.saveOrIssue(sp, this.hand, this.doc, this.sig, this.issue);
         }
      }
   }

   public static final class StampData implements Packets.Packet {
      final String id;
      final boolean exists;
      final StampModel stamp;
      final byte[] png;

      public StampData(String id, boolean exists, StampModel stamp, byte[] png) {
         this.id = id;
         this.exists = exists;
         this.stamp = stamp;
         this.png = png;
      }

      public static Packets.StampData decode(FriendlyByteBuf b) {
         String id = b.readUtf(32);
         boolean exists = b.readBoolean();
         StampModel m = new StampModel();
         byte[] png = new byte[0];
         if (exists) {
            m.id = id;
            m.org = b.readUtf(60);
            m.shape = b.readVarInt();
            m.kind = b.readVarInt();
            m.color = b.readInt();
            m.top = b.readUtf(44);
            m.bottom = b.readUtf(44);
            m.center = b.readUtf(88);
            m.hasImage = b.readBoolean();
            m.approved = b.readBoolean();
            Packets.readElements(b, m.visa);
            png = b.readByteArray(24064);
         }

         return new Packets.StampData(id, exists, m, png);
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 32), 32);
         b.writeBoolean(this.exists);
         if (this.exists) {
            b.writeUtf(Packets.cut(this.stamp.org, 60), 60);
            b.writeVarInt(this.stamp.shape);
            b.writeVarInt(this.stamp.kind);
            b.writeInt(this.stamp.color);
            b.writeUtf(Packets.cut(this.stamp.top, 44), 44);
            b.writeUtf(Packets.cut(this.stamp.bottom, 44), 44);
            b.writeUtf(Packets.cut(this.stamp.center, 88), 88);
            b.writeBoolean(this.stamp.hasImage);
            b.writeBoolean(this.stamp.approved);
            Packets.writeElements(b, this.stamp.visa);
            b.writeByteArray(this.png);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onStampData(this.id, this.exists, this.stamp, this.png));
      }
   }

   public static final class StampRequest implements Packets.Packet {
      final String id;

      public StampRequest(String id) {
         this.id = id;
      }

      public static Packets.StampRequest decode(FriendlyByteBuf b) {
         return new Packets.StampRequest(b.readUtf(32));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 32), 32);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.sendStamp(sp, this.id);
         }
      }
   }

   public static final class SubmitStamp implements Packets.Packet {
      final StampModel stamp;
      final byte[] png;

      public SubmitStamp(StampModel stamp, byte[] png) {
         this.stamp = stamp;
         this.png = png == null ? new byte[0] : png;
      }

      public static Packets.SubmitStamp decode(FriendlyByteBuf b) {
         StampModel m = new StampModel();
         m.org = b.readUtf(60);
         m.shape = b.readVarInt();
         m.kind = b.readVarInt();
         m.color = b.readInt();
         m.top = b.readUtf(44);
         m.bottom = b.readUtf(44);
         m.center = b.readUtf(88);
         Packets.readElements(b, m.visa);
         return new Packets.SubmitStamp(m, b.readByteArray(24064));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.stamp.org, 60), 60);
         b.writeVarInt(this.stamp.shape);
         b.writeVarInt(this.stamp.kind);
         b.writeInt(this.stamp.color);
         b.writeUtf(Packets.cut(this.stamp.top, 44), 44);
         b.writeUtf(Packets.cut(this.stamp.bottom, 44), 44);
         b.writeUtf(Packets.cut(this.stamp.center, 88), 88);
         Packets.writeElements(b, this.stamp.visa);
         b.writeByteArray(this.png);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.submitStamp(sp, this.stamp, this.png);
         }
      }
   }

   public static final class SubmitTemplate implements Packets.Packet {
      final PassportTemplate template;
      final byte[] emblem;

      public SubmitTemplate(PassportTemplate template, byte[] emblem) {
         this.template = template;
         this.emblem = emblem == null ? new byte[0] : emblem;
      }

      public static Packets.SubmitTemplate decode(FriendlyByteBuf b) {
         PassportTemplate t = Packets.readTemplate(b);
         return new Packets.SubmitTemplate(t, b.readByteArray(700000));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         Packets.writeTemplate(b, this.template);
         b.writeByteArray(this.emblem);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PassportService.submitTemplate(sp, this.template, this.emblem);
         }
      }
   }

   public static final class TemplateData implements Packets.Packet {
      final String id;
      final boolean exists;
      final PassportTemplate template;

      public TemplateData(String id, boolean exists, PassportTemplate template) {
         this.id = id;
         this.exists = exists;
         this.template = template;
      }

      public static Packets.TemplateData decode(FriendlyByteBuf b) {
         String id = b.readUtf(24);
         boolean ex = b.readBoolean();
         return new Packets.TemplateData(id, ex, ex ? Packets.readTemplate(b) : new PassportTemplate());
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 24), 24);
         b.writeBoolean(this.exists);
         if (this.exists) {
            Packets.writeTemplate(b, this.template);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onTemplateData(this.id, this.exists, this.template));
      }
   }

   public static final class TemplateRequest implements Packets.Packet {
      final String id;

      public TemplateRequest(String id) {
         this.id = id;
      }

      public static Packets.TemplateRequest decode(FriendlyByteBuf b) {
         return new Packets.TemplateRequest(b.readUtf(24));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeUtf(Packets.cut(this.id, 24), 24);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            PassportService.sendTemplate(sp, this.id);
         }
      }
   }

   public static final class Verify implements Packets.Packet {
      final InteractionHand hand;

      public Verify(InteractionHand hand) {
         this.hand = hand;
      }

      public static Packets.Verify decode(FriendlyByteBuf b) {
         return new Packets.Verify((InteractionHand)b.readEnum(InteractionHand.class));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeEnum(this.hand);
      }

      @Override
      public void handle(Context ctx) {
         ServerPlayer sp = ctx.getSender();
         if (sp != null) {
            DocService.verifyAndSend(sp, this.hand);
         }
      }
   }

   public static final class VerifyResult implements Packets.Packet {
      final int status;
      final List<String> lines;

      public VerifyResult(int status, List<String> lines) {
         this.status = status;
         this.lines = lines;
      }

      public static Packets.VerifyResult decode(FriendlyByteBuf b) {
         int status = b.readVarInt();
         int n = Math.min(32, b.readVarInt());
         List<String> lines = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            lines.add(b.readUtf(300));
         }

         return new Packets.VerifyResult(status, lines);
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeVarInt(this.status);
         int n = Math.min(32, this.lines.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            b.writeUtf(Packets.cut(this.lines.get(i), 300), 300);
         }
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onVerifyResult(this.status, this.lines));
      }
   }

   public static final class ViewRemote implements Packets.Packet {
      final boolean passport;
      final CompoundTag tag;
      final int status;
      final List<String> lines;
      final String from;

      public ViewRemote(boolean passport, CompoundTag tag, int status, List<String> lines, String from) {
         this.passport = passport;
         this.tag = tag;
         this.status = status;
         this.lines = lines;
         this.from = from;
      }

      public static Packets.ViewRemote decode(FriendlyByteBuf b) {
         boolean pass = b.readBoolean();
         CompoundTag tag = b.readNbt();
         int status = b.readVarInt();
         int n = Math.min(32, b.readVarInt());
         List<String> lines = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            lines.add(b.readUtf(300));
         }

         return new Packets.ViewRemote(pass, tag == null ? new CompoundTag() : tag, status, lines, b.readUtf(40));
      }

      @Override
      public void encode(FriendlyByteBuf b) {
         b.writeBoolean(this.passport);
         b.writeNbt(this.tag);
         b.writeVarInt(this.status);
         int n = Math.min(32, this.lines.size());
         b.writeVarInt(n);

         for (int i = 0; i < n; i++) {
            b.writeUtf(Packets.cut(this.lines.get(i), 300), 300);
         }

         b.writeUtf(Packets.cut(this.from, 40), 40);
      }

      @Override
      public void handle(Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onViewRemote(this.passport, this.tag, this.status, this.lines, this.from));
      }
   }
}
