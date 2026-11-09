package ru.realdocs.client;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import ru.realdocs.L;
import ru.realdocs.client.render.ImageFx;

public final class ImageUtil {
   private ImageUtil() {
   }

   public static ImageUtil.Loaded loadForStamp(Path file) throws IOException {
      if (!Files.isRegularFile(file)) {
         throw new IOException(L.t("Файл не найден"));
      } else if (Files.size(file) > 20000000L) {
         throw new IOException(L.t("Файл слишком большой"));
      } else {
         BufferedImage src;
         try (InputStream in = Files.newInputStream(file)) {
            src = ImageIO.read(in);
         }

         if (src == null) {
            throw new IOException(L.t("Не удалось прочитать изображение (нужен PNG)"));
         } else {
            for (int side = 128; side >= 24; side = side * 3 / 4) {
               BufferedImage scaled = scaleToFit(src, side);
               byte[] png = encode(scaled);
               if (png.length <= 24000) {
                  return new ImageUtil.Loaded(png, ImageIO.read(new ByteArrayInputStream(png)));
               }
            }

            throw new IOException(L.t("Изображение слишком сложное, упростите его"));
         }
      }
   }

   private static BufferedImage scaleToFit(BufferedImage src, int side) {
      double k = Math.min(1.0, (double)side / (double)Math.max(src.getWidth(), src.getHeight()));
      int w = Math.max(1, (int)Math.round((double)src.getWidth() * k));
      int h = Math.max(1, (int)Math.round((double)src.getHeight() * k));
      BufferedImage out = new BufferedImage(w, h, 2);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(src, 0, 0, w, h, null);
      g.dispose();
      return out;
   }

   static byte[] encode(BufferedImage img) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      if (!ImageIO.write(img, "png", bos)) {
         throw new IOException(L.t("Не удалось закодировать PNG"));
      } else {
         return bos.toByteArray();
      }
   }

   public static byte[] pngUnder(BufferedImage img, int maxBytes, int startSide) throws IOException {
      for (int side = startSide; side >= 16; side = side * 4 / 5) {
         byte[] png = encode(ImageFx.fit(img, side, side));
         if (png.length <= maxBytes) {
            return png;
         }
      }

      throw new IOException(L.t("Изображение слишком сложное, упростите его"));
   }

   public static byte[] photoPng(BufferedImage src, int maxBytes) throws IOException {
      int[][] sizes = new int[][]{{96, 128}, {84, 112}, {72, 96}, {60, 80}, {48, 64}};
      int[] levels = new int[]{64, 32, 16};

      for (int[] sz : sizes) {
         BufferedImage c = ImageFx.cover(src, sz[0], sz[1]);

         for (int lv : levels) {
            byte[] png = encode(posterize(c, lv));
            if (png.length <= maxBytes) {
               return png;
            }
         }
      }

      throw new IOException(L.t("Не удалось уменьшить фото до допустимого размера"));
   }

   static BufferedImage posterize(BufferedImage src, int levels) {
      BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), 1);
      int step = Math.max(1, 256 / levels);

      for (int y = 0; y < src.getHeight(); y++) {
         for (int x = 0; x < src.getWidth(); x++) {
            int p = src.getRGB(x, y);
            int r = (p >> 16 & 0xFF) / step * step + step / 2;
            int g = (p >> 8 & 0xFF) / step * step + step / 2;
            int b = (p & 0xFF) / step * step + step / 2;
            out.setRGB(x, y, Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b));
         }
      }

      return out;
   }

   public static record Loaded(byte[] png, BufferedImage preview) {
   }
}
