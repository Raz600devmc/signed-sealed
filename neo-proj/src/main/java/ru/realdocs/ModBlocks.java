package ru.realdocs;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.realdocs.block.ArchiveBlock;
import ru.realdocs.block.MailboxBlock;
import ru.realdocs.block.TerminalBlock;
import ru.realdocs.block.TicketGateBlock;
import ru.realdocs.block.PrinterBlock;
import ru.realdocs.block.ShredderBlock;

public final class ModBlocks {
   public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "signedsealed");
   public static final DeferredHolder<Block, Block> PRINTER = BLOCKS.register("printer", () -> new PrinterBlock(office()));
   public static final DeferredHolder<Block, Block> SHREDDER = BLOCKS.register("shredder", () -> new ShredderBlock(office()));
   public static final DeferredHolder<Block, Block> MAILBOX = BLOCKS.register("mailbox", () -> new MailboxBlock(office()));
   public static final DeferredHolder<Item, Item> MAILBOX_ITEM = ModItems.ITEMS
      .register("mailbox", () -> new BlockItem((Block)MAILBOX.get(), new Properties()));
   public static final DeferredHolder<Block, Block> TICKET_GATE = BLOCKS.register("ticket_gate", () -> new TicketGateBlock(office()));
   public static final DeferredHolder<Block, Block> ARCHIVE_SHELF = BLOCKS.register(
      "archive_shelf", () -> new ArchiveBlock(wood(), 12, 0.0, 0.0, 2.0, 16.0, 16.0, 16.0)
   );
   public static final DeferredHolder<Block, Block> FILING_CABINET = BLOCKS.register(
      "filing_cabinet", () -> new ArchiveBlock(office(), 48, 1.0, 0.0, 1.0, 15.0, 16.0, 15.0)
   );
   public static final DeferredHolder<Block, Block> ID_READER = BLOCKS.register("id_reader", () -> new ru.realdocs.block.ReaderBlock(office()));
   public static final DeferredHolder<Block, Block> SCANNER = BLOCKS.register("scanner", () -> new TerminalBlock(office(), true, 1.0, 0.0, 2.0, 15.0, 5.0, 14.0));
   public static final DeferredHolder<Block, Block> COMPUTER = BLOCKS.register("computer", () -> new TerminalBlock(office(), false, 0.5, 0.0, 0.5, 15.5, 14.0, 15.5));
   public static final DeferredHolder<Item, Item> TICKET_GATE_ITEM = ModItems.ITEMS.register("ticket_gate", () -> new BlockItem((Block)TICKET_GATE.get(), new Properties()));
   public static final DeferredHolder<Item, Item> ARCHIVE_SHELF_ITEM = ModItems.ITEMS.register("archive_shelf", () -> new BlockItem((Block)ARCHIVE_SHELF.get(), new Properties()));
   public static final DeferredHolder<Item, Item> FILING_CABINET_ITEM = ModItems.ITEMS.register("filing_cabinet", () -> new BlockItem((Block)FILING_CABINET.get(), new Properties()));
   public static final DeferredHolder<Item, Item> ID_READER_ITEM = ModItems.ITEMS.register("id_reader", () -> new BlockItem((Block)ID_READER.get(), new Properties()));
   public static final DeferredHolder<Item, Item> SCANNER_ITEM = ModItems.ITEMS.register("scanner", () -> new BlockItem((Block)SCANNER.get(), new Properties()));
   public static final DeferredHolder<Item, Item> COMPUTER_ITEM = ModItems.ITEMS.register("computer", () -> new BlockItem((Block)COMPUTER.get(), new Properties()));
   public static final DeferredHolder<Item, Item> PRINTER_ITEM = ModItems.ITEMS
      .register("printer", () -> new BlockItem((Block)PRINTER.get(), new Properties()));
   public static final DeferredHolder<Item, Item> SHREDDER_ITEM = ModItems.ITEMS
      .register("shredder", () -> new BlockItem((Block)SHREDDER.get(), new Properties()));

   private ModBlocks() {
   }

   private static net.minecraft.world.level.block.state.BlockBehaviour.Properties wood() {
      return net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
         .mapColor(MapColor.WOOD)
         .strength(2.0F, 3.0F)
         .sound(SoundType.WOOD)
         .noOcclusion();
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
