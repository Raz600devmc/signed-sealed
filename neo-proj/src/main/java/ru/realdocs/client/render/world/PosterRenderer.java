package ru.realdocs.client.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.realdocs.client.held.Doc3D;
import ru.realdocs.client.held.HeldTextures;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.entity.PosterEntity;
import ru.realdocs.model.PaperModel;

/** Draws a pinned newspaper page flat against the wall. */
public class PosterRenderer extends EntityRenderer<PosterEntity> {
   public PosterRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
   }

   public void render(PosterEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
      PaperCodec.Ref r = PaperCodec.ref(e.item());
      ResourceLocation front = HeldTextures.paperPage(r, 0, 900);
      int tone = 0xFF000000 | PaperModel.TONE_RGB[0];
      ps.pushPose();
      ps.mulPose(Axis.YP.rotationDegrees(-e.facing().toYRot()));
      Doc3D.Model m = Doc3D.Model.sheet(e.posterW(), e.posterH(), front, null, tone);
      m.thick = 0.008F;
      ps.translate(0.0F, 0.0F, -0.012F);
      Doc3D.render(ps, buf, m, 0.0F, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
      super.render(e, yaw, pt, ps, buf, light);
   }

   public ResourceLocation getTextureLocation(PosterEntity e) {
      return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
   }
}
