package ru.realdocs.client.screen;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.realdocs.L;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.render.PassportRenderer;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.model.DocModel;
import ru.realdocs.model.PassportModel;
import ru.realdocs.model.PassportTemplate;

public final class RemoteViewScreen extends ViewerScreen {
   private final boolean passport;
   private final DocModel doc;
   private final PassportModel pass;
   private final int status;
   private final String from;
   private int spread;

   public RemoteViewScreen(boolean passport, CompoundTag tag, int status, List<String> lines, String from) {
      super(Component.literal(passport ? L.t("Паспорт") : L.t("Документ")), InteractionHand.MAIN_HAND, false);
      this.passport = passport;
      this.doc = passport ? null : NbtCodec.readDoc(tag);
      this.pass = passport ? NbtCodec.readPass(tag) : null;
      this.status = status;
      this.from = from;
      this.setVerify(status, lines);
      this.subtitle = L.t("показывает ") + from;
   }

   @Override
   protected BufferedImage renderPage(int px) {
      if (!this.passport) {
         return DocRenderer.render(this.doc, StampCache.INSTANCE, px, this.uvMode()).image();
      } else {
         PassportTemplate t = PassportCache.INSTANCE.template(this.pass.templateId);
         return t == null
            ? this.placeholder(px)
            : PassportRenderer.renderSpread(t, this.pass, PassportCache.INSTANCE, StampCache.INSTANCE, px, false, this.spread, this.uvMode());
      }
   }

   private int spreads() {
      PassportTemplate t = this.passport ? PassportCache.INSTANCE.template(this.pass.templateId) : null;
      return t == null ? 0 : t.extraSides();
   }

   @Override
   protected void addPanelButtons() {
      if (this.passport && this.pass.format == 1) {
         this.addBtn(this.spread == 0 ? L.t("Перевернуть: оборот") : L.t("Перевернуть: лицевая"), () -> {
            this.spread = this.spread == 0 ? 1 : 0;
            this.rebuildWidgets();
         });
      } else if (this.passport && this.spreads() > 0) {
         int x = this.panelX + 8;
         int w = this.panelW - 16;
         this.button("◀", x, this.nextY, 22, () -> {
            this.spread = Math.max(0, this.spread - 1);
            this.rebuildWidgets();
         }).enabled(this.spread > 0);
         this.button(this.spread == 0 ? L.t("Данные") : L.t("Визы ") + this.spread + " / " + this.spreads(), x + 26, this.nextY, w - 52, () -> {
            this.spread = this.spread == 0 ? 1 : 0;
            this.rebuildWidgets();
         });
         this.button("▶", x + w - 22, this.nextY, 22, () -> {
            this.spread = Math.min(this.spreads(), this.spread + 1);
            this.rebuildWidgets();
         }).enabled(this.spread < this.spreads());
         this.nextY += 24;
      }

      this.addSecurityButtons();
   }

   @Override
   protected void requestVerify() {
   }

   @Override
   protected boolean canVerifyHotkey() {
      return false;
   }

   private BufferedImage placeholder(int px) {
      BufferedImage img = new BufferedImage(
         px, (int)((double)(px * PassportTemplate.height(this.pass.format)) / (double)PassportTemplate.width(this.pass.format)), 2
      );
      Graphics2D g = img.createGraphics();
      g.setColor(new Color(235, 230, 215));
      g.fillRect(0, 0, img.getWidth(), img.getHeight());
      g.setColor(Color.GRAY);
      g.setFont(new Font("Serif", 2, px / 22));
      g.drawString(L.t("Загрузка шаблона…"), px / 3, img.getHeight() / 2);
      g.dispose();
      return img;
   }

   @Override
   protected double aspect() {
      return this.passport ? (double)PassportTemplate.height(this.pass.format) / (double)PassportTemplate.width(this.pass.format) : 1.414516129032258;
   }

   @Override
   protected double pageUnitsW() {
      return this.passport ? (double)PassportTemplate.width(this.pass.format) : 620.0;
   }

   @Override
   protected int contentKey() {
      return this.status + this.spread * 7919 + (this.passport ? this.pass.canonical().hashCode() : this.doc.canonical().hashCode());
   }

   @Override
   protected int stampCount() {
      return this.passport ? this.pass.stamps.size() : this.doc.stamps.size();
   }
}
