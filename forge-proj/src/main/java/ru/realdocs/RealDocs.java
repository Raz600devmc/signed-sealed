package ru.realdocs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.realdocs.command.RdCommand;
import ru.realdocs.net.Net;
import ru.realdocs.server.AdminService;
import ru.realdocs.server.MailService;

@Mod("signedsealed")
public class RealDocs {
   public static final String MODID = "signedsealed";
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "signedsealed");
   public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
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
            })
            .build()
   );

   public RealDocs() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      ModItems.ITEMS.register(modBus);
      ModSounds.SOUNDS.register(modBus);
      ModBlocks.BLOCKS.register(modBus);
      TABS.register(modBus);
      Net.register();
      MinecraftForge.EVENT_BUS.register(Perms.class);
      MinecraftForge.EVENT_BUS.register(RdCommand.class);
      MinecraftForge.EVENT_BUS.register(MailService.class);
      MinecraftForge.EVENT_BUS.register(AdminService.class);
   }
}
