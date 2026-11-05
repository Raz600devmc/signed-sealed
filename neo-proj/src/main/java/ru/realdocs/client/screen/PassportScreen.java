package ru.realdocs.client.screen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.Exporter;
import ru.realdocs.client.ImageUtil;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.WebImage;
import ru.realdocs.client.render.ImageFx;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;

public final class PassportScreen extends ViewerScreen {
   private PassportModel pass;
   private final boolean editing;
   private final Map<String, String> values = new LinkedHashMap<>();
   private final Map<String, EditBox> boxes = new LinkedHashMap<>();
   private EditBox photoSrc;
   private String vPhotoSrc = "";
   private BufferedImage localPhoto;
   private byte[] photoPng;
   private int[] signature = new int[0];
   private String status = "";
   private int statusColor = -5592406;
   private boolean loading;
   private int formRev;
   private boolean coverOpen;
   private long flipStart = -1L;
   private boolean flipSwapped;
   private boolean flipTarget;
   private static final long FLIP_MS = 420L;
   private final boolean editIssued;
   private final PassportRenderer.Assets assets = id -> "__local".equals(id) ? this.localPhoto : PassportCache.INSTANCE.image(id);
   private int spread;
   private int formX;
   private int formY;
   private int formW;
   private int formH;
   private int photoY;
   private int sigY;
   private boolean labels;
   private boolean builtWithTemplate;
   private final PageTexture thumb = new PageTexture();
   private int thumbKey;
   private boolean cardTurn;

   public PassportScreen(InteractionHand hand, ItemStack stack) {
      this(hand, stack, false);
   }

   public PassportScreen(InteractionHand hand, ItemStack stack, boolean editIssued) {
      super(Component.literal(kindName(NbtCodec.readPass(stack).format)), hand, NbtCodec.readPass(stack).issued && !editIssued);
      this.pass = NbtCodec.readPass(stack);
      this.editIssued = editIssued && this.pass.issued;
      this.editing = !this.pass.issued || this.editIssued;
      if (this.editIssued) {
         this.values.putAll(this.pass.values);
      }

      this.coverOpen = this.pass.format != 0;
   }

   public static String kindName(int format) {
      return L.t(PassportTemplate.FORMATS[Math.max(0, Math.min(PassportTemplate.FORMATS.length - 1, format))]);
   }

   private boolean book() {
      PassportTemplate t = this.template();
      return t == null ? this.pass.format == 0 : t.book();
   }

   private int fmt() {
      PassportTemplate t = this.template();
      return t == null ? this.pass.format : t.format;
   }

   private PassportTemplate template() {
      return PassportCache.INSTANCE.template(this.pass.templateId);
   }

   private PassportModel current() {
      if (!this.editing) {
         ItemStack st = this.minecraft.player.getItemInHand(this.hand);
         if (st.getItem() instanceof PassportItem) {
            this.pass = NbtCodec.readPass(st);
         }
      }

      return this.pass;
   }

   private PassportModel draft(PassportTemplate t) {
      PassportModel p = new PassportModel();
      p.templateId = this.pass.templateId;
      p.format = t.format;

      for (Entry<String, EditBox> e : this.boxes.entrySet()) {
         this.values.put(e.getKey(), e.getValue().getValue());
      }

      p.values.putAll(this.values);
      p.photo = this.localPhoto != null ? "__local" : (this.editIssued ? this.pass.photo : "");
      p.signature = this.signature.length <= 0 && this.editIssued ? this.pass.signature : this.signature;
      p.serial = this.editIssued ? this.pass.serial : (t.serialPrefix.isEmpty() ? "" : t.serialPrefix + " ") + "000000";
      if (this.editIssued) {
         p.date = this.pass.date;
      }

      return p;
   }

   @Override
   protected BufferedImage renderPage(int px) {
      PassportTemplate t = this.template();
      if (t == null) {
         return this.placeholder(px, PassportCache.INSTANCE.templateMissing(this.pass.templateId) ? L.t("Шаблон недоступен") : L.t("Загрузка шаблона…"));
      } else if (this.editing) {
         return PassportRenderer.renderSpread(t, this.draft(t), this.assets, StampCache.INSTANCE, px, false);
      } else {
         return !this.coverOpen && t.book()
            ? PassportRenderer.renderCover(t, PassportCache.INSTANCE, 700)
            : PassportRenderer.renderSpread(t, this.current(), PassportCache.INSTANCE, StampCache.INSTANCE, px, false, this.spread, this.uvMode());
      }
   }

