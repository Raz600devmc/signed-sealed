package ru.realdocs.client.screen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ClientUploads;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.Fonts;
import ru.realdocs.client.render.PaperRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.model.DocDesign;
import ru.realdocs.model.DocModel;
import ru.realdocs.server.TemplateService;
import ru.realdocs.server.Uploads;

/** Editor for custom document types: look, header, logo and default text, with a live preview. */
public final class TemplateEditorScreen extends UiScreen {
   private static final String SAMPLE = "# Раздел 1. Общие положения\n"
      + "Текст документа печатается здесь. Абзацы разделяются пустой строкой.\n"
      + "- пункт списка\n"
      + "- ещё один пункт\n"
      + "> Цитата или примечание.\n"
      + "---\n"
      + "^ Строка по центру";
   private final Screen parent;
   private final List<CompoundTag> list = new ArrayList<>();
   private final List<CompoundTag> orgs = new ArrayList<>();
   /** -1 = new template. */
   private int index = -1;
   private DocDesign d = DocDesign.preset(3);
   private String subject = "";
   private String body = "";
   private int owner;
   private int preset = 3;
   private String logoSrc = "";
   private boolean uploading;
   private final PageView view = new PageView();
   private int drawnKey;
   private UiTextArea bodyBox;
   private int formX;
   private int formW;

   public TemplateEditorScreen(Screen parent) {
      super(Component.literal(L.t("Шаблоны документов")));
      this.parent = parent;
      this.load();
      String sel = ClientOffice.templates.getString("Select");

      for (int i = 0; i < this.list.size(); i++) {
         if (this.list.get(i).getString("Id").equals(sel)) {
            this.pick(i);
         }
      }

      if (this.index < 0 && !this.list.isEmpty() && sel.isEmpty()) {
         this.pick(0);
      }
   }

   private void load() {
      this.list.clear();
      this.orgs.clear();
      ListTag l = ClientOffice.templates.getList("Templates", 10);

      for (int i = 0; i < l.size(); i++) {
         this.list.add(l.getCompound(i));
      }

      ListTag o = ClientOffice.templates.getList("Orgs", 10);

      for (int i = 0; i < o.size(); i++) {
         this.orgs.add(o.getCompound(i));
      }
   }

   public void reload() {
      String keep = this.index >= 0 && this.index < this.list.size() ? this.list.get(this.index).getString("Id") : "";
      String sel = ClientOffice.templates.getString("Select");
      this.load();
      int found = -1;

      for (int i = 0; i < this.list.size(); i++) {
         String id = this.list.get(i).getString("Id");
         if (id.equals(sel.isEmpty() ? keep : sel)) {
            found = i;
         }
      }

      if (found >= 0) {
         this.pick(found);
      } else if (!keep.isEmpty()) {
         this.index = -1;
      }

      this.rebuildWidgets();
   }

   private void pick(int i) {
      this.index = i;
      CompoundTag c = this.list.get(i);
      this.d = DocDesign.read(c.getCompound("Design"));
      this.subject = c.getString("Subject");
      this.body = c.getString("Body");
      this.owner = 0;

      for (int k = 0; k < this.orgs.size(); k++) {
         if (this.orgs.get(k).getString("Id").equals(c.getString("Org"))) {
            this.owner = k + 1;
         }
      }
   }

   private boolean editable() {
      return this.index < 0 ? ClientOffice.templates.getBoolean("CanCreate") : this.list.get(this.index).getBoolean("Edit");
   }

   private String headLabel() {
      if (this.index < 0) {
         return L.t("Новый шаблон");
      } else {
         CompoundTag c = this.list.get(this.index);
         String org = c.getString("OrgName");
         return c.getString("Id") + " · " + this.d.name + (org.isEmpty() ? "" : " · " + org) + "  (" + (this.index + 1) + "/" + this.list.size() + ")";
      }
   }

   private static String fontName(int f) {
      return f < 0 ? L.t("Классический") : L.t(Fonts.NAMES[Math.min(Fonts.NAMES.length - 1, f)]);
   }

