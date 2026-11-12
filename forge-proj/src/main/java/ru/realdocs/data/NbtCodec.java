package ru.realdocs.data;

import java.util.List;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.DocDesign;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;

public final class NbtCodec {
   public static final String DOC_TAG = "RealDoc";
   public static final String STAMP_ID_TAG = "StampId";
   public static final String STAMP_ORG_TAG = "StampOrg";
   public static final String STAMP_FORGED_TAG = "StampForged";
   public static final String PASS_TAG = "RealPass";

   private NbtCodec() {
   }

   public static DocModel readDoc(ItemStack stack) {
      CompoundTag t = stack.getTagElement("RealDoc");
      return t == null ? new DocModel() : readDoc(t);
   }

   public static void writeDoc(ItemStack stack, DocModel d) {
      stack.getOrCreateTag().put("RealDoc", writeDoc(d));
   }

   public static DocModel readDoc(CompoundTag t) {
      DocModel d = new DocModel();
      d.template = t.getInt("Template");
      d.number = t.getString("Number");
      d.org = t.getString("Org");
      d.position = t.getString("Position");
      d.subject = t.getString("Subject");
      d.body = t.getString("Body");
      d.issued = t.getBoolean("Issued");
      d.docId = t.getString("Id");
      d.date = t.getString("Date");
      d.author = t.getString("Author");
      d.authorUuid = t.getString("AuthorUuid");
      d.sec = t.getString("Sec");
      d.forged = t.getBoolean("Forged");
      d.copy = t.getBoolean("Copy");
      ListTag sigs = t.getList("Sigs", 10);

      for (int i = 0; i < sigs.size(); i++) {
         CompoundTag s = sigs.getCompound(i);
         DocModel.Sig sig = new DocModel.Sig();
         sig.name = s.getString("Name");
         sig.uuid = s.getString("Uuid");
         sig.position = s.getString("Position");
         sig.strokes = s.getIntArray("Strokes");
         d.sigs.add(sig);
      }

      ListTag stamps = t.getList("Stamps", 10);

      for (int i = 0; i < stamps.size(); i++) {
         CompoundTag s = stamps.getCompound(i);
         d.stamps.add(readRef(s));
      }

      if (t.contains("Design")) {
         d.design = DocDesign.read(t.getCompound("Design"));
      }

      d.templateId = t.getString("TplId");
      d.regNo = t.getString("RegNo");
      readList(t, "Refs", d.refs);
      readList(t, "Supersedes", d.supersedes);
      d.expires = t.getString("Expires");
      d.zones = t.getString("Zones");
      d.holder = t.getString("Holder");
      d.pub = t.getBoolean("Public");
      return d;
   }

   public static CompoundTag writeDoc(DocModel d) {
      CompoundTag t = new CompoundTag();
      t.putInt("Template", DocModel.templateIndex(d.template));
      t.putString("Number", d.number);
      t.putString("Org", d.org);
      t.putString("Position", d.position);
      t.putString("Subject", d.subject);
      t.putString("Body", d.body);
      t.putBoolean("Issued", d.issued);
      t.putString("Id", d.docId);
      t.putString("Date", d.date);
      t.putString("Author", d.author);
      t.putString("AuthorUuid", d.authorUuid);
      if (!d.sec.isEmpty()) {
         t.putString("Sec", d.sec);
      }

      if (d.forged) {
         t.putBoolean("Forged", true);
      }

      if (d.copy) {
         t.putBoolean("Copy", true);
      }

      ListTag sigs = new ListTag();

      for (DocModel.Sig s : d.sigs) {
         CompoundTag c = new CompoundTag();
         c.putString("Name", s.name);
         c.putString("Uuid", s.uuid);
         c.putString("Position", s.position);
         c.putIntArray("Strokes", s.strokes);
         sigs.add(c);
      }

      t.put("Sigs", sigs);
      ListTag stamps = new ListTag();

      for (DocModel.StampRef r : d.stamps) {
         stamps.add(writeRef(r));
      }

      t.put("Stamps", stamps);
      if (d.design != null) {
         t.put("Design", d.design.write());
      }

      if (!d.templateId.isEmpty()) {
         t.putString("TplId", d.templateId);
      }

      if (!d.regNo.isEmpty()) {
         t.putString("RegNo", d.regNo);
      }

      writeList(t, "Refs", d.refs);
      writeList(t, "Supersedes", d.supersedes);
      if (!d.expires.isEmpty()) {
         t.putString("Expires", d.expires);
      }

      if (!d.zones.isEmpty()) {
         t.putString("Zones", d.zones);
      }

      if (!d.holder.isEmpty()) {
         t.putString("Holder", d.holder);
      }

      if (d.pub) {
         t.putBoolean("Public", true);
      }

      return t;
   }

