package ru.realdocs.client.screen;

import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.StampRenderer;
import ru.realdocs.data.Tags;
import ru.realdocs.item.StampItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.StampModel;

public final class StampTool {
   public String activeId = "";
   public int rot = 0;
   public int scale = 100;
   public int dir = 1;
   public int cat = 0;
   public int days = 30;
   public static final long DOWN_MS = 150L;
   public static final long UP_END_MS = 360L;
   public static final long FADE_MS = 260L;
   public static final long KEEP_MS = 3500L;
   public final List<StampTool.Anim> anims = new ArrayList<>();
   private final Map<String, PageTexture> textures = new HashMap<>();
   private final Map<String, Integer> texVersion = new HashMap<>();

   public int kind() {
      StampModel m = this.active() ? StampCache.INSTANCE.def(this.activeId) : null;
      return m == null ? -1 : m.kind;
   }

   private int paramKey() {
      return this.dir * 1000 + this.cat * 100 + this.days;
   }

   public boolean active() {
      return !this.activeId.isEmpty();
   }

   public void cancel() {
      this.activeId = "";
   }

   public void select(String id) {
      this.activeId = id;
      this.rot = 0;
      this.scale = 100;
   }

   public void rotate(int d) {
      this.rot = ((this.rot + d + 180) % 360 + 360) % 360 - 180;
   }

   public void resize(int d) {
      this.scale = Math.max(50, Math.min(200, this.scale + d));
   }

