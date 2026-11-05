package ru.realdocs.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.render.MailRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.PaperCodec;
import ru.realdocs.data.Tags;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.FolderItem;
import ru.realdocs.item.NewspaperItem;
import ru.realdocs.item.PassportItem;
import ru.realdocs.model.FolderModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.FolderService;

/** Folder contents: put documents in, take them out, look at them without taking out. */
public final class FolderScreen extends UiScreen {
   private final InteractionHand hand;
   private FolderModel f;
   private List<ItemStack> items = new ArrayList<>();
   private int dataHash;
   private String label = "";
   private int color;
   private final PageTexture cover = new PageTexture();
   private int coverKey = Integer.MIN_VALUE;
   private int scroll;
   private int listY;
   private int rows;
   private int cx;
   private int cy;
   private int cw;
   private int ch;
   private int cardX;
   private int cardW;

   public FolderScreen(InteractionHand hand) {
      super(Component.literal(L.t("Папка")));
      this.hand = hand;
   }

   private ItemStack stack() {
      return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getItemInHand(this.hand) : ItemStack.EMPTY;
   }

   private void reload() {
      ItemStack st = this.stack();
      this.f = MailCodec.readFolder(st);
      this.dataHash = Tags.get(st).hashCode();
      this.items = this.minecraft.level == null ? new ArrayList<>() : MailCodec.items(st, MailCodec.FOLDER_TAG, this.minecraft.level);
      this.label = this.f.label;
      this.color = this.f.color;
   }

   protected void init() {
      if (this.f == null) {
         this.reload();
      }

      this.cardW = Math.max(220, Math.min(280, this.width / 2));
      this.cardX = this.width - this.cardW - 12;
      int areaW = this.cardX - 24;
      int areaH = this.height - 24;
      double aspect = (double)MailRenderer.FW / MailRenderer.FH;
      this.cw = Math.min(areaW, (int)(areaH * aspect));
      this.ch = (int)(this.cw / aspect);
      this.cx = 12 + (areaW - this.cw) / 2;
      this.cy = 12 + (areaH - this.ch) / 2;
      int x = this.cardX + 10;
      int w = this.cardW - 20;
      int y = 36;
      UiField lf = this.field(x, y + 10, w - 76, L.t("например, Дело №12"), this.label, FolderModel.MAX_LABEL, s -> this.label = s);
      lf.label(L.t("Надпись на ярлыке"));
      this.button(L.t("Сохранить"), x + w - 72, y + 10, 72, 18, this::saveLabel);
      y += 34;
      int n = FolderModel.COLOR_RGB.length;
      int sw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int k = i;
         this.button("", x + i * (sw + 3), y, sw, 18, () -> {
            this.color = k;
            this.saveLabel();
         }).swatch(FolderModel.COLOR_RGB[i]).selected(this.color == i);
      }

