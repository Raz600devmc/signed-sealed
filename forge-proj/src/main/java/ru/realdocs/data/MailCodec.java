package ru.realdocs.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ru.realdocs.Plat;
import ru.realdocs.model.FolderModel;
import ru.realdocs.model.MailModel;

/** Item data of envelopes and folders, including the items kept inside them. */
public final class MailCodec {
   public static final String MAIL_TAG = "RealMail";
   public static final String FOLDER_TAG = "RealFolder";

   private MailCodec() {
   }

   public static MailModel readMail(ItemStack stack) {
      CompoundTag t = Tags.element(stack, MAIL_TAG);
      MailModel m = new MailModel();
      if (t != null) {
         m.id = t.getString("Id");
         m.to = t.getString("To");
         m.from = t.getString("From");
         m.fromUuid = t.getString("FromUuid");
         m.text = t.getString("Text");
         m.date = t.getString("Date");
         m.state = t.getInt("State");
         m.wax = t.getInt("Wax");
         m.sealId = t.getString("SealId");
         m.sealOrg = t.getString("SealOrg");
         m.sealForged = t.getBoolean("SealForged");
         m.sealer = t.getString("Sealer");
         m.sealerUuid = t.getString("SealerUuid");
         m.sealDate = t.getString("SealDate");
         m.hash = t.getString("Hash");
         m.brokenDate = t.getString("Broken");
         m.font = t.contains("Font") ? t.getInt("Font") : 6;
         m.postmark = t.getString("Postmark");
      }

      return m;
   }

   public static void writeMail(ItemStack stack, MailModel m) {
      Tags.update(stack, t -> {
         CompoundTag c = t.contains(MAIL_TAG, 10) ? t.getCompound(MAIL_TAG) : new CompoundTag();
         c.putString("Id", m.id);
         c.putString("To", m.to);
         c.putString("From", m.from);
         c.putString("FromUuid", m.fromUuid);
         c.putString("Text", m.text);
         c.putString("Date", m.date);
         c.putInt("State", m.state);
         c.putInt("Wax", m.wax);
         c.putString("SealId", m.sealId);
         c.putString("SealOrg", m.sealOrg);
         c.putBoolean("SealForged", m.sealForged);
         c.putString("Sealer", m.sealer);
         c.putString("SealerUuid", m.sealerUuid);
         c.putString("SealDate", m.sealDate);
         c.putString("Hash", m.hash);
         c.putString("Broken", m.brokenDate);
         c.putInt("Font", m.font);
         c.putString("Postmark", m.postmark);
         t.put(MAIL_TAG, c);
      });
   }

   public static FolderModel readFolder(ItemStack stack) {
      CompoundTag t = Tags.element(stack, FOLDER_TAG);
      FolderModel f = new FolderModel();
      if (t != null) {
         f.label = t.getString("Label");
         f.color = t.getInt("Color");
      }

      return f;
   }

   public static void writeFolder(ItemStack stack, FolderModel f) {
      Tags.update(stack, t -> {
         CompoundTag c = t.contains(FOLDER_TAG, 10) ? t.getCompound(FOLDER_TAG) : new CompoundTag();
         c.putString("Label", f.label);
         c.putInt("Color", f.color);
         t.put(FOLDER_TAG, c);
      });
   }

   /** Raw saved stacks inside an envelope or folder (tag = MAIL_TAG or FOLDER_TAG). */
   public static List<CompoundTag> rawItems(ItemStack stack, String tag) {
      CompoundTag t = Tags.element(stack, tag);
      List<CompoundTag> out = new ArrayList<>();
      if (t != null) {
         ListTag l = t.getList("Items", 10);

         for (int i = 0; i < l.size(); i++) {
            out.add(l.getCompound(i));
         }
      }

      return out;
   }

   public static List<ItemStack> items(ItemStack stack, String tag, Level level) {
      List<ItemStack> out = new ArrayList<>();

      for (CompoundTag c : rawItems(stack, tag)) {
         ItemStack s = Plat.loadStack(c, level);
         if (!s.isEmpty()) {
            out.add(s);
         }
      }

      return out;
   }

   public static int count(ItemStack stack, String tag) {
      CompoundTag t = Tags.element(stack, tag);
      return t == null ? 0 : t.getList("Items", 10).size();
   }

   public static void setItems(ItemStack stack, String tag, List<ItemStack> items, Level level) {
      ListTag l = new ListTag();

      for (ItemStack s : items) {
         if (!s.isEmpty()) {
            l.add(Plat.saveStack(s, level));
         }
      }

      Tags.update(stack, t -> {
         CompoundTag c = t.contains(tag, 10) ? t.getCompound(tag) : new CompoundTag();
         c.put("Items", l);
         t.put(tag, c);
      });
   }
}
