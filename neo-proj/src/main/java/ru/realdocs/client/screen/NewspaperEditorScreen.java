package ru.realdocs.client.screen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.realdocs.L;
import ru.realdocs.Plat;
import ru.realdocs.client.ClientUploads;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PaperCache;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.render.Fonts;
import ru.realdocs.client.render.PaperRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiButton;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.model.PaperElement;
import ru.realdocs.model.PaperModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.Uploads;

/** Free-form page layout editor for newspapers, leaflets and posters. */
public final class NewspaperEditorScreen extends UiScreen {
   private static final int[] INKS = new int[]{0x1E1E22, 0x55555C, 0x8E1B1B, 0x1F3E8C, 0x2E6B35, 0x6B4428, 0xC9A13B, 0xFFFFFF};
   private static final String SAMPLE = "Здесь будет текст статьи. Выделите блок и пишите слева: **жирный**, __курсив__, строка с «# » — подзаголовок. Текст сам перетекает по колонкам.";
   private static PageTexture fontAtlas;
   private static final int ATLAS_ROW = 56;
   private static final int ATLAS_W = 720;
   private final InteractionHand hand;
   private final String loadId;
   private PaperModel m;
   private boolean choosing;
   private int page;
   private int sel = -1;
   private final PageTexture preview = new PageTexture();
   private boolean dirty = true;
   private long lastRender;
   private int renderedPx;
   private Set<Integer> overflow = Set.of();
   private double zoom = 1.0;
   private double panX;
   private double panY;
   private int formX = 8;
   private int formY = 8;
   private int formW;
   private int formH;
   private int cvX;
   private int cvY;
   private int cvW;
   private int cvH;
   private double scale = 1.0;
   private double pageX;
   private double pageY;
   private int dragMode;
   private double dragOffX;
   private double dragOffY;
   private double dragMouseX;
   private double dragMouseY;
   private double dragPanX;
   private double dragPanY;
   private final Deque<PaperModel> undo = new ArrayDeque<>();
   private final Deque<PaperModel> redo = new ArrayDeque<>();
   private boolean textSnapped;
   private int scroll;
   private int contentTop;
   private int contentBottom;
   private final List<AbstractWidget> formWidgets = new ArrayList<>();
   private final List<Object[]> labels = new ArrayList<>();
   private boolean fontPicker;
   private int fpX;
   private int fpY;
   private int fpW;
   private int fpRow;
   private String status = "";
   private int statusColor = -6253953;
   private boolean busy;
   private long publishArm;
   private long templateArm;
   private int templateArmFmt = -1;
   private long closeArm;
   private boolean changed;
   private long lastClick;
   private int lastClickSel = -2;
   private final Map<PaperElement, String> sources = new IdentityHashMap<>();
   private PaperElement loadingFor;
   private UiTextArea textArea;
   private int pageLabelX;

   public NewspaperEditorScreen(InteractionHand hand, PaperCodec.Ref ref) {
      super(Component.literal(L.t("Вёрстка")));
      this.hand = hand;
      this.loadId = ref.id;
      if (ref.id.isEmpty()) {
         this.choosing = true;
         this.m = new PaperModel();
      }
   }

   // ----------------------------------------------------------------- helpers

   private List<PaperElement> els() {
      return this.m.page(this.page);
   }

   private PaperElement selected() {
      List<PaperElement> els = this.els();
      return this.sel >= 0 && this.sel < els.size() ? els.get(this.sel) : null;
   }

   private void snap() {
      if (this.m != null) {
         this.undo.push(this.m.copy());

         while (this.undo.size() > 40) {
            this.undo.removeLast();
         }

         this.redo.clear();
         this.changed = true;
      }
   }

   private void touch() {
      this.dirty = true;
      this.changed = true;
   }

   private void dirtyRebuild() {
      this.touch();
      this.rebuildWidgets();
   }

   private void select(int i) {
      if (i != this.sel) {
         this.sel = i;
         this.textSnapped = false;
         this.fontPicker = false;
         this.scroll = 0;
         this.rebuildWidgets();
      }
   }

   private static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   // ----------------------------------------------------------------- widgets

   protected void init() {
      this.labels.clear();
      this.formWidgets.clear();
      this.textArea = null;
      this.formW = clamp(this.width * 34 / 100, 196, 250);
      this.formH = this.height - 16;
      this.cvX = this.formX + this.formW + 8;
      this.cvW = this.width - this.cvX - 8;
      this.cvY = 54;
      this.cvH = this.height - this.cvY - 34;
      if (this.m == null) {
         this.button(L.t("Закрыть"), this.width / 2 - 40, this.height / 2 + 20, 80, this::onClose).ghost();
      } else if (this.choosing) {
         int w = Math.min(320, this.width - 40);
         int x = (this.width - w) / 2;
         int y = this.height / 2 - 50;
         String[] names = new String[]{L.t("Газета"), L.t("Листовка"), L.t("Плакат"), L.t("Чистый лист")};

         for (int i = 0; i < 4; i++) {
            int k = i;
            int bw = (w - 9) / 4;
            this.button(names[i], x + i * (bw + 3), y + 40, bw, 28, () -> this.choose(k)).primary();
         }

         this.button(L.t("Закрыть"), x + w - 70, y + 82, 70, this::onClose).ghost();
      } else {
         this.buildToolbar();
         this.buildBottom();
         this.contentTop = this.formY + 26;
         this.contentBottom = this.formY + this.formH - 6;
         int y = this.contentTop - this.scroll;
         int x = this.formX + 10;
         int w = this.formW - 20;
         int end = this.selected() == null ? this.buildIssueForm(x, y, w) : this.buildElementForm(x, y, w);
         int contentH = end - y;
         int maxScroll = Math.max(0, contentH - (this.contentBottom - this.contentTop));
         if (this.scroll > maxScroll) {
            this.scroll = maxScroll;
            this.rebuildWidgets();
            return;
         }

         for (AbstractWidget wd : this.formWidgets) {
            int top = wd instanceof UiField f ? f.outerY() : wd.getY();
            int h = wd instanceof UiField f ? f.outerH() : wd.getHeight();
            boolean vis = top >= this.contentTop && top + h <= this.contentBottom;
            wd.visible = vis;
            wd.active = vis && wd.active;
         }
      }
   }

