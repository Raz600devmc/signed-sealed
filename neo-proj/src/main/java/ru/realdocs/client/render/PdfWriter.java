package ru.realdocs.client.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

public final class PdfWriter {
   private PdfWriter() {
   }

   public static byte[] singlePage(BufferedImage page) throws IOException {
      byte[] jpeg = toJpeg(page, 0.93F);
      int w = page.getWidth();
      int h = page.getHeight();
      ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + 2048);
      List<Integer> offsets = new ArrayList<>();
      write(out, "%PDF-1.4\n%âãÏÓ\n");
      offsets.add(out.size());
      write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
      offsets.add(out.size());
      write(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
      offsets.add(out.size());
      write(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");
      offsets.add(out.size());
      write(
         out,
         "4 0 obj\n<< /Type /XObject /Subtype /Image /Width "
            + w
            + " /Height "
            + h
            + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
            + jpeg.length
            + " >>\nstream\n"
      );
      out.write(jpeg);
      write(out, "\nendstream\nendobj\n");
      String content = "q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n";
      offsets.add(out.size());
      write(out, "5 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content + "endstream\nendobj\n");
      int xref = out.size();
      StringBuilder sb = new StringBuilder();
      sb.append("xref\n0 ").append(offsets.size() + 1).append("\n");
      sb.append("0000000000 65535 f \n");

      for (int off : offsets) {
         sb.append(String.format("%010d 00000 n \n", off));
      }

      sb.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\n");
      sb.append("startxref\n").append(xref).append("\n%%EOF\n");
      write(out, sb.toString());
      return out.toByteArray();
   }

   /** Several pages, each an image stretched to ptW x ptH points. */
   public static byte[] pages(List<BufferedImage> pages, double ptW, double ptH) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      List<Integer> offsets = new ArrayList<>();
      int n = pages.size();
      String pw = String.format(java.util.Locale.ROOT, "%.2f", ptW);
      String ph = String.format(java.util.Locale.ROOT, "%.2f", ptH);
      write(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
      offsets.add(out.size());
      write(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
      StringBuilder kids = new StringBuilder();

      for (int i = 0; i < n; i++) {
         kids.append(3 + i * 3).append(" 0 R ");
      }

      offsets.add(out.size());
      write(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>\nendobj\n");

      for (int i = 0; i < n; i++) {
         BufferedImage page = pages.get(i);
         byte[] jpeg = toJpeg(page, 0.92F);
         int po = 3 + i * 3;
         offsets.add(out.size());
         write(out, po + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pw + " " + ph + "] /Resources << /XObject << /Im0 " + (po + 1) + " 0 R >> >> /Contents " + (po + 2) + " 0 R >>\nendobj\n");
         offsets.add(out.size());
         write(out, (po + 1) + " 0 obj\n<< /Type /XObject /Subtype /Image /Width " + page.getWidth() + " /Height " + page.getHeight() + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n");
         out.write(jpeg);
         write(out, "\nendstream\nendobj\n");
         String content = "q\n" + pw + " 0 0 " + ph + " 0 0 cm\n/Im0 Do\nQ\n";
         offsets.add(out.size());
         write(out, (po + 2) + " 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content + "endstream\nendobj\n");
      }

      int xref = out.size();
      StringBuilder sb = new StringBuilder();
      sb.append("xref\n0 ").append(offsets.size() + 1).append("\n");
      sb.append("0000000000 65535 f \n");

      for (int off : offsets) {
         sb.append(String.format("%010d 00000 n \n", off));
      }

      sb.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\n");
      sb.append("startxref\n").append(xref).append("\n%%EOF\n");
      write(out, sb.toString());
      return out.toByteArray();
   }

   private static void write(ByteArrayOutputStream out, String s) throws IOException {
      out.write(s.getBytes(StandardCharsets.ISO_8859_1));
   }

   private static byte[] toJpeg(BufferedImage src, float quality) throws IOException {
      BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), 1);
      Graphics2D g = rgb.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
      g.drawImage(src, 0, 0, null);
      g.dispose();
      ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(2);
      param.setCompressionQuality(quality);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();

      try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
         writer.setOutput(ios);
         writer.write(null, new IIOImage(rgb, null, null), param);
      } finally {
         writer.dispose();
      }

      return bos.toByteArray();
   }
}
