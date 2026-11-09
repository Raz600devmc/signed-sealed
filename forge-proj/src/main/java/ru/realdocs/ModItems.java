package ru.realdocs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.SealingWaxItem;
import ru.realdocs.item.ForgeryKitItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.item.StampItem;

public final class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "signedsealed");
   public static final RegistryObject<Item> DOCUMENT = ITEMS.register("document", () -> new DocumentItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> PASSPORT = ITEMS.register("passport", () -> new PassportItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> STAMP = ITEMS.register("stamp", () -> new StampItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> NEWSPAPER = ITEMS.register("newspaper", () -> new NewspaperItem(new Properties().stacksTo(16)));
   public static final RegistryObject<Item> ENVELOPE = ITEMS.register("envelope", () -> new EnvelopeItem(new Properties().stacksTo(16)));
   public static final RegistryObject<Item> FOLDER = ITEMS.register("folder", () -> new FolderItem(new Properties().stacksTo(1)));
   public static final RegistryObject<Item> SEALING_WAX = ITEMS.register("sealing_wax", () -> new SealingWaxItem(new Properties()));
   public static final RegistryObject<Item> FORGERY_KIT = ITEMS.register("forgery_kit", () -> new ForgeryKitItem(new Properties().durability(32)));

   private ModItems() {
   }
}