   static void readList(CompoundTag t, String key, java.util.List<String> into) {
      ListTag l = t.getList(key, 8);

      for (int i = 0; i < l.size() && i < 6; i++) {
         into.add(l.getString(i));
      }
   }

   static void writeList(CompoundTag t, String key, java.util.List<String> from) {
      if (!from.isEmpty()) {
         ListTag l = new ListTag();

         for (String s : from) {
            l.add(net.minecraft.nbt.StringTag.valueOf(s));
         }

         t.put(key, l);
      }
   }

   public static CompoundTag writeRef(DocModel.StampRef r) {
      CompoundTag c = new CompoundTag();
      c.putString("Id", r.id);
      c.putInt("Page", r.page);
      c.putInt("X", r.x);
      c.putInt("Y", r.y);
      c.putInt("Rot", r.rot);
      c.putInt("Scale", r.scale);
      if (r.hasExtra()) {
         c.putInt("Dir", r.dir);
         c.putInt("Cat", r.cat);
         c.putString("SDate", r.date);
         c.putString("Until", r.until);
         c.putString("Num", r.num);
      }

      if (r.forged) {
         c.putBoolean("Forged", true);
      }

      return c;
   }

   public static DocModel.StampRef readRef(CompoundTag s) {
      DocModel.StampRef r = new DocModel.StampRef(
            s.getString("Id"),
            s.getInt("Page"),
            s.contains("X") ? s.getInt("X") : 500,
            s.contains("Y") ? s.getInt("Y") : 900,
            s.getInt("Rot"),
            s.contains("Scale") ? s.getInt("Scale") : 100
         )
         .extra(s.getInt("Dir"), s.getInt("Cat"), s.getString("SDate"), s.getString("Until"), s.getString("Num"));
      r.forged = s.getBoolean("Forged");
      return r;
   }

   public static CompoundTag writeStamp(StampModel m) {
      CompoundTag t = new CompoundTag();
      t.putString("Id", m.id);
      t.putString("OwnerUuid", m.ownerUuid);
      t.putString("OwnerName", m.ownerName);
      t.putString("Org", m.org);
      t.putInt("Shape", m.shape);
      t.putInt("Kind", m.kind);
      t.putInt("Color", m.color);
      t.putString("Top", m.top);
      t.putString("Bottom", m.bottom);
      t.putString("Center", m.center);
      t.putBoolean("HasImage", m.hasImage);
      t.putBoolean("Approved", m.approved);
      t.putBoolean("EverApproved", m.everApproved || m.approved);
      t.putString("Created", m.created);
      if (!m.visa.isEmpty()) {
         t.put("Visa", writeElements(m.visa));
      }

      return t;
   }

   public static StampModel readStamp(CompoundTag t) {
      StampModel m = new StampModel();
      m.id = t.getString("Id");
      m.ownerUuid = t.getString("OwnerUuid");
      m.ownerName = t.getString("OwnerName");
      m.org = t.getString("Org");
      m.shape = t.getInt("Shape");
      m.kind = t.getInt("Kind");
      m.color = t.getInt("Color");
      m.top = t.getString("Top");
      m.bottom = t.getString("Bottom");
      m.center = t.getString("Center");
      m.hasImage = t.getBoolean("HasImage");
      m.approved = t.getBoolean("Approved");
      m.everApproved = m.approved || t.getBoolean("EverApproved");
      m.created = t.getString("Created");
      readElements(t.getList("Visa", 10), m.visa);
      return m;
   }

   public static PassportModel readPass(ItemStack stack) {
      CompoundTag t = stack.getTagElement("RealPass");
      return t == null ? new PassportModel() : readPass(t);
   }

   public static void writePass(ItemStack stack, PassportModel p) {
      stack.getOrCreateTag().put("RealPass", writePass(p));
   }

