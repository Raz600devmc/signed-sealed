package ru.realdocs.blockentity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.realdocs.ModEntities;
import ru.realdocs.block.ArchiveBlock;
import ru.realdocs.data.MailCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;

/** Archive shelf / filing cabinet contents. */
public class ArchiveBE extends OfficeBE {
   public final List<ItemStack> items = new ArrayList<>();
   /** Client-side: per stored item a colour and kind for the shelf renderer. */
   public int[] visual = new int[0];
   /** Client-side: the top sheet of each shelf's stack, so its page can be drawn. */
   public final ItemStack[] tops = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};

   public ArchiveBE(BlockPos pos, BlockState state) {
      super(ModEntities.ARCHIVE.get(), pos, state);
   }

   public int capacity() {
      return this.getBlockState().getBlock() instanceof ArchiveBlock a ? a.capacity : 12;
   }

   public static boolean fits(ItemStack s) {
      return s.getItem() instanceof DocumentItem || s.getItem() instanceof PassportItem || s.getItem() instanceof NewspaperItem
         || s.getItem() instanceof EnvelopeItem || s.getItem() instanceof FolderItem;
   }

   /** kind << 24 | rgb: 1 folder, 2 document, 3 passport, 4 newspaper, 5 letter. */
   public static int visualOf(ItemStack s) {
      if (s.getItem() instanceof FolderItem) {
         return 0x1000000 | MailCodec.readFolder(s).rgb();
      } else if (s.getItem() instanceof PassportItem) {
         return 0x3000000 | 0x7A2230;
      } else if (s.getItem() instanceof NewspaperItem) {
         return 0x4000000 | 0xD9D3C2;
      } else if (s.getItem() instanceof EnvelopeItem) {
         return 0x5000000 | 0xEFE4CC;
      } else {
         return 0x2000000 | 0xF2EDE0;
      }
   }

   protected void writeClient(CompoundTag t) {
      int[] v = new int[this.items.size()];

      for (int i = 0; i < v.length; i++) {
         v[i] = visualOf(this.items.get(i));
      }

      t.putIntArray("Visual", v);
   }

   protected void readClient(CompoundTag t) {
      this.visual = t.getIntArray("Visual");
   }

   /** Last document, newspaper or letter on shelf {@code row} (slots row*6 .. row*6+5): it lies on top of the stack. */
   public ItemStack top(int row) {
      ItemStack top = ItemStack.EMPTY;

      for (int i = row * 6; i < Math.min(this.items.size(), row * 6 + 6); i++) {
         int kind = visualOf(this.items.get(i)) >>> 24;
         if (kind == 2 || kind == 4 || kind == 5) {
            top = this.items.get(i);
         }
      }

      return top;
   }

   public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
      CompoundTag t = super.getUpdateTag(reg);
      if (this.capacity() == 12) {
         ListTag l = new ListTag();

         for (int row = 0; row < 2; row++) {
            l.add(this.top(row).saveOptional(reg));
         }

         t.put("Tops", l);
      }

      return t;
   }

   protected void loadAdditional(CompoundTag t, HolderLookup.Provider reg) {
      super.loadAdditional(t, reg);
      if (t.contains("ClientOnly")) {
         ListTag l = t.getList("Tops", 10);

         for (int row = 0; row < 2; row++) {
            this.tops[row] = row < l.size() ? ItemStack.parseOptional(reg, l.getCompound(row)) : ItemStack.EMPTY;
         }
      }
   }

   protected void writeData(CompoundTag t, HolderLookup.Provider reg) {
      ListTag l = new ListTag();

      for (ItemStack s : this.items) {
         if (!s.isEmpty()) {
            l.add(s.save(reg));
         }
      }

      t.put("Items", l);
   }

   protected void readData(CompoundTag t, HolderLookup.Provider reg) {
      this.items.clear();
      ListTag l = t.getList("Items", 10);

      for (int i = 0; i < l.size(); i++) {
         ItemStack s = ItemStack.parseOptional(reg, l.getCompound(i));
         if (!s.isEmpty()) {
            this.items.add(s);
         }
      }
   }

}