   private <T extends AbstractWidget> T form(T w) {
      this.formWidgets.add(w);
      return w;
   }

   private UiButton fbtn(String label, int x, int y, int w, int h, Runnable r) {
      return this.form(this.button(label, x, y, w, h, r));
   }

   private void label(String text, int x, int y) {
      this.labels.add(new Object[]{text, x, y, null});
   }

   private void valueLabel(Supplier<String> v, int cx, int y) {
      this.labels.add(new Object[]{null, cx, y, v});
   }

   private void stepper(int x, int y, int w, String label, Supplier<String> value, IntConsumer delta) {
      this.label(label, x + 1, y);
      this.fbtn("−", x, y + 10, 18, 18, () -> {
         this.snapOnce();
         delta.accept(-1);
         this.touch();
      });
      this.fbtn("+", x + w - 18, y + 10, 18, 18, () -> {
         this.snapOnce();
         delta.accept(1);
         this.touch();
      });
      this.valueLabel(value, x + w / 2, y + 15);
   }

   private long lastStepSnap;

   /** Repeated clicks on steppers form one undo step. */
   private void snapOnce() {
      long now = System.currentTimeMillis();
      if (now - this.lastStepSnap > 1200L) {
         this.snap();
      }

      this.lastStepSnap = now;
   }

   private void buildToolbar() {
      int x = this.cvX;
      int y = 8;
      int fitW = 56;
      this.button("◀", x, y, 18, 18, () -> this.goPage(this.page - 1)).enabled(this.page > 0);
      this.pageLabelX = x + 18 + 30;
      this.button("▶", x + 78, y, 18, 18, () -> this.goPage(this.page + 1)).enabled(this.page < this.m.pageCount() - 1);
      int px = x + 102;
      int rest = this.cvW - 102 - fitW - 6;
      int bw = Math.max(30, (rest - 6) / 3);
      this.button(L.t("+ Страница"), px, y, bw, 18, () -> {
         if (this.m.pages.size() < PaperModel.MAX_PAGES) {
            this.snap();
            this.m.pages.add(this.page + 1, new ArrayList<>());
            this.goPage(this.page + 1);
         }
      }).enabled(this.m.pages.size() < PaperModel.MAX_PAGES);
      this.button(L.t("Копия стр."), px + bw + 3, y, bw, 18, () -> {
         if (this.m.pages.size() < PaperModel.MAX_PAGES) {
            this.snap();
            List<PaperElement> c = new ArrayList<>();

            for (PaperElement e : this.els()) {
               c.add(e.copy());
            }

            this.m.pages.add(this.page + 1, c);
            this.goPage(this.page + 1);
         }
      }).enabled(this.m.pages.size() < PaperModel.MAX_PAGES);
      this.button(L.t("Удалить стр."), px + 2 * (bw + 3), y, bw, 18, () -> {
         if (this.m.pages.size() > 1) {
            this.snap();
            this.m.pages.remove(this.page);
            this.goPage(Math.min(this.page, this.m.pages.size() - 1));
         }
      }).danger().enabled(this.m.pages.size() > 1);
      this.button(L.t("Вписать"), this.cvX + this.cvW - fitW, y, fitW, 18, () -> {
         this.zoom = 1.0;
         this.panX = 0.0;
         this.panY = 0.0;
      }).ghost();
      int ay = 30;
      int n = PaperElement.TYPE_NAMES.length;
      int aw = (this.cvW - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int type = i;
         this.button("+ " + L.t(PaperElement.TYPE_NAMES[i]), this.cvX + i * (aw + 3), ay, i == n - 1 ? this.cvW - (n - 1) * (aw + 3) : aw, 18, () -> this.add(type))
            .enabled(this.els().size() < PaperModel.MAX_ELEMENTS);
      }
   }

   private void buildBottom() {
      int y = this.height - 26;
      int right = this.cvX + this.cvW;
      this.button(L.t("Закрыть"), right - 64, y, 64, 20, this::onClose).ghost();
      this.button(L.t("Опубликовать"), right - 64 - 4 - 96, y, 96, 20, () -> this.save(true)).primary().enabled(!this.busy);
      this.button(L.t("Сохранить"), right - 64 - 4 - 96 - 4 - 76, y, 76, 20, () -> this.save(false)).enabled(!this.busy);
      this.button("↶", this.cvX, y, 20, 20, this::undo).enabled(!this.undo.isEmpty()).hint("");
      this.button("↷", this.cvX + 23, y, 20, 20, this::redo).enabled(!this.redo.isEmpty());
   }

