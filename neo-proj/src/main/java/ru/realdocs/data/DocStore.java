package ru.realdocs.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.LevelResource;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;

public class DocStore extends SavedData {
   public static final String NAME = "signedsealed";
   static final int MAX_HISTORY = 24;
   private byte[] secret;
   public final Map<String, DocStore.Rec> docs = new HashMap<>();
   public final Map<String, StampModel> stamps = new LinkedHashMap<>();
   public int stampCounter;
   public final Map<String, PassportTemplate> templates = new LinkedHashMap<>();
   public final Map<String, DocStore.AssetRec> assets = new HashMap<>();
   public final Map<String, DocStore.Rec> passes = new HashMap<>();
   public int templateCounter;
   public int assetCounter;
   public int serialCounter;
   public final Map<String, DocStore.PaperRec> papers = new HashMap<>();
   public final Map<String, DocStore.TitleRec> titles = new LinkedHashMap<>();
   public final Map<String, List<CompoundTag>> mail = new HashMap<>();
   /** Digital archives: network key -> scanned records (metadata + item snapshot). */
   public final Map<String, List<CompoundTag>> digital = new LinkedHashMap<>();
   private static final SecureRandom RNG = new SecureRandom();

   public DocStore() {
      this.secret = new byte[32];
      new SecureRandom().nextBytes(this.secret);
      this.setDirty();
   }

   public static DocStore get(MinecraftServer server) {
      return (DocStore)server.overworld().getDataStorage().computeIfAbsent(new Factory<DocStore>(DocStore::new, (tag, reg) -> load(tag), null), "signedsealed");
   }

