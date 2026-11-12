package ru.realdocs.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import ru.realdocs.L;

public final class WebImage {
   public static final int MAX_BYTES = 6000000;
   private static final int MAX_REDIRECTS = 4;

   private WebImage() {
   }

   public static CompletableFuture<BufferedImage> load(String input) {
      return CompletableFuture.supplyAsync(() -> {
         try {
            return loadSync(input == null ? "" : input.trim());
         } catch (IOException var2) {
            throw new CompletionException(var2);
         }
      });
   }

   public static String reason(Throwable t) {
      Throwable c = t;

      while (c instanceof CompletionException && c.getCause() != null) {
         c = c.getCause();
      }

      String m = c.getMessage();
      return m != null && !m.isBlank() ? m : L.t("не удалось загрузить изображение");
   }

   static BufferedImage loadSync(String in) throws IOException {
      if (in.length() >= 2 && in.startsWith("\"") && in.endsWith("\"")) {
         in = in.substring(1, in.length() - 1);
      }

      if (in.isEmpty()) {
         throw new IOException(L.t("Укажите ссылку или путь к файлу"));
      } else {
         String low = in.toLowerCase();
         if (!low.startsWith("http://") && !low.startsWith("https://")) {
            if (low.startsWith("file:")) {
               in = Path.of(URI.create(in)).toString();
            }

            Path p = Path.of(in);
            if (!Files.isRegularFile(p)) {
               throw new IOException(L.t("Файл не найден: ") + p.getFileName());
            } else if (Files.size(p) > 6000000L) {
               throw new IOException(L.t("Файл больше 6 МБ"));
            } else {
               return decode(Files.readAllBytes(p));
            }
         } else {
            return fetch(in);
         }
      }
   }

   private static BufferedImage fetch(String url) throws IOException {
      HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NEVER).connectTimeout(Duration.ofSeconds(8L)).build();
      String cur = url;

      for (int i = 0; i <= 4; i++) {
         URI uri;
         try {
            uri = URI.create(cur);
         } catch (IllegalArgumentException var14) {
            throw new IOException(L.t("Некорректная ссылка"));
         }

         String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
         if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException(L.t("Разрешены только http и https"));
         }

         checkHost(uri.getHost());
         HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(12L))
            .header("User-Agent", "RealDocs-Mod/1.0")
            .header("Accept", "image/png,image/jpeg,image/gif,image/*;q=0.8")
            .GET()
            .build();

         HttpResponse<InputStream> resp;
         try {
            resp = client.send(req, BodyHandlers.ofInputStream());
         } catch (InterruptedException var13) {
            Thread.currentThread().interrupt();
            throw new IOException(L.t("Загрузка прервана"));
         }

         int code = resp.statusCode();
         if (code < 300 || code >= 400) {
            if (code != 200) {
               resp.body().close();
               throw new IOException(L.t("Сервер ответил кодом ") + code);
            }

            BufferedImage var10;
            try (InputStream body = resp.body()) {
               var10 = decode(readLimited(body));
            }

            return var10;
         }

         String loc = resp.headers().firstValue("Location").orElse("");
         if (loc.isEmpty()) {
            throw new IOException(L.t("Перенаправление без адреса"));
         }

         cur = uri.resolve(loc).toString();
         resp.body().close();
      }

      throw new IOException(L.t("Слишком много перенаправлений"));
   }

   private static void checkHost(String host) throws IOException {
      if (host != null && !host.isEmpty()) {
         for (InetAddress a : InetAddress.getAllByName(host)) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
               throw new IOException(L.t("Адреса из локальной сети запрещены"));
            }
         }
      } else {
         throw new IOException(L.t("В ссылке нет адреса сайта"));
      }
   }

   private static byte[] readLimited(InputStream in) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[16384];

      int n;
      while ((n = in.read(buf)) > 0) {
         out.write(buf, 0, n);
         if (out.size() > 6000000) {
            throw new IOException(L.t("Изображение больше 6 МБ"));
         }
      }

      return out.toByteArray();
   }

   private static BufferedImage decode(byte[] bytes) throws IOException {
      // Check the declared size before decoding, so a tiny file claiming a huge resolution cannot exhaust memory.
      try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
         java.util.Iterator<javax.imageio.ImageReader> readers = in == null ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(in);
         if (readers.hasNext()) {
            javax.imageio.ImageReader r = readers.next();

            try {
               r.setInput(in, true, true);
               if ((long)r.getWidth(0) * (long)r.getHeight(0) > 36000000L || r.getWidth(0) > 12000 || r.getHeight(0) > 12000) {
                  throw new IOException(L.t("Слишком большое разрешение"));
               }
            } finally {
               r.dispose();
            }
         }
      }

      BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
      if (img == null) {
         throw new IOException(L.t("Это не изображение или формат не поддерживается (нужен PNG, JPG, GIF или BMP)"));
      } else if ((long)img.getWidth() * (long)img.getHeight() > 36000000L) {
         throw new IOException(L.t("Слишком большое разрешение"));
      } else {
         return img;
      }
   }
}
