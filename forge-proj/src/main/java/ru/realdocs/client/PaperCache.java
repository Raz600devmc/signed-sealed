package ru.realdocs.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import ru.realdocs.Plat;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.model.PaperModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/** Newspaper layouts fetched from the server by id. */
public final class PaperCache {
   public static final PaperCache INSTANCE = new PaperCache();
   public static volatile int version = 0;
   private final Map<String, PaperModel> models = new HashMap<>();
   private final Set<String> revoked = new HashSet<>();
   private final Set<String> missing = new HashSet<>();
   private final Set<String> requested = new HashSet<>();

   private PaperCache() {
   }

   public PaperModel get(String id) {
      if (id != null && !id.isEmpty()) {
         PaperModel m = this.models.get(id);
         if (m == null && !this.missing.contains(id) && this.requested.add(id)) {
            Net.sendToServer(new MorePackets.PaperRequest(id));
         }

         return m;
      } else {
         return null;
      }
   }

   public boolean missing(String id) {
      return this.missing.contains(id);
   }

   public boolean revoked(String id) {
      return this.revoked.contains(id);
   }

   /** Re-download (drafts change while being edited). */
   public void refresh(String id) {
      if (id != null && !id.isEmpty()) {
         this.requested.remove(id);
         this.missing.remove(id);
         this.models.remove(id);
         this.get(id);
      }
   }

   public void put(String id, boolean exists, byte[] data, boolean rev) {
      PaperModel m = null;
      if (exists && data != null && data.length > 0) {
         try {
            m = PaperCodec.read(Plat.unpack(data, 6000000L));
         } catch (Exception var7) {
            m = null;
         }
      }

      if (m == null) {
         this.missing.add(id);
      } else {
         this.models.put(id, m);
         this.missing.remove(id);
      }

      if (rev) {
         this.revoked.add(id);
      } else {
         this.revoked.remove(id);
      }

      version++;
   }

   /** The editor's own copy after a successful save. */
   public void local(PaperModel m) {
      if (!m.id.isEmpty()) {
         this.models.put(m.id, m.copy());
         this.missing.remove(m.id);
         this.requested.add(m.id);
         version++;
      }
   }

   public void clear() {
      this.models.clear();
      this.revoked.clear();
      this.missing.clear();
      this.requested.clear();
      version++;
   }
}