   public String hmac(String canonical) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(this.secret, "HmacSHA256"));
         byte[] out = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
         StringBuilder sb = new StringBuilder(out.length * 2);

         for (byte b : out) {
            sb.append(String.format("%02x", b));
         }

         return sb.toString();
      } catch (Exception var9) {
         throw new IllegalStateException("HMAC is not available", var9);
      }
   }

   public String secCode(String id) {
      String h = this.hmac("SEC1;" + id).toUpperCase(Locale.ROOT);
      return h.substring(0, 4) + "-" + h.substring(4, 8);
   }

   public static boolean safeEquals(String a, String b) {
      return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
   }

   public String nextStampId() {
      this.stampCounter++;
      this.setDirty();
      return String.format("ST-%04d", this.stampCounter);
   }

   public String nextTemplateId() {
      this.templateCounter++;
      this.setDirty();
      return String.format("PT-%03d", this.templateCounter);
   }

   public String nextAssetId() {
      this.assetCounter++;
      this.setDirty();

      String id;
      do {
         id = "AS-" + Long.toHexString(RNG.nextLong() & 281474976710655L).toUpperCase();
      } while (this.assets.containsKey(id));

      return id;
   }

   public int nextSerial() {
      this.serialCounter++;
      this.setDirty();
      return this.serialCounter;
   }

   public static Path assetDir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("signedsealed").resolve("assets");
   }

   public static void saveAsset(MinecraftServer server, String id, byte[] png) throws IOException {
      Path dir = assetDir(server);
      Files.createDirectories(dir);
      Files.write(dir.resolve(id + ".png"), png);
   }

   public static byte[] loadAsset(MinecraftServer server, String id) {
      try {
         Path p = assetDir(server).resolve(id + ".png");
         return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
      } catch (IOException var3) {
         return new byte[0];
      }
   }

   public static Path stampDir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("signedsealed").resolve("stamps");
   }

   public static void saveStampImage(MinecraftServer server, String id, byte[] png) throws IOException {
      Path dir = stampDir(server);
      Files.createDirectories(dir);
      Files.write(dir.resolve(id + ".png"), png);
   }

   public static byte[] loadStampImage(MinecraftServer server, String id) {
      try {
         Path p = stampDir(server).resolve(id + ".png");
         return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
      } catch (IOException var3) {
         return new byte[0];
      }
   }

   public static void deleteStampImage(MinecraftServer server, String id) {
      try {
         Files.deleteIfExists(stampDir(server).resolve(id + ".png"));
      } catch (IOException var3) {
      }
   }

   public static DocStore load(CompoundTag tag) {
      DocStore s = new DocStore();
      byte[] sec = tag.getByteArray("Secret");
      if (sec.length >= 16) {
         s.secret = sec;
      }

      s.stampCounter = tag.getInt("StampCounter");
      s.templateCounter = tag.getInt("TemplateCounter");
      s.assetCounter = tag.getInt("AssetCounter");
      s.serialCounter = tag.getInt("SerialCounter");
      ListTag tps = tag.getList("Templates", 10);

      for (int i = 0; i < tps.size(); i++) {
         PassportTemplate t = NbtCodec.readTemplate(tps.getCompound(i));
         s.templates.put(t.id, t);
      }

      ListTag ass = tag.getList("Assets", 10);

      for (int i = 0; i < ass.size(); i++) {
         CompoundTag c = ass.getCompound(i);
         DocStore.AssetRec a = new DocStore.AssetRec();
         a.ownerUuid = c.getString("Owner");
         a.kind = c.getString("Kind");
         a.created = c.getString("Created");
         s.assets.put(c.getString("Id"), a);
      }

      ListTag pas = tag.getList("Passes", 10);

      for (int i = 0; i < pas.size(); i++) {
         CompoundTag c = pas.getCompound(i);
         DocStore.Rec r = new DocStore.Rec();
         r.hash = c.getString("Hash");
         r.author = c.getString("Author");
         r.authorUuid = c.getString("AuthorUuid");
         r.date = c.getString("Date");
         r.number = c.getString("Number");
         r.revoked = c.getBoolean("Revoked");
         readHistory(c, r);
         s.passes.put(c.getString("Id"), r);
      }

      ListTag docs = tag.getList("Docs", 10);

      for (int i = 0; i < docs.size(); i++) {
         CompoundTag c = docs.getCompound(i);
         DocStore.Rec r = new DocStore.Rec();
         r.hash = c.getString("Hash");
         r.author = c.getString("Author");
         r.authorUuid = c.getString("AuthorUuid");
         r.date = c.getString("Date");
         r.number = c.getString("Number");
         r.template = c.getInt("Template");
         r.revoked = c.getBoolean("Revoked");
         readHistory(c, r);
         s.docs.put(c.getString("Id"), r);
      }

      ListTag stamps = tag.getList("Stamps", 10);

      for (int i = 0; i < stamps.size(); i++) {
         StampModel m = NbtCodec.readStamp(stamps.getCompound(i));
         s.stamps.put(m.id, m);
      }

      ListTag pl = tag.getList("Papers", 10);

      for (int i = 0; i < pl.size(); i++) {
         CompoundTag c = pl.getCompound(i);
         DocStore.PaperRec r = new DocStore.PaperRec();
         r.ownerUuid = c.getString("Owner");
         r.ownerName = c.getString("OwnerName");
         r.name = c.getString("Name");
         r.issue = c.getInt("Issue");
         r.date = c.getString("Date");
         r.format = c.getInt("Fmt");
         r.pages = c.getInt("Pages");
         r.published = c.getBoolean("Pub");
         r.revoked = c.getBoolean("Revoked");
         r.hash = c.getString("Hash");
         r.time = c.getLong("Time");
         s.papers.put(c.getString("Id"), r);
      }

      ListTag tl = tag.getList("Titles", 10);

      for (int i = 0; i < tl.size(); i++) {
         CompoundTag c = tl.getCompound(i);
         DocStore.TitleRec r = new DocStore.TitleRec();
         r.name = c.getString("Name");
         r.ownerUuid = c.getString("Owner");
         r.ownerName = c.getString("OwnerName");
         r.counter = c.getInt("Counter");
         ListTag st = c.getList("Staff", 10);

         for (int j = 0; j < st.size(); j++) {
            r.staff.put(st.getCompound(j).getString("U"), st.getCompound(j).getString("N"));
         }

         s.titles.put(titleKey(r.name), r);
      }

      CompoundTag dt = tag.getCompound("Digital");

      for (String net : dt.getAllKeys()) {
         ListTag l = dt.getList(net, 10);
         List<CompoundTag> recs = new ArrayList<>();

         for (int i = 0; i < l.size(); i++) {
            recs.add(l.getCompound(i));
         }

         s.digital.put(net, recs);
      }

      CompoundTag mt = tag.getCompound("Mail");

      for (String who : mt.getAllKeys()) {
         ListTag l = mt.getList(who, 10);
         List<CompoundTag> box = new ArrayList<>();

         for (int i = 0; i < l.size(); i++) {
            box.add(l.getCompound(i));
         }

         if (!box.isEmpty()) {
            s.mail.put(who, box);
         }
      }

      return s;
   }

   private static void readHistory(CompoundTag c, DocStore.Rec r) {
      r.time = c.getLong("Time");
      ListTag l = c.getList("Old", 8);

      for (int i = 0; i < l.size(); i++) {
         r.old.add(l.getString(i));
      }
   }

   private static void writeHistory(CompoundTag c, DocStore.Rec r) {
      c.putLong("Time", r.time);
      ListTag l = new ListTag();

      for (String o : r.old) {
         l.add(StringTag.valueOf(o));
      }

      c.put("Old", l);
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putByteArray("Secret", this.secret);
      tag.putInt("StampCounter", this.stampCounter);
      tag.putInt("TemplateCounter", this.templateCounter);
      tag.putInt("AssetCounter", this.assetCounter);
      tag.putInt("SerialCounter", this.serialCounter);
      ListTag tps = new ListTag();

      for (PassportTemplate t : this.templates.values()) {
         tps.add(NbtCodec.writeTemplate(t));
      }

      tag.put("Templates", tps);
      ListTag ass = new ListTag();

      for (Entry<String, DocStore.AssetRec> e : this.assets.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putString("Id", e.getKey());
         c.putString("Owner", e.getValue().ownerUuid);
         c.putString("Kind", e.getValue().kind);
         c.putString("Created", e.getValue().created);
         ass.add(c);
      }

      tag.put("Assets", ass);
      ListTag pas = new ListTag();

      for (Entry<String, DocStore.Rec> e : this.passes.entrySet()) {
         DocStore.Rec r = e.getValue();
         CompoundTag c = new CompoundTag();
         c.putString("Id", e.getKey());
         c.putString("Hash", r.hash);
         c.putString("Author", r.author);
         c.putString("AuthorUuid", r.authorUuid);
         c.putString("Date", r.date);
         c.putString("Number", r.number);
         c.putBoolean("Revoked", r.revoked);
         writeHistory(c, r);
         pas.add(c);
      }

      tag.put("Passes", pas);
      ListTag docs = new ListTag();

      for (Entry<String, DocStore.Rec> e : this.docs.entrySet()) {
         DocStore.Rec r = e.getValue();
         CompoundTag c = new CompoundTag();
         c.putString("Id", e.getKey());
         c.putString("Hash", r.hash);
         c.putString("Author", r.author);
         c.putString("AuthorUuid", r.authorUuid);
         c.putString("Date", r.date);
         c.putString("Number", r.number);
         c.putInt("Template", r.template);
         c.putBoolean("Revoked", r.revoked);
         writeHistory(c, r);
         docs.add(c);
      }

      tag.put("Docs", docs);
      ListTag st = new ListTag();

      for (StampModel m : this.stamps.values()) {
         st.add(NbtCodec.writeStamp(m));
      }

      tag.put("Stamps", st);
      ListTag pl = new ListTag();

      for (Entry<String, DocStore.PaperRec> e : this.papers.entrySet()) {
         DocStore.PaperRec r = e.getValue();
         CompoundTag c = new CompoundTag();
         c.putString("Id", e.getKey());
         c.putString("Owner", r.ownerUuid);
         c.putString("OwnerName", r.ownerName);
         c.putString("Name", r.name);
         c.putInt("Issue", r.issue);
         c.putString("Date", r.date);
         c.putInt("Fmt", r.format);
         c.putInt("Pages", r.pages);
         c.putBoolean("Pub", r.published);
         c.putBoolean("Revoked", r.revoked);
         c.putString("Hash", r.hash);
         c.putLong("Time", r.time);
         pl.add(c);
      }

      tag.put("Papers", pl);
      ListTag tl = new ListTag();

      for (DocStore.TitleRec r : this.titles.values()) {
         CompoundTag c = new CompoundTag();
         c.putString("Name", r.name);
         c.putString("Owner", r.ownerUuid);
         c.putString("OwnerName", r.ownerName);
         c.putInt("Counter", r.counter);
         ListTag stl = new ListTag();

         for (Entry<String, String> m : r.staff.entrySet()) {
            CompoundTag x = new CompoundTag();
            x.putString("U", m.getKey());
            x.putString("N", m.getValue());
            stl.add(x);
         }

         c.put("Staff", stl);
         tl.add(c);
      }

      tag.put("Titles", tl);
      CompoundTag mt = new CompoundTag();

      for (Entry<String, List<CompoundTag>> e : this.mail.entrySet()) {
         if (!e.getValue().isEmpty()) {
            ListTag l = new ListTag();
            l.addAll(e.getValue());
            mt.put(e.getKey(), l);
         }
      }

      tag.put("Mail", mt);
      CompoundTag dt = new CompoundTag();

      for (Entry<String, List<CompoundTag>> e : this.digital.entrySet()) {
         ListTag l = new ListTag();
         l.addAll(e.getValue());
         dt.put(e.getKey(), l);
      }

      tag.put("Digital", dt);
      return tag;
   }

   public static String titleKey(String name) {
      return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
   }

   public static Path paperDir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("signedsealed").resolve("papers");
   }

   public static void savePaper(MinecraftServer server, String id, byte[] data) throws IOException {
      Path dir = paperDir(server);
      Files.createDirectories(dir);
      Path tmp = dir.resolve(id + ".nbt.tmp");
      Files.write(tmp, data);
      Files.move(tmp, dir.resolve(id + ".nbt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   }

   public static byte[] loadPaper(MinecraftServer server, String id) {
      try {
         Path p = paperDir(server).resolve(id + ".nbt");
         return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
      } catch (IOException var3) {
         return new byte[0];
      }
   }

   public static void deletePaper(MinecraftServer server, String id) {
      try {
         Files.deleteIfExists(paperDir(server).resolve(id + ".nbt"));
      } catch (IOException var3) {
      }
   }

   public String nextPaperId() {
      String id;
      do {
         id = "NP-" + Long.toHexString(RNG.nextLong() & 281474976710655L).toUpperCase();
      } while (this.papers.containsKey(id));

      return id;
   }

   public static final class PaperRec {
      public String ownerUuid = "";
      public String ownerName = "";
      public String name = "";
      public int issue;
      public String date = "";
      public int format;
      public int pages = 1;
      public boolean published;
      public boolean revoked;
      public String hash = "";
      public long time;
   }

   public static final class TitleRec {
      public String name = "";
      public String ownerUuid = "";
      public String ownerName = "";
      public int counter;
      public final Map<String, String> staff = new LinkedHashMap<>();

      public boolean member(String uuid) {
         return this.ownerUuid.equals(uuid) || this.staff.containsKey(uuid);
      }
   }

   public static final class AssetRec {
      public String ownerUuid = "";
      public String kind = "";
      public String created = "";
   }

   public static final class Rec {
      public String hash = "";
      public String author = "";
      public String authorUuid = "";
      public String date = "";
      public String number = "";
      public int template;
      public boolean revoked;
      public long time;
      public final List<String> old = new ArrayList<>();

      public void rehash(String newHash) {
         if (!this.hash.isEmpty() && !this.hash.equals(newHash)) {
            this.old.add(this.hash);

            while (this.old.size() > 24) {
               this.old.remove(0);
            }
         }

         this.hash = newHash;
      }

      public boolean isOld(String h) {
         for (String o : this.old) {
            if (DocStore.safeEquals(o, h)) {
               return true;
            }
         }

         return false;
      }
   }
}
