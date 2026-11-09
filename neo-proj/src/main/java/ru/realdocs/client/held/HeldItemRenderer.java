package ru.realdocs.client.held;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class HeldItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static HeldItemRenderer instance;

   public static HeldItemRenderer get() {
      if (instance == null) {
         Minecraft mc = Minecraft.getInstance();
         instance = new HeldItemRenderer(mc);
      }

      return instance;
   }

   private HeldItemRenderer(Minecraft mc) {
      super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
   }

   public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      Doc3D.Model m = HeldTextures.model(stack, false);
      if (m != null) {
         ps.pushPose();
         ps.translate(0.5, 0.5, 0.5);
         float k = 1.0F / Math.max(m.w, m.h) * 0.92F;
         ps.scale(k, k, k);
         Doc3D.render(ps, buf, m, 0.0F, light, overlay);
         ps.popPose();
      }
   }
}
