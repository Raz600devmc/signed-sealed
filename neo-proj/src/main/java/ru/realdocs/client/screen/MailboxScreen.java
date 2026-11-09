package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.MailService;

/** Post box: send the letter from the main hand or collect your mail. */
public final class MailboxScreen extends UiScreen {
   private final BlockPos pos;
   private final int waiting;
   private final boolean holdsLetter;
   private final String to;
   private int x0;
   private int y0;
   private int w0;
   private int h0;

   public MailboxScreen(BlockPos pos, int waiting, boolean holdsLetter, String to) {
      super(Component.literal(L.t("Почтовый ящик")));
      this.pos = pos;
      this.waiting = waiting;
      this.holdsLetter = holdsLetter;
      this.to = to == null ? "" : to;
   }

   protected void init() {
      this.w0 = Math.min(300, this.width - 40);
      this.h0 = 150;
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      this.button(L.t("Забрать письма") + (this.waiting > 0 ? " (" + this.waiting + ")" : ""), x, this.y0 + 60, w, 20, () -> {
         Net.sendToServer(new MorePackets.MailboxAction(this.pos, MailService.BOX_RECEIVE));
         this.onClose();
      }).primary().enabled(this.waiting > 0);
      this.button(L.t("Отправить письмо из руки"), x, this.y0 + 86, w, 20, () -> {
         Net.sendToServer(new MorePackets.MailboxAction(this.pos, MailService.BOX_SEND));
         this.onClose();
      }).enabled(this.holdsLetter && !this.to.isBlank());
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 78, this.y0 + this.h0 - 28, 66, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Почтовый ящик"));
      int x = this.x0 + 12;
      String s = this.waiting > 0 ? L.t("Для вас писем: ") + this.waiting : L.t("Для вас писем нет.");
      g.drawString(this.font, s, x, this.y0 + 30, this.waiting > 0 ? -10628481 : -6253953, false);
      String h = !this.holdsLetter
         ? L.t("Чтобы отправить, возьмите письмо в основную руку.")
         : (this.to.isBlank() ? L.t("На письме не указан получатель.") : L.t("Получатель: ") + this.to);
      g.drawString(this.font, Ui.ellipsize(this.font, h, this.w0 - 24), x, this.y0 + 43, -9543342, false);
      super.render(g, mx, my, pt);
   }
}
