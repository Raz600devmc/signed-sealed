package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiScreen;

/** Asks other players to sign the document in hand; the document waits on the server meanwhile. */
public final class AskSignScreen extends UiScreen {
   private final Screen parent;
   private final InteractionHand hand;
   private String to = "";
   private int x0;
   private int y0;
   private int w0;
   private int h0;

   public AskSignScreen(Screen parent, InteractionHand hand) {
      super(Component.literal(L.t("Запросить подпись")));
      this.parent = parent;
      this.hand = hand;
   }

   protected void init() {
      this.w0 = Math.min(320, this.width - 40);
      this.h0 = 150;
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      NameField f = this.nameField(x, this.y0 + 44, w, L.t("ники через запятую"), this.to, 200, s -> this.to = s, true, false);
      f.label(L.t("Кого попросить подписать"));
      this.setInitialFocus(f);
      this.button(L.t("Отправить на подпись"), x, this.y0 + this.h0 - 28, 150, 20, () -> {
         CompoundTag t = new CompoundTag();
         t.putInt("Hand", this.hand == InteractionHand.OFF_HAND ? 1 : 0);
         t.putString("To", this.to);
         ClientOffice.req("sign_ask", t);
         this.minecraft.setScreen(null);
      }).primary();
      this.button(L.t("Отмена"), this.x0 + this.w0 - 78, this.y0 + this.h0 - 28, 66, 20, () -> this.minecraft.setScreen(this.parent)).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Запросить подпись"));
      int y = this.y0 + 72;

      for (var line : this.font.split(Component.literal(L.t("Документ уйдёт в канцелярию и не изменится, пока все не ответят. Потом он вернётся к вам, а подписавшие получат заверенные копии.")), this.w0 - 24)) {
         g.drawString(this.font, line, this.x0 + 12, y, -6253953, false);
         y += 10;
      }

      super.render(g, mx, my, pt);
   }
}
