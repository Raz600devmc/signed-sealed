package ru.realdocs.client.screen;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.PageTexture;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.Fonts;
import ru.realdocs.client.render.MailRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.client.ui.UiTextArea;
import ru.realdocs.data.MailCodec;
import ru.realdocs.data.Tags;
import ru.realdocs.item.EnvelopeItem;
import ru.realdocs.item.StampItem;
import ru.realdocs.model.MailModel;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.net.Packets;
import ru.realdocs.server.MailService;

/** Writing, sealing, checking and opening a letter. */
public final class EnvelopeScreen extends UiScreen {
   private final InteractionHand hand;
   private final ItemStack readOnly;
   private MailModel m;
   private List<String> attachments = new ArrayList<>();
   private int view;
   private final PageTexture tex = new PageTexture();
   private int texKey = Integer.MIN_VALUE;
   private int dataHash;
   private String to = "";
   private String from = "";
   private String text = "";
   private int letterFont = Fonts.HAND;
   private int wax;
   private String stamp = "";
   private boolean edited;
   private long sealArm;
   private long breakArm;
   private int verifyStatus = -1;
   private final List<String> verifyLines = new ArrayList<>();
   private int px;
   private int py;
   private int pw;
   private int ph;
   private int cardX;
   private int cardW;

   public EnvelopeScreen(InteractionHand hand, ItemStack readOnly) {
      super(Component.literal(L.t("Письмо")));
      this.hand = hand;
      this.readOnly = readOnly;
   }

   private ItemStack stack() {
      if (this.readOnly != null) {
         return this.readOnly;
      } else {
         return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getItemInHand(this.hand) : ItemStack.EMPTY;
      }
   }

   private boolean editable() {
      return this.readOnly == null && this.m != null && this.m.state == MailModel.OPEN;
   }

   private void reload() {
      ItemStack st = this.stack();
      this.m = MailCodec.readMail(st);
      this.dataHash = Tags.get(st).hashCode();
      this.attachments = new ArrayList<>();
      if (this.minecraft != null && this.minecraft.level != null) {
         for (ItemStack s : MailCodec.items(st, MailCodec.MAIL_TAG, this.minecraft.level)) {
            this.attachments.add(s.getHoverName().getString());
         }
      }

      this.to = this.m.to;
      this.from = this.m.from.isEmpty() && this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getGameProfile().getName() : this.m.from;
      this.text = this.m.text;
      this.letterFont = this.m.blank() ? Fonts.HAND : Fonts.clamp(this.m.font);
      this.wax = this.m.wax;
      this.edited = false;
      if (this.m.broken() && this.view == 0) {
         this.view = 2;
      }
   }

