package ru.realdocs.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import ru.realdocs.ModItems;
import ru.realdocs.client.held.HeldItemRenderer;
import ru.realdocs.client.held.HeldTextures;
import ru.realdocs.client.held.InspectScreen;
import ru.realdocs.data.Tags;
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
      bus = Bus.GAME,
      value = {Dist.CLIENT}
   )
   public static final class ForgeBus {
      @SubscribeEvent
      public static void onTick(Post event) {
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

      @SubscribeEvent
      public static void onFrame(Pre event) {
         HeldTextures.tick();
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
      public static void onRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
         event.registerEntityRenderer(ru.realdocs.ModEntities.POSTER.get(), ru.realdocs.client.render.world.PosterRenderer::new);
         event.registerBlockEntityRenderer(ru.realdocs.ModEntities.ARCHIVE.get(), ru.realdocs.client.render.world.ShelfRenderer::new);
      }

      @SubscribeEvent
      public static void onKeys(RegisterKeyMappingsEvent event) {
         event.register(ClientSetup.ADMIN_KEY);
         event.register(ClientSetup.INSPECT_KEY);
      }

      @SubscribeEvent
      public static void onExtensions(RegisterClientExtensionsEvent event) {
         event.registerItem(new IClientItemExtensions() {
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
               return HeldItemRenderer.get();
            }
         }, new Item[]{(Item)ModItems.DOCUMENT.get(), (Item)ModItems.PASSPORT.get(), (Item)ModItems.NEWSPAPER.get(), (Item)ModItems.ENVELOPE.get(), (Item)ModItems.FOLDER.get()});
      }

      @SubscribeEvent
      public static void onClientSetup(FMLClientSetupEvent event) {
         event.enqueueWork(
            () -> {
               ItemProperties.register(
                  (Item)ModItems.DOCUMENT.get(), ResourceLocation.fromNamespaceAndPath("signedsealed", "issued"), (stack, level, entity, seed) -> {
                     CompoundTag tag = Tags.element(stack, "RealDoc");
                     return tag != null && tag.getBoolean("Issued") ? 1.0F : 0.0F;
                  }
               );
               ItemProperties.register(
                  (Item)ModItems.NEWSPAPER.get(), ResourceLocation.fromNamespaceAndPath("signedsealed", "state"), (stack, level, entity, seed) -> {
                     CompoundTag tag = Tags.element(stack, "RealPaper");
                     return tag == null ? 0.0F : (tag.getBoolean("Pub") ? 2.0F : 1.0F);
                  }
               );
               ItemProperties.register(
                  (Item)ModItems.ENVELOPE.get(), ResourceLocation.fromNamespaceAndPath("signedsealed", "state"), (stack, level, entity, seed) -> {
                     CompoundTag tag = Tags.element(stack, "RealMail");
                     return tag == null ? 0.0F : (float)(tag.getInt("State") + 1);
                  }
               );
               ItemProperties.register(
                  (Item)ModItems.PASSPORT.get(), ResourceLocation.fromNamespaceAndPath("signedsealed", "format"), (stack, level, entity, seed) -> {
                     CompoundTag tag = Tags.element(stack, "RealPass");
                     return tag == null ? 0.0F : (float)tag.getInt("Format");
                  }
               );
            }
         );
      }
   }
}
