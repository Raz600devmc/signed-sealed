package ru.realdocs.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import ru.realdocs.model.DocDesign;

/**
 * Data added in 2.4.0, kept apart from the document registry: known players, organizations,
 * mailing lists and newspaper subscriptions, document templates, signature requests, delivery
 * receipts and the registry numbers of issued documents.
 */
public class OfficeData extends SavedData {
   public static final String NAME = "signedsealed_office";
   /** uuid -> last known name of every player who has joined. */
   public final Map<String, String> known = new LinkedHashMap<>();
   public final Map<String, OfficeData.Org> orgs = new LinkedHashMap<>();
   public int orgCounter;
   public final Map<String, OfficeData.MailList> lists = new LinkedHashMap<>();
   /** Newspaper title key -> subscribers (uuid -> name). */
   public final Map<String, Map<String, String>> subs = new LinkedHashMap<>();
   public final Map<String, OfficeData.Template> templates = new LinkedHashMap<>();
   public int templateCounter;
   public final Map<String, OfficeData.SignRequest> requests = new LinkedHashMap<>();
   public int requestCounter;
   /** Sender uuid -> letters they sent, newest last. */
   public final Map<String, List<OfficeData.Receipt>> receipts = new HashMap<>();
   public int receiptCounter;
   /** Messages for players who were offline when something happened. */
   public final Map<String, List<String>> notices = new HashMap<>();
   public long regCounter;
   /** Registry number -> document id. */
   public final Map<String, String> regs = new HashMap<>();
   /** Registry number -> "replacing number\u0001date" for documents that lost force. */
   public final Map<String, String> superseded = new HashMap<>();
   /** Registry number -> latest copy of the document (for registry search on computers). */
   public final Map<String, CompoundTag> regDocs = new HashMap<>();
   /** "p:uuid" or "o:orgId" -> computer inbox entries, oldest first. */
   public final Map<String, List<CompoundTag>> inbox = new HashMap<>();
   public int inboxCounter;

   public static OfficeData get(MinecraftServer server) {
      return (OfficeData)server.overworld().getDataStorage().computeIfAbsent(new Factory<OfficeData>(OfficeData::new, (tag, reg) -> load(tag), null), NAME);
   }

   public static String key(String name) {
      return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
   }

   public String nextReg() {
      this.regCounter++;
      this.setDirty();
      return ru.realdocs.model.DocModel.regFormat(this.regCounter);
   }

   public String uuidOf(String name) {
      String k = key(name);

      for (Entry<String, String> e : this.known.entrySet()) {
         if (key(e.getValue()).equals(k)) {
            return e.getKey();
         }
      }

      return null;
   }

   public String nameOf(String uuid) {
      return this.known.getOrDefault(uuid, "?");
   }

   public OfficeData.Org orgByName(String name) {
      String k = key(name);

      for (OfficeData.Org o : this.orgs.values()) {
         if (key(o.name).equals(k)) {
            return o;
         }
      }

      return null;
   }

   public void notice(String uuid, String text) {
      List<String> l = this.notices.computeIfAbsent(uuid, k -> new ArrayList<>());
      l.add(text);

      while (l.size() > 30) {
         l.remove(0);
      }

      this.setDirty();
   }

   public static final class Org {
      public String id = "";
      public String name = "";
      public String ownerUuid = "";
      public String ownerName = "";
      public String created = "";
      public final Map<String, String> managers = new LinkedHashMap<>();
      public final Map<String, String> members = new LinkedHashMap<>();

      public boolean isOwner(String uuid) {
         return this.ownerUuid.equals(uuid);
      }

      public boolean isManager(String uuid) {
         return this.isOwner(uuid) || this.managers.containsKey(uuid);
      }

      public boolean isMember(String uuid) {
         return this.isManager(uuid) || this.members.containsKey(uuid);
      }

      /** Everyone in the organization: owner, managers and members. */
      public Map<String, String> everyone() {
         Map<String, String> all = new LinkedHashMap<>();
         all.put(this.ownerUuid, this.ownerName);
         all.putAll(this.managers);
         all.putAll(this.members);
         return all;
      }