   private void cycle(Runnable r) {
      r.run();
      this.d.normalize();
      this.rebuildWidgets();
   }

   private int tab;

   protected void init() {
      int pad = 8;
      int pw = (int)((double)(this.height - 2 * pad) / 1.414516129032258);
      this.formW = Math.max(260, Math.min(380, this.width - pw - 3 * pad));
      this.formX = pad;
      int x = this.formX + 10;
      int w = this.formW - 20;
      int half = (w - 4) / 2;
      int y = pad + 26;
      boolean ed = this.editable();
      this.button("◀", x, y, 18, 18, () -> {
         if (!this.list.isEmpty()) {
            this.pick(this.index <= 0 ? this.list.size() - 1 : this.index - 1);
            this.rebuildWidgets();
         }
      });
      this.button(this.headLabel(), x + 21, y, w - 42, 18, () -> {
      }).ghost();
      this.button("▶", x + w - 18, y, 18, 18, () -> {
         if (!this.list.isEmpty()) {
            this.pick(this.index >= this.list.size() - 1 ? 0 : this.index + 1);
            this.rebuildWidgets();
         }
      });
      y += 22;
      String[] tabs = new String[]{L.t("Основное"), L.t("Оформление"), L.t("Текст")};
      int tw = (w - 8) / 3;

      for (int i = 0; i < 3; i++) {
         int k = i;
         this.button(tabs[i], x + i * (tw + 4), y, i == 2 ? w - 2 * (tw + 4) : tw, 18, () -> {
            this.tab = k;
            this.rebuildWidgets();
         }).selected(this.tab == i);
      }

      y += 30;
      int by = this.height - pad - 26;
      if (this.tab == 0) {
         UiField nf = this.field(x, y, half, L.t("Трудовой договор"), this.d.name, DocDesign.MAX_NAME, s -> this.d.name = s);
         nf.label(L.t("Тип документа"));
         nf.setEditable(ed);
         UiField tf = this.field(x + half + 4, y, w - half - 4, L.t("ТРУДОВОЙ ДОГОВОР"), this.d.title, DocDesign.MAX_TITLE, s -> this.d.title = s);
         tf.label(L.t("Заголовок на листе"));
         tf.setEditable(ed);
         y += 31;
         UiField h1 = this.field(x, y, w, L.t("например, Республика Кубия"), this.d.header1, DocDesign.MAX_HEADER, s -> this.d.header1 = s);
         h1.label(L.t("Шапка (две строки над организацией)"));
         h1.setEditable(ed);
         y += 21;
         UiField h2 = this.field(x, y, w, L.t("например, г. Кубоград, ул. Центральная, 1"), this.d.header2, DocDesign.MAX_HEADER, s -> this.d.header2 = s);
         h2.setEditable(ed);
         y += 31;
         UiField ft = this.field(x, y, w, L.t("например, Действителен при предъявлении паспорта"), this.d.footer, DocDesign.MAX_FOOTER, s -> this.d.footer = s);
         ft.label(L.t("Строка внизу листа"));
         ft.setEditable(ed);
         y += 31;
         UiField lf = this.field(x, y, w - 84, L.t("ссылка или путь к PNG"), this.logoSrc, 600, s -> this.logoSrc = s);
         lf.label(L.t("Логотип"));
         lf.setEditable(ed);
         this.button(this.uploading ? L.t("загрузка…") : L.t("Загрузить"), x + w - 80, y, 80, 18, this::uploadLogo).enabled(ed && !this.uploading);
         y += 22;
         this.button(L.t(DocDesign.LOGO_POS[this.d.logoPos]), x, y, w, 18, () -> this.cycle(() -> this.d.logoPos = (this.d.logoPos + 1) % DocDesign.LOGO_POS.length))
            .enabled(ed && !this.d.logo.isEmpty());
         y += 22;
         this.button(this.owner == 0 ? L.t("Личный шаблон") : L.t("Для организации: ") + this.orgs.get(this.owner - 1).getString("Name"), x, y, w, 18, () -> {
            this.owner = (this.owner + 1) % (this.orgs.size() + 1);
            this.rebuildWidgets();
         }).enabled(ed && !this.orgs.isEmpty());
         y += 22;
         if (y + 18 <= by - 4) {
            this.button(L.t("Новый"), x, y, half, 18, () -> {
               this.index = -1;
               this.d = DocDesign.preset(this.preset);
               this.subject = "";
               this.body = "";
               this.owner = 0;
               this.rebuildWidgets();
            }).enabled(ClientOffice.templates.getBoolean("CanCreate"));
            this.button(L.t("Основа: ") + L.t(DocModel.TEMPLATE_NAMES[this.preset]), x + half + 4, y, w - half - 4, 18, () -> {
               this.preset = (this.preset + 1) % DocModel.TEMPLATE_NAMES.length;
               if (this.index < 0) {
                  DocDesign p = DocDesign.preset(this.preset);
                  this.d.name = p.name;
                  this.d.title = p.title;
               }

               this.rebuildWidgets();
            }).enabled(this.index < 0);
         }
      } else if (this.tab == 1) {
         y -= 8;
         this.button(L.t("Рамка: ") + L.t(DocDesign.BORDERS[this.d.border]), x, y, half, 18, () -> this.cycle(() -> this.d.border = (this.d.border + 1) % DocDesign.BORDERS.length)).enabled(ed);
         this.button(L.t("Бумага: ") + L.t(DocDesign.PAPERS[this.d.paper]), x + half + 4, y, w - half - 4, 18, () -> this.cycle(() -> this.d.paper = (this.d.paper + 1) % DocDesign.PAPERS.length))
            .swatch(DocDesign.PAPER_RGB[this.d.paper])
            .enabled(ed);
         y += 22;
         this.button(L.t("Цвет: ") + L.t(DocDesign.ACCENTS[this.d.accent]), x, y, half, 18, () -> this.cycle(() -> this.d.accent = (this.d.accent + 1) % DocDesign.ACCENTS.length))
            .swatch(DocDesign.ACCENT_RGB[this.d.accent])
            .enabled(ed);
         this.button(this.d.titleLeft ? L.t("Заголовок слева") : L.t("Заголовок по центру"), x + half + 4, y, w - half - 4, 18, () -> this.cycle(() -> this.d.titleLeft = !this.d.titleLeft)).enabled(ed);
         y += 22;
         this.button(L.t("Заголовок: ") + fontName(this.d.titleFont), x, y, w, 18, () -> this.cycle(() -> this.d.titleFont = this.d.titleFont + 1 >= Fonts.count() ? -1 : this.d.titleFont + 1)).enabled(ed);
         y += 22;
         this.button(L.t("Текст: ") + fontName(this.d.bodyFont), x, y, w, 18, () -> this.cycle(() -> this.d.bodyFont = this.d.bodyFont + 1 >= Fonts.count() ? -1 : this.d.bodyFont + 1)).enabled(ed);
         y += 22;
         this.button(L.t(DocDesign.BODY_STYLES[this.d.bodyStyle]), x, y, w, 18, () -> this.cycle(() -> this.d.bodyStyle = (this.d.bodyStyle + 1) % DocDesign.BODY_STYLES.length)).enabled(ed);
         y += 22;
         this.button(this.d.hideNumber ? L.t("Без номера") : L.t("С номером"), x, y, half, 18, () -> this.cycle(() -> this.d.hideNumber = !this.d.hideNumber)).enabled(ed);
         this.button(this.d.hideDate ? L.t("Без даты") : L.t("С датой"), x + half + 4, y, w - half - 4, 18, () -> this.cycle(() -> this.d.hideDate = !this.d.hideDate)).enabled(ed);
      } else {
         UiField sf = this.field(x, y, w, L.t("тема по умолчанию"), this.subject, 140, s -> this.subject = s);
         sf.label(L.t("Тема по умолчанию"));
         sf.setEditable(ed);
         y += 22;
         int bh = Math.max(30, by - 22 - y);
         this.bodyBox = new UiTextArea(this.font, x, y, w, bh, L.t("# Заголовок, - пункт, > примечание, ^ по центру, --- линия"));
         this.bodyBox.setCharacterLimit(2400);
         this.bodyBox.setValue(this.body);
         this.bodyBox.setValueListener(s -> this.body = s);
         this.addRenderableWidget(this.bodyBox);
      }

      this.button(L.t("Сохранить"), x, by, 90, 20, this::save).primary().enabled(ed);
      if (this.index >= 0) {
         this.button(L.t("Удалить"), x + 94, by, 76, 20, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("Id", this.list.get(this.index).getString("Id"));
            this.index = -1;
            ClientOffice.req("tpl_delete", t);
         }).danger().enabled(ed);
      }