   protected void init() {
      if (this.m == null) {
         this.reload();
      }

      this.cardW = Math.max(200, Math.min(250, this.width * 2 / 5));
      this.cardX = this.width - this.cardW - 12;
      int areaW = this.cardX - 24;
      int areaH = this.height - 60;
      boolean letter = this.view == 2;
      double aspect = letter ? (double)MailRenderer.LW / MailRenderer.LH : (double)MailRenderer.EW / MailRenderer.EH;
      this.pw = Math.min(areaW, (int)(areaH * aspect));
      this.ph = (int)(this.pw / aspect);
      this.px = 12 + (areaW - this.pw) / 2;
      this.py = 30 + (areaH - this.ph) / 2;
      int tabW = Math.min(90, (areaW - 6) / 3);
      int tx = 12 + (areaW - (tabW * 3 + 6)) / 2;
      String[] tabs = new String[]{L.t("Лицевая"), L.t("Оборот"), L.t("Письмо")};

      for (int i = 0; i < 3; i++) {
         int k = i;
         boolean canRead = i != 2 || this.m.state != MailModel.SEALED;
         this.button(tabs[i], tx + i * (tabW + 3), 8, tabW, 18, () -> {
            this.view = k;
            Sounds.play(Sounds.PAPER, 1.2F, 0.5F);
            this.rebuildWidgets();
         }).selected(this.view == i).enabled(canRead);
      }

      int x = this.cardX + 10;
      int w = this.cardW - 20;
      int y = 38;
      if (this.editable()) {
         UiField ft = this.nameField(x, y + 10, w, L.t("имя игрока, несколько через запятую или @список"), this.to, MailModel.MAX_TO, s -> {
            this.to = s;
            this.edited = true;
         }, true, true);
         ft.label(L.t("Кому"));
         y += 32;
         UiField ff = this.field(x, y + 10, w, L.t("подпись отправителя"), this.from, MailModel.MAX_NAME, s -> {
            this.from = s;
            this.edited = true;
         });
         ff.label(L.t("От кого"));
         y += 32;
         int attachH = 12 + Math.max(12, this.attachments.size() * 18) + 4;
         int sealTop = this.height - 28 - 24 - 22 - 34 - 15;
         int th = Math.max(30, Math.min(100, sealTop - (y + 14 + 24 + attachH + 26)));
         UiTextArea ta = new UiTextArea(this.font, x, y, w, th, L.t("текст письма…"));
         ta.setCharacterLimit(MailModel.MAX_TEXT);
         ta.setValue(this.text);
         ta.setValueListener(s -> {
            this.text = s;
            this.edited = true;
            this.texKey = Integer.MIN_VALUE;
         });
         this.addRenderableWidget(ta);
         y += th + 14;
         this.button("◀", x, y, 18, 18, () -> this.cycleFont(-1));
         this.button(L.t("Почерк: ") + L.t(Fonts.NAMES[this.letterFont]), x + 21, y, w - 42, 18, () -> this.cycleFont(1));
         this.button("▶", x + w - 18, y, 18, 18, () -> this.cycleFont(1));
         y += 24;
         y = this.attachmentRows(x, y, w);
         this.button(L.t("Вложить из другой руки"), x, y, w, 18, () -> this.send(MailService.ATTACH, 0)).enabled(this.attachments.size() < MailModel.MAX_ITEMS);
         y += 26;
      } else {
         y = this.info(x, y, w);
         if (!this.m.sealed()) {
            y = this.attachmentRows(x, y, w);
         }
      }

      int by = this.height - 28;
      this.button(L.t("Закрыть"), this.cardX + this.cardW - 70, by, 60, 20, this::onClose).ghost();
      if (this.readOnly == null) {
         if (this.editable()) {
            this.sealControls(x, w, by);
         } else if (this.m.sealed()) {
            this.button(L.t("Проверить сургуч"), x, by - 24, w, 20, () -> {
               this.verifyStatus = -2;
               Net.sendToServer(new Packets.Verify(this.hand));
            });
            this.button(L.t("Вскрыть"), x, by, w - 76, 20, () -> {
               long now = System.currentTimeMillis();
               if (now - this.breakArm > 3000L) {
                  this.breakArm = now;
                  this.info(L.t("Сломанный сургуч не восстановить. Нажмите ещё раз, чтобы вскрыть."));
               } else {
                  this.send(MailService.BREAK, 0);
                  this.view = 2;
               }
            }).danger();
         }
      }
   }

   private void sealControls(int x, int w, int by) {
      Map<String, String> stamps = new LinkedHashMap<>();
      Inventory inv = this.minecraft.player.getInventory();

      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack st = inv.getItem(i);
         if (st.getItem() instanceof StampItem && Tags.has(st)) {
            CompoundTag t = Tags.get(st);
            String id = t.getString("StampId");
            if (!id.isEmpty()) {
               stamps.put(id, t.getString("StampOrg"));
            }
         }
      }

      int y = by - 24 - 22 - 34;
      int n = MailModel.WAX_RGB.length;
      int sw = (w - 3 * (n - 1)) / n;

      for (int i = 0; i < n; i++) {
         int k = i;
         this.button("", x + i * (sw + 3), y, sw, 18, () -> {
            this.wax = k;
            this.edited = true;
            this.texKey = Integer.MIN_VALUE;
            this.rebuildWidgets();
         }).swatch(MailModel.WAX_RGB[i]).selected(this.wax == i);
      }

