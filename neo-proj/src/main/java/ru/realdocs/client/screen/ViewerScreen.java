package ru.realdocs.client.screen;

import com.mojang.math.Axis;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.ui.Desk;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiButton;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.model.DocModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public abstract class ViewerScreen extends UiScreen {
   protected final PageView view = new PageView();
   protected final StampTool tool = new StampTool();
   protected final InteractionHand hand;
   protected final boolean canStamp;
   protected int verifyStatus = -1;
   protected final List<String> infoLines = new ArrayList<>();
   protected boolean showInfo = true;
   protected String subtitle = "";
   protected int pad = 12;
   protected int panelX;
   protected int panelW = 170;
   protected int nextY;
   protected int cardY;
   protected int cardH;
   private UiButton stampBtn;
   private boolean trayOpen;
   private int renderedKey = Integer.MIN_VALUE;
   private double laidAspect = -1.0;
   protected boolean uvOn;
   protected boolean loupe;
   private int paramsY = -1;
   private boolean opened;
   private boolean needRebuild;
   private long verifyAt;

   protected ViewerScreen(Component title, InteractionHand hand, boolean canStamp) {
      super(title);
      this.hand = hand;
      this.canStamp = canStamp;
   }

   protected abstract BufferedImage renderPage(int var1);

   protected abstract double aspect();

   protected abstract double pageUnitsW();

   protected abstract int contentKey();

   protected abstract int stampCount();

   protected boolean stampingAllowed() {
      return true;
   }

   protected String marksError() {
      return L.t("Погранштампы и визы ставятся только в паспорт.");
   }

   protected void addPanelButtons() {
   }

   protected boolean hasSidePanel() {
      return true;
   }

   protected void renderUnder(GuiGraphics g, int mx, int my) {
   }

   protected void renderOver(GuiGraphics g, int mx, int my) {
   }

   protected int currentPage() {
      return 0;
   }

   protected boolean hasSecurity() {
      return false;
   }

   protected List<double[]> holograms() {
      return List.of();
   }

   protected int uvMode() {
      if (!this.uvOn) {
         return 0;
      } else {
         return this.verifyStatus == 2 ? 1 : 2;
      }
   }

   protected void addSecurityButtons() {
      this.addBtn(L.t("УФ-свет"), () -> {
         this.uvOn = !this.uvOn;
         if (this.uvOn && this.verifyStatus != 2) {
            this.requestVerify();
         }

         this.rebuildWidgets();
      }).selected(this.uvOn).hint("U");
      this.addBtn(L.t("Лупа"), () -> {
         this.loupe = !this.loupe;
         this.rebuildWidgets();
      }).selected(this.loupe).hint("L");
   }

   private void addMarkParams() {
      int kind = this.tool.kind();
      if (kind == 1 || kind == 2) {
         int x = this.panelX + 8;
         int w = this.panelW - 16;
         this.nextY += 6;
         this.paramsY = this.nextY;
         this.nextY += 12;
         if (kind == 1) {
            int half = (w - 4) / 2;
            this.button(L.t("Въезд"), x, this.nextY, half, 18, () -> {
               this.tool.dir = 1;
               this.rebuildWidgets();
            }).selected(this.tool.dir != 2);
            this.button(L.t("Выезд"), x + half + 4, this.nextY, w - half - 4, 18, () -> {
               this.tool.dir = 2;
               this.rebuildWidgets();
            }).selected(this.tool.dir == 2);
            this.nextY += 22;
         }

         String[] cats = DocModel.StampRef.CATEGORIES_UI;
         int cw = (w - 4) / 2;

         for (int i = 0; i < cats.length; i++) {
            int k = i;
            int col = i % 2;
            int row = i / 2;
            this.button(cats[i], x + col * (cw + 4), this.nextY + row * 20, i == cats.length - 1 && col == 0 ? w : cw, 18, () -> {
               this.tool.cat = k;
               this.rebuildWidgets();
            }).selected(this.tool.cat == i);
         }

         this.nextY += (cats.length + 1) / 2 * 20 + 2;
         if (kind == 2) {
            int[] days = DocModel.StampRef.VISA_DAYS;
            int dw = (w - 2 * (days.length - 1)) / days.length;

            for (int i = 0; i < days.length; i++) {
               int d = days[i];
               this.button(String.valueOf(d), x + i * (dw + 2), this.nextY + 12, dw, 18, () -> {
                  this.tool.days = d;
                  this.rebuildWidgets();
               }).selected(this.tool.days == d);
            }

            this.nextY += 34;
         }
      }
   }

   protected double groupAspect() {
      return this.aspect();
   }

   protected String panelTitle() {
      return this.title.getString();
   }

   protected void init() {
      if (!this.opened) {
         this.opened = true;
         Sounds.play(Sounds.PAPER, 1.0F, 0.7F);
      }

      this.panelW = Math.max(150, Math.min(200, this.width / 4));
      this.panelX = this.width - this.panelW - this.pad;
      this.needRebuild = false;
      this.cardY = this.pad;
      this.cardH = this.height - 2 * this.pad;
      this.relayout();
      this.nextY = this.cardY + 28;
      if (this.canStamp) {
         this.stampBtn = this.addBtn(L.t("Взять печать"), () -> {
            if (this.stampingAllowed()) {
               this.trayOpen = !this.trayOpen;
            }
         });
         this.stampBtn.primary().hint("S");
      }

      this.addPanelButtons();
      this.paramsY = -1;
      if (this.tool.active()) {
         this.addMarkParams();
      }

      this.nextY += 4;
      this.addBtn(L.t("Закрыть"), this::onClose).ghost();
   }

   protected void relayout() {
      this.laidAspect = this.aspect();
      int defPanelX = this.width - this.panelW - this.pad;
      this.view.layout(this.pad, this.pad + 16, defPanelX - 2 * this.pad, this.height - 2 * this.pad - 30, this.laidAspect);
      if (this.hasSidePanel()) {
         int gap = 18;
         PageView probe = new PageView();
         probe.layout(this.pad, this.pad + 16, defPanelX - 2 * this.pad, this.height - 2 * this.pad - 30, this.groupAspect());
         int groupW = Math.max(this.view.w, probe.w);
         int total = groupW + gap + this.panelW;
         int startX = Math.max(this.pad, (this.width - total) / 2);
         this.view.x = startX + (groupW - this.view.w) / 2;
         int newPanelX = Math.min(defPanelX, startX + groupW + gap);
         if (newPanelX != this.panelX) {
            if (!this.children().isEmpty()) {
               this.needRebuild = true;
            }

            this.panelX = newPanelX;
         }
      }
   }

   protected UiButton addBtn(String label, Runnable action) {
      UiButton b = this.button(label, this.panelX + 8, this.nextY, this.panelW - 16, action);
      this.nextY += 24;
      return b;
   }

   public void setVerify(int status, List<String> lines) {
      if (this.verifyStatus != status || this.verifyStatus < 0) {
         this.verifyAt = System.currentTimeMillis();
         Sounds.play(Sounds.PAPER, 1.2F, 0.5F);
      }

      this.verifyStatus = status;
      this.infoLines.clear();
      this.infoLines.addAll(lines);
      this.showInfo = true;
   }

   protected void requestVerify() {
      Net.sendToServer(new Packets.Verify(this.hand));
      this.verifyStatus = -2;
      this.verifyAt = System.currentTimeMillis();
   }

   protected static String statusTitle(int s) {
      return switch (s) {
         case -2 -> L.t("Проверка…");
         default -> L.t("Не документ");
         case 1 -> L.t("Не выпущен");
         case 2 -> L.t("Подлинный");
         case 3 -> L.t("Подделка");
         case 4 -> L.t("Нет в реестре");
         case 5 -> L.t("Отозван");
         case 6 -> L.t("Устаревшая копия");
         case 7 -> L.t("Копия");
      };
   }

   protected static int statusColor(int s) {
      return switch (s) {
         case -2 -> -6253953;
         default -> -868018;
         case 2 -> -10628481;
         case 3, 4, 5 -> -1088669;
         case 6, 7 -> -24504;
      };
   }

   protected static int statusInk(int s) {
      return switch (s) {
         case -2 -> -9740206;
         default -> -6656498;
         case 2 -> -14452406;
         case 3, 4, 5 -> -5232086;
         case 6, 7 -> -4693990;
      };
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      if (this.aspect() != this.laidAspect) {
         this.relayout();
      }

      if (this.needRebuild) {
         this.needRebuild = false;
         this.rebuildWidgets();
      }

      this.header(g, this.title.getString(), this.subtitle, this.view.x, this.view.y - 16);
      int key = this.contentKey() * 31 + StampCache.version * 17 + PassportCache.version + this.uvMode() * 1009;
      if (key != this.renderedKey || !this.view.tex.ready()) {
         this.renderedKey = key;
         BufferedImage img = this.renderPage(1400);
         if (img != null) {
            this.view.tex.set(img);
         }
      }

      Desk.paperShadow(g, this.view.x, this.view.y, this.view.w, this.view.h);
      this.view.draw(g);
      this.drawHolograms(g, mx, my);
      this.tool.prune(this.stampCount());
      this.tool.drawPending(g, this.view, this.pageUnitsW());
      if (this.hasSidePanel()) {
         Ui.card(g, this.font, this.panelX, this.cardY, this.panelW, this.cardH, this.panelTitle());
         this.drawVerify(g);
         if (this.paramsY >= 0) {
            int k = this.tool.kind();
            Ui.section(g, this.font, k == 2 ? L.t("Виза: тип и срок (дней)") : L.t("Отметка о пересечении"), this.panelX + 8, this.paramsY, this.panelW - 16);
         }
      }

      this.renderUnder(g, mx, my);
      super.render(g, mx, my, pt);
      this.renderOver(g, mx, my);
      if (this.loupe && !this.tool.active() && this.view.contains((double)mx, (double)my)) {
         this.drawLoupe(g, mx, my);
      }

      if (this.trayOpen) {
         this.drawTray(g, mx, my);
      }

      if (this.tool.active()) {
         String hint = L.t("ЛКМ — поставить   Колесо / Q E — повернуть   Ctrl+колесо — размер   ПКМ — отмена");
         int w = this.font.width(hint) + 20;
         int x = this.view.x + (this.view.w - w) / 2;
         int y = this.view.y + this.view.h + 8;
         Ui.round(g, x, y, w, 16, -534898670);
         Ui.outline(g, x, y, w, 16, Ui.alpha(-2641559, 0.5F));
         g.drawString(this.font, hint, x + 10, y + 4, -3555408, false);
         this.tool.drawDevice(g, this.view, this.pageUnitsW(), mx, my);
      }

      this.renderToast(g);
   }

   private void drawLoupe(GuiGraphics g, int mx, int my) {
      if (this.view.tex.ready()) {
         int size = 120;
         double zoom = 3.2;
         int bx = mx + 14;
         int by = my - size - 10;
         if (bx + size > this.width - 4) {
            bx = mx - size - 14;
         }

         if (by < 4) {
            by = my + 14;
         }

         double fx = (double)(mx - this.view.x) / (double)this.view.w;
         double fy = (double)(my - this.view.y) / (double)this.view.h;
         double texPerScreen = (double)this.view.tex.w / (double)this.view.w;
         double srcW = (double)size * texPerScreen / zoom;
         double srcH = (double)size * texPerScreen / zoom;
         double u = fx * (double)this.view.tex.w - srcW / 2.0;
         double v = fy * (double)this.view.tex.h - srcH / 2.0;
         Ui.shadow(g, bx, by, size, size, 5);
         g.fill(bx - 2, by - 2, bx + size + 2, by + size + 2, -12964318);
         g.enableScissor(bx, by, bx + size, by + size);
         g.fill(bx, by, bx + size, by + size, -1185572);
         int iu = (int)Math.floor(u);
         int iv = (int)Math.floor(v);
         int sw = (int)Math.ceil(srcW) + 2;
         int sh = (int)Math.ceil(srcH) + 2;
         double scale = (double)size / srcW;
         int dx = bx - (int)Math.round((u - (double)iu) * scale);
         int dy = by - (int)Math.round((v - (double)iv) * scale);
         g.blit(
            this.view.tex.id(),
            dx,
            dy,
            (int)Math.round((double)sw * scale),
            (int)Math.round((double)sh * scale),
            (float)iu,
            (float)iv,
            sw,
            sh,
            this.view.tex.w,
            this.view.tex.h
         );
         g.disableScissor();
         Ui.outline(g, bx - 1, by - 1, size + 2, size + 2, -2641559);
         g.fill(bx + size / 2 - 3, by + size / 2, bx + size / 2 + 4, by + size / 2 + 1, 1627336752);
         g.fill(bx + size / 2, by + size / 2 - 3, bx + size / 2 + 1, by + size / 2 + 4, 1627336752);
      }
   }

   private void drawHolograms(GuiGraphics g, int mx, int my) {
      if (!this.uvOn) {
         long t = System.currentTimeMillis();

         for (double[] h : this.holograms()) {
            int x = (int)((double)this.view.x + h[0] * (double)this.view.w);
            int y = (int)((double)this.view.y + h[1] * (double)this.view.h);
            int w = Math.max(4, (int)(h[2] * (double)this.view.w));
            int hh = Math.max(4, (int)(h[3] * (double)this.view.h));
            double phase = (double)(t % 2600L) / 2600.0 + (double)(mx - this.view.x) / (double)Math.max(1, this.view.w) * 0.8;
            phase -= Math.floor(phase);
            int band = Math.max(3, w / 3);
            int bx = x - band + (int)(phase * (double)(w + 2 * band));
            g.enableScissor(x, y, x + w, y + hh);

            for (int i = -band; i <= band; i++) {
               float a = 1.0F - (float)Math.abs(i) / (float)band;
               int col = Ui.alpha(i < 0 ? -4656385 : -6409, a * 0.55F);
               g.fill(bx + i, y, bx + i + 1, y + hh, col);
            }

            g.disableScissor();
         }
      }
   }

   private void drawVerify(GuiGraphics g) {
      if (this.verifyStatus != -1) {
         int x = this.panelX + 6;
         int w = this.panelW - 12;
         int top = Math.max(this.nextY + 8, this.cardY + 40);
         int bottom = this.cardY + this.cardH - 6;
         if (bottom - top >= 46) {
            int ink = statusInk(this.verifyStatus);
            List<String> lines = new ArrayList<>();
            if (this.showInfo) {
               for (String l : this.infoLines) {
                  lines.addAll(Ui.wrap(this.font, l, w - 16));
               }
            }

            int maxLines = Math.max(0, (bottom - top - 44) / 10);
            boolean cut = lines.size() > maxLines;
            if (cut) {
               lines = new ArrayList<>(lines.subList(0, Math.max(0, maxLines - 1)));
            }

            int h = 40 + lines.size() * 10 + (cut ? 10 : 0);
            float age = Math.min(1.0F, (float)(System.currentTimeMillis() - this.verifyAt) / 260.0F);
            float ease = 1.0F - (1.0F - age) * (1.0F - age);
            int y = bottom - (int)((float)h * ease);
            g.enableScissor(x - 4, top, x + w + 6, bottom + 6);
            Desk.paperShadow(g, x, y, w, h);
            int paper = -856867;
            g.fill(x, y + 3, x + w, y + h, paper);

            for (int i = 0; i < w; i += 4) {
               int d = (i * 7919 + 13) % 3;
               g.fill(x + i, y + d, x + Math.min(w, i + 4), y + 3, paper);
            }

            g.fill(x, y + h - 1, x + w, y + h, 570425344);

            for (int ly = y + 38; ly < y + h - 6; ly += 10) {
               g.fill(x + 6, ly + 8, x + w - 6, ly + 9, 338706528);
            }

            g.drawString(this.font, L.t("СПРАВКА О ПРОВЕРКЕ"), x + 8, y + 7, -8753570, false);
            String st = statusTitle(this.verifyStatus).toUpperCase(Locale.ROOT);
            int sw = this.font.width(st) + 12;
            g.pose().pushPose();
            g.pose().translate((float)(x + 8) + (float)sw / 2.0F, (float)(y + 24), 0.0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-3.0F));
            float sc = Math.min(1.0F, (float)(w - 20) / (float)sw);
            g.pose().scale(sc, sc, 1.0F);
            int a = Ui.alpha(ink, 0.85F);
            Ui.outline(g, -sw / 2, -8, sw, 16, a);
            Ui.outline(g, -sw / 2 + 2, -6, sw - 4, 12, Ui.alpha(ink, 0.4F));
            g.drawString(this.font, st, -sw / 2 + 6, -4, a, false);
            g.pose().popPose();
            int yy = y + 38;

            for (String l : lines) {
               g.drawString(this.font, l, x + 8, yy, -12897751, false);
               yy += 10;
            }

            if (cut) {
               g.drawString(this.font, "…", x + 8, yy, -8753570, false);
            }

            g.disableScissor();
         }
      }
   }

   private int trayX() {
      return this.panelX - this.trayW() - 6;
   }

   private int trayW() {
      return 170;
   }

   private int trayY() {
      return this.stampBtn == null ? this.cardY + 28 : this.stampBtn.getY();
   }

   private void drawTray(GuiGraphics g, int mx, int my) {
      Map<String, String> stamps = StampTool.inventoryStamps(this.minecraft.player);
      int x = this.trayX();
      int y = this.trayY();
      int w = this.trayW();
      int h = stamps.isEmpty() ? 52 : 26 + stamps.size() * 28 + 4;
      Ui.card(g, this.font, x, y, w, h, L.t("Ваши печати"));
      if (stamps.isEmpty()) {
         g.drawString(this.font, L.t("Печатей нет"), x + 10, y + 26, -3555408, false);
         g.drawString(this.font, L.t("Их выдаёт администрация"), x + 10, y + 37, -6253953, false);
      } else {
         int yy = y + 24;

         for (Entry<String, String> e : stamps.entrySet()) {
            boolean hover = mx >= x + 4 && mx < x + w - 4 && my >= yy && my < yy + 26;
            if (hover) {
               Ui.round(g, x + 4, yy, w - 8, 26, -12964318);
            }

            Ui.round(g, x + 8, yy + 2, 22, 22, -1185572);
            PageTexture t = this.tool.thumb(e.getKey());
            if (t != null) {
               int s = 20;
               int th = Math.max(6, s * t.h / t.w);
               g.blit(t.id(), x + 9, yy + 3 + (s - th) / 2, s, th, 0.0F, 0.0F, t.w, t.h, t.w, t.h);
            }

            String name = e.getValue().isEmpty() ? e.getKey() : e.getValue();
            g.drawString(this.font, Ui.ellipsize(this.font, name, w - 46), x + 36, yy + 4, -1183755, false);
            g.drawString(this.font, e.getKey(), x + 36, yy + 14, -6253953, false);
            yy += 28;
         }
      }
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.trayOpen && button == 0) {
         Map<String, String> stamps = StampTool.inventoryStamps(this.minecraft.player);
         int x = this.trayX();
         int w = this.trayW();
         if (mx >= (double)x && mx < (double)(x + w) && my >= (double)(this.trayY() + 24)) {
            int idx = (int)((my - (double)this.trayY() - 24.0) / 28.0);
            if (idx >= 0 && idx < stamps.size()) {
               this.tool.select(new ArrayList<>(stamps.keySet()).get(idx));
               this.trayOpen = false;
               this.rebuildWidgets();
               return true;
            }
         }

         if (this.stampBtn == null || !this.stampBtn.isMouseOver(mx, my)) {
            this.trayOpen = false;
         }
      }

      if (this.tool.active()) {
         if (button == 1) {
            this.tool.cancel();
            this.rebuildWidgets();
            return true;
         }

         if (button == 0 && this.view.contains(mx, my)) {
            if (!this.tool.animating() && this.stampingAllowed()) {
               int nx = this.view.nx(mx);
               int ny = this.view.ny(my);
               int k = this.tool.kind();
               String me = this.marksError();
               if (k != 0 && k >= 0 && me != null) {
                  this.error(
                     !me.isEmpty()
                        ? me
                        : (
                           k == 2
                              ? L.t("Визу ставят на страницу для виз — перелистните паспорт.")
                              : L.t("Погранштамп ставят на страницу для виз — перелистните паспорт.")
                        )
                  );
                  return true;
               }

               Sounds.play(Sounds.STAMP, 1.0F, 0.8F);
               Net.sendToServer(
                  new Packets.PlaceStamp(
                     this.hand, this.tool.activeId, nx, ny, this.tool.rot, this.tool.scale, this.currentPage(), this.tool.dir, this.tool.cat, this.tool.days
                  )
               );
               this.tool.place(this.tool.activeId, nx, ny, this.stampCount());
            }

            return true;
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
      if (this.tool.active()) {
         int d = delta > 0.0 ? 1 : -1;
         if (hasControlDown()) {
            this.tool.resize(d * 5);
         } else {
            this.tool.rotate(d * 5);
         }

         return true;
      } else {
         return super.mouseScrolled(mx, my, scrollX, delta);
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key != 256 || !this.tool.active() && !this.trayOpen) {
         if (!this.tool.active() || key != 81 && key != 69) {
            return this.hotkeysAllowed() && mods == 0 && this.hotkey(key) ? true : super.keyPressed(key, scan, mods);
         } else {
            this.tool.rotate(key == 81 ? -5 : 5);
            return true;
         }
      } else {
         this.tool.cancel();
         this.trayOpen = false;
         this.rebuildWidgets();
         return true;
      }
   }

   protected boolean hotkeysAllowed() {
      if (!this.hasSidePanel()) {
         return false;
      } else {
         GuiEventListener f = this.getFocused();
         return !(f instanceof EditBox) && !(f instanceof UiField) && !(f instanceof UiTextArea);
      }
   }

   private boolean hotkey(int key) {
      switch (key) {
         case 76:
            if (!this.hasSecurity()) {
               return false;
            }

            this.loupe = !this.loupe;
            Sounds.play(Sounds.CLICK, 1.0F, 0.5F);
            this.rebuildWidgets();
            return true;
         case 83:
            if (this.canStamp && this.stampingAllowed()) {
               if (this.tool.active()) {
                  this.tool.cancel();
                  this.rebuildWidgets();
               } else {
                  this.trayOpen = !this.trayOpen;
               }

               return true;
            }

            return false;
         case 85:
            if (!this.hasSecurity()) {
               return false;
            }

            this.uvOn = !this.uvOn;
            if (this.uvOn && this.verifyStatus != 2) {
               this.requestVerify();
            }

            Sounds.play(Sounds.CLICK, 1.0F, 0.5F);
            this.rebuildWidgets();
            return true;
         case 86:
            if (!this.canVerifyHotkey()) {
               return false;
            }

            this.requestVerify();
            return true;
         default:
            return false;
      }
   }

   protected boolean canVerifyHotkey() {
      return true;
   }

   public void removed() {
      this.view.close();
      this.tool.close();
      super.removed();
   }

   public void testTool(String id) {
      this.tool.select(id);
      this.rebuildWidgets();
   }
}
