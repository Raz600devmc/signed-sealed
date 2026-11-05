package ru.realdocs.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import ru.realdocs.L;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.PdfWriter;
import ru.realdocs.model.DocModel;

public final class Exporter {
   private static final int EXPORT_WIDTH = 1240;

   private Exporter() {
   }

   public static Path exportPng(DocModel d) throws IOException {
      BufferedImage img = DocRenderer.render(d, StampCache.INSTANCE, 1240).image();
      Path file = target(d, "png");
      if (!ImageIO.write(img, "png", file.toFile())) {
         throw new IOException(L.t("Не удалось записать PNG"));
      } else {
         return file;
      }
   }

   public static Path exportPdf(DocModel d) throws IOException {
      BufferedImage img = DocRenderer.render(d, StampCache.INSTANCE, 1240).image();
      Path file = target(d, "pdf");
      Files.write(file, PdfWriter.singlePage(img));
      return file;
   }

   private static Path target(DocModel d, String ext) throws IOException {
      Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("signedsealed_exports");
      Files.createDirectories(dir);
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String num = d.number == null ? "" : d.number.replaceAll("[^\\p{L}\\p{N}_-]", "_");
      String name = L.t(d.templateName()).toLowerCase() + (num.isEmpty() ? "" : "_" + num) + "_" + stamp + "." + ext;
      return dir.resolve(name);
   }

   public static Path exportPdfPages(java.util.List<BufferedImage> pages, String prefix, double ptW, double ptH) throws IOException {
      Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("signedsealed_exports");
      Files.createDirectories(dir);
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String safe = prefix.replaceAll("[^\\p{L}\\p{N}_-]", "_");
      Path file = dir.resolve(safe + "_" + stamp + ".pdf");
      Files.write(file, PdfWriter.pages(pages, ptW, ptH));
      return file;
   }

   public static Path exportImage(BufferedImage img, String prefix) throws IOException {
      Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("signedsealed_exports");
      Files.createDirectories(dir);
      String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
      String safe = prefix.replaceAll("[^\\p{L}\\p{N}_-]", "_");
      Path file = dir.resolve(safe + "_" + stamp + ".png");
      if (!ImageIO.write(img, "png", file.toFile())) {
         throw new IOException(L.t("Не удалось записать PNG"));
      } else {
         return file;
      }
   }
}
