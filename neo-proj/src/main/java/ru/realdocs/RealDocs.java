package ru.realdocs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.realdocs.command.RdCommand;
import ru.realdocs.net.Net;
import ru.realdocs.server.AdminService;
import ru.realdocs.server.MailService;

@Mod("signedsealed")
public class RealDocs {
   public static final String MODID = "signedsealed";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "signedsealed");
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
      "main",
      () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.signedsealed"))
            .icon(() -> new ItemStack((ItemLike)ModItems.DOCUMENT.get()))
            .displayItems((params, output) -> {
               output.accept((ItemLike)ModItems.DOCUMENT.get());
               output.accept((ItemLike)ModItems.NEWSPAPER.get());
               output.accept((ItemLike)ModItems.ENVELOPE.get());
               output.accept((ItemLike)ModItems.SEALING_WAX.get());
               output.accept((ItemLike)ModItems.FOLDER.get());
               output.accept((ItemLike)ModItems.FORGERY_KIT.get());
               output.accept((ItemLike)ModBlocks.PRINTER_ITEM.get());
               output.accept((ItemLike)ModBlocks.SHREDDER_ITEM.get());
               output.accept((ItemLike)ModBlocks.MAILBOX_ITEM.get());
               output.accept((ItemLike)ModBlocks.TICKET_GATE_ITEM.get());
               output.accept((ItemLike)ModBlocks.ARCHIVE_SHELF_ITEM.get());
               output.accept((ItemLike)ModBlocks.FILING_CABINET_ITEM.get());
               output.accept((ItemLike)ModBlocks.SCANNER_ITEM.get());
               output.accept((ItemLike)ModBlocks.COMPUTER_ITEM.get());
            })
            .build()
   );

   public RealDocs(IEventBus modBus, ModContainer container) {
      ModItems.ITEMS.register(modBus);
      ModSounds.SOUNDS.register(modBus);
      ModBlocks.BLOCKS.register(modBus);
      ModEntities.BLOCK_ENTITIES.register(modBus);
      ModEntities.ENTITIES.register(modBus);
      TABS.register(modBus);
      modBus.addListener(Net::register);
      NeoForge.EVENT_BUS.register(Perms.class);
      NeoForge.EVENT_BUS.register(RdCommand.class);
      NeoForge.EVENT_BUS.register(AdminService.class);
      NeoForge.EVENT_BUS.register(MailService.class);
   }
}
