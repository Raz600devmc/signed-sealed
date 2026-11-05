package ru.realdocs.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class PassportCache implements PassportRenderer.Assets {
   public static final PassportCache INSTANCE = new PassportCache();
   public static volatile int version = 0;
   private final Map<String, PassportTemplate> templates = new HashMap<>();
   private final Set<String> missingTemplates = new HashSet<>();
   private final Set<String> requestedTemplates = new HashSet<>();
   private final Map<String, BufferedImage> assets = new HashMap<>();
   private final Set<String> missingAssets = new HashSet<>();
   private final Set<String> requestedAssets = new HashSet<>();

   private PassportCache() {
   }

   public PassportTemplate template(String id) {
      if (id != null && !id.isEmpty()) {
         PassportTemplate t = this.templates.get(id);
         if (t == null && !this.missingTemplates.contains(id) && this.requestedTemplates.add(id)) {
            Net.sendToServer(new Packets.TemplateRequest(id));
         }

         return t;
      } else {
         return null;
      }
   }

   public boolean templateMissing(String id) {
      return this.missingTemplates.contains(id);
   }

   @Override
   public BufferedImage image(String id) {
      if (id != null && !id.isEmpty()) {
         BufferedImage img = this.assets.get(id);
         if (img == null && !this.missingAssets.contains(id) && this.requestedAssets.add(id)) {
            Net.sendToServer(new Packets.AssetRequest(id));
         }

         return img;
      } else {
         return null;
      }
   }

   public void putTemplate(String id, boolean exists, PassportTemplate t) {
      if (exists) {
         this.templates.put(id, t);
      } else {
         this.missingTemplates.add(id);
      }

      version++;
   }

   public void putAsset(String id, boolean exists, byte[] png) {
      if (exists && png != null && png.length != 0) {
         try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img != null) {
               this.assets.put(id, img);
            } else {
               this.missingAssets.add(id);
            }
         } catch (IOException var5) {
            this.missingAssets.add(id);
         }
      } else {
         this.missingAssets.add(id);
      }

      version++;
   }

   public void forgetMissing(String id) {
      if (id != null && !id.isEmpty()) {
         if (this.missingAssets.remove(id)) {
            this.requestedAssets.remove(id);
         }
      }
   }

   public void clear() {
      this.templates.clear();
      this.missingTemplates.clear();
      this.requestedTemplates.clear();
      this.assets.clear();
      this.missingAssets.clear();
      this.requestedAssets.clear();
      version++;
   }
}
