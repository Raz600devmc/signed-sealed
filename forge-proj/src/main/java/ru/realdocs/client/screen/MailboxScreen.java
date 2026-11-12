package ru.realdocs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.net.MorePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.MailService;

/** Post box: send the letter or newspaper from the main hand, collect your mail, see what you sent. */
public final class MailboxScreen extends UiScreen {
   private static final int SENT_ROWS = 6;
   private final BlockPos pos;
   private final int waiting;
   private final boolean holdsLetter;
   private final String to;
   private final CompoundTag extra;
   private String paperTo = "@subscribers";
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int sentY;
   private int paperY;

   public MailboxScreen(BlockPos pos, int waiting, boolean holdsLetter, String to) {
      super(Component.literal(L.t("Почтовый ящик")));
      this.pos = pos;
      this.waiting = waiting;
      this.holdsLetter = holdsLetter;
      this.to = to == null ? "" : to;
      this.extra = ClientOffice.mailbox;
   }

   private boolean paper() {
      return this.extra.contains("Paper");
   }

   private ListTag sent() {
      return this.extra.getList("Sent", 10);
   }

   private int infoY;
   private int sendY;
   private java.util.List<net.minecraft.util.FormattedCharSequence> info = java.util.List.of();

   private String status() {
      return !this.holdsLetter
         ? L.t("Чтобы отправить, возьмите письмо в основную руку.")
         : (this.to.isBlank() ? L.t("На письме не указан получатель.") : L.t("Получатель: ") + this.to);
   }

   protected void init() {
      int rows = Math.min(SENT_ROWS, this.sent().size());
      this.w0 = Math.min(340, this.width - 40);
      int w = this.w0 - 24;
      java.util.List<net.minecraft.util.FormattedCharSequence> lines = new java.util.ArrayList<>(this.font.split(Component.literal(this.status()), w));
      lines.addAll(this.font.split(Component.literal(L.t("Несколько адресатов и @списки — через запятую на конверте.")), w));
      this.info = lines;
      int h = 28 + 12 + 26;
      h += 2 + lines.size() * 10 + 4;
      h += 26;
      if (this.paper()) {
         h += 62;
      }

      h += 6 + 14 + Math.max(1, rows) * 12;
      h += 10 + 28;
      this.h0 = h;
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = Math.max(6, (this.height - this.h0) / 2);
      int x = this.x0 + 12;
      int y = this.y0 + 40;
      this.button(L.t("Забрать письма") + (this.waiting > 0 ? " (" + this.waiting + ")" : ""), x, y, w, 20, () -> {
         Net.sendToServer(new MorePackets.MailboxAction(this.pos, MailService.BOX_RECEIVE));
         this.onClose();
      }).primary().enabled(this.waiting > 0);
      y += 28;
      this.infoY = y;
      y += lines.size() * 10 + 4;
      this.sendY = y;
      this.button(L.t("Отправить письмо из руки"), x, y, w, 20, () -> {
         Net.sendToServer(new MorePackets.MailboxAction(this.pos, MailService.BOX_SEND));
         this.onClose();
      }).enabled(this.holdsLetter && !this.to.isBlank());
      y += 30;
      if (this.paper()) {
         this.paperY = y;
         NameField f = this.nameField(x, y + 24, w - 96, L.t("@subscribers, @список, ники…"), this.paperTo, 300, s -> this.paperTo = s, true, true);
         f.label(L.t("Кому разослать"));
         this.button(L.t("Разослать"), x + w - 90, y + 24, 90, 18, () -> {
            CompoundTag t = new CompoundTag();
            t.putInt("X", this.pos.getX());
            t.putInt("Y", this.pos.getY());
            t.putInt("Z", this.pos.getZ());
            t.putString("To", this.paperTo);
            ClientOffice.req("mail_paper", t);
            this.onClose();
         }).primary();
         y += 62;
      }

      this.sentY = y;
      int by = this.y0 + this.h0 - 28;
      this.button(L.t("Списки рассылки"), x, by, 130, 20, () -> {
         ClientOffice.req("lists");
         this.minecraft.setScreen(new MailListScreen(this));
      });
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 90, by, 78, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Почтовый ящик"));
      int x = this.x0 + 12;
      int w = this.w0 - 24;
      String s = this.waiting > 0 ? L.t("Для вас писем: ") + this.waiting : L.t("Для вас писем нет.");
      g.drawString(this.font, s, x, this.y0 + 27, this.waiting > 0 ? -10628481 : -6253953, false);
      int ly = this.infoY;
      int statusLines = this.font.split(Component.literal(this.status()), w).size();

      for (int i = 0; i < this.info.size(); i++) {
         g.drawString(this.font, this.info.get(i), x, ly, i < statusLines ? -9543342 : -6253953, false);
         ly += 10;
      }

      if (this.paper()) {
         Ui.section(g, this.font, L.t("Рассылка газеты"), x, this.paperY, w);
         String p = "«"
            + this.extra.getString("Paper")
            + "» · "
            + L.t("в руке: ")
            + this.extra.getInt("Copies")
            + " · "
            + L.t("подписчиков: ")
            + this.extra.getInt("Subs");
         g.drawString(this.font, Ui.ellipsize(this.font, p, w), x, this.paperY + 46, -6253953, false);
      }

      ListTag sent = this.sent();
      Ui.section(g, this.font, L.t("Отправленные"), x, this.sentY, w);
      if (sent.isEmpty()) {
         g.drawString(this.font, L.t("Вы ещё не отправляли писем."), x, this.sentY + 14, -6253953, false);
      }

      for (int i = 0; i < Math.min(SENT_ROWS, sent.size()); i++) {
         CompoundTag c = sent.getCompound(i);
         int ry = this.sentY + 14 + i * 12;
         String got = c.getString("Got");
         String status = got.isEmpty() ? L.t("в ящике") : L.t("вручено ") + got;
         int sw = this.font.width(status);
         g.drawString(this.font, status, x + w - sw, ry, got.isEmpty() ? -3555408 : -10628481, false);
         String left = c.getString("Sent") + " → " + c.getString("To") + " · " + L.t(c.getString("What"));
         g.drawString(this.font, Ui.ellipsize(this.font, left, w - sw - 8), x, ry, -1183755, false);
      }

      super.render(g, mx, my, pt);
   }
}
