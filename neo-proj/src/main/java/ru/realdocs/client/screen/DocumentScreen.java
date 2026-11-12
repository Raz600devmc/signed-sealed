package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.Exporter;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class DocumentScreen extends ViewerScreen {
   private DocModel doc;
   private final boolean editing;
   private final String myUuid;
   private int template;
   private EditBox number;
   private EditBox org;
   private EditBox position;
   private EditBox subject;
   private MultiLineEditBox body;
   private boolean dirtyDraft;
   private String templateId = "";
   private ru.realdocs.model.DocDesign templateDesign;
   private String vRefs = "";
   private String vSup = "";
   private String vExp = "";
   private String vHolder = "";
   private String vZones = "";
   private boolean vPub;
   private int page;
   private boolean overflow;
   private String vNumber;
   private String vOrg;
   private String vPosition;
   private String vSubject;
   private String vBody;
   private int formX;
   private int formY;
   private int formW;
   private int formH;
   private int bodyY;
   private int footY;

   public DocumentScreen(InteractionHand hand, ItemStack stack) {
      super(Component.literal(NbtCodec.readDoc(stack).issued ? L.t("Документ") : L.t("Предпросмотр")), hand, NbtCodec.readDoc(stack).issued);
      this.doc = NbtCodec.readDoc(stack);
      this.editing = !this.doc.issued;
      this.template = this.doc.template;
      this.vNumber = this.doc.number;
      this.vOrg = this.doc.org;
      this.vPosition = this.doc.position;
      this.vSubject = this.doc.subject;
      this.vBody = this.doc.body;
      this.myUuid = Minecraft.getInstance().player.getUUID().toString();
      this.templateId = this.doc.templateId;
      this.templateDesign = this.doc.design;
      this.vRefs = DocModel.refsText(this.doc.refs);
      this.vSup = DocModel.refsText(this.doc.supersedes);
      this.vExp = this.doc.expires;
      this.vHolder = this.doc.holder;
      this.vZones = this.doc.zones;
      this.vPub = this.doc.pub;
      if (this.editing) {
         ClientOffice.req("templates");
      }
   }

   /** Called when the server sends the list of document templates. */
   public void templatesArrived() {
      if (this.editing) {
         if (!this.templateId.isEmpty()) {
            CompoundTag c = ClientOffice.template(this.templateId);
            if (c != null) {
               this.templateDesign = ru.realdocs.model.DocDesign.read(c.getCompound("Design"));
            }
         }

         this.rebuildWidgets();
      }
   }

   private void useTemplate(String id) {
      this.current();
      CompoundTag c = id.isEmpty() ? null : ClientOffice.template(id);
      this.templateId = c == null ? "" : id;
      this.templateDesign = c == null ? null : ru.realdocs.model.DocDesign.read(c.getCompound("Design"));
      if (c != null) {
         String cur = this.body == null ? this.vBody : this.body.getValue();
         if (cur.isBlank()) {
            this.vBody = c.getString("Body");
         }

         String sub = this.subject == null ? this.vSubject : this.subject.getValue();
         if (sub.isBlank()) {
            this.vSubject = c.getString("Subject");
         }
      }

      this.syncFields();
      this.dirtyDraft = true;
      this.rebuildWidgets();
   }

   private void syncFields() {
      if (this.body != null && !this.vBody.equals(this.body.getValue()) && this.body.getValue().isBlank()) {
         this.body.setValue(this.vBody);
      }

      if (this.subject != null && this.subject.getValue().isBlank()) {
         this.subject.setValue(this.vSubject);
      }
   }

   private DocModel current() {
      if (!this.editing) {
         ItemStack st = this.minecraft.player.getItemInHand(this.hand);
         if (st.getItem() instanceof DocumentItem) {
            this.doc = NbtCodec.readDoc(st);
         }

         return this.doc;
      } else {
         DocModel d = new DocModel();
         d.template = this.template;
         d.number = this.number == null ? this.vNumber : this.number.getValue();
         d.org = this.org == null ? this.vOrg : this.org.getValue();
         d.position = this.position == null ? this.vPosition : this.position.getValue();
         d.subject = this.subject == null ? this.vSubject : this.subject.getValue();
         d.body = this.body == null ? this.vBody : this.body.getValue();
         d.templateId = this.templateId;
         d.design = this.templateId.isEmpty() ? null : this.templateDesign;
         d.refs.addAll(DocModel.parseRefs(this.vRefs));
         d.supersedes.addAll(DocModel.parseRefs(this.vSup));
         d.expires = this.vExp == null ? "" : this.vExp.trim();
         d.holder = this.vHolder == null ? "" : this.vHolder.trim();
         d.zones = this.vZones == null ? "" : this.vZones.trim();
         d.pub = this.vPub;
         this.vNumber = d.number;
         this.vOrg = d.org;
         this.vPosition = d.position;
         this.vSubject = d.subject;
         this.vBody = d.body;
         return d;
      }
   }

   @Override
   protected BufferedImage renderPage(int px) {
      DocRenderer.Result r = DocRenderer.render(this.current(), StampCache.INSTANCE, px, this.editing ? 0 : this.uvMode());
      this.overflow = r.overflow();
      return r.image();
   }

   @Override
   protected double aspect() {
      return 1.414516129032258;
   }

   @Override
   protected double pageUnitsW() {
      return 620.0;
   }

   @Override
   protected int contentKey() {
      DocModel d = this.current();
      return Objects.hash(
         d.template, d.number, d.org, d.position, d.subject, d.body, d.issued, d.docId, d.sigs.size(), d.canonical().hashCode(), ru.realdocs.client.PassportCache.version
      );
   }

   @Override
   protected int stampCount() {
      return this.current().stamps.size();
   }

   @Override
   protected boolean hasSidePanel() {
      return !this.editing;
   }

   @Override
   protected String panelTitle() {
      return L.t(this.current().templateName()) + (this.current().number.isEmpty() ? "" : " № " + this.current().number);
   }

   @Override
   protected void init() {
      if (!this.editing) {
         super.init();
      } else {
         this.formX = this.pad;
         this.formY = this.pad;
         this.formW = Math.max(220, Math.min(330, this.width * 2 / 5));
         this.formH = this.height - 2 * this.pad;
         int x = this.formX + 10;
         int w = this.formW - 20;
         int y = this.formY + 38;
         List<CompoundTag> tpls = ClientOffice.templateList();
         int count = DocModel.TEMPLATE_NAMES.length + tpls.size();
         int cur = DocModel.templateIndex(this.template);

         for (int i = 0; i < tpls.size(); i++) {
            if (!this.templateId.isEmpty() && tpls.get(i).getString("Id").equals(this.templateId)) {
               cur = DocModel.TEMPLATE_NAMES.length + i;
            }
         }

         int cur0 = cur;
         String tl = cur < DocModel.TEMPLATE_NAMES.length
            ? L.t(DocModel.TEMPLATE_NAMES[cur])
            : ru.realdocs.model.DocDesign.read(tpls.get(cur - DocModel.TEMPLATE_NAMES.length).getCompound("Design")).name;
         java.util.function.IntConsumer choose = k -> {
            int n = (k % count + count) % count;
            if (n < DocModel.TEMPLATE_NAMES.length) {
               this.current();
               this.template = n;
               this.templateId = "";
               this.templateDesign = null;
               this.dirtyDraft = true;
               this.rebuildWidgets();
            } else {
               this.useTemplate(tpls.get(n - DocModel.TEMPLATE_NAMES.length).getString("Id"));
            }
         };
         this.button("◀", x, y, 18, 18, () -> choose.accept(cur0 - 1));
         this.button(tl + "  (" + (cur + 1) + "/" + count + ")", x + 21, y, w - 21 - 21 - 74, 18, () -> choose.accept(cur0 + 1)).selected(true);
         this.button("▶", x + w - 74 - 18 - 3, y, 18, 18, () -> choose.accept(cur0 + 1));
         this.button(L.t("Шаблоны…"), x + w - 70, y, 70, 18, () -> {
            ClientOffice.req("templates");
            this.minecraft.setScreen(new TemplateEditorScreen(this));
         });
         y += 26;
         boolean req = !this.vRefs.isBlank() || !this.vSup.isBlank() || !this.vExp.isBlank() || !this.vHolder.isBlank() || !this.vZones.isBlank() || this.vPub;
         int tw = (w - 6) / 2;
         this.button(L.t("Документ"), x, y, tw, 18, () -> {
            this.current();
            this.page = 0;
            this.rebuildWidgets();
         }).selected(this.page == 0);
         this.button(L.t("Реквизиты") + (req ? " •" : ""), x + tw + 6, y, w - tw - 6, 18, () -> {
            this.current();
            this.page = 1;
            this.rebuildWidgets();
         }).selected(this.page == 1);
         y += 24;
         this.footY = this.formY + this.formH - 10 - 20 - 24;
         if (this.page == 0) {
            int nw = (w - 6) * 2 / 5;
            this.number = this.box(x, y, nw, "42/2026", this.vNumber, 20, L.t("Номер"));
            this.position = this.box(x + nw + 6, y, w - nw - 6, L.t("Например, Мэр"), this.vPosition, 60, L.t("Ваша должность"));
            y += 32;
            this.org = this.box(x, y, w, L.t("Кто выпускает документ"), this.vOrg, 80, L.t("Организация"));
            y += 32;
            this.subject = this.box(x, y, w, L.t("О чём документ"), this.vSubject, 140, L.t("Тема"));
            y += 32;
            this.bodyY = y;
            int bodyH = Math.max(40, this.footY - y - 10 - 16);
            this.body = new UiTextArea(this.font, x, y + 10, w, bodyH, L.t("Текст документа…"));
            this.body.setCharacterLimit(2400);
            this.body.setValue(this.vBody);
            this.body.setValueListener(s -> this.dirtyDraft = true);
            this.addRenderableWidget(this.body);
         } else {
            this.number = null;
            this.position = null;
            this.org = null;
            this.subject = null;
            this.body = null;
            int rw = (w - 6) / 2;
            UiField rf = this.box(x, y, rw, "RD-000012", this.vRefs, 120, L.t("Ссылается на (рег. №)"));
            rf.setResponder(v -> {
               this.vRefs = v;
               this.dirtyDraft = true;
            });
            UiField sf = this.box(x + rw + 6, y, w - rw - 6, "RD-000010", this.vSup, 120, L.t("Отменяет (рег. №)"));
            sf.setResponder(v -> {
               this.vSup = v;
               this.dirtyDraft = true;
            });
            y += 32;
            UiField ef = this.box(x, y, rw, L.t("дд.мм.гггг"), this.vExp, 12, L.t("Действует до"));
            ef.setResponder(v -> {
               this.vExp = v;
               this.dirtyDraft = true;
            });
            NameField hf = this.nameField(x + rw + 6, y + 10, w - rw - 6, L.t("ник игрока"), this.vHolder, 40, v -> {
               this.vHolder = v;
               this.dirtyDraft = true;
            }, false, false);
            hf.label(L.t("Кому выдан"));
            y += 32;
            UiField zf = this.box(x, y, rw, L.t("Депо, Стройка-1"), this.vZones, 80, L.t("Зоны"));
            zf.setResponder(v -> {
               this.vZones = v;
               this.dirtyDraft = true;
            });
            this.button(this.vPub ? L.t("Публичный в реестре") : L.t("Закрытый в реестре"), x + rw + 6, y + 10, w - rw - 6, 18, () -> {
               this.vPub = !this.vPub;
               this.dirtyDraft = true;
               this.rebuildWidgets();
            }).selected(this.vPub);
            y += 32;
            this.bodyY = y + 4;
         }
         this.footY = this.formY + this.formH - 10 - 20 - 24;
         int half = (w - 6) / 2;
         this.button(L.t("Подписать и выпустить"), x, this.footY, w, this::openSign).primary();
         this.button(L.t("Сохранить черновик"), x, this.footY + 24, half, this::saveDraft);
         this.button(L.t("Закрыть"), x + half + 6, this.footY + 24, w - half - 6, this::onClose).ghost();
         int vx = this.formX + this.formW + this.pad;
         this.view.layout(vx, this.pad + 16, this.width - vx - this.pad, this.height - 2 * this.pad - 16, this.aspect());
      }
   }

   @Override
   protected void relayout() {
      if (!this.editing) {
         super.relayout();
      }
   }

   private UiField box(int x, int y, int w, String hint, String value, int max, String label) {
      UiField f = this.field(x, y + 10, w, hint, value, max, s -> this.dirtyDraft = true);
      f.label(label);
      return f;
   }

   @Override
   protected void renderUnder(GuiGraphics g, int mx, int my) {
      if (this.editing) {
         Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, L.t("Новый документ"));
         int x = this.formX + 10;
         int w = this.formW - 20;
         Ui.section(g, this.font, L.t("Тип документа"), x, this.formY + 26, w);
         if (this.page == 0) {
            Ui.label(g, this.font, L.t("Текст документа"), x + 1, this.bodyY);
         } else {
            int ty = this.bodyY;
            for (String line : new String[]{
               L.t("Действует до — реальная дата; после неё документ недействителен."),
               L.t("Кому выдан — считыватель пропустит только этого игрока."),
               L.t("Зоны — считыватель с такой зоной примет пропуск."),
               L.t("Закрытый — в реестре видны только статус и тип, подробности — участникам.")
            }) {
               for (net.minecraft.util.FormattedCharSequence fl : this.font.split(net.minecraft.network.chat.Component.literal(line), w)) {
                  if (ty + 9 > this.footY - 16) {
                     break;
                  }
                  g.drawString(this.font, fl, x, ty, -7303024, false);
                  ty += 10;
               }
               ty += 3;
            }
         }
         int bottom = this.body == null ? this.footY - 14 : this.body.getY() + this.body.getHeight() + 3;
         if (this.overflow) {
            g.drawString(this.font, L.t("Текст не помещается на страницу"), x, bottom, -868018, false);
         }
      }
   }

   @Override
   protected void addPanelButtons() {
      DocModel d = this.current();
      this.addBtn(L.t("Проверить подлинность"), this::requestVerify).hint("V");
      boolean can = !d.hasAuthorizedSignature(this.myUuid) && d.sigs.size() < 4;
      this.addBtn(L.t("Добавить подпись"), this::openCoSign).enabled(can);
      boolean mine = d.hasAuthorizedSignature(this.myUuid) || d.authorUuid.equals(this.myUuid);
      this.addBtn(L.t("Запросить подпись"), () -> this.minecraft.setScreen(new AskSignScreen(this, this.hand)))
         .enabled(d.issued && !d.copy && mine && d.sigs.size() < 4);
      this.addSecurityButtons();
      this.addBtn(L.t("Экспорт в PNG"), () -> this.export(false));
      this.addBtn(L.t("Экспорт в PDF"), () -> this.export(true));
   }

   @Override
   protected List<double[]> holograms() {
      return !this.editing && this.current().issued
         ? List.of(new double[]{0.8516129032258064, 0.03876852907639681, 0.08709677419354839, 0.04561003420752566})
         : List.of();
   }

   private void export(boolean pdf) {
      try {
         Path p = pdf ? Exporter.exportPdf(this.current()) : Exporter.exportPng(this.current());
         this.ok(L.t("Сохранено: ") + p.getFileName() + L.t(" (папка signedsealed_exports)"));
      } catch (Exception var3) {
         this.error(L.t("Ошибка экспорта: ") + var3.getMessage());
      }
   }

   private void saveDraft() {
      Net.sendToServer(new Packets.SaveDoc(this.hand, false, this.current(), new int[0]));
      this.dirtyDraft = false;
      this.ok(L.t("Черновик сохранён"));
   }

   private void openSign() {
      DocModel d = this.current();
      if (d.org.isBlank() || d.body.isBlank()) {
         this.error(L.t("Заполните организацию и текст документа."));
      } else if (this.overflow) {
         this.error(L.t("Текст не помещается на страницу — сократите его."));
      } else {
         this.minecraft.setScreen(new SignatureScreen(this, false, "", L.t("Выпустить"), (pos, sig) -> {
            Net.sendToServer(new Packets.SaveDoc(this.hand, true, d, sig));
            this.minecraft.setScreen(null);
         }));
      }
   }

   private void openCoSign() {
      this.minecraft.setScreen(new SignatureScreen(this, true, "", L.t("Подписать"), (pos, sig) -> {
         Net.sendToServer(new Packets.CoSign(this.hand, pos, sig));
         this.minecraft.setScreen(null);
      }));
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      super.render(g, mx, my, pt);
   }

   public void onClose() {
      if (this.editing && this.dirtyDraft) {
         Net.sendToServer(new Packets.SaveDoc(this.hand, false, this.current(), new int[0]));
      }

      super.onClose();
   }
}
