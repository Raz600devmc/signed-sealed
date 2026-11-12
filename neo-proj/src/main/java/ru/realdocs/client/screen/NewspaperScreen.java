package ru.realdocs.client.screen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.realdocs.L;
import ru.realdocs.client.Exporter;
import ru.realdocs.client.PaperCache;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.render.Fonts;
import ru.realdocs.client.render.PaperRenderer;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.model.PaperModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;

/** Reading a published newspaper: page by page, with loupe, verification and export. */
public final class NewspaperScreen extends ViewerScreen {
   private final PaperCodec.Ref ref;
   private final boolean fromHand;
   private int page;

   public NewspaperScreen(InteractionHand hand, PaperCodec.Ref ref, boolean fromHand) {
      super(Component.literal(L.t(NewspaperItem.kind(ref.format)) + " «" + ref.name + "»"), hand, false);
      this.ref = ref;
      this.fromHand = fromHand;
      this.subtitle = (ref.issue > 0 ? "№ " + ref.issue + "  " : "") + ref.date;
   }

   private PaperModel model() {
      return PaperCache.INSTANCE.get(this.ref.id);
   }

   private int pages() {
      PaperModel m = this.model();
      return m == null ? Math.max(1, this.ref.pages) : m.pageCount();
   }

   @Override
   protected BufferedImage renderPage(int px) {
      PaperModel m = this.model();
      if (m == null) {
         int ph = (int)Math.round((double)px * PaperModel.H / PaperModel.W);
         BufferedImage img = new BufferedImage(px, ph, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = img.createGraphics();
         Fonts.hints(g);
         g.setColor(new Color(PaperModel.TONE_RGB[0]));
         g.fillRect(0, 0, px, ph);
         Fonts.Face f = Fonts.face(Fonts.SERIF, false, true, px / 30.0F);
         String s = PaperCache.INSTANCE.missing(this.ref.id) ? L.t("Выпуск не найден на сервере") : L.t("Загрузка…");
         g.setColor(new Color(90, 86, 78));
         f.draw(g, s, (px - f.width(s)) / 2.0, ph / 2.0);
         g.dispose();
         return img;
      } else {
         return PaperRenderer.render(m, Math.min(this.page, m.pageCount() - 1), px, PassportCache.INSTANCE::image, false).image;
      }
   }

   @Override
   protected double aspect() {
      return (double)PaperModel.W / PaperModel.H;
   }

   @Override
   protected double pageUnitsW() {
      return PaperModel.W;
   }

   @Override
   protected int contentKey() {
      return Objects.hash(this.ref.id, this.page, PaperCache.version, this.model() == null);
   }

   @Override
   protected int stampCount() {
      return 0;
   }

   @Override
   protected int currentPage() {
      return this.page;
   }

   private void turn(int d) {
      int np = Math.max(0, Math.min(this.pages() - 1, this.page + d));
      if (np != this.page) {
         this.page = np;
         Sounds.play(Sounds.PAGE, 1.0F, 0.8F);
         this.rebuildWidgets();
      }
   }

   @Override
   protected void addPanelButtons() {
      int x = this.panelX + 8;
      int w = this.panelW - 16;
      int half = (w - 4) / 2;
      this.button("◀", x, this.nextY, half, 20, () -> this.turn(-1)).enabled(this.page > 0);
      this.button("▶", x + half + 4, this.nextY, w - half - 4, 20, () -> this.turn(1)).enabled(this.page < this.pages() - 1);
      this.nextY += 24;
      this.nextY += 12;
      if (this.fromHand) {
         this.addBtn(L.t("Проверить подлинность"), this::requestVerify);
      }

      this.addBtn(L.t("Лупа"), () -> {
         this.loupe = !this.loupe;
         this.rebuildWidgets();
      }).selected(this.loupe).hint("L");
      if (this.ref.published) {
         this.addBtn(L.t("Подписка: вкл/выкл"), () -> {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putString("Name", this.ref.name);
            t.putBoolean("Toggle", true);
            ru.realdocs.client.ClientOffice.req("paper_sub", t);
         });
      }

      this.addBtn(L.t("Сохранить PDF"), this::exportPdf);
      this.addBtn(L.t("Сохранить страницу PNG"), this::exportPng);
      PaperModel m = this.model();
      if (this.fromHand && m != null && this.minecraft != null && this.minecraft.player != null
         && m.editorUuid.equals(this.minecraft.player.getUUID().toString())) {
         this.addBtn(L.t("Макет следующего номера"), () -> {
            Net.sendToServer(new MorePackets.PaperClone(this.hand));
            this.onClose();
         }).primary();
      }
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      super.render(g, mx, my, pt);
      String p = L.t("Страница ") + (this.page + 1) + " / " + this.pages();
      g.drawCenteredString(this.font, p, this.view.x + this.view.w / 2, this.view.y + this.view.h + 6, -6253953);
   }

   private void exportPng() {
      try {
         Path f = Exporter.exportImage(this.renderPage(1600), this.ref.name + "_" + this.ref.issue + "_p" + (this.page + 1));
         this.ok(L.t("Сохранено: ") + f.getFileName());
      } catch (Exception var2) {
         this.error(L.t("Ошибка экспорта: ") + var2.getMessage());
      }
   }

   private void exportPdf() {
      PaperModel m = this.model();
      if (m != null) {
         try {
            List<BufferedImage> pages = new ArrayList<>();

            for (int i = 0; i < m.pageCount(); i++) {
               pages.add(PaperRenderer.render(m, i, 1400, PassportCache.INSTANCE::image, false).image);
            }

            Path f = Exporter.exportPdfPages(pages, this.ref.name + "_" + this.ref.issue, 595.0, 595.0 * PaperModel.H / PaperModel.W);
            this.ok(L.t("Сохранено: ") + f.getFileName());
         } catch (Exception var4) {
            this.error(L.t("Ошибка экспорта: ") + var4.getMessage());
         }
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 263 || key == 266) {
         this.turn(-1);
         return true;
      } else if (key == 262 || key == 267) {
         this.turn(1);
         return true;
      } else if (key == 76) {
         this.loupe = !this.loupe;
         this.rebuildWidgets();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.loupe && this.view.contains(mx, my) && button == 0) {
         this.turn(mx < this.view.x + this.view.w / 2.0 ? -1 : 1);
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }
}