      CompoundTag write() {
         CompoundTag t = new CompoundTag();
         t.putString("Id", this.id);
         t.putString("Name", this.name);
         t.putString("Owner", this.ownerUuid);
         t.putString("OwnerName", this.ownerName);
         t.putString("Created", this.created);
         t.put("Managers", writeMap(this.managers));
         t.put("Members", writeMap(this.members));
         return t;
      }

      static OfficeData.Org read(CompoundTag t) {
         OfficeData.Org o = new OfficeData.Org();
         o.id = t.getString("Id");
         o.name = t.getString("Name");
         o.ownerUuid = t.getString("Owner");
         o.ownerName = t.getString("OwnerName");
         o.created = t.getString("Created");
         readMap(t.getList("Managers", 10), o.managers);
         readMap(t.getList("Members", 10), o.members);
         return o;
      }
   }

   public static final class MailList {
      /** "p:&lt;uuid&gt;" for a personal list, "o:&lt;org id&gt;" for an organization's list. */
      public String owner = "";
      public String name = "";
      public final Map<String, String> members = new LinkedHashMap<>();

      public String key() {
         return this.owner + "|" + OfficeData.key(this.name);
      }

      CompoundTag write() {
         CompoundTag t = new CompoundTag();
         t.putString("Owner", this.owner);
         t.putString("Name", this.name);
         t.put("Members", writeMap(this.members));
         return t;
      }

      static OfficeData.MailList read(CompoundTag t) {
         OfficeData.MailList l = new OfficeData.MailList();
         l.owner = t.getString("Owner");
         l.name = t.getString("Name");
         readMap(t.getList("Members", 10), l.members);
         return l;
      }
   }

   public static final class Template {
      public String id = "";
      public String ownerUuid = "";
      public String ownerName = "";
      /** Organization that shares the template with its members, empty for a personal one. */
      public String orgId = "";
      public String created = "";
      public DocDesign design = new DocDesign();
      public String subject = "";
      public String body = "";

      public CompoundTag write() {
         CompoundTag t = new CompoundTag();
         t.putString("Id", this.id);
         t.putString("Owner", this.ownerUuid);
         t.putString("OwnerName", this.ownerName);
         t.putString("Org", this.orgId);
         t.putString("Created", this.created);
         t.put("Design", this.design.write());
         t.putString("Subject", this.subject);
         t.putString("Body", this.body);
         return t;
      }

      public static OfficeData.Template read(CompoundTag t) {
         OfficeData.Template x = new OfficeData.Template();
         x.id = t.getString("Id");
         x.ownerUuid = t.getString("Owner");
         x.ownerName = t.getString("OwnerName");
         x.orgId = t.getString("Org");
         x.created = t.getString("Created");
         x.design = DocDesign.read(t.getCompound("Design"));
         x.subject = t.getString("Subject");
         x.body = t.getString("Body");
         return x;
      }
   }

   public static final class SignRequest {
      public static final int PENDING = 0;
      public static final int SIGNED = 1;
      public static final int DECLINED = 2;
      public String id = "";
      public String fromUuid = "";
      public String fromName = "";
      public String title = "";
      public long created;
      /** The document itself, kept here unchanged until everyone has answered. */
      public CompoundTag item = new CompoundTag();
      /** uuid -> name of each person asked to sign. */
      public final Map<String, String> names = new LinkedHashMap<>();
      public final Map<String, Integer> states = new LinkedHashMap<>();

      public boolean pending() {
         for (int s : this.states.values()) {
            if (s == PENDING) {
               return true;
            }
         }

         return false;
      }

      CompoundTag write() {
         CompoundTag t = new CompoundTag();
         t.putString("Id", this.id);
         t.putString("From", this.fromUuid);
         t.putString("FromName", this.fromName);
         t.putString("Title", this.title);
         t.putLong("Created", this.created);
         t.put("Item", this.item);
         ListTag l = new ListTag();

         for (Entry<String, String> e : this.names.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putString("U", e.getKey());
            c.putString("N", e.getValue());
            c.putInt("S", this.states.getOrDefault(e.getKey(), PENDING));
            l.add(c);
         }

         t.put("Targets", l);
         return t;
      }

