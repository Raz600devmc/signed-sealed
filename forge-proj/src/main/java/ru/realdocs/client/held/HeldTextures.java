package ru.realdocs.client.held;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;

public final class HeldTextures {
   private static final int MAX = 24;
   private static final Map<String, PageTexture> CACHE = new LinkedHashMap<>(16, 0.75F, true);
   private static long lastFrame = -1L;
   private static long frame;

   private HeldTextures() {
   }

   public static void tick() {
      frame++;
   }

   private static ResourceLocation get(String key, Supplier<BufferedImage> painter) {
      PageTexture t = CACHE.get(key);
      if (t != null && t.ready()) {
         return t.id();
      } else if (lastFrame == frame) {
         return null;
      } else {
         lastFrame = frame;

         BufferedImage img;
         try {
            img = painter.get();
         } catch (Exception var5) {
            return null;
         }

         if (img == null) {
            return null;
         } else {
            if (t == null) {
               t = new PageTexture();
               CACHE.put(key, t);
            }

            t.set(img);

            while (CACHE.size() > 24) {
               Iterator<Entry<String, PageTexture>> it = CACHE.entrySet().iterator();
               it.next().getValue().close();
               it.remove();
            }

            return t.id();
         }
      }
   }

   public static void clear() {
      for (PageTexture t : CACHE.values()) {
         t.close();
      }

      CACHE.clear();
   }

   private static String ver() {
      return StampCache.version + ":" + PassportCache.version;
   }

   public static Doc3D.Model model(ItemStack stack, boolean open) {
      if (stack.getItem() instanceof DocumentItem) {
         DocModel d = NbtCodec.readDoc(stack);
         String key = "doc:" + d.canonical().hashCode() + ":" + ver();
         ResourceLocation front = get(key, () -> DocRenderer.render(d, StampCache.INSTANCE, 420).image());
         return Doc3D.Model.sheet(0.62F, 0.877F, front, null, -725022);
      } else if (stack.getItem() instanceof PassportItem) {
         PassportModel p = NbtCodec.readPass(stack);
         PassportTemplate t = PassportCache.INSTANCE.template(p.templateId);
         String base = "pass:" + p.canonical().hashCode() + ":" + ver();
         int fmt = t == null ? p.format : t.format;
         switch (fmt) {
            case 1: {
               ResourceLocation f = t == null
                  ? null
                  : get(base + ":f", () -> PassportRenderer.renderSpread(t, p, PassportCache.INSTANCE, StampCache.INSTANCE, 512, false, 0, 0));
               ResourceLocation b = t == null
                  ? null
                  : get(base + ":b", () -> PassportRenderer.renderSpread(t, p, PassportCache.INSTANCE, StampCache.INSTANCE, 512, false, 1, 0));
               return Doc3D.Model.card(f, b, t == null ? -1117964 : 0xFF000000 | t.bg);
            }
            case 2:
            case 3: {
               float w = (float)PassportTemplate.width(fmt) / 640.0F * 0.62F;
               float h = (float)PassportTemplate.height(fmt) / 640.0F * 0.62F;
               ResourceLocation f = t == null
                  ? null
                  : get(base + ":f", () -> PassportRenderer.renderSpread(t, p, PassportCache.INSTANCE, StampCache.INSTANCE, 512, false, 0, 0));
               return Doc3D.Model.sheet(w, h, f, null, t == null ? -791591 : 0xFF000000 | t.bg);
            }
            default:
               int cover = t == null ? -14730605 : 0xFF000000 | t.coverColor;
               ResourceLocation c = t == null ? null : get(base + ":c", () -> PassportRenderer.renderCover(t, PassportCache.INSTANCE, 300));
               ResourceLocation s = open && t != null
                  ? get(base + ":s", () -> PassportRenderer.renderSpread(t, p, PassportCache.INSTANCE, StampCache.INSTANCE, 900, false, 0, 0))
                  : null;
               return Doc3D.Model.book(c, s, cover, t == null ? -1186094 : 0xFF000000 | t.bg);
         }
      } else if (stack.getItem() instanceof ru.realdocs.item.NewspaperItem) {
         ru.realdocs.data.PaperCodec.Ref r = ru.realdocs.data.PaperCodec.ref(stack);
         ru.realdocs.model.PaperModel pm = r.id.isEmpty() ? null : ru.realdocs.client.PaperCache.INSTANCE.get(r.id);
         int tone = 0xFF000000 | ru.realdocs.model.PaperModel.TONE_RGB[pm == null ? 0 : Math.max(0, Math.min(4, pm.tone))];
         float w = 0.62F;
         float h = w * ru.realdocs.model.PaperModel.H / ru.realdocs.model.PaperModel.W;
         ResourceLocation front = pm == null
            ? null
            : get("paper:" + r.id + ":" + ru.realdocs.client.PaperCache.version + ":" + ver(), () -> ru.realdocs.client.render.PaperRenderer.render(pm, 0, 460, ru.realdocs.client.PassportCache.INSTANCE::image, false).image);
         ResourceLocation back = pm == null || pm.pageCount() < 2
            ? null
            : get("paper:" + r.id + ":b:" + ru.realdocs.client.PaperCache.version + ":" + ver(), () -> ru.realdocs.client.render.PaperRenderer.render(pm, pm.pageCount() - 1, 460, ru.realdocs.client.PassportCache.INSTANCE::image, false).image);
         Doc3D.Model m = Doc3D.Model.sheet(w, h, front, back, tone);
         m.thick = 0.012F + Math.min(0.03F, 0.004F * (pm == null ? r.pages : pm.pageCount()));
         return m;
      } else if (stack.getItem() instanceof ru.realdocs.item.EnvelopeItem) {
         ru.realdocs.model.MailModel mm = ru.realdocs.data.MailCodec.readMail(stack);
         String base = "mail:" + mm.id + ":" + mm.to + ":" + mm.from + ":" + mm.state + ":" + mm.wax + ":" + mm.font + ":" + mm.postmark + ":" + ver();
         ResourceLocation f = get(base + ":f", () -> ru.realdocs.client.render.MailRenderer.front(mm, 420));
         ResourceLocation b = get(base + ":b", () -> ru.realdocs.client.render.MailRenderer.back(mm, 420, StampCache.INSTANCE));
         Doc3D.Model m = Doc3D.Model.sheet(0.62F, 0.62F * ru.realdocs.client.render.MailRenderer.EH / ru.realdocs.client.render.MailRenderer.EW, f, b, -1055539);
         m.thick = 0.014F;
         return m;
      } else if (stack.getItem() instanceof ru.realdocs.item.FolderItem) {
         ru.realdocs.model.FolderModel fm = ru.realdocs.data.MailCodec.readFolder(stack);
         int n = ru.realdocs.data.MailCodec.count(stack, ru.realdocs.data.MailCodec.FOLDER_TAG);
         ResourceLocation c = get("folder:" + fm.label + ":" + fm.color + ":" + n, () -> ru.realdocs.client.render.MailRenderer.folder(fm, n, 300));
         Doc3D.Model m = Doc3D.Model.book(c, null, 0xFF000000 | fm.rgb(), -1186094);
         m.w = 0.46F;
         m.h = 0.62F;
         m.thick = 0.02F + Math.min(0.05F, 0.004F * n);
         return m;
      } else {
         return null;
      }
   }
}
