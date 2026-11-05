package ru.realdocs.client.held;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import ru.realdocs.client.PageTexture;

public final class Doc3D {
   private static PageTexture white;

   private Doc3D() {
   }

   private static ResourceLocation white() {
      if (white == null || !white.ready()) {
         white = new PageTexture();
         BufferedImage img = new BufferedImage(4, 4, 2);

         for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
               img.setRGB(x, y, -1);
            }
         }

         white.set(img);
      }

      return white.id();
   }

   private static void quad(
      PoseStack ps,
      MultiBufferSource buf,
      ResourceLocation tex,
      int argb,
      int light,
      int overlay,
      float[] p0,
      float[] p1,
      float[] p2,
      float[] p3,
      float u0,
      float v0,
      float u1,
      float v1,
      float nx,
      float ny,
      float nz
   ) {
      VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(tex == null ? white() : tex));
      Matrix4f m = ps.last().pose();
      Pose n = ps.last();
      int a = argb >>> 24;
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      if (tex != null) {
         b = 255;
         g = 255;
         r = 255;
      }

      v(vc, m, n, p0, r, g, b, a, u0, v1, light, overlay, nx, ny, nz);
      v(vc, m, n, p1, r, g, b, a, u1, v1, light, overlay, nx, ny, nz);
      v(vc, m, n, p2, r, g, b, a, u1, v0, light, overlay, nx, ny, nz);
      v(vc, m, n, p3, r, g, b, a, u0, v0, light, overlay, nx, ny, nz);
   }

   private static void v(
      VertexConsumer vc, Matrix4f m, Pose n, float[] p, int r, int g, int b, int a, float u, float v, int light, int overlay, float nx, float ny, float nz
   ) {
      vc.addVertex(m, p[0], p[1], p[2]).setColor(r, g, b, a).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(n, nx, ny, nz);
   }

   private static float[] p(float x, float y, float z) {
      return new float[]{x, y, z};
   }

   static void slab(
      PoseStack ps,
      MultiBufferSource buf,
      float w,
      float h,
      float t,
      ResourceLocation front,
      ResourceLocation back,
      int faceColor,
      int backColor,
      int side,
      int light,
      int overlay,
      float fu0,
      float fu1,
      boolean mirrorBack
   ) {
      float x0 = -w / 2.0F;
      float x1 = w / 2.0F;
      float y0 = -h / 2.0F;
      float y1 = h / 2.0F;
      float z0 = -t / 2.0F;
      float z1 = t / 2.0F;
      quad(ps, buf, front, faceColor, light, overlay, p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1), fu0, 0.0F, fu1, 1.0F, 0.0F, 0.0F, 1.0F);
      if (mirrorBack) {
         quad(ps, buf, back, backColor, light, overlay, p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F);
      } else {
         quad(ps, buf, back, backColor, light, overlay, p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, -1.0F);
      }

      quad(ps, buf, null, side, light, overlay, p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F);
      quad(ps, buf, null, side, light, overlay, p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1), 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, -1.0F, 0.0F);
      quad(ps, buf, null, side, light, overlay, p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F);
      quad(ps, buf, null, side, light, overlay, p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), 0.0F, 0.0F, 1.0F, 1.0F, -1.0F, 0.0F, 0.0F);
   }

   private static int shade(int argb, float k) {
      int r = (int)((float)(argb >> 16 & 0xFF) * k);
      int g = (int)((float)(argb >> 8 & 0xFF) * k);
      int b = (int)((float)(argb & 0xFF) * k);
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   public static void render(PoseStack ps, MultiBufferSource buf, Doc3D.Model m, float openAmount, int light, int overlay) {
      switch (m.kind) {
         case SHEET:
            slab(ps, buf, m.w, m.h, m.thick, m.front, m.back, m.paper, shade(m.paper, 0.93F), shade(m.paper, 0.85F), light, overlay, 0.0F, 1.0F, m.back != null);
            break;
         case CARD:
            slab(ps, buf, m.w, m.h, m.thick, m.front, m.back, m.color, m.color, shade(m.color, 0.8F), light, overlay, 0.0F, 1.0F, true);
            break;
         case BOOK:
            book(ps, buf, m, openAmount, light, overlay);
      }
   }

   private static void book(PoseStack ps, MultiBufferSource buf, Doc3D.Model m, float open, int light, int overlay) {
      float w = m.w;
      float h = m.h;
      float t = m.thick;
      int cover = m.color;
      int dark = shade(cover, 0.7F);
      int pages = m.paper;
      if (!(open <= 0.001F) && m.spread != null) {
         float angle = 90.0F * (1.0F - open) + 9.0F;

         for (int side = 0; side < 2; side++) {
            ps.pushPose();
            float dir = side == 0 ? -1.0F : 1.0F;
            ps.mulPose(Axis.YP.rotationDegrees(side == 0 ? angle : -angle));
            ps.translate(dir * w / 2.0F, 0.0F, 0.0F);
            float u0 = side == 0 ? 0.0F : 0.5F;
            float u1 = side == 0 ? 0.5F : 1.0F;
            slab(ps, buf, w, h, t / 2.0F, m.spread, null, pages, cover, pages, light, overlay, u0, u1, false);
            ps.translate(0.0F, 0.0F, -t / 4.0F - 0.003F);
            slab(ps, buf, w + 0.01F, h + 0.012F, 0.006F, null, null, cover, cover, dark, light, overlay, 0.0F, 1.0F, false);
            ps.popPose();
         }
      } else {
         slab(ps, buf, w, h, t, m.front, null, cover, dark, pages, light, overlay, 0.0F, 1.0F, false);
         ps.pushPose();
         ps.translate(-w / 2.0F + 0.002F, 0.0F, 0.0F);
         slab(ps, buf, 0.006F, h + 0.002F, t + 0.004F, null, null, dark, dark, dark, light, overlay, 0.0F, 1.0F, false);
         ps.popPose();
      }
   }

   public static enum Kind {
      SHEET,
      CARD,
      BOOK;
   }

   public static final class Model {
      public Doc3D.Kind kind;
      public float w;
      public float h;
      public float thick;
      public ResourceLocation front;
      public ResourceLocation back;
      public ResourceLocation spread;
      public int color;
      public int paper;

      public static Doc3D.Model sheet(float w, float h, ResourceLocation front, ResourceLocation back, int paper) {
         Doc3D.Model m = new Doc3D.Model();
         m.kind = Doc3D.Kind.SHEET;
         m.w = w;
         m.h = h;
         m.thick = 0.006F;
         m.front = front;
         m.back = back;
         m.color = paper;
         m.paper = paper;
         return m;
      }

      public static Doc3D.Model card(ResourceLocation front, ResourceLocation back, int color) {
         Doc3D.Model m = new Doc3D.Model();
         m.kind = Doc3D.Kind.CARD;
         m.w = 0.56F;
         m.h = 0.3535F;
         m.thick = 0.012F;
         m.front = front;
         m.back = back;
         m.color = color;
         m.paper = color;
         return m;
      }

      public static Doc3D.Model book(ResourceLocation cover, ResourceLocation spread, int coverColor, int paper) {
         Doc3D.Model m = new Doc3D.Model();
         m.kind = Doc3D.Kind.BOOK;
         m.w = 0.42F;
         m.h = 0.6F;
         m.thick = 0.05F;
         m.front = cover;
         m.spread = spread;
         m.color = coverColor;
         m.paper = paper;
         return m;
      }
   }
}