      this.button(L.t("Закрыть"), x + w - 70, by, 70, 20, this::onClose).ghost();
      int vx = this.formX + this.formW + pad;
      this.view.layout(vx, pad, this.width - vx - pad, this.height - 2 * pad, 1.414516129032258);
      this.drawnKey = 0;
   }

   private void uploadLogo() {
      String src = this.logoSrc == null ? "" : this.logoSrc.trim();
      if (src.isEmpty()) {
         this.error(L.t("Укажите ссылку или путь к файлу."));
      } else {
         this.uploading = true;
         this.rebuildWidgets();
         WebImage.load(src).thenAccept(img -> this.minecraft.execute(() -> {
            try {
               byte[] png = ImageUtil.pngUnder(PaperRenderer.limit(img, TemplateService.LOGO_SIDE), TemplateService.LOGO_BYTES - 2000, TemplateService.LOGO_SIDE);
               ClientUploads.send(Uploads.KIND_LOGO, png, (ok, id, msg) -> {
                  this.uploading = false;
                  if (ok) {
                     this.d.logo = id;
                     if (this.d.logoPos == 0) {
                        this.d.logoPos = 1;
                     }

                     PassportCache.INSTANCE.putAsset(id, true, png);
                     this.ok(L.t("Логотип загружен — не забудьте сохранить шаблон."));
                  } else {
                     this.error(msg);
                  }

                  this.rebuildWidgets();
               });
            } catch (Exception var4) {
               this.uploading = false;
               this.error(L.t("Ошибка: ") + var4.getMessage());
               this.rebuildWidgets();
            }
         })).exceptionally(ex -> {
            this.minecraft.execute(() -> {
               this.uploading = false;
               this.error(L.t("Ошибка: ") + WebImage.reason(ex));
               this.rebuildWidgets();
            });
            return null;
         });
      }
   }

   private void save() {
      CompoundTag t = new CompoundTag();
      t.putString("Id", this.index < 0 ? "" : this.list.get(this.index).getString("Id"));
      t.putString("Org", this.owner == 0 ? "" : this.orgs.get(this.owner - 1).getString("Id"));
      t.put("Design", this.d.copy().write());
      t.putString("Subject", this.subject);
      t.putString("Body", this.body);
      ClientOffice.req("tpl_save", t);
   }

   private DocModel sample() {
      DocModel m = new DocModel();
      m.design = this.d.copy();
      m.org = L.t("Организация");
      m.number = "12/2026";
      m.position = L.t("Должность");
      m.date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
      m.subject = this.subject.isBlank() ? L.t("Тема документа") : this.subject;
      m.body = this.body.isBlank() ? L.t(SAMPLE) : this.body;
      return m;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.formX, 8, this.formW, this.height - 16, L.t("Шаблоны документов"));
      int key = Objects.hash(this.d.canonical(), this.subject, this.body, this.view.w, this.view.h, StampCache.version, PassportCache.version);
      if (key != this.drawnKey || !this.view.tex.ready()) {
         this.drawnKey = key;
         this.view.tex.set(DocRenderer.render(this.sample(), StampCache.INSTANCE, Math.max(200, Math.min(1240, this.view.w * 2))).image());
      }

      this.view.draw(g);
      super.render(g, mx, my, pt);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public void removed() {
      this.view.close();
      super.removed();
   }
}