      y += 22;
      List<String> ids = new ArrayList<>(stamps.keySet());
      ids.add(0, "");
      int cur = Math.max(0, ids.indexOf(this.stamp));
      String label = cur == 0 ? L.t("Без оттиска печати") : L.t("Оттиск: ") + stamps.get(ids.get(cur));
      this.button(label, x, y, w, 18, () -> {
         int next = (ids.indexOf(this.stamp) + 1) % ids.size();
         this.stamp = ids.get(Math.max(0, next));
         this.rebuildWidgets();
      }).enabled(ids.size() > 1);
      y += 22;
      this.button(L.t("Сохранить"), x, by, 70, 20, () -> {
         this.send(MailService.SAVE, this.letterFont);
         this.ok(L.t("Письмо сохранено."));
      });
      this.button(L.t("Запечатать"), x, by - 24, w, 20, () -> {
         long now = System.currentTimeMillis();
         if (this.to.isBlank()) {
            this.error(L.t("Укажите получателя."));
         } else if (now - this.sealArm > 3000L) {
            this.sealArm = now;
            this.info(L.t("Нужен сургуч в инвентаре. После этого письмо не изменить — нажмите ещё раз."));
         } else {
            this.send(MailService.SEAL, this.wax | this.letterFont << 8);
            this.view = 1;
         }
      }).primary();
   }

   private int attachmentRows(int x, int y, int w) {
      this.attachY = y;
      y += 12;

      for (int i = 0; i < this.attachments.size(); i++) {
         int k = i;
         if (this.readOnly == null && this.m.state != MailModel.SEALED) {
            this.button(L.t("Достать"), x + w - 56, y, 56, 16, () -> this.send(MailService.DETACH, k));
         }

         y += 18;
      }

      if (this.attachments.isEmpty()) {
         y += 12;
      }

      return y + 4;
   }

   private int attachY = -1;
   private int infoY = -1;

   private int info(int x, int y, int w) {
      this.infoY = y;
      return y + 70;
   }

   private void cycleFont(int d) {
      this.letterFont = Math.floorMod(this.letterFont + d, Fonts.count());
      this.edited = true;
      this.texKey = Integer.MIN_VALUE;
      this.rebuildWidgets();
   }

   private void send(int action, int n) {
      this.edited = false;
      Net.sendToServer(new MorePackets.MailAction(this.hand, action, this.to, this.from, this.text, this.stamp, n));
   }

   public void setVerify(int status, List<String> lines) {
      this.verifyStatus = status;
      this.verifyLines.clear();
      this.verifyLines.addAll(lines);
   }

   public void tick() {
      super.tick();
      ItemStack st = this.stack();
      if (this.readOnly == null && !(st.getItem() instanceof EnvelopeItem)) {
         this.minecraft.setScreen(null);
      } else if (Tags.get(st).hashCode() != this.dataHash) {
         String keepTo = this.to;
         String keepFrom = this.from;
         String keepText = this.text;
         boolean keep = this.edited;
         this.reload();
         if (keep && this.m.state == MailModel.OPEN) {
            this.to = keepTo;
            this.from = keepFrom;
            this.text = keepText;
            this.edited = true;
         }

         this.texKey = Integer.MIN_VALUE;
         this.rebuildWidgets();
      }
   }

   private MailModel shown() {
      MailModel s = new MailModel();
      s.id = this.m.id.isEmpty() ? "draft" : this.m.id;
      s.to = this.editable() ? this.to : this.m.to;
      s.from = this.editable() ? this.from : this.m.from;
      s.text = this.editable() ? this.text : this.m.text;
      s.date = this.m.date;
      s.state = this.m.state;
      s.wax = this.editable() ? this.wax : this.m.wax;
      s.sealId = this.m.sealId;
      s.sealOrg = this.m.sealOrg;
      s.sealer = this.m.sealer;
      s.font = this.editable() ? this.letterFont : this.m.font;
      s.postmark = this.m.postmark;
      return s;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      MailModel s = this.shown();
      int key = Objects.hash(this.view, s.to, s.from, s.text, s.state, s.wax, s.font, s.sealId, s.postmark, StampCache.version, this.pw);
      if (key != this.texKey) {
         this.texKey = key;
         int rpx = Math.max(400, Math.min(1400, (int)(this.pw * this.minecraft.getWindow().getGuiScale())));
         BufferedImage img = switch (this.view) {
            case 1 -> MailRenderer.back(s, rpx, StampCache.INSTANCE);
            case 2 -> MailRenderer.letter(s, rpx);
            default -> MailRenderer.front(s, rpx);
         };
         this.tex.set(img);
      }

      Ui.shadow(g, this.px, this.py, this.pw, this.ph, 6);
      if (this.tex.ready()) {
         g.blit(this.tex.id(), this.px, this.py, this.pw, this.ph, 0.0F, 0.0F, this.tex.w, this.tex.h, this.tex.w, this.tex.h);
      }

      Ui.card(g, this.font, this.cardX, 8, this.cardW, this.height - 16, L.t(this.m.sealed() ? "Запечатанное письмо" : (this.m.broken() ? "Вскрытое письмо" : "Письмо")));
      int x = this.cardX + 10;
      int w = this.cardW - 20;
      if (this.infoY >= 0 && !this.editable()) {
         int y = this.infoY;
         g.drawString(this.font, Ui.ellipsize(this.font, L.t("Кому: ") + this.m.to, w), x, y, -1183755, false);
         g.drawString(this.font, Ui.ellipsize(this.font, L.t("От: ") + this.m.from, w), x, y + 12, -3555408, false);
         if (this.m.state != MailModel.OPEN) {
            String seal = L.t("Сургуч: ") + (this.m.sealOrg.isEmpty() ? L.t("без оттиска") : "«" + this.m.sealOrg + "»");
            g.drawString(this.font, Ui.ellipsize(this.font, seal, w), x, y + 28, -6253953, false);
            g.drawString(this.font, Ui.ellipsize(this.font, L.t("Запечатал ") + this.m.sealer + ", " + this.m.sealDate, w), x, y + 40, -6253953, false);
         }

         if (this.m.broken()) {
            g.drawString(this.font, L.t("Вскрыто ") + this.m.brokenDate, x, y + 52, -24504, false);
         } else if (!this.m.postmark.isEmpty()) {
            g.drawString(this.font, L.t("Почтовый штемпель: ") + this.m.postmark, x, y + 52, -9543342, false);
         }
      }

      if (this.attachY >= 0 && (this.editable() || !this.m.sealed())) {
         Ui.section(g, this.font, L.t("Вложения") + " " + this.attachments.size() + "/" + MailModel.MAX_ITEMS, x, this.attachY, w);

         for (int i = 0; i < this.attachments.size(); i++) {
            g.drawString(this.font, Ui.ellipsize(this.font, this.attachments.get(i), w - 62), x, this.attachY + 16 + i * 18, -1183755, false);
         }

         if (this.attachments.isEmpty()) {
            g.drawString(this.font, L.t("пусто"), x, this.attachY + 14, -9543342, false);
         }
      }

      if (this.editable()) {
         int y = this.height - 28 - 24 - 22 - 34 - 11;
         Ui.label(g, this.font, L.t("Сургуч и печать"), x + 1, y);
      }

      if (this.m.sealed() && this.view == 2) {
         g.drawCenteredString(this.font, L.t("Письмо запечатано — сначала вскройте его."), this.px + this.pw / 2, this.py + this.ph / 2, -1183755);
      }

      if (this.verifyStatus != -1) {
         int y = this.height - 28 - 24 - 14 - this.verifyLines.size() * 10 - 12;
         int col = this.verifyStatus == -2 ? -6253953 : (this.verifyStatus == 2 ? -10628481 : -1088669);
         g.drawString(this.font, this.verifyStatus == -2 ? L.t("Проверка…") : (this.verifyStatus == 2 ? L.t("Сургуч подлинный") : L.t("Внимание!")), x, y, col, false);

         for (int i = 0; i < this.verifyLines.size(); i++) {
            List<String> wrapped = Ui.wrap(this.font, this.verifyLines.get(i), w);
            for (String l : wrapped) {
               y += 10;
               g.drawString(this.font, l, x, y, -3555408, false);
            }
         }
      }

      super.render(g, mx, my, pt);
      this.renderToast(g);
   }

   public void onClose() {
      if (this.edited && this.editable()) {
         this.send(MailService.SAVE, this.letterFont);
      }

      super.onClose();
   }

   public void removed() {
      this.tex.close();
      super.removed();
   }
}
