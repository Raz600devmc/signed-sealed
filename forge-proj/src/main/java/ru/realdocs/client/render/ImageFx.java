package ru.realdocs.client.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

public final class ImageFx {
   private ImageFx() {
   }

   public static BufferedImage toArgb(BufferedImage src) {
      BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), 2);
      Graphics2D g = out.createGraphics();
      g.drawImage(src, 0, 0, null);
      g.dispose();
      return out;
   }

   public static BufferedImage removeBackground(BufferedImage src, int tolerance) {
      BufferedImage img = toArgb(src);
      int w = img.getWidth();
      int h = img.getHeight();
      int[] px = img.getRGB(0, 0, w, h, null, 0, w);
      int ref = averageCorners(px, w, h);
      boolean[] seen = new boolean[w * h];
      ArrayDeque<Integer> q = new ArrayDeque<>();

      for (int x = 0; x < w; x++) {
         push(q, seen, x, 0, w);
         push(q, seen, x, h - 1, w);
      }

      for (int y = 0; y < h; y++) {
         push(q, seen, 0, y, w);
         push(q, seen, w - 1, y, w);
      }

      while (!q.isEmpty()) {
         int i = q.poll();
         int x = i % w;
         int y = i / w;
         if (similar(px[i], ref, tolerance)) {
            px[i] = 0;
            if (x > 0) {
               push(q, seen, x - 1, y, w);
            }

            if (x < w - 1) {
               push(q, seen, x + 1, y, w);
            }

            if (y > 0) {
               push(q, seen, x, y - 1, w);
            }

            if (y < h - 1) {
               push(q, seen, x, y + 1, w);
            }
         }
      }

      img.setRGB(0, 0, w, h, px, 0, w);
      return img;
   }

   private static void push(ArrayDeque<Integer> q, boolean[] seen, int x, int y, int w) {
      int i = y * w + x;
      if (!seen[i]) {
         seen[i] = true;
         q.add(i);
      }
   }

   private static int averageCorners(int[] px, int w, int h) {
      int[] c = new int[]{px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]};
      long a = 0L;
      long r = 0L;
      long g = 0L;
      long b = 0L;

      for (int v : c) {
         a += (long)(v >>> 24);
         r += (long)(v >> 16 & 0xFF);
         g += (long)(v >> 8 & 0xFF);
         b += (long)(v & 0xFF);
      }

      return (int)(a / 4L) << 24 | (int)(r / 4L) << 16 | (int)(g / 4L) << 8 | (int)(b / 4L);
   }

   private static boolean similar(int p, int ref, int tol) {
      int pa = p >>> 24;
      int ra = ref >>> 24;
      if (pa < 16) {
         return true;
      } else if (ra < 16) {
         return false;
      } else {
         int dr = (p >> 16 & 0xFF) - (ref >> 16 & 0xFF);
         int dg = (p >> 8 & 0xFF) - (ref >> 8 & 0xFF);
         int db = (p & 0xFF) - (ref & 0xFF);
         return Math.sqrt((double)(dr * dr + dg * dg + db * db)) <= (double)tol;
      }
   }

   public static BufferedImage inkify(BufferedImage src, int rgb, double threshold) {
      BufferedImage img = toArgb(src);
      int w = img.getWidth();
      int h = img.getHeight();
      int[] px = img.getRGB(0, 0, w, h, null, 0, w);

      for (int i = 0; i < px.length; i++) {
         int p = px[i];
         int a = p >>> 24;
         if (a != 0) {
            double lum = (0.299 * (double)(p >> 16 & 0xFF) + 0.587 * (double)(p >> 8 & 0xFF) + 0.114 * (double)(p & 0xFF)) / 255.0;
            double dens = Math.max(0.0, Math.min(1.0, (threshold - lum) / Math.max(0.05, threshold)));
            int na = (int)Math.round((double)a * dens);
            px[i] = na << 24 | rgb & 16777215;
         }
      }

      img.setRGB(0, 0, w, h, px, 0, w);
      return img;
   }

   public static BufferedImage tint(BufferedImage src, int rgb) {
      BufferedImage img = toArgb(src);
      int w = img.getWidth();
      int h = img.getHeight();
      int[] px = img.getRGB(0, 0, w, h, null, 0, w);

      for (int i = 0; i < px.length; i++) {
         int a = px[i] >>> 24;
         if (a != 0) {
            px[i] = a << 24 | rgb & 16777215;
         }
      }

      img.setRGB(0, 0, w, h, px, 0, w);
      return img;
   }

   public static BufferedImage autoCrop(BufferedImage src) {
      BufferedImage img = toArgb(src);
      int w = img.getWidth();
      int h = img.getHeight();
      int minX = w;
      int minY = h;
      int maxX = -1;
      int maxY = -1;

      for (int y = 0; y < h; y++) {
         for (int x = 0; x < w; x++) {
            if (img.getRGB(x, y) >>> 24 > 12) {
               if (x < minX) {
                  minX = x;
               }

               if (x > maxX) {
                  maxX = x;
               }

               if (y < minY) {
                  minY = y;
               }

               if (y > maxY) {
                  maxY = y;
               }
            }
         }
      }

      return maxX < 0 ? img : img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
   }

   public static BufferedImage fit(BufferedImage src, int maxW, int maxH) {
      double k = Math.min(1.0, Math.min((double)maxW / (double)src.getWidth(), (double)maxH / (double)src.getHeight()));
      int w = Math.max(1, (int)Math.round((double)src.getWidth() * k));
      int h = Math.max(1, (int)Math.round((double)src.getHeight() * k));
      if (w == src.getWidth() && h == src.getHeight()) {
         return toArgb(src);
      } else {
         BufferedImage out = new BufferedImage(w, h, 2);
         Graphics2D g = out.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
         g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         g.drawImage(src, 0, 0, w, h, null);
         g.dispose();
         return out;
      }
   }

   public static BufferedImage cover(BufferedImage src, int w, int h) {
      double k = Math.max((double)w / (double)src.getWidth(), (double)h / (double)src.getHeight());
      int sw = (int)Math.round((double)src.getWidth() * k);
      int sh = (int)Math.round((double)src.getHeight() * k);
      BufferedImage out = new BufferedImage(w, h, 1);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, h);
      g.drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
      g.dispose();
      return out;
   }
}
