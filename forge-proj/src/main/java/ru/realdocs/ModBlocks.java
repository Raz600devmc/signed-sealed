package ru.realdocs;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.realdocs.block.MailboxBlock;
import ru.realdocs.block.PrinterBlock;
import ru.realdocs.block.ShredderBlock;

public final class ModBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "signedsealed");
   public static final RegistryObject<Block> PRINTER = BLOCKS.register("printer", () -> new PrinterBlock(office()));
   public static final RegistryObject<Block> SHREDDER = BLOCKS.register("shredder", () -> new ShredderBlock(office()));
   public static final RegistryObject<Block> MAILBOX = BLOCKS.register("mailbox", () -> new MailboxBlock(office()));
   public static final RegistryObject<Item> MAILBOX_ITEM = ModItems.ITEMS
      .register("mailbox", () -> new BlockItem((Block)MAILBOX.get(), new Properties()));
   public static final RegistryObject<Item> PRINTER_ITEM = ModItems.ITEMS.register("printer", () -> new BlockItem((Block)PRINTER.get(), new Properties()));
   public static final RegistryObject<Item> SHREDDER_ITEM = ModItems.ITEMS.register("shredder", () -> new BlockItem((Block)SHREDDER.get(), new Properties()));

   private ModBlocks() {
   }

   private static net.minecraft.world.level.block.state.BlockBehaviour.Properties office() {
      return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
         .mapColor(MapColor.METAL)
         .strength(2.0F, 6.0F)
         .sound(SoundType.METAL)
         .noOcclusion()
         .requiresCorrectToolForDrops();
   }
}