      y += 30;
      this.listY = y + 12;
      int bottom = this.height - 8 - 26 - 50;
      this.rows = Math.max(1, (bottom - this.listY) / 20);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.items.size() - this.rows)));

      for (int i = this.scroll; i < Math.min(this.items.size(), this.scroll + this.rows); i++) {
         int k = i;
         int ry = this.listY + (i - this.scroll) * 20;
         this.button(L.t("Открыть"), x + w - 112, ry, 54, 18, () -> this.open(k)).enabled(this.viewable(this.items.get(k)));
         this.button(L.t("Достать"), x + w - 54, ry, 54, 18, () -> this.act(FolderService.TAKE, k));
      }

      int by = this.height - 8 - 26 - 44;
      this.button(L.t("Вложить из другой руки"), x, by, w, 18, () -> this.act(FolderService.PUT, 0)).enabled(this.items.size() < FolderModel.MAX_ITEMS);
      this.button(L.t("Вложить все документы из инвентаря"), x, by + 22, w, 18, () -> this.act(FolderService.PUT_ALL, 0))
         .enabled(this.items.size() < FolderModel.MAX_ITEMS);
      this.button(L.t("Закрыть"), this.cardX + this.cardW - 70, this.height - 34, 60, 20, this::onClose).ghost();
   }

   private boolean viewable(ItemStack s) {
      return s.getItem() instanceof DocumentItem || s.getItem() instanceof PassportItem || s.getItem() instanceof NewspaperItem || s.getItem() instanceof EnvelopeItem;
   }

   private void open(int i) {
      if (i >= 0 && i < this.items.size()) {
         ItemStack s = this.items.get(i);
         if (s.getItem() instanceof NewspaperItem) {
            PaperCodec.Ref r = PaperCodec.ref(s);
            if (r.published) {
               this.minecraft.setScreen(new NewspaperScreen(this.hand, r, false));
            } else {
               this.info(L.t("Это неопубликованный макет: достаньте его, чтобы открыть редактор."));
            }
         } else if (s.getItem() instanceof EnvelopeItem) {
            this.minecraft.setScreen(new EnvelopeScreen(this.hand, s.copy()));
         } else {
            this.act(FolderService.VIEW, i);
         }
      }
   }

   private void act(int action, int index) {
      Net.sendToServer(new MorePackets.FolderAction(this.hand, action, index, this.label, this.color));
   }

   private void saveLabel() {
      Net.sendToServer(new MorePackets.FolderAction(this.hand, FolderService.SET, 0, this.label, this.color));
      this.coverKey = Integer.MIN_VALUE;
      Sounds.play(Sounds.PAPER, 1.3F, 0.4F);
   }

   public void tick() {
      super.tick();
      ItemStack st = this.stack();
      if (!(st.getItem() instanceof FolderItem)) {
         this.minecraft.setScreen(null);
      } else if (Tags.get(st).hashCode() != this.dataHash) {
         String keep = this.label;
         this.reload();
         this.label = keep;
         this.coverKey = Integer.MIN_VALUE;
         this.rebuildWidgets();
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      FolderModel shown = new FolderModel();
      shown.label = this.label;
      shown.color = this.color;
      int key = (shown.label + "|" + shown.color + "|" + this.items.size() + "|" + this.cw).hashCode();
      if (key != this.coverKey) {
         this.coverKey = key;
         int px = Math.max(300, Math.min(1000, (int)(this.cw * this.minecraft.getWindow().getGuiScale())));
         this.cover.set(MailRenderer.folder(shown, this.items.size(), px));
      }

      Ui.shadow(g, this.cx, this.cy, this.cw, this.ch, 6);
      if (this.cover.ready()) {
         g.blit(this.cover.id(), this.cx, this.cy, this.cw, this.ch, 0.0F, 0.0F, this.cover.w, this.cover.h, this.cover.w, this.cover.h);
      }

      Ui.card(g, this.font, this.cardX, 8, this.cardW, this.height - 16, L.t("Папка") + (this.label.isEmpty() ? "" : " «" + this.label + "»"));
      int x = this.cardX + 10;
      int w = this.cardW - 20;
      Ui.label(g, this.font, L.t("Цвет обложки"), x + 1, 60);
      Ui.section(g, this.font, L.t("Содержимое") + " " + this.items.size() + "/" + FolderModel.MAX_ITEMS, x, this.listY - 12, w);
      if (this.items.isEmpty()) {
         g.drawString(this.font, L.t("Папка пуста."), x, this.listY + 4, -9543342, false);
      }

      for (int i = this.scroll; i < Math.min(this.items.size(), this.scroll + this.rows); i++) {
         int ry = this.listY + (i - this.scroll) * 20;
         g.drawString(this.font, Ui.ellipsize(this.font, this.items.get(i).getHoverName().getString(), w - 118), x, ry + 5, -1183755, false);
      }

      if (this.items.size() > this.rows) {
         g.drawString(this.font, L.t("колесо — прокрутка"), x, this.listY + this.rows * 20, -9543342, false);
      }

      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      int ns = Math.max(0, Math.min(Math.max(0, this.items.size() - this.rows), this.scroll - (int)Math.signum(sy)));
      if (ns != this.scroll) {
         this.scroll = ns;
         this.rebuildWidgets();
      }

      return true;
   }

   public void onClose() {
      if (this.f != null && (!this.label.equals(this.f.label) || this.color != this.f.color)) {
         this.saveLabel();
      }

      super.onClose();
   }

   public void removed() {
      this.cover.close();
      super.removed();
   }
}
