package ru.realdocs.client.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.Plat;
import ru.realdocs.client.held.Doc3D;
import ru.realdocs.client.held.HeldTextures;
import ru.realdocs.entity.NoticeBoardEntity;

/** A cork board in a wooden frame with up to six sheets pinned in a 3x2 grid. */
public class NoticeBoardRenderer extends EntityRenderer<NoticeBoardEntity> {
   public static final ResourceLocation TEX = Plat.rl("textures/entity/notice_board.png");
   private static final float[] TILT = new float[]{-2.5F, 1.5F, -1.0F, 2.0F, -1.8F, 1.2F};

   public NoticeBoardRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
   }

   public void render(NoticeBoardEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
      ps.pushPose();
      ps.mulPose(Axis.YP.rotationDegrees(-e.facing().toYRot()));
      Doc3D.Model board = Doc3D.Model.sheet(NoticeBoardEntity.W, NoticeBoardEntity.H, TEX, null, -1);
      board.thick = NoticeBoardEntity.DEPTH;
      Doc3D.render(ps, buf, board, 0.0F, light, OverlayTexture.NO_OVERLAY);
      float z = NoticeBoardEntity.DEPTH / 2.0F + 0.004F;

      for (int i = 0; i < NoticeBoardEntity.SLOTS; i++) {
         ItemStack s = e.item(i);
         if (!s.isEmpty()) {
            Doc3D.Model m = HeldTextures.model(s, false);
            if (m != null) {
               float cx = (float)(i % 3 - 1) * 0.94F;
               float cy = i < 3 ? 0.44F : -0.44F;
               float sc = Math.min(0.8F / m.w, 0.8F / m.h);
               ps.pushPose();
               ps.translate(cx, cy, z + m.thick * sc / 2.0F);
               ps.mulPose(Axis.ZP.rotationDegrees(TILT[i]));
               ps.scale(sc, sc, sc);
               Doc3D.render(ps, buf, m, 0.0F, light, OverlayTexture.NO_OVERLAY);
               ps.popPose();
               ps.pushPose();
               ps.translate(cx, cy + m.h * sc / 2.0F - 0.05F, z + m.thick * sc + 0.006F);
               Doc3D.Model pin = Doc3D.Model.sheet(0.05F, 0.05F, null, null, 0xFFB8322C);
               pin.thick = 0.02F;
               Doc3D.render(ps, buf, pin, 0.0F, light, OverlayTexture.NO_OVERLAY);
               ps.popPose();
            }
         }
      }

      String label = e.label();
      if (!label.isEmpty()) {
         Font font = this.getFont();
         ps.pushPose();
         ps.translate(0.0F, NoticeBoardEntity.H / 2.0F - 0.0625F, NoticeBoardEntity.DEPTH / 2.0F + 0.002F);
         float k = 0.0085F;
         ps.scale(k, -k, k);
         int w = font.width(label);
         float max = (NoticeBoardEntity.W - 0.3F) / k;
         if ((float)w > max) {
            float f = max / (float)w;
            ps.scale(f, f, f);
         }

         font.drawInBatch(label, (float)(-w) / 2.0F, -4.0F, 0xFFF3E3C2, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
         ps.popPose();
      }

      ps.popPose();
      super.render(e, yaw, pt, ps, buf, light);
   }

   public ResourceLocation getTextureLocation(NoticeBoardEntity e) {
      return TEX;
   }
}
