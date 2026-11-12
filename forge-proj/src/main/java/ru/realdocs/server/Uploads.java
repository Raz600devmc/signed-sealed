package ru.realdocs.server;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.L;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/**
 * Reassembles uploads that do not fit into one client packet (serverbound payloads are capped at
 * about 32 KB): newspaper pictures and page layouts arrive in chunks.
 */
public final class Uploads {
   public static final int CHUNK = 30000;
   public static final int KIND_IMAGE = 0;
   public static final int KIND_LAYOUT = 1;
   /** Logo for a document template (2.4.0). */
   public static final int KIND_LOGO = 2;
   public static final int MAX_IMAGE = 640000;
   public static final int MAX_LAYOUT = 960000;
   private static final int MAX_PENDING = 6;
   private static final long TTL = 180000L;
   private static final Map<UUID, Map<Integer, Buf>> PENDING = new HashMap<>();
   private static final Map<UUID, Map<Integer, byte[]>> LAYOUTS = new HashMap<>();

   private Uploads() {
   }

   private static final class Buf {
      final int kind;
      final byte[][] parts;
      int got;
      int size;
      final long started = System.currentTimeMillis();

      Buf(int kind, int total) {
         this.kind = kind;
         this.parts = new byte[total][];
      }
   }

   public static int maxParts(int kind) {
      return ((kind == KIND_IMAGE || kind == KIND_LOGO ? MAX_IMAGE : MAX_LAYOUT) + CHUNK - 1) / CHUNK;
   }

   private static void expire(Map<Integer, Buf> m) {
      long now = System.currentTimeMillis();
      Iterator<Buf> it = m.values().iterator();

      while (it.hasNext()) {
         if (now - it.next().started > TTL) {
            it.remove();
         }
      }
   }

   public static void chunk(ServerPlayer sp, int token, int kind, int index, int total, byte[] data) {
      if (kind != KIND_IMAGE && kind != KIND_LAYOUT && kind != KIND_LOGO) {
         return;
      }

      if (total < 1 || total > maxParts(kind) || index < 0 || index >= total || data == null || data.length > CHUNK) {
         Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Файл слишком большой.")));
         return;
      }

      Map<Integer, Buf> mine = PENDING.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
      expire(mine);
      Buf b = mine.get(token);
      if (b == null) {
         if (mine.size() >= MAX_PENDING) {
            Net.sendTo(sp, new MorePackets.UploadResult(token, false, "", L.k("Слишком много загрузок одновременно, подождите.")));
            return;
         }

         b = new Buf(kind, total);
         mine.put(token, b);
      }

      if (b.kind != kind || b.parts.length != total || b.parts[index] != null) {
         return;
      }

      b.parts[index] = data;
      b.got++;
      b.size += data.length;
      if (b.got == total) {
         mine.remove(token);
         ByteArrayOutputStream out = new ByteArrayOutputStream(b.size);

         for (byte[] p : b.parts) {
            out.writeBytes(p);
         }

         byte[] all = out.toByteArray();
         if (kind == KIND_IMAGE) {
            PaperService.acceptImage(sp, token, all);
         } else if (kind == KIND_LOGO) {
            TemplateService.acceptLogo(sp, token, all);
         } else {
            Map<Integer, byte[]> l = LAYOUTS.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
            if (l.size() > 4) {
               l.clear();
            }

            l.put(token, all);
         }
      }
   }

   public static byte[] takeLayout(ServerPlayer sp, int token) {
      Map<Integer, byte[]> l = LAYOUTS.get(sp.getUUID());
      return l == null ? null : l.remove(token);
   }

   public static void forget(ServerPlayer sp) {
      PENDING.remove(sp.getUUID());
      LAYOUTS.remove(sp.getUUID());
   }
}