   public static CompoundTag writePass(PassportModel p) {
      CompoundTag t = new CompoundTag();
      t.putString("Template", p.templateId);
      t.putString("Photo", p.photo);
      t.putIntArray("Sig", p.signature);
      t.putBoolean("Issued", p.issued);
      t.putString("Id", p.passId);
      t.putString("Serial", p.serial);
      t.putString("Date", p.date);
      t.putString("Holder", p.holderName);
      t.putString("HolderUuid", p.holderUuid);
      t.putString("Issuer", p.issuer);
      t.putString("IssuerUuid", p.issuerUuid);
      if (!p.sec.isEmpty()) {
         t.putString("Sec", p.sec);
      }

      if (p.format != 0) {
         t.putInt("Format", p.format);
      }

      if (p.forged) {
         t.putBoolean("Forged", true);
      }

      ListTag vals = new ListTag();

      for (Entry<String, String> e : p.values.entrySet()) {
         CompoundTag c = new CompoundTag();
         c.putString("K", e.getKey());
         c.putString("V", e.getValue());
         vals.add(c);
      }

      t.put("Values", vals);
      ListTag stamps = new ListTag();

      for (DocModel.StampRef r : p.stamps) {
         stamps.add(writeRef(r));
      }

      t.put("Stamps", stamps);
      return t;
   }

   public static PassportModel readPass(CompoundTag t) {
      PassportModel p = new PassportModel();
      p.templateId = t.getString("Template");
      p.photo = t.getString("Photo");
      p.signature = t.getIntArray("Sig");
      p.issued = t.getBoolean("Issued");
      p.passId = t.getString("Id");
      p.serial = t.getString("Serial");
      p.date = t.getString("Date");
      p.holderName = t.getString("Holder");
      p.holderUuid = t.getString("HolderUuid");
      p.issuer = t.getString("Issuer");
      p.issuerUuid = t.getString("IssuerUuid");
      p.sec = t.getString("Sec");
      p.format = t.getInt("Format");
      p.forged = t.getBoolean("Forged");
      ListTag vals = t.getList("Values", 10);

      for (int i = 0; i < vals.size(); i++) {
         CompoundTag c = vals.getCompound(i);
         p.values.put(c.getString("K"), c.getString("V"));
      }

      ListTag stamps = t.getList("Stamps", 10);

      for (int i = 0; i < stamps.size(); i++) {
         p.stamps.add(readRef(stamps.getCompound(i)));
      }

      return p;
   }

   public static CompoundTag writeTemplate(PassportTemplate t) {
      CompoundTag c = new CompoundTag();
      c.putString("Id", t.id);
      c.putInt("Format", t.format);
      c.putString("Name", t.name);
      c.putString("Org", t.org);
      c.putString("OwnerUuid", t.ownerUuid);
      c.putString("OwnerName", t.ownerName);
      c.putBoolean("Approved", t.approved);
      c.putString("Created", t.created);
      if (!t.replaces.isEmpty()) {
         c.putString("Replaces", t.replaces);
      }

      c.putString("Title", t.title);
      c.putString("Subtitle", t.subtitle);
      c.putInt("Bg", t.bg);
      c.putInt("Accent", t.accent);
      c.putInt("Pattern", t.pattern);
      c.putInt("CoverColor", t.coverColor);
      c.putString("CoverTitle", t.coverTitle);
      c.putString("CoverText", t.coverText);
      c.putString("Emblem", t.emblem);
      c.putInt("EmblemCode", t.emblemCode);
      c.putString("SerialPrefix", t.serialPrefix);
      c.putString("AutoStamp", t.autoStampId);
      c.putString("Flag", t.flag);
      c.putInt("VisaSpreads", t.visaSpreads);
      c.putString("VisaTitle", t.visaTitle);
      c.putInt("ValidYears", t.validYears);
      c.put("Cover", writeElements(t.cover));
      c.put("Decor", writeElements(t.decor));
      c.put("Back", writeElements(t.back));
      c.putInt("CoverTexture", t.coverTexture);
      c.putInt("CoverFrame", t.coverFrame);
      c.putBoolean("HideHeader", t.hideHeader);
      ListTag fs = new ListTag();

      for (PassportTemplate.Field f : t.fields) {
         CompoundTag x = new CompoundTag();
         x.putString("Key", f.key);
         x.putString("Label", f.label);
         x.putInt("Type", f.type);
         x.putInt("X", f.x);
         x.putInt("Y", f.y);
         x.putInt("W", f.w);
         x.putInt("H", f.h);
         x.putInt("Size", f.size);
         x.putBoolean("Bold", f.bold);
         x.putBoolean("Required", f.required);
         x.putString("Prefix", f.prefix);
         x.putString("Hint", f.hint);
         fs.add(x);
      }

      c.put("Fields", fs);
      return c;
   }

