package ru.realdocs.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.realdocs.ModItems;
import ru.realdocs.client.held.HeldTextures;
import ru.realdocs.client.held.InspectScreen;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class ClientSetup {
   public static final KeyMapping ADMIN_KEY = new KeyMapping("key.signedsealed.admin", Type.KEYSYM, 75, "key.categories.signedsealed");
   public static final KeyMapping INSPECT_KEY = new KeyMapping("key.signedsealed.inspect", Type.KEYSYM, 86, "key.categories.signedsealed");

   private ClientSetup() {
   }

   @EventBusSubscriber(
      modid = "signedsealed",
      bus = Bus.FORGE,
      value = {Dist.CLIENT}
   )
   public static final class ForgeBus {
      @SubscribeEvent
      public static void onTick(ClientTickEvent event) {
         if (event.phase == Phase.END) {
            while (ClientSetup.INSPECT_KEY.consumeClick()) {
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null && mc.screen == null) {
                  for (InteractionHand h : InteractionHand.values()) {
                     Item it = mc.player.getItemInHand(h).getItem();
                     if (it instanceof DocumentItem || it instanceof PassportItem || it instanceof ru.realdocs.item.NewspaperItem || it instanceof ru.realdocs.item.EnvelopeItem || it instanceof ru.realdocs.item.FolderItem) {
                        mc.setScreen(new InspectScreen(h));
                        break;
                     }
                  }
               }
            }

            while (ClientSetup.ADMIN_KEY.consumeClick()) {
               if (Minecraft.getInstance().player != null) {
                  Net.sendToServer(new Packets.AdminOpen(""));
               }
            }
         }
      }

      @SubscribeEvent
      public static void onFrame(RenderTickEvent event) {
         if (event.phase == Phase.START) {
            HeldTextures.tick();
         }
      }

      @SubscribeEvent
      public static void onLogout(LoggingOut event) {
         HeldTextures.clear();
         StampCache.INSTANCE.clear();
         PassportCache.INSTANCE.clear();
         PaperCache.INSTANCE.clear();
         ClientUploads.clear();
      }
   }

   @EventBusSubscriber(
      modid = "signedsealed",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class ModBus {
      @SubscribeEvent
      public static void onKeys(RegisterKeyMappingsEvent event) {
         event.register(ClientSetup.ADMIN_KEY);
         event.register(ClientSetup.INSPECT_KEY);
      }

      @SubscribeEvent
      public static void onRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
         event.registerEntityRenderer(ru.realdocs.ModEntities.POSTER.get(), ru.realdocs.client.render.world.PosterRenderer::new);
         event.registerBlockEntityRenderer(ru.realdocs.ModEntities.ARCHIVE.get(), ru.realdocs.client.render.world.ShelfRenderer::new);
      }

      @SubscribeEvent
      public static void onClientSetup(FMLClientSetupEvent event) {
         event.enqueueWork(() -> {
            ItemProperties.register(
                  (Item)ModItems.NEWSPAPER.get(), new ResourceLocation("signedsealed", "state"), (stack, level, entity, seed) -> {
                     CompoundTag tag = stack.getTagElement("RealPaper");
                     return tag == null ? 0.0F : (tag.getBoolean("Pub") ? 2.0F : 1.0F);
                  }
               );
               ItemProperties.register(
                  (Item)ModItems.ENVELOPE.get(), new ResourceLocation("signedsealed", "state"), (stack, level, entity, seed) -> {
                     CompoundTag tag = stack.getTagElement("RealMail");
                     return tag == null ? 0.0F : (float)(tag.getInt("State") + 1);
                  }
               );
               ItemProperties.register((Item)ModItems.DOCUMENT.get(), new ResourceLocation("signedsealed", "issued"), (stack, level, entity, seed) -> {
               CompoundTag tag = stack.getTagElement("RealDoc");
               return tag != null && tag.getBoolean("Issued") ? 1.0F : 0.0F;
            });
            ItemProperties.register((Item)ModItems.PASSPORT.get(), new ResourceLocation("signedsealed", "format"), (stack, level, entity, seed) -> {
               CompoundTag tag = stack.getTagElement("RealPass");
               return tag == null ? 0.0F : (float)tag.getInt("Format");
            });
         });
      }
   }
}
