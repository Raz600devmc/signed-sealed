package ru.realdocs.client.render.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import ru.realdocs.ModBlocks;
import ru.realdocs.blockentity.ArchiveBE;
import ru.realdocs.client.held.Doc3D;
import ru.realdocs.client.held.HeldTextures;
import ru.realdocs.client.render.MailRenderer;
import ru.realdocs.model.PaperModel;

/**
 * Archive shelf contents. Each shelf holds 6 slots: folders and passports stand as spines on the left,
 * documents, newspapers and letters lie in one stack on the right, every sheet shifted a little so each edge shows.
 * Sizes match the item in an item frame (an issued document is about 0.33 x 0.46 of a block); the top sheet shows its real page.
 */
public class ShelfRenderer implements BlockEntityRenderer<ArchiveBE> {
   private static final float LEFT = -0.4375F;
   private static final float RIGHT = 0.4375F;
   /** Held-model units to blocks: a document (0.877 tall in hand) becomes 0.46 of a block, as in an item frame. */
   private static final float SCALE = 0.525F;

   public ShelfRenderer(BlockEntityRendererProvider.Context ctx) {
   }

   public void render(ArchiveBE be, float pt, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      if (be.getBlockState().is(ModBlocks.ARCHIVE_SHELF.get()) && be.visual.length != 0) {
         Direction f = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
         ps.pushPose();
         ps.translate(0.5F, 0.0F, 0.5F);
         ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));

         for (int row = 0; row < 2; row++) {
            this.shelf(be.visual, row * 6, row == 0 ? 0.535F : 0.0835F, be.tops[row], ps, buf, light, overlay);
         }

         ps.popPose();
      }
   }

   private void shelf(int[] v, int from, float y, ItemStack top, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      int to = Math.min(v.length, from + 6);
      float x = LEFT + 0.04F;
      int last = -1;

      for (int i = from; i < to; i++) {
         int kind = v[i] >>> 24;
         if (kind == 1 || kind == 3) {
            float t = kind == 1 ? 0.045F : 0.03F;
            spine(v[i], kind, x + t / 2.0F, y, t, ps, buf, light, overlay);
            x += t + 0.012F;
         } else if (kind == 2 || kind == 4 || kind == 5) {
            last = i;
         }
      }

      if (last < 0) {
         return;
      }

      float cx = (x + RIGHT) / 2.0F;
      float h = y;
      int n = 0;

      for (int i = from; i <= last; i++) {
         int kind = v[i] >>> 24;
         if (kind == 2 || kind == 4 || kind == 5) {
            Doc3D.Model m = i == last && !top.isEmpty() ? HeldTextures.model(top, false) : null;
            if (m == null) {
               m = blank(v[i], kind, n);
            }

            h = sheet(m, n, cx, h, ps, buf, light, overlay);
            n++;
         }
      }
   }

   /** A folder or passport standing upright, spine to the front. */
   private static void spine(int vis, int kind, float x, float y, float t, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      float h = kind == 1 ? 0.33F : 0.315F;
      float d = kind == 1 ? 0.24F : 0.22F;
      ps.pushPose();
      ps.translate(x, y + h / 2.0F, 0.0F);
      ps.mulPose(Axis.YP.rotationDegrees(90.0F));
      Doc3D.Model m = Doc3D.Model.card(null, null, 0xFF000000 | vis & 0xFFFFFF);
      m.w = d;
      m.h = h;
      m.thick = t;
      Doc3D.render(ps, buf, m, 0.0F, light, overlay);
      ps.popPose();
   }

   /** Plain paper of the right size for a sheet further down the stack. */
   private static Doc3D.Model blank(int vis, int kind, int n) {
      int paper = 0xFF000000 | vis & 0xFFFFFF;
      if (n % 2 == 1) {
         paper = shade(paper, 0.95F);
      }

      Doc3D.Model m = switch (kind) {
         case 4 -> Doc3D.Model.sheet(0.62F, 0.62F * PaperModel.H / PaperModel.W, null, null, paper);
         case 5 -> Doc3D.Model.sheet(0.62F, 0.62F * MailRenderer.EH / MailRenderer.EW, null, null, paper);
         default -> Doc3D.Model.sheet(0.62F, 0.877F, null, null, paper);
      };
      m.thick = kind == 4 ? 0.03F : 0.0F;
      return m;
   }

   /** Lays one sheet on top of the stack and returns the new stack height. */
   private static float sheet(Doc3D.Model m, int n, float cx, float y, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      // thick enough that the edge of every sheet reads as its own line
      m.thick = Math.max(m.thick, 0.02F);
      float t = m.thick * SCALE;
      // stable jitter per position in the stack so every sheet's edge peeks out
      float dx = ((n * 7 + 3) % 5 - 2) * 0.012F;
      float dz = ((n * 3 + 1) % 5 - 2) * 0.01F;
      float rot = ((n * 5 + 2) % 7 - 3) * 2.0F;
      ps.pushPose();
      ps.translate(cx + dx, y + t / 2.0F, dz);
      ps.mulPose(Axis.YP.rotationDegrees(rot));
      ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
      // head of the page at the back, so it reads right from the front of the shelf
      ps.mulPose(Axis.ZP.rotationDegrees(180.0F));
      ps.scale(SCALE, SCALE, SCALE);
      Doc3D.render(ps, buf, m, 0.0F, light, overlay);
      ps.popPose();
      return y + t + 0.002F;
   }

   private static int shade(int argb, float k) {
      int r = (int)((argb >> 16 & 0xFF) * k);
      int g = (int)((argb >> 8 & 0xFF) * k);
      int b = (int)((argb & 0xFF) * k);
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }
}
