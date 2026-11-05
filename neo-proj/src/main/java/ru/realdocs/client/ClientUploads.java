package ru.realdocs.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.Uploads;

/** Sends large payloads in chunks and routes the server's answer back to the caller. */
public final class ClientUploads {
   public interface Callback {
      void done(boolean ok, String id, String msg);
   }

   private static final Map<Integer, Callback> WAITING = new HashMap<>();
   private static int next = ThreadLocalRandom.current().nextInt(1, 1000000);

   private ClientUploads() {
   }

   public static int send(int kind, byte[] data, Callback cb) {
      int token = ++next;
      if (cb != null) {
         WAITING.put(token, cb);
      }

      int total = Math.max(1, (data.length + Uploads.CHUNK - 1) / Uploads.CHUNK);

      for (int i = 0; i < total; i++) {
         int from = i * Uploads.CHUNK;
         int to = Math.min(data.length, from + Uploads.CHUNK);
         Net.sendToServer(new MorePackets.Upload(token, kind, i, total, Arrays.copyOfRange(data, from, to)));
      }

      return token;
   }

   /** Registers a callback for a token whose answer comes from another packet (layout commit). */
   public static void expect(int token, Callback cb) {
      WAITING.put(token, cb);
   }

   static void result(int token, boolean ok, String id, String msg) {
      Callback cb = WAITING.remove(token);
      if (cb != null) {
         cb.done(ok, id, msg);
      }
   }

   public static void clear() {
      WAITING.clear();
   }
}
