package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.model.DocModel;

/** A document someone asked you to sign: read it, then sign or decline without holding the item. */
public final class SignRequestScreen extends UiScreen {
   private static final String[] STATES = new String[]{"ждёт", "подписал(а)", "отказался(ась)"};
   private final CompoundTag data;
   private final DocModel doc;
   private final PageView view = new PageView();
   private int drawnKey;
   private int panelX;
   private int panelW;

   public SignRequestScreen(CompoundTag data) {
      super(Component.literal(L.t("Документ на подпись")));
      this.data = data;
      this.doc = NbtCodec.readDoc(data.getCompound("Doc"));
   }

   private String id() {
      return this.data.getString("Id");
   }

   protected void init() {
      int pad = 10;
      this.panelW = Math.min(220, this.width / 3);
      this.panelX = this.width - pad - this.panelW;
      this.view.layout(pad, pad, this.panelX - 2 * pad, this.height - 2 * pad, 1.414516129032258);
      this.drawnKey = 0;
      int x = this.panelX + 10;
      int w = this.panelW - 20;
      int y = this.height - pad - 30;
      int mine = this.data.getInt("Mine");
      this.button(L.t("Закрыть"), x, y, w, 20, this::onClose).ghost();
      if (this.data.getBoolean("IsFrom")) {
         y -= 24;
         this.button(L.t("Отозвать и вернуть себе"), x, y, w, 20, () -> {
            this.send("sign_cancel");
            this.onClose();
         }).danger();
      }

      if (mine == 0) {
         y -= 24;
         this.button(L.t("Отказаться"), x, y, w, 20, () -> {
            this.send("sign_decline");
            this.onClose();
         });
         y -= 24;
         this.button(L.t("Подписать"), x, y, w, 20, () -> this.minecraft.setScreen(new SignatureScreen(this, true, "", L.t("Подписать"), (pos, sig) -> {
               CompoundTag t = new CompoundTag();
               t.putString("Id", this.id());
               t.putString("Pos", pos == null ? "" : pos);
               t.putIntArray("Sig", sig);
               ClientOffice.req("sign_do", t);
               this.onClose();
            }))).primary().enabled(this.data.getBoolean("CanSign"));
      }
   }

   private void send(String action) {
      CompoundTag t = new CompoundTag();
      t.putString("Id", this.id());
      ClientOffice.req(action, t);
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      int key = ((this.view.w * 31 + this.view.h) * 31 + StampCache.version) * 31 + ru.realdocs.client.PassportCache.version;
      if (key != this.drawnKey || !this.view.tex.ready()) {
         this.drawnKey = key;
         this.view.tex.set(DocRenderer.render(this.doc, StampCache.INSTANCE, Math.max(200, Math.min(1240, this.view.w * 2))).image());
      }

      this.view.draw(g);
      int top = 10;
      Ui.card(g, this.font, this.panelX, top, this.panelW, this.height - 20, L.t("Документ на подпись"));
      int x = this.panelX + 10;
      int w = this.panelW - 20;
      int y = top + 30;
      y = this.wrap(g, this.data.getString("Title"), x, y, w, -1183755);
      y += 4;
      g.drawString(this.font, Ui.ellipsize(this.font, L.t("Просит: ") + this.data.getString("From"), w), x, y, -6253953, false);
      y += 16;
      Ui.section(g, this.font, L.t("Подписи"), x, y, w);
      y += 14;
      ListTag l = this.data.getList("Targets", 10);

      for (int i = 0; i < l.size(); i++) {
         CompoundTag c = l.getCompound(i);
         int st = Math.max(0, Math.min(2, c.getInt("State")));
         int col = st == 1 ? -10628481 : (st == 2 ? -1088669 : -3555408);
         g.drawString(this.font, Ui.ellipsize(this.font, c.getString("Name") + " — " + L.t(STATES[st]), w), x, y, col, false);
         y += 11;
      }

      y += 8;
      String hint = this.data.getInt("Mine") == 0
         ? L.t("Подписанный экземпляр останется у автора, вам придёт заверенная копия.")
         : L.t("Документ хранится в канцелярии, пока все не ответят.");
      this.wrap(g, hint, x, y, w, -7633519);
      super.render(g, mx, my, pt);
   }

   private int wrap(GuiGraphics g, String s, int x, int y, int w, int color) {
      for (var line : this.font.split(Component.literal(s), w)) {
         g.drawString(this.font, line, x, y, color, false);
         y += 10;
      }

      return y;
   }

   public void removed() {
      this.view.close();
      super.removed();
   }
}