   private int buildIssueForm(int x, int y, int w) {
      this.label(L.t("Выпуск"), x + 1, y);
      y += 22;
      UiField name = this.form(this.field(x, y, w, L.t("например, Вестник Кубограда"), this.m.name, PaperModel.NAME, s -> {
         if (!s.equals(this.m.name)) {
            this.m.name = s;
            this.touch();
         }
      }));
      name.label(L.t("Название издания  {name}"));
      y += 34;
      UiField price = this.form(this.field(x, y, w, L.t("цена, слоган или награда"), this.m.price, PaperModel.PRICE, s -> {
         if (!s.equals(this.m.price)) {
            this.m.price = s;
            this.touch();
         }
      }));
      price.label(L.t("Цена / подзаголовок  {price}"));
      y += 28;
      this.label(L.t("Вид издания"), x + 1, y);
      y += 10;
      int third = (w - 6) / 3;

      for (int i = 0; i < 3; i++) {
         int k = i;
         this.fbtn(PaperModel.FORMATS[i], x + i * (third + 3), y, i == 2 ? w - 2 * (third + 3) : third, 18, () -> {
            this.snap();
            this.m.format = k;
            this.dirtyRebuild();
         }).selected(this.m.format == i);
      }

      y += 24;
      this.label(L.t("Бумага"), x + 1, y);
      y += 10;
      int nt = PaperModel.TONES.length;
      int tw = (w - 3 * (nt - 1)) / nt;

      for (int i = 0; i < nt; i++) {
         int k = i;
         this.fbtn(PaperModel.TONES[i], x + i * (tw + 3), y, i == nt - 1 ? w - (nt - 1) * (tw + 3) : tw, 18, () -> {
            this.snap();
            this.m.tone = k;
            this.dirtyRebuild();
         }).swatch(PaperModel.TONE_RGB[i]).selected(this.m.tone == i);
      }

      y += 26;
      this.label(L.t("Сверстать заново по шаблону"), x + 1, y);
      y += 10;

      for (int i = 0; i < 3; i++) {
         int k = i;
         this.fbtn(PaperModel.FORMATS[i], x + i * (third + 3), y, i == 2 ? w - 2 * (third + 3) : third, 18, () -> this.applyTemplate(k)).ghost();
      }

      y += 26;
      String[] help = new String[]{
         L.t("Подстановки: {name} {issue} {date} {price} {editor} {page} {pages}"),
         L.t("В статье: **жирный**, __курсив__, «# » — подзаголовок"),
         L.t("Колёсико — масштаб, правая кнопка — двигать лист"),
         L.t("Ctrl+Z — отменить, Ctrl+D — копия, Del — удалить"),
         L.t("Картинку можно перетащить из папки прямо в окно")
      };

      for (String h : help) {
         for (String l : Ui.wrap(this.font, h, w)) {
            this.labels.add(new Object[]{l, x + 1, y, null, Integer.valueOf(-9543342)});
            y += 10;
         }

         y += 3;
      }

      return y;
   }

   private int buildElementForm(int x, int y, int w) {
      PaperElement e = this.selected();
      this.fbtn(L.t("← Выпуск"), x, y, 64, 16, () -> this.select(-1)).ghost();
      this.fbtn("◀", x + w - 40, y, 18, 16, () -> this.select((this.sel - 1 + this.els().size()) % this.els().size()));
      this.fbtn("▶", x + w - 18, y, 18, 16, () -> this.select((this.sel + 1) % this.els().size()));
      y += 20;
      this.label(L.t(PaperElement.TYPE_NAMES[e.type]) + "  " + (this.sel + 1) + "/" + this.els().size(), x + 1, y);
      y += 14;
      if (e.textual()) {
         int th = e.type == PaperElement.TEXT ? Math.max(60, Math.min(120, this.height / 4)) : 40;
         UiTextArea ta = new UiTextArea(this.font, x, y, w, th, L.t(e.type == PaperElement.TEXT ? "текст статьи…" : "текст заголовка…"));
         ta.setCharacterLimit(e.type == PaperElement.TEXT ? PaperElement.MAX_TEXT : PaperElement.MAX_TITLE);
         ta.setValue(e.text);
         ta.setValueListener(s -> {
            if (!s.equals(e.text)) {
               if (!this.textSnapped) {
                  this.snap();
                  this.textSnapped = true;
               }

               e.text = s;
               this.touch();
            }
         });
         this.textArea = this.form(this.addRenderableWidget(ta));
         y += th + 6;
         this.fbtn(L.t("Шрифт: ") + L.t(Fonts.NAMES[Fonts.clamp(e.font)]), x, y, w, 18, () -> {
            this.fontPicker = true;
            this.fpW = Math.min(300, this.width - 40);
            this.fpRow = 22;
            this.fpX = Math.min(x, this.width - this.fpW - 10);
            this.fpY = Math.max(10, Math.min(this.height - Fonts.count() * this.fpRow - 30, 30));
         });
         y += 24;
         int sw = (w - 8) / 3;
         this.stepper(x, y, sw, L.t("Размер"), () -> String.valueOf(e.size), d -> e.size = clamp(e.size + d * (e.size >= 30 ? 2 : 1), 5, e.type == PaperElement.TITLE ? 200 : 60));
         this.stepper(x + sw + 4, y, sw, L.t("Интервал"), () -> e.lead + "%", d -> e.lead = clamp(e.lead + d * 5, 80, 220));
         if (e.type == PaperElement.TEXT) {
            this.stepper(x + 2 * (sw + 4), y, w - 2 * (sw + 4), L.t("Колонки"), () -> String.valueOf(e.cols), d -> e.cols = clamp(e.cols + d, 1, 5));
         } else {
            this.stepper(x + 2 * (sw + 4), y, w - 2 * (sw + 4), L.t("Разрядка"), () -> String.valueOf(e.tracking), d -> e.tracking = clamp(e.tracking + d * 2, -10, 60));
         }

         y += 32;
         int bw = (w - 3 * 6) / 7;
         this.toggle(L.t("Ж"), x, y, bw, e, PaperElement.F_BOLD);
         this.toggle(L.t("К"), x + (bw + 3), y, bw, e, PaperElement.F_ITALIC);
         this.toggle(L.t("АБ"), x + 2 * (bw + 3), y, bw, e, PaperElement.F_CAPS);
         String[] al = new String[]{L.t("Л"), L.t("Ц"), L.t("П"), "≡"};

         for (int i = 0; i < 4; i++) {
            int k = i;
            this.fbtn(al[i], x + (3 + i) * (bw + 3), y, i == 3 ? w - 6 * (bw + 3) : bw, 18, () -> {
               this.snap();
               e.align = k;
               this.dirtyRebuild();
            }).selected(e.align == i);
         }

         y += 22;
         if (e.type == PaperElement.TEXT) {
            int tw = (w - 6) / 3;
            this.toggle(L.t("Буквица"), x, y, tw, e, PaperElement.F_DROPCAP);
            this.toggle(L.t("Абзацы"), x + tw + 3, y, tw, e, PaperElement.F_INDENT);
            this.toggle(L.t("Линейки"), x + 2 * (tw + 3), y, w - 2 * (tw + 3), e, PaperElement.F_RULES);
            y += 24;
         }

         y = this.colorRow(x, y, w, e);
      } else if (e.type == PaperElement.IMAGE) {
         UiField src = this.form(this.field(x, y + 10, w - 64, L.t("ссылка или путь к файлу"), this.sources.getOrDefault(e, ""), 600, s -> this.sources.put(e, s)));
         src.label(L.t("Картинка (PNG, JPG)"));
         this.fbtn(L.t("Загрузить"), x + w - 60, y + 10, 60, 18, () -> this.startImage(e, this.sources.getOrDefault(e, ""))).primary()
            .enabled(this.loadingFor == null);
         y += 32;
         this.labels.add(new Object[]{null, x + 1, y, (Supplier<String>)() -> this.imageStatus(e), Integer.valueOf(-9543342), Boolean.TRUE});
         y += 14;
         this.label(L.t("Как вписать"), x + 1, y);
         y += 10;
         int third = (w - 6) / 3;

         for (int i = 0; i < 3; i++) {
            int k = i;
            this.fbtn(PaperElement.FIT_NAMES[i], x + i * (third + 3), y, i == 2 ? w - 2 * (third + 3) : third, 18, () -> {
               this.snap();
               e.fit = k;
               this.dirtyRebuild();
            }).selected(e.fit == i);
         }

         y += 24;
         this.label(L.t("Печать"), x + 1, y);
         y += 10;
         int nf = PaperElement.FILTER_NAMES.length;
         int fw = (w - 3 * (nf - 1)) / nf;

         for (int i = 0; i < nf; i++) {
            int k = i;
            this.fbtn(PaperElement.FILTER_NAMES[i], x + i * (fw + 3), y, i == nf - 1 ? w - (nf - 1) * (fw + 3) : fw, 18, () -> {
               this.snap();
               e.filter = k;
               this.dirtyRebuild();
            }).selected(e.filter == i);
         }

         y += 24;
         this.toggle(L.t("Рамка вокруг"), x, y, (w - 3) / 2, e, PaperElement.F_BORDER);
         y += 24;
      } else {
         if (e.type == PaperElement.FRAME) {
            this.stepper(x, y, 80, L.t("Толщина"), () -> String.valueOf(e.thick), d -> e.thick = clamp(e.thick + d, 1, 24));
            y += 32;
         }

         y = this.colorRow(x, y, w, e);
      }

      this.stepper(x, y, 90, L.t("Плотность"), () -> e.alpha + "%", d -> e.alpha = clamp(e.alpha + d * 10, 5, 100));
      y += 34;
      int q = (w - 9) / 4;
      List<PaperElement> els = this.els();
      this.fbtn(L.t("Выше"), x, y, q, 18, () -> {
         if (this.sel < els.size() - 1) {
            this.snap();
            els.add(this.sel + 1, els.remove(this.sel));
            this.sel++;
            this.dirtyRebuild();
         }
      }).enabled(this.sel < els.size() - 1);
      this.fbtn(L.t("Ниже"), x + q + 3, y, q, 18, () -> {
         if (this.sel > 0) {
            this.snap();
            els.add(this.sel - 1, els.remove(this.sel));
            this.sel--;
            this.dirtyRebuild();
         }
      }).enabled(this.sel > 0);
      this.fbtn(L.t("Копия"), x + 2 * (q + 3), y, q, 18, this::duplicate).enabled(els.size() < PaperModel.MAX_ELEMENTS);
      this.fbtn(L.t("Удалить"), x + 3 * (q + 3), y, w - 3 * (q + 3), 18, this::deleteSelected).danger();
      y += 24;
      return y;
   }