   public static Map<String, String> inventoryStamps(Player p) {
      Map<String, String> out = new LinkedHashMap<>();
      Inventory inv = p.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.getItem() instanceof StampItem && Tags.has(st)) {
            String id = Tags.get(st).getString("StampId");
            if (!id.isEmpty()) {
               out.putIfAbsent(id, Tags.get(st).getString("StampOrg"));
            }
         }
      }

      return out;
   }

   public PageTexture texture(String id) {
      StampModel m = StampCache.INSTANCE.def(id);
      if (m == null) {
         return null;
      } else {
         PageTexture t = this.textures.get(id);
         Integer ver = this.texVersion.get(id);
         int key = StampCache.version * 100003 + (m.kind == 0 ? 0 : this.paramKey());
         if (t == null || ver == null || ver != key) {
            if (t == null) {
               t = new PageTexture();
               this.textures.put(id, t);
            }

            BufferedImage img = m.hasImage ? StampCache.INSTANCE.image(id) : null;
            DocModel.StampRef ref = new DocModel.StampRef()
               .extra(this.dir, this.cat, "", LocalDate.now().plusDays((long)this.days).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), "");
            t.set(StampRenderer.render(m, img, m.kind == 2 ? 420 : 256, ref));
            this.texVersion.put(id, key);
         }

         return t;
      }
   }

   public void place(String id, int nx, int ny, int pendingBefore) {
      this.anims.add(new StampTool.Anim(id, nx, ny, this.rot, this.scale, System.currentTimeMillis(), pendingBefore));
   }

   public void prune(int stampCount) {
      long now = System.currentTimeMillis();
      this.anims.removeIf(a -> now - a.start > 3500L || stampCount > a.countBefore && now - a.start > 360L);
   }

   public boolean animating() {
      long now = System.currentTimeMillis();

      for (StampTool.Anim a : this.anims) {
         if (now - a.start < 360L) {
            return true;
         }
      }

      return false;
   }

   public void drawPending(GuiGraphics g, PageView v, double pageUnitsW) {
      long now = System.currentTimeMillis();

      for (StampTool.Anim a : this.anims) {
         long t = now - a.start;
         if (t >= 150L) {
            PageTexture tex = this.texture(a.id);
            StampModel m = StampCache.INSTANCE.def(a.id);
            if (tex != null && m != null) {
               float k = (float)Math.min(1.0, (double)(t - 150L) / 260.0);
               float alpha = 0.93F * k;
               float spread = 1.06F - 0.06F * k;
               this.draw(g, v, pageUnitsW, tex, m, v.sx(a.nx), v.sy(a.ny), (double)a.rot, (double)((float)a.scale * spread), alpha);
            }
         }
      }
   }

   public void drawDevice(GuiGraphics g, PageView v, double pageUnitsW, int mx, int my) {
      if (this.active()) {
         PageTexture tex = this.texture(this.activeId);
         StampModel m = StampCache.INSTANCE.def(this.activeId);
         long now = System.currentTimeMillis();
         StampTool.Anim last = this.anims.isEmpty() ? null : this.anims.get(this.anims.size() - 1);
         long t = last == null ? Long.MAX_VALUE : now - last.start;
         boolean locked = last != null && t < 360L;
         double cx = locked ? v.sx(last.nx) : (double)mx;
         double cy = locked ? v.sy(last.ny) : (double)my;
         int drot = locked ? last.rot : this.rot;
         int dscale = locked ? last.scale : this.scale;
         double lift;
         if (!locked) {
            lift = v.contains((double)mx, (double)my) ? 14.0 : 0.0;
         } else if (t < 150L) {
            lift = 14.0 * (1.0 - easeIn((double)t / 150.0));
         } else {
            lift = 14.0 * easeOut((double)(t - 150L) / 210.0);
         }

         double stampPx = m == null ? 80.0 : DocRenderer.stampWidth(m) * ((double)dscale / 100.0) * ((double)v.w / pageUnitsW);
         double hPx = tex == null ? stampPx : stampPx * (double)tex.h / (double)tex.w;
         int shadowA = (int)(60.0 * (1.0 - Math.min(1.0, lift / 18.0) * 0.6));
         int sw = (int)(stampPx * (0.55 + lift / 60.0));
         int sh = (int)Math.max(3.0, hPx * 0.18);
         g.fill((int)(cx - (double)sw / 2.0 + lift), (int)(cy + lift * 0.9), (int)(cx + (double)sw / 2.0 + lift), (int)(cy + lift * 0.9) + sh, shadowA << 24);
         if (tex != null && m != null && !locked && v.contains((double)mx, (double)my)) {
            this.draw(g, v, pageUnitsW, tex, m, cx, cy, (double)drot, (double)dscale, 0.45F);
         }

         int bw = (int)Math.max(26.0, Math.min(stampPx * 0.62, 70.0));
         int plateY = (int)(cy - lift - Math.min(hPx / 2.0, 40.0) - 2.0);
         int x0 = (int)cx - bw / 2;
         g.fill(x0 - 3, plateY, x0 + bw + 3, plateY + 7, -10919829);
         g.fill(x0 - 3, plateY, x0 + bw + 3, plateY + 2, -7432283);
         int nx0 = (int)cx - 5;
         g.fill(nx0, plateY - 12, nx0 + 10, plateY, -9748962);
         int kw = (int)((double)bw * 0.7);
         int kx = (int)cx - kw / 2;
         g.fill(kx, plateY - 34, kx + kw, plateY - 12, -7711704);
         g.fill(kx, plateY - 34, kx + kw, plateY - 31, -4883902);
         g.fill(kx, plateY - 15, kx + kw, plateY - 12, -10602728);
      }
   }

   private void draw(
      GuiGraphics g, PageView v, double pageUnitsW, PageTexture tex, StampModel m, double cx, double cy, double rotDeg, double scalePct, float alpha
   ) {
      double w = DocRenderer.stampWidth(m) * (scalePct / 100.0) * ((double)v.w / pageUnitsW);
      double h = w * (double)tex.h / (double)tex.w;
      g.pose().pushPose();
      g.pose().translate(cx, cy, 0.0);
      g.pose().mulPose(Axis.ZP.rotationDegrees((float)rotDeg));
      g.setColor(1.0F, 1.0F, 1.0F, alpha);
      g.blit(tex.id(), (int)Math.round(-w / 2.0), (int)Math.round(-h / 2.0), (int)Math.round(w), (int)Math.round(h), 0.0F, 0.0F, tex.w, tex.h, tex.w, tex.h);
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      g.pose().popPose();
   }

   private static double easeIn(double x) {
      x = Math.max(0.0, Math.min(1.0, x));
      return x * x;
   }

   private static double easeOut(double x) {
      x = Math.max(0.0, Math.min(1.0, x));
      return 1.0 - (1.0 - x) * (1.0 - x);
   }

   public void close() {
      for (PageTexture t : this.textures.values()) {
         t.close();
      }

      this.textures.clear();
      this.texVersion.clear();
   }

   public PageTexture thumb(String id) {
      return this.texture(id);
   }

   public Minecraft mc() {
      return Minecraft.getInstance();
   }

   public static final class Anim {
      final String id;
      final int nx;
      final int ny;
      final int rot;
      final int scale;
      final long start;
      final int countBefore;

      Anim(String id, int nx, int ny, int rot, int scale, long start, int countBefore) {
         this.id = id;
         this.nx = nx;
         this.ny = ny;
         this.rot = rot;
         this.scale = scale;
         this.start = start;
         this.countBefore = countBefore;
      }
   }
}
