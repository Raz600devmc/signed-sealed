package ru.realdocs.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import ru.realdocs.L;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class StampCache implements DocRenderer.StampSource {
   public static final StampCache INSTANCE = new StampCache();
   public static volatile int version = 0;
   private final Map<String, StampModel> defs = new HashMap<>();
   private final Map<String, BufferedImage> images = new HashMap<>();
   private final Set<String> requested = new HashSet<>();

   private StampCache() {
   }

   @Override
   public StampModel def(String id) {
      if (id != null && !id.isEmpty()) {
         StampModel m = this.defs.get(id);
         if (m == null && this.requested.add(id)) {
            Net.sendToServer(new Packets.StampRequest(id));
         }

         return m;
      } else {
         return null;
      }
   }

   @Override
   public BufferedImage image(String id) {
      return this.images.get(id);
   }

   public void put(String id, boolean exists, StampModel m, byte[] png) {
      if (!exists) {
         StampModel t = new StampModel();
         t.id = id;
         t.org = L.t("неизвестная печать");
         t.top = L.t("ПЕЧАТЬ НЕ НАЙДЕНА");
         t.color = 7829367;
         t.approved = false;
         this.defs.put(id, t);
      } else {
         this.defs.put(id, m);
         if (png != null && png.length > 0) {
            try {
               BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
               if (img != null) {
                  this.images.put(id, img);
               }
            } catch (IOException var6) {
            }
         }
      }

      version++;
   }

   public void clear() {
      this.defs.clear();
      this.images.clear();
      this.requested.clear();
      version++;
   }
}