   private int colorRow(int x, int y, int w, PaperElement e) {
      this.label(L.t("Цвет"), x + 1, y);
      y += 10;
      int n = INKS.length;
      int sw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int c = INKS[i];
         this.fbtn("", x + i * (sw + 3), y, sw, 18, () -> {
            this.snap();
            e.color = c;
            this.dirtyRebuild();
         }).swatch(c).selected((e.color & 0xFFFFFF) == c);
      }

      return y + 26;
   }

   private void toggle(String label, int x, int y, int w, PaperElement e, int flag) {
      this.fbtn(label, x, y, w, 18, () -> {
         this.snap();
         e.set(flag, !e.has(flag));
         this.dirtyRebuild();
      }).selected(e.has(flag));
   }

   private String imageStatus(PaperElement e) {
      if (this.loadingFor == e) {
         return L.t("Загрузка и отправка на сервер…");
      } else if (e.asset.isEmpty()) {
         return L.t("Не выбрана. Можно перетащить файл в окно.");
      } else {
         return L.t("Загружена: ") + e.asset;
      }
   }

   // ----------------------------------------------------------------- actions

   private void choose(int k) {
      String sample = L.t(SAMPLE);
      if (k < 3) {
         this.m = PaperModel.blank(k, sample);
      } else {
         this.m = new PaperModel();
         this.m.pages.add(new ArrayList<>());
         this.m.tone = 1;
      }

      this.choosing = false;
      this.changed = true;
      this.dirty = true;
      Sounds.play(Sounds.PAPER, 1.0F, 0.7F);
      this.rebuildWidgets();
   }

   private void applyTemplate(int fmt) {
      long now = System.currentTimeMillis();
      if (this.templateArmFmt == fmt && now - this.templateArm < 3000L) {
         this.snap();
         PaperModel t = PaperModel.blank(fmt, L.t(SAMPLE));
         this.m.pages.clear();
         this.m.pages.addAll(t.pages);
         this.m.format = fmt;
         this.m.tone = t.tone;
         this.page = 0;
         this.sel = -1;
         this.templateArmFmt = -1;
         this.dirtyRebuild();
         this.ok(L.t("Вёрстка заменена шаблоном. Ctrl+Z — вернуть."));
      } else {
         this.templateArmFmt = fmt;
         this.templateArm = now;
         this.info(L.t("Все страницы заменятся шаблоном. Нажмите ещё раз, чтобы подтвердить."));
      }
   }

   private void goPage(int p) {
      int np = clamp(p, 0, this.m.pageCount() - 1);
      if (np != this.page) {
         Sounds.play(Sounds.PAGE, 1.0F, 0.6F);
      }

      this.page = np;
      this.sel = -1;
      this.dirty = true;
      this.scroll = 0;
      this.rebuildWidgets();
   }

   private void add(int type) {
      if (this.els().size() < PaperModel.MAX_ELEMENTS) {
         this.snap();
         PaperElement e = switch (type) {
            case PaperElement.TEXT -> {
               PaperElement t = new PaperElement(type, 60, 300, 300, 240);
               t.text = L.t(SAMPLE);
               t.set(PaperElement.F_INDENT, true);
               yield t;
            }
            case PaperElement.TITLE -> {
               PaperElement t = new PaperElement(type, 60, 240, 640, 50);
               t.text = L.t("Заголовок");
               t.size = 34;
               t.align = 0;
               t.set(PaperElement.F_BOLD, true);
               yield t;
            }
            case PaperElement.IMAGE -> {
               PaperElement t = new PaperElement(type, 180, 300, 400, 280);
               t.filter = PaperElement.FILTER_HALFTONE;
               yield t;
            }
            case PaperElement.LINE -> new PaperElement(type, 40, 400, 680, 2);
            case PaperElement.FRAME -> new PaperElement(type, 40, 300, 340, 260);
            default -> {
               PaperElement t = new PaperElement(type, 0, 380, 760, 60);
               t.color = 0x8E1B1B;
               t.alpha = 90;
               yield t;
            }
         };
         if (type == PaperElement.TITLE || type == PaperElement.TEXT) {
            e.font = this.lastFont();
         }

         this.els().add(e);
         this.sel = this.els().size() - 1;
         this.textSnapped = true;
         this.scroll = 0;
         this.dirtyRebuild();
      }
   }

   private int lastFont() {
      List<PaperElement> els = this.els();

      for (int i = els.size() - 1; i >= 0; i--) {
         if (els.get(i).type == PaperElement.TEXT) {
            return els.get(i).font;
         }
      }

      return Fonts.SERIF;
   }

   private void duplicate() {
      PaperElement e = this.selected();
      if (e != null && this.els().size() < PaperModel.MAX_ELEMENTS) {
         this.snap();
         PaperElement c = e.copy();
         c.x += 12;
         c.y += 12;
         this.els().add(c);
         this.sel = this.els().size() - 1;
         this.dirtyRebuild();
      }
   }

   private void deleteSelected() {
      if (this.selected() != null) {
         this.snap();
         this.els().remove(this.sel);
         this.sel = -1;
         this.dirtyRebuild();
      }
   }

   private void undo() {
      if (!this.undo.isEmpty()) {
         this.redo.push(this.m.copy());
         this.m = this.undo.pop();
         this.afterHistory();
      }
   }

   private void redo() {
      if (!this.redo.isEmpty()) {
         this.undo.push(this.m.copy());
         this.m = this.redo.pop();
         this.afterHistory();
      }
   }

   private void afterHistory() {
      this.page = Math.min(this.page, this.m.pageCount() - 1);
      if (this.sel >= this.els().size()) {
         this.sel = -1;
      }

      this.textSnapped = false;
      this.dirtyRebuild();
   }

   private void startImage(PaperElement e, String input) {
      if (this.loadingFor == null && input != null && !input.isBlank()) {
         this.loadingFor = e;
         this.rebuildWidgets();
         WebImage.load(input).thenAccept(img -> this.minecraft.execute(() -> {
            try {
               byte[] png = ImageUtil.pngUnder(PaperRenderer.limit(img, 1024), Uploads.MAX_IMAGE - 2000, 1024);
               ClientUploads.send(Uploads.KIND_IMAGE, png, (ok, id, msg) -> {
                  this.loadingFor = null;
                  if (ok) {
                     this.snap();
                     e.asset = id;
                     PassportCache.INSTANCE.putAsset(id, true, png);
                     this.ok(L.t("Картинка загружена: ") + png.length / 1024 + L.t(" КБ"));
                  } else {
                     this.error(msg);
                  }

                  this.dirtyRebuild();
               });
            } catch (Exception var5) {
               this.loadingFor = null;
               this.error(L.t("Ошибка: ") + var5.getMessage());
               this.rebuildWidgets();
            }
         })).exceptionally(ex -> {
            this.minecraft.execute(() -> {
               this.loadingFor = null;
               this.error(L.t("Ошибка: ") + WebImage.reason(ex));
               this.rebuildWidgets();
            });
            return null;
         });
      }
   }

   public void onFilesDrop(List<Path> files) {
      if (this.m != null && !this.choosing && !files.isEmpty()) {
         PaperElement e = this.selected();
         if (e == null || e.type != PaperElement.IMAGE) {
            this.add(PaperElement.IMAGE);
            e = this.selected();
         }

         if (e != null && e.type == PaperElement.IMAGE) {
            String p = files.get(0).toString();
            this.sources.put(e, p);
            this.startImage(e, p);
         }
      }
   }

   private void save(boolean publish) {
      if (this.busy || this.m == null) {
         return;
      }

      if (publish) {
         long now = System.currentTimeMillis();
         if (this.m.name.isBlank()) {
            this.error(L.t("Укажите название издания (вкладка «Выпуск»)."));
            this.select(-1);
            return;
         }

         if (now - this.publishArm > 4000L) {
            this.publishArm = now;
            this.info(L.t("После публикации выпуск не меняется. Нажмите «Опубликовать» ещё раз."));
            return;
         }
      }

      this.m.sanitize();
      byte[] data;
      try {
         data = Plat.pack(PaperCodec.write(this.m));
      } catch (Exception var5) {
         this.error(L.t("Ошибка: ") + var5.getMessage());
         return;
      }

      if (data.length > Uploads.MAX_LAYOUT) {
         this.error(L.t("Вёрстка слишком большая: сократите тексты."));
         return;
      }

      this.busy = true;
      this.status = publish ? L.t("Публикация…") : L.t("Сохранение…");
      this.statusColor = -868018;
      this.rebuildWidgets();
      int token = ClientUploads.send(Uploads.KIND_LAYOUT, data, (ok, id, msg) -> {
         this.busy = false;
         if (ok) {
            this.m.id = id;
            this.changed = false;
            PaperCache.INSTANCE.local(this.m);
            this.status = msg;
            this.statusColor = -10628481;
            if (publish) {
               Sounds.play(Sounds.STAMP, 1.0F, 0.8F);
               this.minecraft.setScreen(null);
               return;
            }

            this.ok(msg);
         } else {
            this.status = msg;
            this.statusColor = -1088669;
            this.error(msg);
         }

         this.rebuildWidgets();
      });
      Net.sendToServer(new MorePackets.PaperCommit(this.hand, token, publish));
   }

   public void onClose() {
      long now = System.currentTimeMillis();
      if (this.changed && !this.choosing && now - this.closeArm > 3000L) {
         this.closeArm = now;
         this.info(L.t("Есть несохранённые изменения. Закройте ещё раз, чтобы выйти без сохранения."));
      } else {
         super.onClose();
      }
   }

   // ----------------------------------------------------------------- rendering

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      if (this.m == null) {
         this.tryLoad();
      }

      if (this.m == null) {
         String s = PaperCache.INSTANCE.missing(this.loadId) ? L.t("Вёрстка не найдена или недоступна.") : L.t("Загрузка вёрстки…");
         g.drawCenteredString(this.font, s, this.width / 2, this.height / 2 - 10, -3555408);
         super.render(g, mx, my, pt);
         this.renderToast(g);
         return;
      }

      if (this.choosing) {
         int w = Math.min(320, this.width - 40);
         int x = (this.width - w) / 2;
         int y = this.height / 2 - 50;
         Ui.card(g, this.font, x - 10, y - 10, w + 20, 126, L.t("Что будем верстать?"));
         g.drawString(this.font, L.t("Шаблон можно сменить позже на вкладке «Выпуск»."), x, y + 22, -6253953, false);
         super.render(g, mx, my, pt);
         this.renderToast(g);
         return;
      }

      Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, L.t("Вёрстка") + (this.m.name.isEmpty() ? "" : " · " + this.m.name));
      g.fill(this.cvX, this.cvY, this.cvX + this.cvW, this.cvY + this.cvH, 0xFF15171B);
      Ui.outline(g, this.cvX, this.cvY, this.cvW, this.cvH, -12767194);
      this.layoutPage();
      this.updatePreview();
      g.enableScissor(this.cvX + 1, this.cvY + 1, this.cvX + this.cvW - 1, this.cvY + this.cvH - 1);
      int px = (int)Math.round(this.pageX);
      int py = (int)Math.round(this.pageY);
      int pw = (int)Math.round(PaperModel.W * this.scale);
      int ph = (int)Math.round(PaperModel.H * this.scale);
      Ui.shadow(g, px, py, pw, ph, 6);
      if (this.preview.ready()) {
         g.blit(this.preview.id(), px, py, pw, ph, 0.0F, 0.0F, this.preview.w, this.preview.h, this.preview.w, this.preview.h);
      }

      List<PaperElement> els = this.els();
      int hover = this.dragMode == 0 && this.inCanvas(mx, my) ? this.hit(mx, my) : -1;

      for (int i = 0; i < els.size(); i++) {
         boolean s = i == this.sel;
         int color = s ? -2641559 : (i == hover ? -1426063361 : 0x33000000);
         this.box(g, els.get(i), color, s);
         if (this.overflow.contains(i)) {
            PaperElement e = els.get(i);
            int x2 = (int)Math.round(this.pageX + (e.x + e.w) * this.scale);
            int y2 = (int)Math.round(this.pageY + (e.y + e.h) * this.scale);
            g.fill(x2 - 11, y2 - 9, x2 - 1, y2 - 1, -1088669);
            g.drawString(this.font, "+", x2 - 9, y2 - 9, -1, false);
         }
      }

      g.disableScissor();
      g.drawString(this.font, (this.page + 1) + " / " + this.m.pageCount(), this.pageLabelX - this.font.width((this.page + 1) + " / " + this.m.pageCount()) / 2, 13, -1183755, false);
      String zs = Math.round(this.zoom * 100.0) + "%";
      g.drawString(this.font, zs, this.cvX + this.cvW - 56 - 6 - this.font.width(zs), 13, -9543342, false);
      if (!this.overflow.isEmpty()) {
         g.drawString(this.font, L.t("Красный «+» — текст не поместился в блок"), this.cvX + 48, this.height - 20, -1088669, false);
      } else if (!this.status.isEmpty()) {
         g.drawString(this.font, Ui.ellipsize(this.font, this.status, this.cvW - 330), this.cvX + 48, this.height - 20, this.statusColor, false);
      }

      for (Object[] l : this.labels) {
         int y = (Integer)l[2];
         if (y >= this.contentTop - 2 && y <= this.contentBottom - 8) {
            int col = l.length > 4 && l[4] != null ? (Integer)l[4] : -6253953;
            if (l[0] != null) {
               g.drawString(this.font, (String)l[0], (Integer)l[1], y, col, false);
            } else {
               String v = (String)((Supplier<?>)l[3]).get();
               if (l.length > 5) {
                  g.drawString(this.font, Ui.ellipsize(this.font, v, this.formW - 24), (Integer)l[1], y, col, false);
               } else {
                  g.drawString(this.font, v, (Integer)l[1] - this.font.width(v) / 2, y, -1183755, false);
               }
            }
         }
      }

      super.render(g, mx, my, pt);
      if (this.fontPicker) {
         this.renderFontPicker(g, mx, my);
      }

      this.renderToast(g);
   }

   private void tryLoad() {
      PaperModel loaded = PaperCache.INSTANCE.get(this.loadId);
      if (loaded != null) {
         this.m = loaded.copy();
         this.m.id = this.loadId;
         this.choosing = false;
         this.dirty = true;
         this.rebuildWidgets();
      }
   }

   private void layoutPage() {
      double fit = Math.min((this.cvW - 16.0) / PaperModel.W, (this.cvH - 16.0) / PaperModel.H);
      this.scale = fit * this.zoom;
      double pw = PaperModel.W * this.scale;
      double ph = PaperModel.H * this.scale;
      this.pageX = this.cvX + (this.cvW - pw) / 2.0 + this.panX;
      this.pageY = this.cvY + Math.max(8.0, (this.cvH - ph) / 2.0) + this.panY;
   }

   private void updatePreview() {
      long now = System.currentTimeMillis();
      double gs = this.minecraft.getWindow().getGuiScale();
      int want = clamp((int)Math.round(PaperModel.W * this.scale * gs), 380, 1800);
      boolean sizeChanged = this.renderedPx == 0 || Math.abs(want - this.renderedPx) > this.renderedPx * 0.2;
      int key = PassportCache.version;
      if (key != this.assetsVersion) {
         this.assetsVersion = key;
         this.dirty = true;
      }

      if ((this.dirty || sizeChanged || !this.preview.ready()) && now - this.lastRender > 70L) {
         PaperRenderer.Result r = PaperRenderer.render(this.m, this.page, want, PassportCache.INSTANCE::image, true);
         this.preview.set(r.image);
         this.overflow = r.overflow;
         this.renderedPx = want;
         this.dirty = false;
         this.lastRender = System.currentTimeMillis();
      }
   }

   private int assetsVersion = -1;
   private boolean pendingSnap;

   private void box(GuiGraphics g, PaperElement e, int color, boolean handle) {
      int x1 = (int)Math.round(this.pageX + e.x * this.scale);
      int y1 = (int)Math.round(this.pageY + e.y * this.scale);
      int x2 = (int)Math.round(this.pageX + (e.x + e.w) * this.scale);
      int y2 = (int)Math.round(this.pageY + (e.y + Math.max(e.h, 2)) * this.scale);
      g.renderOutline(x1 - 1, y1 - 1, Math.max(2, x2 - x1 + 2), Math.max(2, y2 - y1 + 2), color);
      if (handle) {
         g.fill(x2 - 3, y2 - 3, x2 + 3, y2 + 3, color);
         g.fill(x2 - 2, y2 - 2, x2 + 2, y2 + 2, -1);
      }
   }

   private static BufferedImage atlas() {
      int n = Fonts.count();
      BufferedImage img = new BufferedImage(ATLAS_W, ATLAS_ROW * n, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      Fonts.hints(g);

      for (int i = 0; i < n; i++) {
         Fonts.Face f = Fonts.face(i, false, false, 30.0F);
         g.setColor(new Color(240, 236, 226));
         f.draw(g, L.t(Fonts.NAMES[i]), 12.0, i * ATLAS_ROW + 38);
         double w = f.width(L.t(Fonts.NAMES[i]));
         Fonts.Face s = Fonts.face(i, false, false, 24.0F);
         g.setColor(new Color(170, 164, 150));
         s.draw(g, "  Aa Ää ß · Яя Ёё 123", 12.0 + w, i * ATLAS_ROW + 38);
      }

      g.dispose();
      return img;
   }

   private void renderFontPicker(GuiGraphics g, int mx, int my) {
      if (fontAtlas == null || !fontAtlas.ready()) {
         fontAtlas = new PageTexture();
         fontAtlas.set(atlas());
      }

      int n = Fonts.count();
      int h = n * this.fpRow + 26;
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 300.0F);
      Ui.card(g, this.font, this.fpX, this.fpY, this.fpW, h, L.t("Шрифт"));
      PaperElement e = this.selected();

      for (int i = 0; i < n; i++) {
         int ry = this.fpY + 22 + i * this.fpRow;
         boolean hov = mx >= this.fpX + 4 && mx < this.fpX + this.fpW - 4 && my >= ry && my < ry + this.fpRow;
         if (e != null && Fonts.clamp(e.font) == i) {
            Ui.round(g, this.fpX + 4, ry, this.fpW - 8, this.fpRow - 1, -12897502);
         } else if (hov) {
            Ui.round(g, this.fpX + 4, ry, this.fpW - 8, this.fpRow - 1, 587202559);
         }

         int dh = this.fpRow - 4;
         int dw = (int)Math.round((double)ATLAS_W * dh / ATLAS_ROW);
         int maxW = this.fpW - 12;
         int vw = Math.min(dw, maxW);
         int uw = (int)Math.round((double)vw * ATLAS_ROW / dh);
         g.blit(fontAtlas.id(), this.fpX + 6, ry + 2, vw, dh, 0.0F, (float)(i * ATLAS_ROW), uw, ATLAS_ROW, fontAtlas.w, fontAtlas.h);
      }

      g.pose().popPose();
   }

   // ----------------------------------------------------------------- input

   private boolean inCanvas(double mx, double my) {
      return mx >= this.cvX && mx < this.cvX + this.cvW && my >= this.cvY && my < this.cvY + this.cvH;
   }

   private double ux(double mx) {
      return (mx - this.pageX) / this.scale;
   }

   private double uy(double my) {
      return (my - this.pageY) / this.scale;
   }

   private int hit(double mx, double my) {
      double px = this.ux(mx);
      double py = this.uy(my);
      double pad = 3.0 / this.scale;
      List<PaperElement> els = this.els();
      PaperElement s = this.selected();
      if (s != null && inside(s, px, py, pad)) {
         return this.sel;
      } else {
         for (int i = els.size() - 1; i >= 0; i--) {
            if (inside(els.get(i), px, py, pad)) {
               return i;
            }
         }

         return -1;
      }
   }

   private static boolean inside(PaperElement e, double px, double py, double pad) {
      return px >= e.x - pad && px <= e.x + e.w + pad && py >= e.y - pad && py <= e.y + Math.max(e.h, 2) + pad;
   }

   private boolean typing() {
      GuiEventListener f = this.getFocused();
      return f instanceof EditBox || f instanceof MultiLineEditBox;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.fontPicker) {
         int n = Fonts.count();
         PaperElement e = this.selected();
         for (int i = 0; i < n; i++) {
            int ry = this.fpY + 22 + i * this.fpRow;
            if (mx >= this.fpX + 4 && mx < this.fpX + this.fpW - 4 && my >= ry && my < ry + this.fpRow && e != null) {
               this.snap();
               e.font = i;
               Sounds.play(Sounds.CLICK, 1.2F, 0.5F);
            }
         }

         this.fontPicker = false;
         this.dirtyRebuild();
         return true;
      } else if (this.m != null && !this.choosing && this.inCanvas(mx, my)) {
         this.layoutPage();
         if (button == 0) {
            int i = this.hit(mx, my);
            if (i >= 0) {
               long now = System.currentTimeMillis();
               boolean dbl = i == this.lastClickSel && now - this.lastClick < 350L;
               this.lastClick = now;
               this.lastClickSel = i;
               PaperElement e = this.els().get(i);
               double px = this.ux(mx);
               double py = this.uy(my);
               double c = 7.0 / this.scale;
               boolean corner = px >= e.x + e.w - c && py >= e.y + Math.max(e.h, 2) - c;
               this.select(i);
               if (dbl && this.textArea != null) {
                  this.setFocused(this.textArea);
                  return true;
               }

               this.pendingSnap = true;
               this.dragMode = corner ? 2 : 1;
               this.dragOffX = px - e.x;
               this.dragOffY = py - e.y;
               this.setFocused(null);
               return true;
            } else {
               this.select(-1);
               this.setFocused(null);
            }
         }

         this.dragMode = 3;
         this.dragMouseX = mx;
         this.dragMouseY = my;
         this.dragPanX = this.panX;
         this.dragPanY = this.panY;
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragMode == 3) {
         this.panX = this.dragPanX + (mx - this.dragMouseX);
         this.panY = this.dragPanY + (my - this.dragMouseY);
         return true;
      } else if (this.dragMode != 0 && this.selected() != null) {
         PaperElement e = this.selected();
         if (this.pendingSnap) {
            this.pendingSnap = false;
            this.snap();
         }

         this.layoutPage();
         double px = this.ux(mx);
         double py = this.uy(my);
         boolean free = hasShiftDown();
         if (this.dragMode == 1) {
            e.x = snapTo(Math.max(-e.w / 2.0, Math.min(PaperModel.W - e.w / 2.0, px - this.dragOffX)), free);
            e.y = snapTo(Math.max(-e.h / 2.0, Math.min(PaperModel.H - e.h / 2.0, py - this.dragOffY)), free);
         } else {
            int min = e.type == PaperElement.LINE ? 1 : 8;
            e.w = clamp(snapTo(px - e.x, free), min, PaperModel.W);
            e.h = clamp(snapTo(py - e.y, free), min, PaperModel.H);
         }

         this.touch();
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   private static int snapTo(double v, boolean free) {
      return free ? (int)Math.round(v) : (int)Math.round(v / 2.0) * 2;
   }

   public boolean mouseReleased(double mx, double my, int button) {
      this.dragMode = 0;
      return super.mouseReleased(mx, my, button);
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.m != null && !this.choosing && this.inCanvas(mx, my)) {
         this.layoutPage();
         double ux = this.ux(mx);
         double uy = this.uy(my);
         double nz = Math.max(0.5, Math.min(6.0, this.zoom * (sy > 0.0 ? 1.15 : 1.0 / 1.15)));
         if (nz != this.zoom) {
            this.zoom = nz;
            this.panX = 0.0;
            this.panY = 0.0;
            this.layoutPage();
            this.panX = mx - (this.pageX + ux * this.scale);
            this.panY = my - (this.pageY + uy * this.scale);
         }

         return true;
      } else if (mx < this.formX + this.formW && this.m != null && !this.choosing) {
         int ns = Math.max(0, this.scroll - (int)Math.signum(sy) * 20);
         if (ns != this.scroll) {
            this.scroll = ns;
            this.rebuildWidgets();
         }

         return true;
      } else {
         return super.mouseScrolled(mx, my, sx, sy);
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 256) {
         if (this.fontPicker) {
            this.fontPicker = false;
            return true;
         } else {
            this.onClose();
            return true;
         }
      } else if (this.m == null || this.choosing) {
         return super.keyPressed(key, scan, mods);
      } else if (this.typing()) {
         return super.keyPressed(key, scan, mods);
      } else {
         boolean ctrl = (mods & 2) != 0;
         if (ctrl && key == 90) {
            this.undo();
            return true;
         } else if (ctrl && key == 89) {
            this.redo();
            return true;
         } else if (ctrl && key == 68) {
            this.duplicate();
            return true;
         } else if (key == 266) {
            this.goPage(this.page - 1);
            return true;
         } else if (key == 267) {
            this.goPage(this.page + 1);
            return true;
         } else {
            PaperElement e = this.selected();
            if (e == null) {
               return super.keyPressed(key, scan, mods);
            } else {
               int step = (mods & 1) != 0 ? 10 : 1;
               switch (key) {
                  case 261:
                     this.deleteSelected();
                     return true;
                  case 262:
                     this.snapOnce();
                     e.x += step;
                     break;
                  case 263:
                     this.snapOnce();
                     e.x -= step;
                     break;
                  case 264:
                     this.snapOnce();
                     e.y += step;
                     break;
                  case 265:
                     this.snapOnce();
                     e.y -= step;
                     break;
                  default:
                     return super.keyPressed(key, scan, mods);
               }

               this.touch();
               return true;
            }
         }
      }
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }
}
