package ru.realdocs;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.SealingWaxItem;
import ru.realdocs.item.ForgeryKitItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.item.StampItem;

public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "signedsealed");
   public static final DeferredHolder<Item, Item> DOCUMENT = ITEMS.register("document", () -> new DocumentItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> PASSPORT = ITEMS.register("passport", () -> new PassportItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> STAMP = ITEMS.register("stamp", () -> new StampItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> NEWSPAPER = ITEMS.register("newspaper", () -> new NewspaperItem(new Properties().stacksTo(16)));
   public static final DeferredHolder<Item, Item> NOTICE_BOARD = ITEMS.register("notice_board", () -> new ru.realdocs.item.NoticeBoardItem(new Item.Properties().stacksTo(16)));
   public static final DeferredHolder<Item, Item> ENVELOPE = ITEMS.register("envelope", () -> new EnvelopeItem(new Properties().stacksTo(16)));
   public static final DeferredHolder<Item, Item> FOLDER = ITEMS.register("folder", () -> new FolderItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> SEALING_WAX = ITEMS.register("sealing_wax", () -> new SealingWaxItem(new Properties()));
   public static final DeferredHolder<Item, Item> FORGERY_KIT = ITEMS.register("forgery_kit", () -> new ForgeryKitItem(new Properties().durability(32)));

   private ModItems() {
   }
}
