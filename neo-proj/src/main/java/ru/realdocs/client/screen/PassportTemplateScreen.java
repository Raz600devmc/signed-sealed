package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.render.DesignRenderer;
import ru.realdocs.client.render.ImageFx;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.model.DesignElement;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class PassportTemplateScreen extends UiScreen {
   private static final int[] BG = new int[]{15327948, 15722200, 14477040, 15786208, 14741728, 16119285, 14275305};
   private static final int[] ACCENT = new int[]{2046611, 11740702, 1993530, 5974662, 1710618, 9067008, 1006702};
   private static final int[] COVER = new int[]{2046611, 8003359, 1985835, 2829099, 4860523, 1002588, 7031326};
   private PassportTemplate t;
   private PassportTemplate original;
   private int tab;
   private int sel = 0;
   private int fieldScroll;
   private int elSel = -1;
   private int coverMode = 1;
   private static final int TAB_COVER = 3;
   private static final int TAB_DECOR = 4;
   private static final int[] INKS = new int[]{16777215, 1710618, 2046611, 11740702, 1993530, 9067008, 5974662};
   private final List<Object[]> labels = new ArrayList<>();
   private int pickerY = -1;
   private BufferedImage emblemOriginal;
   private BufferedImage emblemImg;
   private byte[] emblemPng;
   private boolean emblemRemoveBg;
   private String emblemSrc = "";
   private boolean loading;
   private final PageTexture preview = new PageTexture();
   private boolean dirty = true;
   private int lastCacheVersion = -1;
   private int dragMode;
   private double dragOffX;
   private double dragOffY;
   private int formX = 12;
   private int formY = 12;
   private int formW;
   private int formH;
   private int prevX;
   private int prevY;
   private int prevW;
   private int prevH;
   private int cardX;
   private int cardW;
   private final PassportRenderer.Assets assets = id -> "__emblem".equals(id) ? this.emblemImg : PassportCache.INSTANCE.image(id);
   private int stepperY = -1;

   public PassportTemplateScreen() {
      this("");
   }

   public PassportTemplateScreen(String preset) {
      super(Component.literal(L.t("Шаблон документа")));
      PassportTemplate base = PassportTemplate.preset(preset);
      this.t = base != null ? base : PassportTemplate.sample();
      this.t.emblem = "";
      localize(this.t);
   }

   public PassportTemplateScreen(PassportTemplate existing) {
      super(Component.literal(L.t("Изменение шаблона")));
      this.original = existing.copy();
      this.t = existing.copy();
   }

   private boolean editingExisting() {
      return this.original != null;
   }

   private static void localize(PassportTemplate t) {
      t.name = L.t(t.name);
      t.org = L.t(t.org);
      t.title = L.t(t.title);
      t.subtitle = L.t(t.subtitle);
      t.coverTitle = L.t(t.coverTitle);
      t.visaTitle = L.t(t.visaTitle);
      StringBuilder sb = new StringBuilder();

      for (String line : t.coverText.split("\n")) {
         sb.append(sb.length() == 0 ? "" : "\n").append(L.t(line));
      }

      t.coverText = sb.toString();

      for (PassportTemplate.Field f : t.fields) {
         f.label = L.t(f.label);
      }

      for (List<DesignElement> list : List.of(t.cover, t.decor, t.back)) {
         for (DesignElement e : list) {
            if (e.type == 0) {
               e.text = L.t(e.text);
            }
         }
      }
   }

   public PassportTemplateScreen startTab(int tab) {
      this.tab = tab;
      if (tab == 3 && this.t.cover.isEmpty()) {
         this.t.cover.addAll(this.t.defaultCover());
      }

      return this;
   }

   protected void init() {
      this.labels.clear();
      this.pickerY = -1;
      this.stepperY = -1;
      this.formW = Math.max(240, Math.min(320, this.width * 2 / 5));
      this.formH = this.height - 24;
      this.cardX = this.formX + this.formW + 10;
      this.cardW = this.width - this.cardX - 12;
      int maxH = this.formH - 26 - 40;
      this.prevW = this.cardW - 20;
      this.prevH = (int)((double)(this.designH() * this.prevW) / (double)this.designW());
      if (this.prevH > maxH) {
         this.prevH = maxH;
         this.prevW = (int)((double)(this.prevH * this.designW()) / (double)this.designH());
      }

      this.prevX = this.cardX + (this.cardW - this.prevW) / 2;
      this.prevY = this.formY + 28;
      int x = this.formX + 10;
      int w = this.formW - 20;
      int y = this.formY + 28;
      if (this.tab == 3 && !this.hasSecondTab()) {
         this.tab = 0;
      }

      String third = this.t.book() ? L.t("Обложка") : L.t("Оборот");
      String[] all = new String[]{L.t("Основное"), L.t("Стиль"), L.t("Поля ") + this.t.fields.size(), third, L.t("Декор")};
      List<Integer> shown = new ArrayList<>();

      for (int i = 0; i < all.length; i++) {
         if (i != 3 || this.hasSecondTab()) {
            shown.add(i);
         }
      }

      int[] tw = new int[shown.size()];
      int sum = 0;

      for (int ix = 0; ix < shown.size(); ix++) {
         sum += tw[ix] = this.font.width(all[shown.get(ix)]) + 8;
      }

      int extra = w - sum - 3 * (shown.size() - 1);
      int bx = x;

      for (int ix = 0; ix < shown.size(); ix++) {
         int bw = tw[ix] + (ix == shown.size() - 1 ? x + w - bx - tw[ix] : extra / shown.size());
         int k = shown.get(ix);
         this.button(all[k], bx, y, bw, 18, () -> this.setTab(k)).selected(this.tab == k);
         bx += bw + 3;
      }

      y += 30;
      if (this.tab == 0) {
         this.buildMain(x, y, w);
      } else if (this.tab == 1) {
         this.buildLook(x, y, w);
      } else if (this.tab == 2) {
         this.buildFields(x, y, w);
      } else {
         this.buildDesign(x, y, w);
      }

      int footY = this.formY + this.formH - 30;
      int q = (w - 6) / 4;
      this.button(L.t("Сбросить"), x, footY, q, () -> {
         if (this.editingExisting()) {
            this.t = this.original.copy();
            this.emblemOriginal = null;
            this.emblemImg = null;
            this.emblemPng = null;
         } else {
            this.t = PassportTemplate.sample(this.t.format);
            localize(this.t);
            this.t.emblem = this.emblemImg != null ? "__emblem" : "";
         }

         this.sel = 0;
         this.elSel = -1;
         if (this.tab == 3) {
            this.t.cover.addAll(this.t.defaultCover());
         }

         this.dirty = true;
         this.rebuildWidgets();
      }).ghost();
      this.button(L.t("Закрыть"), x + q + 3, footY, q, this::onClose).ghost();
      this.button(this.editingExisting() ? L.t("Сохранить изменения") : L.t("Отправить шаблон"), x + 2 * q + 6, footY, w - 2 * q - 6, this::submit).primary();
   }

   private boolean hasSecondTab() {
      return this.t.format == 0 || this.t.format == 1;
   }

   private boolean designTab() {
      return this.tab == 3 && this.t.book();
   }

   private int designW() {
      return this.designTab() ? 300 : this.t.w();
   }

   private int designH() {
      return this.designTab() ? 420 : this.t.h();
   }

   private List<DesignElement> elements() {
      return this.tab == 3 ? (this.t.book() ? this.t.cover : this.t.back) : this.t.decor;
   }

   private void setFormat(int f) {
      if (f != this.t.format) {
         String emb = this.t.emblem;
         this.t = PassportTemplate.sample(f);
         localize(this.t);
         this.t.emblem = emb;
         this.sel = 0;
         this.elSel = -1;
         this.fieldScroll = 0;
         this.dirty = true;
         this.rebuildWidgets();
      }
   }

   private void setTab(int n) {
      if (n != this.tab) {
         this.elSel = -1;
      }

      this.tab = n;
      if (n == 3 && this.t.book() && this.t.cover.isEmpty()) {
         this.t.cover.addAll(this.t.defaultCover());
      }

      this.dirty = true;
      this.rebuildWidgets();
   }

   private UiField edit(int x, int y, int w, String label, String hint, String value, int max, Consumer<String> set) {
      UiField f = this.field(x, y + 10, w, hint, value, max, s -> {
         set.accept(s);
         this.dirty = true;
      });
      f.label(label);
      return f;
   }

   private void buildMain(int x, int y, int w) {
      int n = PassportTemplate.FORMATS.length;
      int fw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int k = i;
         this.button(L.t(PassportTemplate.FORMATS[i]), x + i * (fw + 3), y, i == n - 1 ? w - (n - 1) * (fw + 3) : fw, 18, () -> this.setFormat(k))
            .selected(this.t.format == i)
            .enabled(!this.editingExisting() || this.t.format == i);
      }

      y += 24;
      this.edit(x, y, w, L.t("Название шаблона"), L.t("для списков и выдачи"), this.t.name, 60, s -> this.t.name = s);
      y += 32;
      boolean bk = this.t.book();
      this.edit(
         x,
         y,
         w,
         bk ? L.t("Подзаголовок разворота") : L.t("Организация (подзаголовок)"),
         bk ? L.t("например, ПАСПОРТ · PASSPORT") : L.t("например, ГОРОДСКАЯ ПОЛИЦИЯ"),
         this.t.org,
         80,
         s -> this.t.org = s
      );
      y += 32;
      this.edit(
         x,
         y,
         w,
         bk ? L.t("Заголовок разворота") : L.t("Заголовок"),
         bk ? L.t("например, РЕСПУБЛИКА НОВОГРАД") : L.t("например, УДОСТОВЕРЕНИЕ"),
         this.t.title,
         60,
         s -> this.t.title = s
      );
      y += 32;
      int half = (w - 6) / 2;
      this.edit(x, y, half, L.t("Серия номера"), L.t("например RP"), this.t.serialPrefix, 8, s -> this.t.serialPrefix = s);
      this.edit(
         x + half + 6,
         y,
         w - half - 6,
         L.t("Печать при выдаче"),
         L.t("ID, необязательно"),
         this.t.autoStampId,
         24,
         s -> this.t.autoStampId = s.trim().toUpperCase(Locale.ROOT)
      );
      y += 32;
      if (!bk) {
         this.stepperY = y;
         this.button("−", x + half + 6, y + 10, 18, 18, () -> this.step(0, -1));
         this.button("+", x + half + 6 + 52, y + 10, 18, 18, () -> this.step(0, 1));
      } else {
         this.edit(x, y, half, L.t("Надпись на обложке"), L.t("ПАСПОРТ"), this.t.coverTitle, 40, s -> this.t.coverTitle = s);
         this.edit(
            x + half + 6,
            y,
            w - half - 6,
            L.t("Страна на обложке"),
            L.t("| — новая строка"),
            this.t.coverText.replace('\n', '|'),
            120,
            s -> this.t.coverText = s.replace('|', '\n')
         );
         y += 32;
         this.edit(x, y, half, L.t("Цвета флага (медальон)"), "h:FFFFFF,1F3A93,B3261E", this.t.flag, 80, s -> this.t.flag = s.trim());
         this.edit(x + half + 6, y, w - half - 6, L.t("Заголовок визовых страниц"), L.t("ВИЗЫ · VISAS"), this.t.visaTitle, 40, s -> this.t.visaTitle = s);
         y += 32;
         this.stepperY = y;
         this.button("−", x, y + 10, 18, 18, () -> this.step(-1, 0));
         this.button("+", x + 52, y + 10, 18, 18, () -> this.step(1, 0));
         this.button("−", x + half + 6, y + 10, 18, 18, () -> this.step(0, -1));
         this.button("+", x + half + 6 + 52, y + 10, 18, 18, () -> this.step(0, 1));
      }
   }

   private void step(int spreads, int years) {
      this.t.visaSpreads = Math.max(0, Math.min(8, this.t.visaSpreads + spreads));
      this.t.validYears = Math.max(1, Math.min(30, this.t.validYears + years));
      this.dirty = true;
   }

   private void swatches(int x, int y, int w, int[] palette, int current, Consumer<Integer> set) {
      int n = palette.length;
      int sw = Math.min(26, (w - (n - 1) * 3) / n);

      for (int i = 0; i < n; i++) {
         int c = palette[i];
         this.button("", x + i * (sw + 3), y, sw, 18, () -> {
            set.accept(c);
            this.dirty = true;
            this.rebuildWidgets();
         }).swatch(c).selected((current & 16777215) == c);
      }
   }

   private void buildLook(int x, int y, int w) {
      this.swatches(x, y + 10, w, BG, this.t.bg, c -> this.t.bg = c);
      y += 32;
      this.swatches(x, y + 10, w, ACCENT, this.t.accent, c -> this.t.accent = c);
      y += 32;
      this.swatches(x, y + 10, w, COVER, this.t.coverColor, c -> this.t.coverColor = c);
      y += 32;
      int pw = (w - 9) / 4;

      for (int i = 0; i < PassportTemplate.PATTERNS.length; i++) {
         int k = i;
         this.button(L.t(PassportTemplate.PATTERNS[i]), x + i * (pw + 3), y + 10, pw, 18, () -> {
            this.t.pattern = k;
            this.dirty = true;
            this.rebuildWidgets();
         }).selected(this.t.pattern == i);
      }

      y += 32;
      int en = PassportTemplate.EMBLEM_NAMES.length;
      int ew = (w - (en - 1) * 3) / en;

      for (int i = 0; i < en; i++) {
         int k = i;
         String name = i == 0 ? L.t("Нет") : L.t(PassportTemplate.EMBLEM_NAMES[i]).replace(L.t("Герб "), "");
         this.button(name, x + i * (ew + 3), y + 10, ew, 18, () -> {
            this.t.emblemCode = k;
            this.dirty = true;
            this.rebuildWidgets();
         }).selected(this.t.emblemCode == i);
      }

      y += 32;
      UiField src = this.field(x, y + 10, w - 80, L.t("Ссылка или файл"), this.emblemSrc, 500, s -> this.emblemSrc = s);
      src.label(L.t("Эмблема или герб (своя картинка)"));
      this.button(L.t("Загрузить"), x + w - 76, y + 9, 76, () -> this.startEmblem(this.emblemSrc)).primary();
      y += 34;
      int half = (w - 6) / 2;
      this.button(L.t("Убрать фон"), x, y, half, () -> {
         this.emblemRemoveBg = !this.emblemRemoveBg;
         this.rebuildEmblem();
         this.rebuildWidgets();
      }).selected(this.emblemRemoveBg).enabled(this.emblemOriginal != null);
      this.button(L.t("Убрать эмблему"), x + half + 6, y, w - half - 6, () -> {
         this.emblemOriginal = null;
         this.rebuildEmblem();
         this.rebuildWidgets();
      }).danger().enabled(this.emblemOriginal != null || this.editingExisting() && !this.t.emblem.isEmpty());
   }

   private int fieldsListRows() {
      return 4;
   }

   private void buildFields(int x, int y, int w) {
      int cols = 2;
      int rows = this.fieldsListRows();
      int cw = (w - 4) / cols;
      int total = this.t.fields.size();
      int maxScroll = Math.max(0, (total + cols - 1) / cols - rows);
      this.fieldScroll = Math.max(0, Math.min(this.fieldScroll, maxScroll));

      for (int i = this.fieldScroll * cols; i < Math.min(total, (this.fieldScroll + rows) * cols); i++) {
         PassportTemplate.Field f = this.t.fields.get(i);
         int idx = i;
         int r = i / cols - this.fieldScroll;
         String name = f.label.isEmpty() ? f.key : f.label;
         this.button(name, x + i % cols * (cw + 4), y + r * 20, cw, 18, () -> {
            this.sel = idx;
            this.rebuildWidgets();
         }).selected(i == this.sel);
      }

      y += rows * 20 + 4;
      int half = (w - 6) / 2;
      this.button(L.t("+ Добавить поле"), x, y, half, () -> {
         PassportTemplate.Field fx = new PassportTemplate.Field();
         fx.key = "field" + (this.t.fields.size() + 1);
         fx.label = L.t("Новое поле");
         fx.x = this.t.book() ? 338 : 30;
         fx.y = (this.t.book() ? 100 : 70) + this.t.fields.size() % 6 * 40;
         fx.w = Math.min(220, this.t.w() - fx.x - 20);
         fx.h = 36;
         this.t.fields.add(fx);
         this.sel = this.t.fields.size() - 1;
         this.fieldScroll = Math.max(0, this.sel / 2 - this.fieldsListRows() + 1);
         this.dirty = true;
         this.rebuildWidgets();
      }).enabled(this.t.fields.size() < 16);
      this.button(L.t("Удалить поле"), x + half + 6, y, w - half - 6, () -> {
         if (this.sel >= 0 && this.sel < this.t.fields.size()) {
            this.t.fields.remove(this.sel);
         }

         this.sel = Math.max(0, Math.min(this.sel, this.t.fields.size() - 1));
         this.dirty = true;
         this.rebuildWidgets();
      }).danger().enabled(!this.t.fields.isEmpty());
      y += 28;
      if (this.sel >= 0 && this.sel < this.t.fields.size()) {
         PassportTemplate.Field f = this.t.fields.get(this.sel);
         int nw = (w - 6) * 3 / 5;
         this.edit(x, y, nw, L.t("Название поля"), L.t("подпись на развороте"), f.label, 40, s -> f.label = s);
         this.edit(x + nw + 6, y, w - nw - 6, L.t("Ключ"), L.t("латиница"), f.key, 24, s -> f.key = s.replaceAll("[^a-zA-Z0-9_]", ""));
         y += 32;
         int types = PassportTemplate.FIELD_TYPES.length;

         for (int i = 0; i < types; i++) {
            int k = i;
            int col = i % 4;
            int row = i / 4;
            int bw = (w - 9) / 4;
            this.button(
                  L.t(PassportTemplate.FIELD_TYPES[i]).replace(L.t("Срок действия"), L.t("Срок")).replace(L.t("Дата выдачи"), L.t("Выдан")),
                  x + col * (bw + 3),
                  y + row * 20,
                  bw,
                  18,
                  () -> {
                     f.type = k;
                     if (k == 2 && f.w < 80) {
                        f.w = 130;
                        f.h = 170;
                     }

                     this.dirty = true;
                     this.rebuildWidgets();
                  }
               )
               .selected(f.type == i);
         }

         y += 44;
         int q = (w - 9) / 4;
         this.button(L.t("Шрифт −"), x, y, q, () -> {
            f.size = Math.max(8, f.size - 1);
            this.dirty = true;
         });
         this.button(L.t("Шрифт +"), x + q + 3, y, q, () -> {
            f.size = Math.min(40, f.size + 1);
            this.dirty = true;
         });
         this.button(L.t("Жирный"), x + 2 * (q + 3), y, q, () -> {
            f.bold = !f.bold;
            this.dirty = true;
            this.rebuildWidgets();
         }).selected(f.bold);
         this.button(L.t("Обязат."), x + 3 * (q + 3), y, w - 3 * (q + 3), () -> {
            f.required = !f.required;
            this.rebuildWidgets();
         }).selected(f.required);
      }
   }

   private void dirtyRebuild() {
      this.dirty = true;
      this.rebuildWidgets();
   }

   private void label(String text, int x, int y) {
      this.labels.add(new Object[]{text, x, y, null});
   }

   private void stepper(int x, int y, int w, String label, Supplier<String> value, IntConsumer delta) {
      this.label(label, x + 1, y);
      this.button("−", x, y + 10, 18, 18, () -> {
         delta.accept(-1);
         this.dirty = true;
      });
      this.button("+", x + w - 18, y + 10, 18, 18, () -> {
         delta.accept(1);
         this.dirty = true;
      });
      this.labels.add(new Object[]{null, x + w / 2, y + 15, value});
   }

   private void buildDesign(int x, int y, int w) {
      int half = (w - 6) / 2;
      if (this.tab == 3 && this.t.book()) {
         this.button(L.t("Основа обложки"), x, y, half, 18, () -> {
            this.coverMode = 0;
            this.rebuildWidgets();
         }).selected(this.coverMode == 0);
         this.button(L.t("Элементы  ") + this.t.cover.size(), x + half + 6, y, w - half - 6, 18, () -> {
            this.coverMode = 1;
            this.rebuildWidgets();
         }).selected(this.coverMode == 1);
         y += 26;
         if (this.coverMode == 0) {
            this.buildCoverBase(x, y, w);
            return;
         }
      } else if (this.tab != 3) {
         this.button(this.t.hideHeader ? L.t("Стандартная шапка: скрыта") : L.t("Стандартная шапка: показана"), x, y, w, 18, () -> {
            this.t.hideHeader = !this.t.hideHeader;
            this.dirtyRebuild();
         }).selected(!this.t.hideHeader);
         y += 26;
      }

      this.buildElements(x, y, w);
   }

   private void buildCoverBase(int x, int y, int w) {
      this.label(L.t("Цвет обложки"), x + 1, y);
      this.swatches(x, y + 10, w, COVER, this.t.coverColor, c -> this.t.coverColor = c);
      y += 32;
      this.label(L.t("Фактура"), x + 1, y);
      int n = PassportTemplate.TEXTURES.length;
      int bw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int k = i;
         this.button(L.t(PassportTemplate.TEXTURES[i]), x + i * (bw + 3), y + 10, bw, 18, () -> {
            this.t.coverTexture = k;
            this.dirtyRebuild();
         }).selected(this.t.coverTexture == i);
      }

      y += 32;
      this.label(L.t("Золотая рамка"), x + 1, y);
      n = PassportTemplate.FRAMES.length;
      bw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int k = i;
         this.button(L.t(PassportTemplate.FRAMES[i]), x + i * (bw + 3), y + 10, bw, 18, () -> {
            this.t.coverFrame = k;
            this.dirtyRebuild();
         }).selected(this.t.coverFrame == i);
      }

      y += 36;
      this.button(L.t("Собрать заново из надписей"), x, y, w, () -> {
         this.t.cover.clear();
         this.t.cover.addAll(this.t.defaultCover());
         this.elSel = -1;
         this.ok(L.t("Обложка собрана из надписей и эмблемы."));
         this.dirtyRebuild();
      });
      y += 24;
      this.button(L.t("Убрать все элементы"), x, y, w, () -> {
         this.t.cover.clear();
         this.elSel = -1;
         this.dirtyRebuild();
      }).danger().enabled(!this.t.cover.isEmpty());
   }

   private DesignElement newElement(int type) {
      boolean cov = this.tab == 3 && this.t.book();
      DesignElement e;
      if (cov) {
         e = switch (type) {
            case 0 -> DesignElement.text(L.t("НАДПИСЬ"), 40, 200, 220, 24, 14, 14);
            default -> new DesignElement(type, 100, 150, 100, 100);
            case 3 -> new DesignElement(type, 110, 240, 80, 1);
            case 4 -> new DesignElement(type, 34, 34, 232, 352);
            case 5 -> new DesignElement(type, 0, 196, 300, 28);
         };
         e.ink = 1;
         if (type == 5) {
            e.ink = 0;
            e.color = 0;
            e.alpha = 30;
         }
      } else {
         int W = this.t.w();
         int H = this.t.h();
         int cx = this.t.book() ? 479 : W / 2;
         int cy = this.t.book() ? 230 : H / 2;
         int fw = this.t.book() ? 286 : W - 40;
         int fh = this.t.book() ? 416 : H - 50;

         e = switch (type) {
            case 0 -> DesignElement.text(L.t("НАДПИСЬ"), cx - 120, cy - 12, 240, 24, 14, 10);
            default -> new DesignElement(type, cx - 75, cy - 75, 150, 150);
            case 3 -> new DesignElement(type, cx - fw / 2 + 8, cy - 60, fw - 16, 1);
            case 4 -> new DesignElement(type, cx - fw / 2, cy - fh / 2, fw, fh);
            case 5 -> new DesignElement(type, cx - fw / 2, cy - 80, fw, 22);
         };
         e.ink = 0;
         e.color = this.t.accent & 16777215;
         if (type == 1 || type == 2) {
            e.alpha = 22;
         }

         if (type == 5) {
            e.alpha = 18;
         }
      }

      e.size = type == 4 ? 8 : e.size;
      return e;
   }

   private void buildElements(int x, int y, int w) {
      List<DesignElement> els = this.elements();
      if (this.elSel >= els.size()) {
         this.elSel = els.size() - 1;
      }

      this.button("◀", x, y, 18, 18, () -> {
         if (!els.isEmpty()) {
            this.elSel = (this.elSel - 1 + els.size()) % els.size();
         }

         this.rebuildWidgets();
      }).enabled(els.size() > 1);
      this.button("▶", x + w - 18, y, 18, 18, () -> {
         if (!els.isEmpty()) {
            this.elSel = (this.elSel + 1) % els.size();
         }

         this.rebuildWidgets();
      }).enabled(els.size() > 1);
      this.pickerY = y;
      y += 24;
      int bw = (w - 6) / 3;

      for (int i0 = 0; i0 < DesignElement.TYPE_NAMES.length; i0++) {
         int i = i0;
         this.button("+ " + L.t(DesignElement.TYPE_NAMES[i]), x + i % 3 * (bw + 3), y + i / 3 * 20, i % 3 == 2 ? w - 2 * (bw + 3) : bw, 18, () -> {
            els.add(this.newElement(i));
            this.elSel = els.size() - 1;
            this.dirtyRebuild();
         }).enabled(els.size() < 24);
      }

      y += 46;
      if (this.elSel >= 0 && this.elSel < els.size()) {
         DesignElement e = els.get(this.elSel);
         if (e.type == 0) {
            this.edit(x, y, w, L.t("Текст  (| — новая строка)"), L.t("текст"), e.text, 60, s -> e.text = s);
            y += 32;
            int sw = 58;
            this.stepper(x, y, sw, L.t("Размер"), () -> String.valueOf(e.size), d -> e.size = Math.max(5, Math.min(60, e.size + d)));
            this.stepper(x + sw + 6, y, sw, L.t("Разрядка"), () -> String.valueOf(e.spacing), d -> e.spacing = Math.max(0, Math.min(40, e.spacing + 2 * d)));
            int ax = x + 2 * (sw + 6);
            this.label(L.t("Выравнивание"), ax + 1, y);
            String[] al = new String[]{L.t("Л"), L.t("Ц"), L.t("П")};

            for (int i = 0; i < 3; i++) {
               int k = i;
               this.button(al[i], ax + i * 20, y + 10, 18, 18, () -> {
                  e.align = k;
                  this.dirtyRebuild();
               }).selected(e.align == i);
            }

            this.button(L.t("Ж"), x + w - 18, y + 10, 18, 18, () -> {
               e.bold = !e.bold;
               this.dirtyRebuild();
            }).selected(e.bold);
            y += 32;
         } else if (e.type == 4) {
            this.stepper(x, y, 70, L.t("Толщина"), () -> String.valueOf(e.size), d -> e.size = Math.max(5, Math.min(60, e.size + d)));
            y += 32;
         }

         this.label(L.t("Нанесение"), x + 1, y);
         int n = DesignElement.INK_NAMES.length;
         int iw = (w - 3 * (n - 1)) / n;

         for (int i = 0; i < n; i++) {
            int k = i;
            String name = i == 0 && e.type == 1 ? L.t("Оригинал") : L.t(DesignElement.INK_NAMES[i]);
            this.button(name, x + i * (iw + 3), y + 10, i == n - 1 ? w - (n - 1) * (iw + 3) : iw, 18, () -> {
               e.ink = k;
               this.dirtyRebuild();
            }).selected(e.ink == i);
         }

         y += 32;
         int aw = 72;
         if (e.ink == 0 && e.type != 1) {
            this.label(L.t("Цвет"), x + 1, y);
            this.swatches(x, y + 10, w - aw - 8, INKS, e.color, c -> e.color = c);
         }

         this.stepper(x + w - aw, y, aw, L.t("Плотность"), () -> e.alpha + "%", d -> e.alpha = Math.max(10, Math.min(100, e.alpha + 10 * d)));
         y += 32;
         int q = (w - 9) / 4;
         this.button(L.t("Выше"), x, y, q, 18, () -> {
            if (this.elSel < els.size() - 1) {
               els.add(this.elSel + 1, els.remove(this.elSel));
               this.elSel++;
            }

            this.dirtyRebuild();
         }).enabled(this.elSel < els.size() - 1);
         this.button(L.t("Ниже"), x + q + 3, y, q, 18, () -> {
            if (this.elSel > 0) {
               els.add(this.elSel - 1, els.remove(this.elSel));
               this.elSel--;
            }

            this.dirtyRebuild();
         }).enabled(this.elSel > 0);
         this.button(L.t("Копия"), x + 2 * (q + 3), y, q, 18, () -> {
            DesignElement c = e.copy();
            c.x += 8;
            c.y += 8;
            els.add(c);
            this.elSel = els.size() - 1;
            this.dirtyRebuild();
         }).enabled(els.size() < 24);
         this.button(L.t("Удалить"), x + 3 * (q + 3), y, w - 3 * (q + 3), 18, () -> {
            els.remove(this.elSel);
            this.elSel = Math.min(this.elSel, els.size() - 1);
            this.dirtyRebuild();
         }).danger();
      } else {
         this.label(els.isEmpty() ? L.t("Добавьте элемент кнопками выше.") : L.t("Выберите элемент стрелками или щелчком"), x + 1, y);
         if (!els.isEmpty()) {
            this.label(L.t("по предпросмотру."), x + 1, y + 11);
         }
      }
   }

   private void startEmblem(String input) {
      if (!this.loading) {
         this.loading = true;
         this.info(L.t("Загрузка эмблемы…"));
         WebImage.load(input).thenAccept(img -> this.minecraft.execute(() -> {
               this.loading = false;
               this.emblemOriginal = ImageFx.fit(img, 512, 512);
               if (!this.emblemRemoveBg && this.emblemOriginal.getRGB(0, 0) >>> 24 > 200) {
                  this.emblemRemoveBg = true;
               }

               this.rebuildEmblem();
               if (this.tab == 1) {
                  this.rebuildWidgets();
               }
            })).exceptionally(ex -> {
            this.minecraft.execute(() -> {
               this.loading = false;
               this.error(L.t("Ошибка: ") + WebImage.reason(ex));
            });
            return null;
         });
      }
   }

   private void rebuildEmblem() {
      this.dirty = true;
      if (this.emblemOriginal == null) {
         this.emblemImg = null;
         this.emblemPng = null;
         this.t.emblem = "";
      } else {
         try {
            BufferedImage img = ImageFx.toArgb(this.emblemOriginal);
            if (this.emblemRemoveBg) {
               img = ImageFx.removeBackground(img, 40);
            }

            img = ImageFx.autoCrop(img);
            this.emblemPng = ImageUtil.pngUnder(img, 16000, 200);
            this.emblemImg = ImageIO.read(new ByteArrayInputStream(this.emblemPng));
            this.t.emblem = "__emblem";
            this.ok(L.t("Эмблема готова: ") + (double)this.emblemPng.length / 1000.0 + L.t(" КБ"));
         } catch (Exception var2) {
            this.emblemPng = null;
            this.emblemImg = null;
            this.t.emblem = "";
            this.error(L.t("Ошибка: ") + var2.getMessage());
         }
      }
   }

   public void onFilesDrop(List<Path> files) {
      if (!files.isEmpty()) {
         this.emblemSrc = files.get(0).toString();
         this.tab = 1;
         this.startEmblem(this.emblemSrc);
         this.rebuildWidgets();
      }
   }

   private void submit() {
      PassportTemplate out = this.t.copy();
      out.emblem = this.editingExisting() && this.emblemPng == null && !"__emblem".equals(this.t.emblem) ? this.t.emblem : "";
      if (!this.editingExisting()) {
         out.id = "";
      }

      out.name = out.name.trim();
      if (out.name.isEmpty()) {
         this.error(L.t("Укажите название шаблона."));
      } else if (out.fields.isEmpty()) {
         this.error(L.t("Добавьте хотя бы одно поле."));
      } else {
         out.sanitize();
         Net.sendToServer(new Packets.SubmitTemplate(out, this.emblemPng));
         this.minecraft.setScreen(null);
      }
   }

   private double scale() {
      return (double)this.prevW / (double)this.designW();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      int x = this.formX + 10;
      int w = this.formW - 20;
      int y = this.formY + 58;
      Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, L.t("Шаблон: ") + L.t(PassportTemplate.FORMATS[this.t.format]));
      if (this.tab == 0 && this.stepperY >= 0) {
         int half = (w - 6) / 2;
         if (this.t.book()) {
            Ui.label(g, this.font, L.t("Разворотов для виз"), x + 1, this.stepperY);
         }

         Ui.label(g, this.font, L.t("Срок действия, лет"), x + half + 7, this.stepperY);
         String a = String.valueOf(this.t.visaSpreads);
         String b = String.valueOf(this.t.validYears);
         if (this.t.book()) {
            g.drawString(this.font, a, x + 35 - this.font.width(a) / 2, this.stepperY + 15, -1183755, false);
         }

         g.drawString(this.font, b, x + half + 6 + 35 - this.font.width(b) / 2, this.stepperY + 15, -1183755, false);
      }

      if (this.tab == 1) {
         String[] labels = new String[]{L.t("Фон страниц"), L.t("Акцент (рамки, заголовки)"), L.t("Обложка"), L.t("Узор"), L.t("Эмблема")};

         for (int i = 0; i < labels.length; i++) {
            Ui.label(g, this.font, labels[i], x + 1, y + i * 32);
         }
      } else if (this.tab == 2 && this.t.fields.size() > this.fieldsListRows() * 2) {
         g.drawString(this.font, L.t("колесо — прокрутка"), x + w - this.font.width(L.t("колесо — прокрутка")), y - 10 + 0, -9543342, false);
      }

      for (Object[] l : this.labels) {
         if (l[0] != null) {
            Ui.label(g, this.font, (String)l[0], (Integer)l[1], (Integer)l[2]);
         } else {
            String v = (String)((Supplier)l[3]).get();
            g.drawString(this.font, v, (Integer)l[1] - this.font.width(v) / 2, (Integer)l[2], -1183755, false);
         }
      }

      List<DesignElement> els = this.tab >= 3 ? this.elements() : List.of();
      if (this.pickerY >= 0) {
         String s = els.isEmpty()
            ? L.t("нет элементов")
            : (this.elSel < 0 ? els.size() + L.t(" шт. · не выбран") : this.elSel + 1 + " / " + els.size() + " · " + els.get(this.elSel).label());
         s = Ui.ellipsize(this.font, s, w - 48);
         Ui.round(g, x + 21, this.pickerY, w - 42, 18, -15067887);
         g.drawString(this.font, s, x + w / 2 - this.font.width(s) / 2, this.pickerY + 5, this.elSel >= 0 ? -1183755 : -6253953, false);
      }

      boolean cover = this.tab == 3 && this.t.book();
      boolean back = this.tab == 3 && !this.t.book();
      Ui.card(
         g,
         this.font,
         this.cardX,
         this.formY,
         this.cardW,
         this.formH,
         cover ? L.t("Предпросмотр обложки") : (back ? L.t("Предпросмотр оборота") : (this.t.book() ? L.t("Предпросмотр разворота") : L.t("Предпросмотр")))
      );
      if (this.dirty || !this.preview.ready() || this.lastCacheVersion != PassportCache.version) {
         PassportTemplate tmp = this.t.copy();
         tmp.sanitize();
         DesignRenderer.placeholders = true;

         try {
            this.preview
               .set(
                  cover
                     ? PassportRenderer.renderCover(tmp, this.assets, Math.min(900, this.prevW * 3))
                     : PassportRenderer.renderSpread(tmp, null, this.assets, StampCache.INSTANCE, 1280, this.tab == 2, back ? 1 : 0, 0)
               );
         } finally {
            DesignRenderer.placeholders = false;
         }

         this.dirty = false;
         this.lastCacheVersion = PassportCache.version;
      }

      if (!cover) {
         Ui.paperFrame(g, this.prevX, this.prevY, this.prevW, this.prevH);
      } else {
         Ui.shadow(g, this.prevX, this.prevY, this.prevW, this.prevH, 6);
      }

      g.blit(this.preview.id(), this.prevX, this.prevY, this.prevW, this.prevH, 0.0F, 0.0F, this.preview.w, this.preview.h, this.preview.w, this.preview.h);
      if (this.tab == 2 && this.sel >= 0 && this.sel < this.t.fields.size()) {
         PassportTemplate.Field f = this.t.fields.get(this.sel);
         this.box(g, f.x, f.y, f.w, f.h, -2641559, true);
      }

      if (this.tab >= 3 && (!cover || this.coverMode != 0)) {
         for (int i = 0; i < els.size(); i++) {
            DesignElement e = els.get(i);
            if (i != this.elSel) {
               this.box(g, e.x, e.y, e.w, Math.max(e.h, 2), 1442840575, false);
            }
         }

         if (this.elSel >= 0 && this.elSel < els.size()) {
            DesignElement e = els.get(this.elSel);
            this.box(g, e.x, e.y, e.w, Math.max(e.h, 2), -2641559, true);
         }
      }

      int ty = this.prevY + this.prevH + 12;
      String hint = this.tab == 2
         ? L.t("Перетащите поле мышью; уголок справа снизу меняет размер.")
         : (this.tab >= 3 ? L.t("Тащите элемент мышью; уголок — размер, стрелки — сдвиг.") : L.t("Перетащите картинку в окно игры, чтобы задать эмблему."));
      g.drawCenteredString(this.font, hint, this.cardX + this.cardW / 2, ty, -6253953);
      g.drawCenteredString(this.font, L.t("Шаблон попадёт на утверждение администрации."), this.cardX + this.cardW / 2, ty + 11, -9543342);
      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   private void box(GuiGraphics g, int ex, int ey, int ew, int eh, int color, boolean handle) {
      double k = this.scale();
      int x1 = this.prevX + (int)((double)ex * k);
      int y1 = this.prevY + (int)((double)ey * k);
      int x2 = this.prevX + (int)((double)(ex + ew) * k);
      int y2 = this.prevY + (int)((double)(ey + eh) * k);
      g.enableScissor(this.prevX, this.prevY, this.prevX + this.prevW, this.prevY + this.prevH);
      g.renderOutline(x1 - 1, y1 - 1, x2 - x1 + 2, y2 - y1 + 2, color);
      if (handle) {
         g.fill(x2 - 4, y2 - 4, x2 + 2, y2 + 2, color);
      }

      g.disableScissor();
   }

   private int hitElement(double mx, double my) {
      List<DesignElement> els = this.elements();
      double px = (mx - (double)this.prevX) / this.scale();
      double py = (my - (double)this.prevY) / this.scale();
      double pad = 4.0 / this.scale();
      if (this.elSel >= 0 && this.elSel < els.size() && inside(els.get(this.elSel), px, py, pad)) {
         return this.elSel;
      } else {
         for (int i = els.size() - 1; i >= 0; i--) {
            if (inside(els.get(i), px, py, pad)) {
               return i;
            }
         }

         return -1;
      }
   }

   private static boolean inside(DesignElement e, double px, double py, double pad) {
      return px >= (double)e.x - pad && px <= (double)(e.x + e.w) + pad && py >= (double)e.y - pad && py <= (double)(e.y + Math.max(e.h, 1)) + pad;
   }

   private int hitField(double mx, double my) {
      double px = (mx - (double)this.prevX) / this.scale();
      double py = (my - (double)this.prevY) / this.scale();

      for (int i = this.t.fields.size() - 1; i >= 0; i--) {
         PassportTemplate.Field f = this.t.fields.get(i);
         if (px >= (double)f.x && px <= (double)(f.x + f.w) && py >= (double)f.y && py <= (double)(f.y + f.h)) {
            return i;
         }
      }

      return -1;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      boolean inPrev = mx >= (double)this.prevX && mx < (double)(this.prevX + this.prevW) && my >= (double)this.prevY && my < (double)(this.prevY + this.prevH);
      if (button == 0 && inPrev && this.tab >= 3) {
         int i = this.hitElement(mx, my);
         if (i >= 0) {
            DesignElement e = this.elements().get(i);
            double px = (mx - (double)this.prevX) / this.scale();
            double py = (my - (double)this.prevY) / this.scale();
            double c = 8.0 / this.scale();
            boolean corner = px >= (double)(e.x + e.w) - c && py >= (double)(e.y + Math.max(e.h, 1)) - c && e.type != 3;
            if (i != this.elSel || this.tab == 3 && this.t.book() && this.coverMode == 0) {
               this.elSel = i;
               this.coverMode = 1;
               this.rebuildWidgets();
            }

            this.dragMode = corner ? 2 : 1;
            this.dragOffX = px - (double)e.x;
            this.dragOffY = py - (double)e.y;
            return true;
         } else if (super.mouseClicked(mx, my, button)) {
            return true;
         } else {
            if (this.elSel != -1) {
               this.elSel = -1;
               this.rebuildWidgets();
            }

            return true;
         }
      } else {
         if (button == 0 && inPrev && this.tab < 3) {
            int i = this.hitField(mx, my);
            if (i >= 0) {
               PassportTemplate.Field f = this.t.fields.get(i);
               double px = (mx - (double)this.prevX) / this.scale();
               double py = (my - (double)this.prevY) / this.scale();
               boolean corner = px >= (double)(f.x + f.w - 10) && py >= (double)(f.y + f.h - 10);
               if (i != this.sel || this.tab != 2) {
                  this.sel = i;
                  this.tab = 2;
                  this.fieldScroll = Math.max(0, this.sel / 2 - this.fieldsListRows() + 1);
                  this.dirty = true;
                  this.rebuildWidgets();
               }

               this.dragMode = corner ? 2 : 1;
               this.dragOffX = px - (double)f.x;
               this.dragOffY = py - (double)f.y;
               return true;
            }
         }

         return super.mouseClicked(mx, my, button);
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragMode != 0 && this.tab >= 3) {
         List<DesignElement> els = this.elements();
         if (this.elSel >= 0 && this.elSel < els.size()) {
            DesignElement e = els.get(this.elSel);
            double px = (mx - (double)this.prevX) / this.scale();
            double py = (my - (double)this.prevY) / this.scale();
            int W = this.designW();
            int H = this.designH();
            if (this.dragMode == 1) {
               e.x = snap(Math.max((double)(-e.w) / 2.0, Math.min((double)W - (double)e.w / 2.0, px - this.dragOffX)));
               e.y = snap(Math.max((double)(-e.h) / 2.0, Math.min((double)H - (double)e.h / 2.0, py - this.dragOffY)));
            } else {
               e.w = Math.max(6, Math.min(W, snap(px - (double)e.x)));
               if (e.type != 3) {
                  e.h = Math.max(6, Math.min(H, snap(py - (double)e.y)));
               }

               if (e.type == 2 || e.type == 1) {
                  int m = Math.max(e.w, e.h);
                  e.w = e.h = Math.min(m, Math.min(W, H));
               }
            }

            this.dirty = true;
            return true;
         } else {
            return true;
         }
      } else if (this.dragMode != 0 && this.sel >= 0 && this.sel < this.t.fields.size()) {
         PassportTemplate.Field f = this.t.fields.get(this.sel);
         double px = (mx - (double)this.prevX) / this.scale();
         double py = (my - (double)this.prevY) / this.scale();
         if (this.dragMode == 1) {
            f.x = snap(Math.max(0.0, Math.min((double)(this.t.w() - f.w), px - this.dragOffX)));
            f.y = snap(Math.max(0.0, Math.min((double)(this.t.h() - f.h), py - this.dragOffY)));
         } else {
            f.w = Math.max(20, Math.min(this.t.w() - f.x, snap(px - (double)f.x)));
            f.h = Math.max(16, Math.min(this.t.h() - f.y, snap(py - (double)f.y)));
         }

         this.dirty = true;
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   public boolean mouseReleased(double mx, double my, int button) {
      this.dragMode = 0;
      return super.mouseReleased(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
      if (this.tab == 2 && mx < (double)(this.formX + this.formW)) {
         this.fieldScroll = this.fieldScroll - (int)Math.signum(delta);
         this.rebuildWidgets();
         return true;
      } else {
         return super.mouseScrolled(mx, my, scrollX, delta);
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      boolean typing = this.getFocused() instanceof UiField;
      if (this.tab >= 3 && !typing && this.elSel >= 0 && this.elSel < this.elements().size()) {
         DesignElement e = this.elements().get(this.elSel);
         int step = (mods & 1) != 0 ? 10 : 1;
         switch (key) {
            case 261:
               this.elements().remove(this.elSel);
               this.elSel = Math.min(this.elSel, this.elements().size() - 1);
               this.dirtyRebuild();
               return true;
            case 262:
               e.x += step;
               break;
            case 263:
               e.x -= step;
               break;
            case 264:
               e.y += step;
               break;
            case 265:
               e.y -= step;
               break;
            default:
               return super.keyPressed(key, scan, mods);
         }

         this.dirty = true;
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   private static int snap(double v) {
      return (int)Math.round(v / 2.0) * 2;
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }
}