      static OfficeData.SignRequest read(CompoundTag t) {
         OfficeData.SignRequest r = new OfficeData.SignRequest();
         r.id = t.getString("Id");
         r.fromUuid = t.getString("From");
         r.fromName = t.getString("FromName");
         r.title = t.getString("Title");
         r.created = t.getLong("Created");
         r.item = t.getCompound("Item");
         ListTag l = t.getList("Targets", 10);

         for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            r.names.put(c.getString("U"), c.getString("N"));
            r.states.put(c.getString("U"), c.getInt("S"));
         }

         return r;
      }
   }

   public static final class Receipt {
      public String id = "";
      public String toName = "";
      public String what = "";
      public long sent;
      public long collected;

      CompoundTag write() {
         CompoundTag t = new CompoundTag();
         t.putString("Id", this.id);
         t.putString("To", this.toName);
         t.putString("What", this.what);
         t.putLong("Sent", this.sent);
         t.putLong("Got", this.collected);
         return t;
      }

      static OfficeData.Receipt read(CompoundTag t) {
         OfficeData.Receipt r = new OfficeData.Receipt();
         r.id = t.getString("Id");
         r.toName = t.getString("To");
         r.what = t.getString("What");
         r.sent = t.getLong("Sent");
         r.collected = t.getLong("Got");
         return r;
      }
   }

   static ListTag writeMap(Map<String, String> m) {
      ListTag l = new ListTag();

      for (Entry<String, String> e : m.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putString("U", e.getKey());
         c.putString("N", e.getValue());
         l.add(c);
      }

      return l;
   }

   static void readMap(ListTag l, Map<String, String> into) {
      for (int i = 0; i < l.size(); i++) {
         CompoundTag c = l.getCompound(i);
         into.put(c.getString("U"), c.getString("N"));
      }
   }

   public static OfficeData load(CompoundTag tag) {
      OfficeData d = new OfficeData();
      readMap(tag.getList("Known", 10), d.known);
      d.orgCounter = tag.getInt("OrgCounter");
      d.templateCounter = tag.getInt("TplCounter");
      d.requestCounter = tag.getInt("ReqCounter");
      d.receiptCounter = tag.getInt("RcptCounter");
      d.regCounter = tag.getLong("RegCounter");
      ListTag l = tag.getList("Orgs", 10);

      for (int i = 0; i < l.size(); i++) {
         OfficeData.Org o = OfficeData.Org.read(l.getCompound(i));
         d.orgs.put(o.id, o);
      }

      l = tag.getList("Lists", 10);

      for (int i = 0; i < l.size(); i++) {
         OfficeData.MailList ml = OfficeData.MailList.read(l.getCompound(i));
         d.lists.put(ml.key(), ml);
      }

      CompoundTag st = tag.getCompound("Subs");

      for (String k : st.getAllKeys()) {
         Map<String, String> m = new LinkedHashMap<>();
         readMap(st.getList(k, 10), m);
         d.subs.put(k, m);
      }

      l = tag.getList("Templates", 10);

      for (int i = 0; i < l.size(); i++) {
         OfficeData.Template t = OfficeData.Template.read(l.getCompound(i));
         d.templates.put(t.id, t);
      }

      l = tag.getList("Requests", 10);

      for (int i = 0; i < l.size(); i++) {
         OfficeData.SignRequest r = OfficeData.SignRequest.read(l.getCompound(i));
         d.requests.put(r.id, r);
      }

      CompoundTag rc = tag.getCompound("Receipts");

      for (String k : rc.getAllKeys()) {
         ListTag rl = rc.getList(k, 10);
         List<OfficeData.Receipt> out = new ArrayList<>();

         for (int i = 0; i < rl.size(); i++) {
            out.add(OfficeData.Receipt.read(rl.getCompound(i)));
         }

         d.receipts.put(k, out);
      }

      CompoundTag nt = tag.getCompound("Notices");

      for (String k : nt.getAllKeys()) {
         ListTag nl = nt.getList(k, 8);
         List<String> out = new ArrayList<>();

         for (int i = 0; i < nl.size(); i++) {
            out.add(nl.getString(i));
         }

         d.notices.put(k, out);
      }

      CompoundTag rg = tag.getCompound("Regs");

      for (String k : rg.getAllKeys()) {
         d.regs.put(k, rg.getString(k));
      }

      CompoundTag rd = tag.getCompound("RegDocs");

      for (String k : rd.getAllKeys()) {
         d.regDocs.put(k, rd.getCompound(k));
      }

      CompoundTag ib = tag.getCompound("Inbox");

      for (String k : ib.getAllKeys()) {
         ListTag il = ib.getList(k, 10);
         List<CompoundTag> out = new ArrayList<>();

         for (int i = 0; i < il.size(); i++) {
            out.add(il.getCompound(i));
         }

         d.inbox.put(k, out);
      }

      d.inboxCounter = tag.getInt("InboxCounter");
      CompoundTag sp = tag.getCompound("Superseded");

      for (String k : sp.getAllKeys()) {
         d.superseded.put(k, sp.getString(k));
      }

      return d;
   }

   public CompoundTag save(CompoundTag tag, Provider registries) {
      return this.saveAll(tag);
   }

   CompoundTag saveAll(CompoundTag tag) {
      tag.put("Known", writeMap(this.known));
      tag.putInt("OrgCounter", this.orgCounter);
      tag.putInt("TplCounter", this.templateCounter);
      tag.putInt("ReqCounter", this.requestCounter);
      tag.putInt("RcptCounter", this.receiptCounter);
      tag.putLong("RegCounter", this.regCounter);
      ListTag l = new ListTag();

      for (OfficeData.Org o : this.orgs.values()) {
         l.add(o.write());
      }

      tag.put("Orgs", l);
      l = new ListTag();

      for (OfficeData.MailList ml : this.lists.values()) {
         l.add(ml.write());
      }

      tag.put("Lists", l);
      CompoundTag st = new CompoundTag();

      for (Entry<String, Map<String, String>> e : this.subs.entrySet()) {
         if (!e.getValue().isEmpty()) {
            st.put(e.getKey(), writeMap(e.getValue()));
         }
      }

      tag.put("Subs", st);
      l = new ListTag();

      for (OfficeData.Template t : this.templates.values()) {
         l.add(t.write());
      }

      tag.put("Templates", l);
      l = new ListTag();

      for (OfficeData.SignRequest r : this.requests.values()) {
         l.add(r.write());
      }

      tag.put("Requests", l);
      CompoundTag rc = new CompoundTag();

      for (Entry<String, List<OfficeData.Receipt>> e : this.receipts.entrySet()) {
         ListTag rl = new ListTag();

         for (OfficeData.Receipt r : e.getValue()) {
            rl.add(r.write());
         }

         rc.put(e.getKey(), rl);
      }

      tag.put("Receipts", rc);
      CompoundTag nt = new CompoundTag();

      for (Entry<String, List<String>> e : this.notices.entrySet()) {
         ListTag nl = new ListTag();

         for (String s : e.getValue()) {
            nl.add(StringTag.valueOf(s));
         }

         nt.put(e.getKey(), nl);
      }

      tag.put("Notices", nt);
      CompoundTag rg = new CompoundTag();

      for (Entry<String, String> e : this.regs.entrySet()) {
         rg.putString(e.getKey(), e.getValue());
      }

      tag.put("Regs", rg);
      CompoundTag sp = new CompoundTag();

      for (Entry<String, String> e : this.superseded.entrySet()) {
         sp.putString(e.getKey(), e.getValue());
      }

      tag.put("Superseded", sp);
      CompoundTag rd = new CompoundTag();

      for (Entry<String, CompoundTag> e : this.regDocs.entrySet()) {
         rd.put(e.getKey(), e.getValue());
      }

      tag.put("RegDocs", rd);
      CompoundTag ib = new CompoundTag();

      for (Entry<String, List<CompoundTag>> e : this.inbox.entrySet()) {
         if (!e.getValue().isEmpty()) {
            ListTag il = new ListTag();
            il.addAll(e.getValue());
            ib.put(e.getKey(), il);
         }
      }

      tag.put("Inbox", ib);
      tag.putInt("InboxCounter", this.inboxCounter);
      return tag;
   }
}
