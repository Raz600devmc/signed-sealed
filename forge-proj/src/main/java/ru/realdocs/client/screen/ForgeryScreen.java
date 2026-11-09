package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.ImageFx;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.model.StampModel;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class ForgeryScreen extends UiScreen {
   private final InteractionHand kitHand;
   private final boolean isDoc;
   private final boolean isPass;
   private final DocModel doc;
   private final PassportModel pass;
   private final Map<String, UiField> boxes = new LinkedHashMap<>();
   private final Map<String, String> values = new LinkedHashMap<>();
   private UiTextArea body;
   private String vBody = "";
   private String photoSrc = "";
   private BufferedImage localPhoto;
   private byte[] photoPng;
   private boolean loading;
   private final PageTexture preview = new PageTexture();
   private int renderedKey = Integer.MIN_VALUE;
   private int formX = 12;
   private int formY = 12;
   private int formW;
   private int formH;
   private int pvX;
   private int pvY;
   private int pvW;
   private int pvH;
   private int stampsY = -1;
   private boolean built;

   public ForgeryScreen(InteractionHand kitHand) {
      super(Component.literal(L.t("Набор фальсификатора")));
      this.kitHand = kitHand;
      InteractionHand other = kitHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack target = Minecraft.getInstance().player.getItemInHand(other);
      this.isDoc = target.getItem() instanceof DocumentItem;
      this.isPass = target.getItem() instanceof PassportItem;
      this.doc = this.isDoc ? NbtCodec.readDoc(target) : null;
      this.pass = this.isPass ? NbtCodec.readPass(target) : null;
      if (this.isDoc) {
         this.values.put("#number", this.doc.number);
         this.values.put("#org", this.doc.org);
         this.values.put("#position", this.doc.position);
         this.values.put("#subject", this.doc.subject);
         this.vBody = this.doc.body;
      } else if (this.isPass) {
         this.values.putAll(this.pass.values);
      }
   }

   private PassportTemplate template() {
      return this.isPass ? PassportCache.INSTANCE.template(this.pass.templateId) : null;
   }

   private List<DocModel.StampRef> stamps() {
      return this.isDoc ? this.doc.stamps : (this.isPass ? this.pass.stamps : List.of());
   }

   protected void init() {
      this.boxes.clear();
      this.formW = Math.max(240, Math.min(330, this.width * 2 / 5));
      this.formH = this.height - 24;
      int x = this.formX + 10;
      int w = this.formW - 20;
      int y = this.formY + 30;
      int footY = this.formY + this.formH - 30;
      if (!this.isDoc && !this.isPass) {
         this.button(L.t("Закрыть"), x, footY, w, this::onClose).ghost();
      } else {
         int half = (w - 6) / 2;
         if (this.isDoc) {
            this.edit(x, y, half, L.t("Номер"), "#number", 20);
            this.edit(x + half + 6, y, w - half - 6, L.t("Должность"), "#position", 60);
            y += 32;
            this.edit(x, y, w, L.t("Организация"), "#org", 80);
            y += 32;
            this.edit(x, y, w, L.t("Тема"), "#subject", 140);
            y += 32;
            int bodyH = Math.max(40, footY - 70 - y - this.stampRowsH());
            this.body = new UiTextArea(this.font, x, y + 10, w, bodyH, L.t("Текст документа…"));
            this.body.setCharacterLimit(2400);
            this.body.setValue(this.vBody);
            this.addRenderableWidget(this.body);
            y += bodyH + 16;
         } else {
            PassportTemplate t = this.template();
            this.built = t != null;
            if (t != null) {
               List<PassportTemplate.Field> inputs = new ArrayList<>();

               for (PassportTemplate.Field f : t.fields) {
                  if (PassportTemplate.playerInput(f)) {
                     inputs.add(f);
                  }
               }

               boolean two = inputs.size() > 5;
               int cw = two ? half : w;

               for (int i = 0; i < inputs.size(); i++) {
                  PassportTemplate.Field fx = inputs.get(i);
                  int col = two ? i % 2 : 0;
                  int row = two ? i / 2 : i;
                  this.edit(x + col * (cw + 6), y + row * 30, two ? (col == 0 ? half : w - half - 6) : w, fx.label, fx.key, 60);
               }

               y += (two ? (inputs.size() + 1) / 2 : inputs.size()) * 30 + 4;
               if (t.fields.stream().anyMatch(fx -> fx.type == 2)) {
                  UiField src = this.field(x, y + 10, w - 80, L.t("Ссылка или файл"), this.photoSrc, 500, s -> this.photoSrc = s);
                  src.label(L.t("Другое фото (необязательно)"));
                  this.button(L.t("Загрузить"), x + w - 76, y + 9, 76, () -> this.loadPhoto(this.photoSrc));
                  y += 34;
               }
            }
         }

         this.stampsY = y;
         int sy = y + 12;
         int n = 0;
         Set<String> seen = new HashSet<>();

         for (DocModel.StampRef r : this.stamps()) {
            if (seen.add(r.id) && n < 4) {
               StampModel sm = StampCache.INSTANCE.def(r.id);
               String name = sm == null ? r.id : sm.org;
               String id = r.id;
               this.button(L.t("Поддельная печать: ") + name, x, sy + n * 22, w, 20, () -> {
                  Net.sendToServer(new Packets.ForgeStamp(this.kitHand, id));
                  this.onClose();
               });
               n++;
            }
         }

         this.button(L.t("Сделать подделку"), x, footY - 24, w, this::forge).primary();
         this.button(L.t("Закрыть"), x, footY, w, this::onClose).ghost();
         this.pvX = this.formX + this.formW + 14;
         this.pvY = this.formY + 22;
         this.pvW = this.width - this.pvX - 12;
         this.pvH = this.height - this.pvY - 40;
      }
   }

   private int stampRowsH() {
      long distinct = this.stamps().stream().map(r -> r.id).distinct().count();
      return 14 + (int)Math.min(4L, distinct) * 22;
   }

   private void edit(int x, int y, int w, String label, String key, int max) {
      UiField f = this.field(x, y + 10, w, "", this.values.getOrDefault(key, ""), max, s -> this.values.put(key, s));
      f.label(label);
      this.boxes.put(key, f);
   }

   private void loadPhoto(String input) {
      if (!this.loading) {
         this.loading = true;
         this.info(L.t("Загрузка фото…"));
         WebImage.load(input).thenAccept(img -> this.minecraft.execute(() -> {
               this.loading = false;

               try {
                  this.photoPng = ImageUtil.photoPng(ImageFx.fit(img, 1024, 1024), 16000);
                  this.localPhoto = ImageIO.read(new ByteArrayInputStream(this.photoPng));
                  this.ok(L.t("Фото готово: ") + (double)this.photoPng.length / 1000.0 + L.t(" КБ"));
               } catch (Exception var3) {
                  this.error(L.t("Ошибка: ") + var3.getMessage());
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

   public void onFilesDrop(List<Path> files) {
      if (this.isPass && !files.isEmpty()) {
         this.loadPhoto(files.get(0).toString());
      }
   }

   private void forge() {
      Map<String, String> out = new LinkedHashMap<>(this.values);
      if (this.isDoc && this.body != null) {
         out.put("#body", this.body.getValue());
      }

      Net.sendToServer(new Packets.ForgeCopy(this.kitHand, out, this.photoPng));
      this.onClose();
   }

   private BufferedImage renderForged(int px) {
      if (this.isDoc) {
         DocModel d = this.copyDoc();
         return DocRenderer.render(d, StampCache.INSTANCE, px).image();
      } else {
         PassportTemplate t = this.template();
         if (t == null) {
            return null;
         } else {
            PassportModel p = new PassportModel();
            p.templateId = this.pass.templateId;
            p.format = this.pass.format;
            p.issued = true;
            p.passId = this.pass.passId;
            p.serial = this.pass.serial.isEmpty() ? (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + "000000" : this.pass.serial;
            p.date = this.pass.date;
            p.sec = this.pass.sec;
            p.holderName = this.pass.holderName;
            p.signature = this.pass.signature;
            p.photo = this.localPhoto != null ? "__local" : this.pass.photo;
            p.values.putAll(this.values);
            p.stamps.addAll(this.pass.stamps);
            PassportRenderer.Assets as = id -> "__local".equals(id) ? this.localPhoto : PassportCache.INSTANCE.image(id);
            return PassportRenderer.renderSpread(t, p, as, StampCache.INSTANCE, px, false, 0, 0);
         }
      }
   }

   private DocModel copyDoc() {
      DocModel d = new DocModel();
      d.template = this.doc.template;
      d.number = this.values.getOrDefault("#number", this.doc.number);
      d.org = this.values.getOrDefault("#org", this.doc.org);
      d.position = this.values.getOrDefault("#position", this.doc.position);
      d.subject = this.values.getOrDefault("#subject", this.doc.subject);
      d.body = this.body == null ? this.vBody : this.body.getValue();
      d.issued = true;
      d.docId = this.doc.docId;
      d.date = this.doc.date;
      d.author = this.doc.author;
      d.authorUuid = this.doc.authorUuid;
      d.sec = this.doc.sec;
      d.sigs.addAll(this.doc.sigs);
      d.stamps.addAll(this.doc.stamps);
      return d;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      if (this.isPass && !this.built && this.template() != null) {
         for (Entry<String, UiField> e : this.boxes.entrySet()) {
            this.values.put(e.getKey(), e.getValue().getValue());
         }

         this.rebuildWidgets();
      }

      Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, L.t("Набор фальсификатора"));
      int x = this.formX + 10;
      int w = this.formW - 20;
      if (!this.isDoc && !this.isPass) {
         List<String> lines = Ui.wrap(this.font, L.t("Возьмите в другую руку документ, паспорт, удостоверение, билет или штраф."), w);
         int y = this.formY + 34;

         for (String l : lines) {
            g.drawString(this.font, l, x, y, -3555408, false);
            y += 11;
         }

         super.render(g, mx, my, pt);
      } else {
         if (this.stampsY >= 0 && !this.stamps().isEmpty()) {
            Ui.section(g, this.font, L.t("Печати на документе"), x, this.stampsY, w);
         }

         if (this.vBody != null && this.body != null) {
            this.vBody = this.body.getValue();
         }

         int key = Objects.hash(
            this.values.hashCode(), this.vBody, this.localPhoto == null ? 0 : this.localPhoto.hashCode(), StampCache.version, PassportCache.version
         );
         if (key != this.renderedKey || !this.preview.ready()) {
            BufferedImage img = this.renderForged(1200);
            if (img != null) {
               this.preview.set(img);
               this.renderedKey = key;
            }
         }

         if (this.preview.ready()) {
            double asp = (double)this.preview.h / (double)this.preview.w;
            int dw = this.pvW;
            int dh = (int)((double)this.pvW * asp);
            if (dh > this.pvH) {
               dh = this.pvH;
               dw = (int)((double)this.pvH / asp);
            }

            int dx = this.pvX + (this.pvW - dw) / 2;
            int dy = this.pvY + (this.pvH - dh) / 2;
            Ui.paperFrame(g, dx, dy, dw, dh);
            g.blit(this.preview.id(), dx, dy, dw, dh, 0.0F, 0.0F, this.preview.w, this.preview.h, this.preview.w, this.preview.h);
            this.header(g, L.t("Так будет выглядеть подделка"), "", dx, dy - 14);
         }

         String warn = L.t("Внешне не отличить. Но проверка по реестру, УФ-свет и код защиты подделку выдадут.");
         g.drawCenteredString(this.font, Ui.ellipsize(this.font, warn, this.pvW), this.pvX + this.pvW / 2, this.height - 30, -868018);
         super.render(g, mx, my, pt);
         this.renderToast(g);
      }
   }

   public void removed() {
      this.preview.close();
      super.removed();
   }
}