   private BufferedImage placeholder(int px, String text) {
      BufferedImage img = new BufferedImage(
         px, (int)((double)(px * PassportTemplate.height(this.pass.format)) / (double)PassportTemplate.width(this.pass.format)), 2
      );
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(235, 230, 215));
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.setColor(Color.GRAY);
      g.setFont(new Font("Serif", 2, px / 22));
      g.drawString(text, px / 3, img.getHeight() / 2);
      g.dispose();
      return img;
   }

   @Override
   protected double aspect() {
      return !this.editing && !this.coverOpen && this.template() != null && this.book()
         ? 1.4
         : (double)PassportTemplate.height(this.fmt()) / (double)PassportTemplate.width(this.fmt());
   }

   @Override
   protected double groupAspect() {
      return (double)PassportTemplate.height(this.fmt()) / (double)PassportTemplate.width(this.fmt());
   }

   @Override
   protected double pageUnitsW() {
      return !this.editing && !this.coverOpen && this.book() ? 300.0 : (double)PassportTemplate.width(this.fmt());
   }

   @Override
   protected int contentKey() {
      PassportTemplate t = this.template();
      if (!this.editing) {
         return Objects.hash(this.current().canonical().hashCode(), this.coverOpen, t == null, this.spread);
      } else {
         int h = 0;

         for (Entry<String, EditBox> e : this.boxes.entrySet()) {
            h = h * 31 + e.getValue().getValue().hashCode();
         }

         return Objects.hash(h, this.signature.length, this.photoPng == null ? 0 : this.photoPng.length, t == null, this.formRev);
      }
   }

   @Override
   protected int stampCount() {
      return this.current().stamps.size();
   }

   @Override
   protected int currentPage() {
      return this.spread;
   }

   private int spreads() {
      PassportTemplate t = this.template();
      return t == null ? 0 : t.extraSides();
   }

   private void turn(int d) {
      if (this.coverOpen && !this.editing) {
         int n = Math.max(0, Math.min(this.spreads(), this.spread + d));
         if (n != this.spread) {
            this.spread = n;
            this.tool.anims.clear();
            Sounds.play(Sounds.PAGE, 1.0F + (float)Math.random() * 0.15F);
            this.rebuildWidgets();
         }
      }
   }

   @Override
   protected List<double[]> holograms() {
      PassportTemplate t = this.template();
      if (!this.editing && this.coverOpen && this.spread == 0 && t != null) {
         List<double[]> out = new ArrayList<>();

         for (PassportTemplate.Field f : t.fields) {
            if (f.type == 2) {
               out.add(
                  new double[]{(double)(f.x + f.w - 46) / (double)t.w(), (double)(f.y + f.h - 36) / (double)t.h(), 58.0 / (double)t.w(), 44.0 / (double)t.h()}
               );
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   @Override
   public boolean keyPressed(int key, int scan, int mods) {
      if (!this.editing && this.coverOpen && (key == 263 || key == 262)) {
         this.turn(key == 263 ? -1 : 1);
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   @Override
   protected boolean stampingAllowed() {
      return !this.editing && this.coverOpen;
   }

   @Override
   protected String marksError() {
      if (!this.book()) {
         return L.t("Погранштампы и визы ставятся только в паспорт.");
      } else {
         return this.spread == 0 ? null : "";
      }
   }

   @Override
   protected boolean hasSidePanel() {
      return !this.editing;
   }

   @Override
   protected String panelTitle() {
      PassportTemplate t = this.template();
      return t == null ? kindName(this.pass.format) : L.t(t.name);
   }

   @Override
   protected void init() {
      if (!this.editing) {
         super.init();
      } else {
         PassportTemplate t = this.template();
         this.builtWithTemplate = t != null;
         this.formX = this.pad;
         this.formY = this.pad;
         this.formW = Math.max(230, Math.min(340, this.width * 2 / 5));
         this.formH = this.height - 2 * this.pad;
         int x = this.formX + 10;
         int w = this.formW - 20;
         int y = this.formY + 30;
         this.boxes.clear();
         int footY = this.formY + this.formH - 10 - 20 - 24;
         if (t != null) {
            List<PassportTemplate.Field> inputs = new ArrayList<>();

            for (PassportTemplate.Field f : t.fields) {
               if (PassportTemplate.playerInput(f)) {
                  inputs.add(f);
               }
            }

            if (inputs.size() > 10) {
               inputs = inputs.subList(0, inputs.size());
            }

            boolean hasPhoto = t.fields.stream().anyMatch(fx -> fx.type == 2);
            boolean hasSig = t.fields.stream().anyMatch(fx -> fx.type == 4);
            int extra = (hasPhoto ? 62 : 0) + (hasSig ? 28 : 0);
            int avail = footY - y - extra - 6;
            boolean two = inputs.size() > 4;
            int rows = two ? (inputs.size() + 1) / 2 : inputs.size();
            this.labels = rows * 32 <= avail;
            if (!this.labels && !two && rows * 22 > avail) {
               two = true;
               rows = (inputs.size() + 1) / 2;
            }

            int rowH = this.labels ? 32 : 22;
            int cw = two ? (w - 6) / 2 : w;

            for (int i = 0; i < inputs.size(); i++) {
               PassportTemplate.Field fx = inputs.get(i);
               int col = two ? i % 2 : 0;
               int row = two ? i / 2 : i;
               String hint = fx.type == 1 ? L.t("дд.мм.гггг") : (fx.hint.isBlank() ? (this.labels ? "" : fx.label) : fx.hint);
               String label = fx.label + (fx.required ? " *" : "");
               UiField e = this.field(
                  x + col * (cw + 6),
                  y + row * rowH + (this.labels ? 10 : 0),
                  cw,
                  this.labels ? hint : label,
                  this.values.getOrDefault(fx.key, ""),
                  60,
                  s -> this.formRev++
               );
               if (this.labels) {
                  e.label(label);
               }

               this.boxes.put(fx.key, e);
            }

            y += rows * rowH + 6;
            if (hasPhoto) {
               this.photoY = y;
               int tx = x + 44;
               this.photoSrc = this.field(tx, y + 10, w - 44 - 80, L.t("Ссылка или файл"), this.vPhotoSrc, 500, s -> this.vPhotoSrc = s);
               ((UiField)this.photoSrc).label(L.t("Фотография"));
               this.button(L.t("Загрузить"), x + w - 74, y + 9, 74, () -> this.startPhoto(this.vPhotoSrc));
               y += 62;
            }

            if (hasSig) {
               this.sigY = y;
               boolean sigReady = this.signature.length > 0 || this.editIssued && this.pass.signature.length > 0;
               this.button(
                     sigReady ? L.t("Подпись готова — переписать") : L.t("Нарисовать подпись"),
                     x,
                     y,
                     w,
                     () -> this.minecraft.setScreen(new SignatureScreen(this, false, "", L.t("Готово"), (pos, sig) -> {
                           this.signature = sig;
                           this.formRev++;
                           this.minecraft.setScreen(this);
                        }))
                  )
                  .selected(sigReady);
               y += 28;
            }
         }

         String action = this.editIssued ? L.t("Сохранить изменения") : (t != null && !t.book() ? L.t("Оформить") : L.t("Оформить паспорт"));
         this.button(action, x, footY, w, this::issue).primary().enabled(t != null);
         this.button(L.t("Закрыть"), x, footY + 24, w, this::onClose).ghost();
         int vx = this.formX + this.formW + this.pad;
         this.view.layout(vx, this.pad + 16, this.width - vx - this.pad, this.height - 2 * this.pad - 16, this.aspect());
      }
   }

   @Override
   protected void renderUnder(GuiGraphics g, int mx, int my) {
      if (this.editing) {
         PassportTemplate t = this.template();
         String head = this.editIssued ? L.t("Изменение данных") : (t != null && !t.book() ? L.t("Оформление") : L.t("Оформление паспорта"));
         Ui.card(g, this.font, this.formX, this.formY, this.formW, this.formH, head + (t == null ? "" : " · " + L.t(t.name)));
         int x = this.formX + 10;
         int w = this.formW - 20;
         if (t == null) {
            g.drawString(
               this.font,
               PassportCache.INSTANCE.templateMissing(this.pass.templateId) ? L.t("Шаблон этого бланка недоступен") : L.t("Загрузка шаблона…"),
               x,
               this.formY + 32,
               -6253953,
               false
            );
         } else {
            if (this.photoSrc != null) {
               int py = this.photoY + 2;
               int pw = 36;
               int ph = 48;
               Ui.round(g, x, py, pw, ph, -15396595);
               Ui.outline(g, x, py, pw, ph, this.localPhoto != null ? -2641559 : -11912404);
               if (this.localPhoto == null) {
                  g.drawCenteredString(this.font, L.t("фото"), x + pw / 2, py + ph / 2 - 4, -9543342);
               } else {
                  if (!this.thumb.ready() || this.thumbKey != System.identityHashCode(this.localPhoto)) {
                     this.thumb.set(this.localPhoto);
                     this.thumbKey = System.identityHashCode(this.localPhoto);
                  }

                  if (this.thumb.ready()) {
                     g.blit(this.thumb.id(), x + 1, py + 1, pw - 2, ph - 2, 0.0F, 0.0F, this.thumb.w, this.thumb.h, this.thumb.w, this.thumb.h);
                  }
               }

               String st = this.status.isEmpty() ? L.t("или перетащите файл в окно игры") : this.status;
               g.drawString(
                  this.font, Ui.ellipsize(this.font, st, w - 48), x + 45, this.photoY + 34, this.status.isEmpty() ? -9543342 : this.statusColor, false
               );
            }
         }
      }
   }

   @Override
   protected void relayout() {
      if (!this.editing) {
         super.relayout();
      }
   }

   @Override
   protected void addPanelButtons() {
      if (this.book()) {
         this.addBtn(this.coverOpen ? L.t("Закрыть паспорт") : L.t("Открыть паспорт"), () -> this.startFlip(!this.coverOpen));
      }

      if (this.fmt() == 1) {
         this.addBtn(this.spread == 0 ? L.t("Перевернуть: оборот") : L.t("Перевернуть: лицевая"), () -> this.startTurnCard());
      } else if (this.coverOpen && this.spreads() > 0) {
         int x = this.panelX + 8;
         int w = this.panelW - 16;
         this.button("◀", x, this.nextY, 22, () -> this.turn(-1)).enabled(this.spread > 0);
         this.button(this.spread == 0 ? L.t("Данные") : L.t("Визы ") + this.spread + " / " + this.spreads(), x + 26, this.nextY, w - 52, () -> {
            this.spread = this.spread == 0 ? 1 : 0;
            this.tool.anims.clear();
            this.rebuildWidgets();
         });
         this.button("▶", x + w - 22, this.nextY, 22, () -> this.turn(1)).enabled(this.spread < this.spreads());
         this.nextY += 24;
      }

      this.addBtn(L.t("Проверить подлинность"), this::requestVerify).hint("V");
      if (this.coverOpen) {
         this.addSecurityButtons();
      }

      if (this.canEditClient()) {
         this.addBtn(
            L.t("Изменить данные"), () -> this.minecraft.setScreen(new PassportScreen(this.hand, this.minecraft.player.getItemInHand(this.hand), true))
         );
      }

      this.addBtn(L.t("Экспорт в PNG"), () -> {
         PassportTemplate t = this.template();
         if (t != null) {
            try {
               BufferedImage img = PassportRenderer.renderSpread(t, this.current(), PassportCache.INSTANCE, StampCache.INSTANCE, 1280, false, this.spread, 0);
               String[] names = new String[]{"passport_", "id_", "ticket_", "fine_"};
               Path p = Exporter.exportImage(img, names[Math.max(0, Math.min(3, t.format))] + this.current().serial.replace(' ', '_'));
               this.ok(L.t("Сохранено: ") + p.getFileName() + L.t(" (папка signedsealed_exports)"));
            } catch (Exception var5) {
               this.error(L.t("Ошибка экспорта: ") + var5.getMessage());
            }
         }
      });
   }

   private void startFlip(boolean open) {
      if (this.flipStart < 0L && open != this.coverOpen) {
         if (!open) {
            this.tool.cancel();
         }

         this.flipStart = System.currentTimeMillis();
         this.flipSwapped = false;
         this.flipTarget = open;
         this.cardTurn = false;
         Sounds.play(Sounds.PAGE, 1.0F);
      }
   }

   private void startTurnCard() {
      if (this.flipStart < 0L) {
         this.tool.cancel();
         this.flipStart = System.currentTimeMillis();
         this.flipSwapped = false;
         this.flipTarget = true;
         this.cardTurn = true;
         Sounds.play(Sounds.CARD, 1.0F);
      }
   }

   private void swapSide() {
      if (this.cardTurn) {
         this.spread = this.spread == 0 ? 1 : 0;
         this.tool.anims.clear();
      } else {
         this.coverOpen = this.flipTarget;
         if (!this.coverOpen) {
            this.spread = 0;
            this.uvOn = false;
         }
      }
   }

   private boolean canEditClient() {
      PassportTemplate t = this.template();
      return t != null && this.minecraft.player != null && !this.editing
         ? t.ownerUuid.equals(this.minecraft.player.getUUID().toString()) || this.minecraft.player.hasPermissions(2)
         : false;
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      if (!this.editing && this.flipStart >= 0L) {
         double t = (double)(System.currentTimeMillis() - this.flipStart) / 420.0;
         if (!(t >= 1.0)) {
            if (t >= 0.5 && !this.flipSwapped) {
               this.swapSide();
               this.flipSwapped = true;
               this.relayout();
            }

            double f = t < 0.5 ? 1.0 - 2.0 * t : 2.0 * t - 1.0;
            int ox = this.view.x;
            int ow = this.view.w;
            int sw = Math.max(2, (int)((double)ow * f));
            this.view.x = ox + (ow - sw) / 2;
            this.view.w = sw;
            super.render(g, mx, my, pt);
            this.view.x = ox;
            this.view.w = ow;
            return;
         }

         this.flipStart = -1L;
         if (!this.flipSwapped) {
            this.swapSide();
            this.flipSwapped = true;
            this.relayout();
         }

         this.rebuildWidgets();
      }

      if (this.editing && !this.builtWithTemplate && this.template() != null) {
         for (Entry<String, EditBox> e : this.boxes.entrySet()) {
            this.values.put(e.getKey(), e.getValue().getValue());
         }

         this.rebuildWidgets();
      }

      super.render(g, mx, my, pt);
      if (!this.editing && !this.coverOpen && this.flipStart < 0L && this.book()) {
         String s = L.t("Нажмите на обложку, чтобы открыть");
         g.drawCenteredString(this.font, s, this.view.x + this.view.w / 2, this.view.y + this.view.h + 8, -6253953);
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (!this.editing && !this.coverOpen && this.book() && button == 0 && this.view.contains(mx, my) && !this.tool.active()) {
         this.startFlip(true);
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   private void startPhoto(String input) {
      if (!this.loading) {
         this.loading = true;
         this.status = L.t("Загрузка фото…");
         this.statusColor = -868018;
         WebImage.load(input).thenAccept(img -> this.minecraft.execute(() -> {
               this.loading = false;

               try {
                  BufferedImage src = ImageFx.fit(img, 1024, 1024);
                  this.photoPng = ImageUtil.photoPng(src, 16000);
                  this.localPhoto = ImageIO.read(new ByteArrayInputStream(this.photoPng));
                  this.status = L.t("Фото готово: ") + (double)this.photoPng.length / 1000.0 + L.t(" КБ");
                  this.statusColor = -10628481;
                  this.formRev++;
               } catch (Exception var3) {
                  this.status = L.t("Ошибка: ") + var3.getMessage();
                  this.statusColor = -1088669;
               }
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

   public void onFilesDrop(List<Path> files) {
      if (this.editing && this.photoSrc != null && !files.isEmpty()) {
         this.vPhotoSrc = files.get(0).toString();
         this.photoSrc.setValue(this.vPhotoSrc);
         this.startPhoto(this.vPhotoSrc);
      }
   }

   private void issue() {
      PassportTemplate t = this.template();
      if (t != null) {
         Map<String, String> out = new LinkedHashMap<>();

         for (Entry<String, EditBox> e : this.boxes.entrySet()) {
            out.put(e.getKey(), e.getValue().getValue().trim());
         }

         for (PassportTemplate.Field f : t.fields) {
            if (PassportTemplate.playerInput(f) && f.required && out.getOrDefault(f.key, "").isEmpty()) {
               this.error(L.t("Заполните поле «") + f.label + "».");
               return;
            }

            if (f.type == 2 && f.required && this.photoPng == null && !this.editIssued) {
               this.error(L.t("Добавьте фото."));
               return;
            }

            if (f.type == 4 && f.required && this.signature.length == 0 && !this.editIssued) {
               this.error(L.t("Нарисуйте подпись."));
               return;
            }
         }

         Net.sendToServer(new Packets.IssuePassport(this.hand, out, this.photoPng, this.signature, this.editIssued));
         Sounds.play(Sounds.PAPER, 1.0F);
         this.minecraft.setScreen(null);
      }
   }

   @Override
   public void removed() {
      this.thumb.close();
      this.thumbKey = 0;
      super.removed();
   }
}
