package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.render.ImageFx;
import ru.realdocs.client.render.StampRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class StampDesignerScreen extends UiScreen {
   private final StampModel m = new StampModel();
   private EditBox org;
   private EditBox top;
   private EditBox bottom;
   private EditBox center;
   private EditBox source;
   private EditBox hex;
   private int tab;
   private BufferedImage original;
   private BufferedImage processed;
   private byte[] png;
   private boolean removeBg;
   private boolean ink;
   private boolean crop = true;
   private int tol = 40;
   private int thr = 70;
   private String status = "";
   private int statusColor = -5592406;
   private boolean dirty = true;
   private boolean loading;
   private final PageTexture preview = new PageTexture();
   private int left;
   private int formW;
   private int formY;
   private int formH;
   private String vOrg = "";
   private String vTop = "";
   private String vBottom = "";
   private String vCenter = "";
   private String vSource = "";
   private static final String[] SHAPE_SHORT = new String[]{"Круг", "Овал", "Прямоуг.", "Картинка"};

   public StampDesignerScreen() {
      super(Component.literal(L.t("Конструктор печати")));
      this.m.shape = 0;
   }

   protected void init() {
      this.left = 12;
      this.formY = 12;
      this.formW = Math.max(240, Math.min(330, this.width * 2 / 5));
      this.formH = this.height - 24;
      int x = this.left + 10;
      int w = this.formW - 20;
      int y = this.formY + 28;
      int half = (w - 4) / 2;
      this.button(L.t("Оформление"), x, y, half, 18, () -> this.setTab(0)).selected(this.tab == 0);
      this.button(L.t("Изображение") + (this.processed != null ? " ✔" : ""), x + half + 4, y, w - half - 4, 18, () -> this.setTab(1)).selected(this.tab == 1);
      y += 30;
      if (this.tab == 0) {
         int kw = (w - 6) / 3;

         for (int i0 = 0; i0 < StampModel.KIND_NAMES.length; i0++) {
            int i = i0;
            this.button(L.t(StampModel.KIND_NAMES[i]), x + i * (kw + 3), y + 10, i == 2 ? w - 2 * (kw + 3) : kw, 18, () -> {
               this.syncValues();
               this.m.kind = i;
               if (i == 1 && this.m.shape > 2) {
                  this.m.shape = 2;
               }

               this.dirty = true;
               this.rebuildWidgets();
            }).selected(this.m.kind == i);
         }

         y += 30;
      }

      boolean mark = this.m.kind != 0;
      int fy = y + 10 + (this.m.kind == 2 ? 0 : 30) + 30 + 13;
      this.org = this.edit(x, fy, w, L.t("Например, Мэрия Новограда"), this.vOrg, 60, L.t("Название (для списков)"));
      this.top = this.edit(
         x,
         fy + 30,
         w,
         mark ? L.t("Например, REPUBLIK NOVOGRAD") : L.t("Текст по верхней дуге"),
         this.vTop,
         44,
         mark ? L.t("Государство") : L.t("Верхняя дуга")
      );
      this.bottom = this.edit(
         x,
         fy + 60,
         w,
         this.m.kind == 1 ? L.t("Например, AIRPORT NOVOGRAD") : (this.m.kind == 2 ? L.t("Например, КОНСУЛЬСТВО") : L.t("Текст по нижней дуге")),
         this.vBottom,
         44,
         this.m.kind == 1 ? L.t("Пункт пропуска") : (this.m.kind == 2 ? L.t("Кем выдана") : L.t("Нижняя дуга"))
      );
      this.center = this.edit(x, fy + 90, w, L.t("| — перенос строки"), this.vCenter, 88, L.t("Центр"));

      for (EditBox e : new EditBox[]{this.org, this.top, this.bottom, this.center}) {
         e.visible = this.tab == 0;
      }

      this.center.visible = this.tab == 0 && !mark;
      if (this.tab == 0 && this.m.kind == 2) {
         this.button(this.m.visa.isEmpty() ? L.t("Оформление визы…") : L.t("Оформление визы: своё ✔"), x, fy + 90, w, () -> {
            this.syncValues();
            this.m.org = this.vOrg;
            this.m.top = this.vTop;
            this.m.bottom = this.vBottom;
            this.dirty = true;
            this.minecraft.setScreen(new VisaDesignerScreen(this, this.m, this.processed));
         }).selected(!this.m.visa.isEmpty());
      }

      if (this.tab == 0 && this.m.kind != 2) {
         int shapes = this.m.kind == 1 ? 3 : SHAPE_SHORT.length;
         int sw = (w - 3 * (shapes - 1)) / shapes;

         for (int i = 0; i < shapes; i++) {
            int k = i;
            this.button(L.t(SHAPE_SHORT[i]), x + i * (sw + 3), y + 10, sw, 18, () -> {
               this.m.shape = k;
               this.dirty = true;
               this.syncValues();
               this.rebuildWidgets();
            }).selected(this.m.shape == i);
         }

         y += 30;
      }

      if (this.tab == 0) {
         for (int i = 0; i < StampModel.PRESET_COLORS.length; i++) {
            int c = StampModel.PRESET_COLORS[i];
            this.button("", x + i * 25, y + 10, 22, 18, () -> {
               this.m.color = c;
               this.onColorChanged();
               this.syncValues();
               this.rebuildWidgets();
            }).swatch(c).selected((this.m.color & 16777215) == c);
         }

         int hx = x + StampModel.PRESET_COLORS.length * 25 + 6;
         this.hex = this.field(hx, y + 10, x + w - hx, "#RRGGBB", String.format("%06X", this.m.color & 16777215), 7, sv -> {
            try {
               String t = sv.startsWith("#") ? sv.substring(1) : sv;
               if (t.length() == 6) {
                  this.m.color = Integer.parseInt(t, 16);
                  this.onColorChanged();
               }
            } catch (NumberFormatException var3x) {
            }
         });
      } else {
         this.source = this.field(x, y + 10, w - 80, L.t("Ссылка или файл"), this.vSource, 500, sv -> this.vSource = sv);
         ((UiField)this.source).label(L.t("Источник (PNG, JPG, GIF, BMP)"));
         this.button(L.t("Загрузить"), x + w - 76, y + 9, 76, this::loadFromField).primary();
         y += 46;
         int bw = w - 44 - 8;
         this.button(L.t("Убрать фон"), x, y, bw, () -> {
            this.removeBg = !this.removeBg;
            this.rebuildImage();
            this.rebuildWidgets();
         }).selected(this.removeBg);
         this.button("−", x + bw + 4, y, 22, () -> {
            this.tol = Math.max(5, this.tol - 8);
            this.rebuildImage();
         }).enabled(this.removeBg);
         this.button("+", x + bw + 30, y, 22, () -> {
            this.tol = Math.min(160, this.tol + 8);
            this.rebuildImage();
         }).enabled(this.removeBg);
         y += 24;
         this.button(L.t("В один цвет (оттиск)"), x, y, bw, () -> {
            this.ink = !this.ink;
            this.rebuildImage();
            this.rebuildWidgets();
         }).selected(this.ink);
         this.button("−", x + bw + 4, y, 22, () -> {
            this.thr = Math.max(20, this.thr - 8);
            this.rebuildImage();
         }).enabled(this.ink);
         this.button("+", x + bw + 30, y, 22, () -> {
            this.thr = Math.min(95, this.thr + 8);
            this.rebuildImage();
         }).enabled(this.ink);
         y += 24;
         this.button(L.t("Обрезать пустые края"), x, y, w, () -> {
            this.crop = !this.crop;
            this.rebuildImage();
            this.rebuildWidgets();
         }).selected(this.crop);
         y += 24;
         this.button(L.t("Убрать картинку"), x, y, w, () -> {
            this.original = null;
            if (this.m.shape == 3) {
               this.m.shape = 0;
            }

            this.rebuildImage();
            this.info(L.t("Картинка убрана"));
            this.rebuildWidgets();
         }).danger().enabled(this.original != null);
      }

      int footY = this.formY + this.formH - 10 - 20 - 24;
      this.button(L.t("Отправить на утверждение"), x, footY, w, this::submit).primary();
      this.button(L.t("Закрыть"), x, footY + 24, w, this::onClose).ghost();
   }

   private void setTab(int t) {
      this.syncValues();
      this.tab = t;
      this.rebuildWidgets();
   }

   private void syncValues() {
      if (this.org != null) {
         this.vOrg = this.org.getValue();
      }

      if (this.top != null) {
         this.vTop = this.top.getValue();
      }

      if (this.bottom != null) {
         this.vBottom = this.bottom.getValue();
      }

      if (this.center != null) {
         this.vCenter = this.center.getValue();
      }
   }

   private EditBox edit(int x, int y, int w, String hint, String value, int max, String label) {
      UiField f = this.field(x, y, w, hint, value, max, sv -> this.dirty = true);
      f.label(label);
      return f;
   }

   private void loadFromField() {
      this.vSource = this.source.getValue();
      this.startLoad(this.vSource);
   }

   private void startLoad(String input) {
      if (!this.loading) {
         this.loading = true;
         this.status = L.t("Загрузка…");
         this.statusColor = -868018;
         WebImage.load(input).thenAccept(img -> this.minecraft.execute(() -> {
               this.loading = false;
               this.original = ImageFx.fit(img, 512, 512);
               if (this.original.getWidth() > 64 && !this.ink && !this.removeBg && looksOpaque(this.original)) {
                  this.removeBg = true;
               }

               if (this.m.kind == 0 && this.m.shape != 3) {
                  this.m.shape = 3;
                  this.info(L.t("Показывается только ваша картинка. Чтобы добавить рамку и надписи — выберите форму."));
               }

               this.rebuildImage();
               this.syncValues();
               this.rebuildWidgets();
            })).exceptionally(ex -> {
            this.minecraft.execute(() -> {
               this.loading = false;
               this.status = L.t("Ошибка: ") + WebImage.reason(ex);
               this.statusColor = -1088669;
            });
            return null;
         });
      }
   }

   private static boolean looksOpaque(BufferedImage img) {
      return img.getRGB(0, 0) >>> 24 > 200 && img.getRGB(img.getWidth() - 1, img.getHeight() - 1) >>> 24 > 200;
   }

   private void rebuildImage() {
      this.dirty = true;
      if (this.original == null) {
         this.processed = null;
         this.png = null;
         this.m.hasImage = false;
      } else {
         try {
            BufferedImage img = ImageFx.toArgb(this.original);
            if (this.removeBg) {
               img = ImageFx.removeBackground(img, this.tol);
            }

            if (this.ink) {
               img = ImageFx.inkify(img, this.m.color, (double)this.thr / 100.0);
            }

            if (this.crop) {
               img = ImageFx.autoCrop(img);
            }

            this.png = ImageUtil.pngUnder(img, 24000, 256);
            this.processed = ImageIO.read(new ByteArrayInputStream(this.png));
            this.m.hasImage = true;
            this.status = L.t("Картинка готова: ") + (double)this.png.length / 1000.0 + L.t(" КБ");
            this.statusColor = -10628481;
         } catch (Exception var2) {
            this.png = null;
            this.processed = null;
            this.m.hasImage = false;
            this.status = L.t("Ошибка: ") + var2.getMessage();
            this.statusColor = -1088669;
         }
      }
   }

   private void onColorChanged() {
      this.dirty = true;
      if (this.ink && this.original != null) {
         this.rebuildImage();
      }
   }

   public void onFilesDrop(List<Path> files) {
      if (!files.isEmpty()) {
         this.vSource = files.get(0).toString();
         this.syncValues();
         this.tab = 1;
         this.rebuildWidgets();
         this.startLoad(this.vSource);
      }
   }

   private void submit() {
      StampModel out = this.m.copy();
      out.org = this.org.getValue().trim();
      out.top = this.top.getValue().trim();
      out.bottom = this.bottom.getValue().trim();
      out.center = this.center.getValue().trim();
      if (out.org.isEmpty()) {
         this.error(L.t("Укажите название организации."));
      } else if (out.shape == 3 && this.png == null) {
         this.error(L.t("Для формы «Картинка» загрузите изображение."));
      } else {
         Net.sendToServer(new Packets.SubmitStamp(out, this.png));
         this.minecraft.setScreen(null);
      }
   }

   private StampModel previewModel() {
      StampModel p = this.m.copy();
      this.vOrg = this.org.getValue();
      this.vTop = this.top.getValue();
      this.vBottom = this.bottom.getValue();
      this.vCenter = this.center.getValue();
      p.top = this.vTop;
      p.bottom = this.vBottom;
      p.center = this.vCenter;
      p.hasImage = this.processed != null;
      return p;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.left, this.formY, this.formW, this.formH, L.t("Конструктор печати"));
      int x = this.left + 10;
      int w = this.formW - 20;
      int y = this.formY + 58;
      if (this.tab == 0) {
         Ui.section(g, this.font, L.t("Вид"), x, y, w);
         int sy = y + 30;
         if (this.m.kind != 2) {
            Ui.section(g, this.font, L.t("Форма"), x, sy, w);
            sy += 30;
         }

         Ui.section(g, this.font, this.m.kind == 2 ? L.t("Цвет визы") : L.t("Цвет чернил"), x, sy, w);
         Ui.section(g, this.font, L.t("Надписи"), x, sy + 30, w);
      } else {
         int iy = y + 46 + 96 + 10;
         if (this.removeBg) {
            g.drawString(
               this.font, L.t("допуск ") + this.tol, x + w - 52 - this.font.width(L.t("допуск ") + this.tol) - 6, y + 46 + 6, -3555408, false
            );
         }

         if (this.ink) {
            g.drawString(
               this.font,
               L.t("порог ") + this.thr + "%",
               x + w - 52 - this.font.width(L.t("порог ") + this.thr + "%") - 6,
               y + 70 + 6,
               -3555408,
               false
            );
         }

         g.drawString(this.font, L.t("Можно перетащить файл прямо в окно игры."), x, iy, -6253953, false);
         if (this.processed != null) {
            g.drawString(
               this.font,
               L.t("Готово: ")
                  + this.processed.getWidth()
                  + "×"
                  + this.processed.getHeight()
                  + " px, "
                  + (this.png == null ? 0.0 : (double)this.png.length / 1000.0)
                  + L.t(" КБ"),
               x,
               iy + 12,
               -10628481,
               false
            );
         } else if (!this.status.isEmpty()) {
            g.drawString(this.font, Ui.ellipsize(this.font, this.status, w), x, iy + 12, this.statusColor, false);
         }
      }

      if (this.dirty || !this.preview.ready()) {
         this.preview.set(StampRenderer.render(this.previewModel(), this.processed, 420, null));
         this.dirty = false;
      }

      int px = this.left + this.formW + 12;
      int pw = this.width - px - 12;
      int ch = this.formH;
      Ui.card(g, this.font, px, this.formY, pw, ch, L.t("Предпросмотр оттиска"));
      int area = Math.min(pw - 60, ch - 36 - 50 - 40);
      int sw = Math.min(area, 260);
      int sh = sw * this.preview.h / Math.max(1, this.preview.w);
      if (sh > area) {
         sh = area;
         sw = area * this.preview.w / Math.max(1, this.preview.h);
      }

      int paperW = Math.min(pw - 24, sw + 70);
      int paperH = sh + 50;
      int paperX = px + (pw - paperW) / 2;
      int paperY = this.formY + 36;
      Ui.paperFrame(g, paperX, paperY, paperW, paperH);
      g.fill(paperX, paperY, paperX + paperW, paperY + paperH, -1185572);
      g.blit(
         this.preview.id(),
         paperX + (paperW - sw) / 2,
         paperY + (paperH - sh) / 2,
         sw,
         sh,
         0.0F,
         0.0F,
         this.preview.w,
         this.preview.h,
         this.preview.w,
         this.preview.h
      );
      int ty = paperY + paperH + 14;

      for (String line : new String[]{L.t("Макет уходит на утверждение администрации."), L.t("После утверждения печать выдают игроку.")}) {
         g.drawCenteredString(this.font, line, px + pw / 2, ty, -6253953);
         ty += 11;
      }

      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }
}
