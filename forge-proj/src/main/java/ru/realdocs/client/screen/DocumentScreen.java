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
import ru.realdocs.L;
import ru.realdocs.client.Exporter;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.ui.Ui;
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
      return Objects.hash(d.template, d.number, d.org, d.position, d.subject, d.body, d.issued, d.docId, d.sigs.size(), d.canonical().hashCode());
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
         int tw = (w - 3) / 2;

         for (int i = 0; i < DocModel.TEMPLATE_NAMES.length; i++) {
            int t = i;
            this.button(L.t(DocModel.TEMPLATE_NAMES[i]), x + i % 2 * (tw + 3), y + i / 2 * 21, i % 2 == 0 ? tw : w - tw - 3, 18, () -> {
               this.template = t;
               this.dirtyDraft = true;
               this.rebuildWidgets();
            }).selected(DocModel.templateIndex(this.template) == i);
         }

         y += 47;
         int nw = (w - 6) * 2 / 5;
         this.number = this.box(x, y, nw, "42/2026", this.vNumber, 20, L.t("Номер"));
         this.position = this.box(x + nw + 6, y, w - nw - 6, L.t("Например, Мэр"), this.vPosition, 60, L.t("Ваша должность"));
         y += 32;
         this.org = this.box(x, y, w, L.t("Кто выпускает документ"), this.vOrg, 80, L.t("Организация"));
         y += 32;
         this.subject = this.box(x, y, w, L.t("О чём документ"), this.vSubject, 140, L.t("Тема"));
         y += 32;
         this.footY = this.formY + this.formH - 10 - 20 - 24;
         this.bodyY = y;
         int bodyH = Math.max(40, this.footY - y - 10 - 16);
         this.body = new UiTextArea(this.font, x, y + 10, w, bodyH, L.t("Текст документа…"));
         this.body.setCharacterLimit(2400);
         this.body.setValue(this.vBody);
         this.body.setValueListener(s -> this.dirtyDraft = true);
         this.addRenderableWidget(this.body);
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
         Ui.label(g, this.font, L.t("Текст документа"), x + 1, this.bodyY);
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
