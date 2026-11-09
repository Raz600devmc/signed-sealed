package ru.realdocs.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class PageTexture {
   private DynamicTexture tex;
   private ResourceLocation id;
   public int w;
   public int h;

   public boolean ready() {
      return this.tex != null;
   }

   public ResourceLocation id() {
      return this.id;
   }

   public void set(BufferedImage img) {
      int iw = img.getWidth();
      int ih = img.getHeight();
      if (this.tex == null || iw != this.w || ih != this.h) {
         this.close();
         NativeImage ni = new NativeImage(Format.RGBA, iw, ih, false);
         this.tex = new DynamicTexture(ni);
         this.id = Minecraft.getInstance().getTextureManager().register("signedsealed_tex", this.tex);
         this.w = iw;
         this.h = ih;
      }

      NativeImage ni = this.tex.getPixels();
      if (ni != null) {
         int[] px = img.getRGB(0, 0, iw, ih, null, 0, iw);

         for (int y = 0; y < ih; y++) {
            int row = y * iw;

            for (int x = 0; x < iw; x++) {
               int argb = px[row + x];
               int abgr = argb & -16711936 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
               ni.setPixelRGBA(x, y, abgr);
            }
         }

         this.tex.upload();
      }
   }

   public void close() {
      if (this.tex != null) {
         Minecraft.getInstance().getTextureManager().release(this.id);
         this.tex = null;
         this.id = null;
      }
   }
}