   static ListTag writeElements(List<DesignElement> list) {
      ListTag l = new ListTag();

      for (DesignElement e : list) {
         CompoundTag x = new CompoundTag();
         x.putInt("T", e.type);
         x.putIntArray("R", new int[]{e.x, e.y, e.w, e.h});
         x.putString("S", e.text);
         x.putIntArray("P", new int[]{e.size, e.bold ? 1 : 0, e.align, e.spacing, e.ink, e.color, e.alpha});
         l.add(x);
      }

      return l;
   }

   static void readElements(ListTag l, List<DesignElement> out) {
      for (int i = 0; i < l.size(); i++) {
         CompoundTag x = l.getCompound(i);
         int[] r = x.getIntArray("R");
         int[] p = x.getIntArray("P");
         if (r.length >= 4 && p.length >= 7) {
            DesignElement e = new DesignElement(x.getInt("T"), r[0], r[1], r[2], r[3]);
            e.text = x.getString("S");
            e.size = p[0];
            e.bold = p[1] != 0;
            e.align = p[2];
            e.spacing = p[3];
            e.ink = p[4];
            e.color = p[5];
            e.alpha = p[6];
            out.add(e);
         }
      }
   }

   public static PassportTemplate readTemplate(CompoundTag c) {
      PassportTemplate t = new PassportTemplate();
      t.id = c.getString("Id");
      t.format = c.getInt("Format");
      t.name = c.getString("Name");
      t.org = c.getString("Org");
      t.ownerUuid = c.getString("OwnerUuid");
      t.ownerName = c.getString("OwnerName");
      t.approved = c.getBoolean("Approved");
      t.created = c.getString("Created");
      t.replaces = c.getString("Replaces");
      t.title = c.getString("Title");
      t.subtitle = c.getString("Subtitle");
      t.bg = c.getInt("Bg");
      t.accent = c.getInt("Accent");
      t.pattern = c.getInt("Pattern");
      t.coverColor = c.getInt("CoverColor");
      t.coverTitle = c.getString("CoverTitle");
      t.coverText = c.getString("CoverText");
      t.emblem = c.getString("Emblem");
      t.emblemCode = c.getInt("EmblemCode");
      t.serialPrefix = c.getString("SerialPrefix");
      t.autoStampId = c.getString("AutoStamp");
      t.flag = c.getString("Flag");
      t.visaSpreads = c.contains("VisaSpreads") ? c.getInt("VisaSpreads") : 3;
      if (c.contains("VisaTitle")) {
         t.visaTitle = c.getString("VisaTitle");
      }

      if (c.contains("ValidYears")) {
         t.validYears = c.getInt("ValidYears");
      }

      readElements(c.getList("Cover", 10), t.cover);
      readElements(c.getList("Decor", 10), t.decor);
      readElements(c.getList("Back", 10), t.back);
      if (c.contains("CoverFrame")) {
         t.coverFrame = c.getInt("CoverFrame");
      }

      t.coverTexture = c.getInt("CoverTexture");
      t.hideHeader = c.getBoolean("HideHeader");
      ListTag fs = c.getList("Fields", 10);

      for (int i = 0; i < fs.size(); i++) {
         CompoundTag x = fs.getCompound(i);
         PassportTemplate.Field f = new PassportTemplate.Field();
         f.key = x.getString("Key");
         f.label = x.getString("Label");
         f.type = x.getInt("Type");
         f.x = x.getInt("X");
         f.y = x.getInt("Y");
         f.w = x.getInt("W");
         f.h = x.getInt("H");
         f.size = x.getInt("Size");
         f.bold = x.getBoolean("Bold");
         f.required = x.getBoolean("Required");
         f.prefix = x.getString("Prefix");
         f.hint = x.getString("Hint");
         t.fields.add(f);
      }

      return t;
   }
}
