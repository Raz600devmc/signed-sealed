package ru.realdocs.client.screen;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.PassportCache;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.StampCache;
import ru.realdocs.client.render.DocRenderer;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiButton;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;
import ru.realdocs.data.NbtCodec;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.model.DocModel;
import ru.realdocs.net.ArchivePackets;
import ru.realdocs.net.Net;
import ru.realdocs.server.Describe;
import ru.realdocs.server.DigitalService;

/**
 * The office computer: a small desktop operating system ("КанцОС") with a taskbar, a start menu
 * and windows for the digital archive, the document registry, the inbox and sending documents.
 */
public final class ComputerScreen extends UiScreen {
   enum App {
      ARCHIVE("Цифровой архив"),
      REGISTRY("Реестр документов"),
      INBOX("Входящие"),
      SEND("Отправить документ"),
      VIEWER("Просмотр"),
      ABOUT("О системе");

      final String title;

      App(String title) {
         this.title = title;
      }
   }

   private static final App[] DESKTOP = new App[]{App.ARCHIVE, App.REGISTRY, App.INBOX, App.SEND, App.ABOUT};
   private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
   private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
   private static final int BLACK = -16777216;
   private static final int GREY_TXT = -11184811;
   private static final int FACE = -2830136;
   private static final int SEL = -16112534;
   private static final String[] REG_STATE = new String[]{"Действует", "Утратил силу", "Срок истёк", "Аннулирован"};
   private static final int[] REG_COLOR = new int[]{-14375918, -2328063, -5701632, -4776932};
   private ArchivePackets.ComputerData data;
   private final List<App> open = new ArrayList<>();
   private App active;
   private boolean startOpen;
   private int sx0;
   private int sy0;
   private int sw;
   private int sh;
   private int mx0;
   private int my0;
   private int mw;
   private int mh;
   private int wx;
   private int wy;
   private int ww;
   private int wh;
   private int cx;
   private int cy;
   private int cw;
   private int ch;
   private String query;
   private int sel = -1;
   private int scroll;
   private long deleteArm;
   private int listRows;
   private String regQuery = "";
   private CompoundTag reg;
   private CompoundTag inbox = new CompoundTag();
   private int inSel = -1;
   private int inScroll;
   private String sendTo = "";
   private boolean sendCopy;
   private boolean sendSign;
   private String sendNote = "";
   private CompoundTag viewDoc;
   private int viewStatus = -1;
   private List<String> viewLines = List.of();
   private String viewFrom = "";
   private final PageView view = new PageView();
   private int viewKey;

   public ComputerScreen(ArchivePackets.ComputerData data) {
      super(Component.literal(L.t("Компьютер")));
      this.data = data;
      this.query = data.query;
      this.requestInbox();
   }

   public boolean isFor(ArchivePackets.ComputerData d) {
      return this.data.pos.equals(d.pos);
   }

   public void update(ArchivePackets.ComputerData d) {
      this.data = d;
      this.sel = d.entries.isEmpty() ? -1 : Math.min(Math.max(this.sel, 0), d.entries.size() - 1);
      this.scroll = 0;
      this.rebuildWidgets();
   }

   public void onInbox(CompoundTag t) {
      this.inbox = t;
      int n = this.inboxList().size();
      this.inSel = n == 0 ? -1 : Math.min(Math.max(this.inSel, 0), n - 1);
      this.inScroll = Math.min(this.inScroll, Math.max(0, n - 1));
      this.rebuildWidgets();
   }

   public void onRegistry(CompoundTag t) {
      this.reg = t;
      this.regQuery = t.getString("Reg").isEmpty() ? t.getString("Query") : t.getString("Reg");
      this.openApp(App.REGISTRY);
   }

   /** Shows a document the server sent for viewing inside the viewer window. */
   public void showDocument(CompoundTag doc, int status, List<String> lines, String from) {
      this.viewDoc = doc;
      this.viewStatus = status;
      this.viewLines = lines;
      this.viewFrom = from;
      this.viewKey = 0;
      this.openApp(App.VIEWER);
   }

   private CompoundTag pos(CompoundTag t) {
      t.putInt("X", this.data.pos.getX());
      t.putInt("Y", this.data.pos.getY());
      t.putInt("Z", this.data.pos.getZ());
      return t;
   }

   private void requestInbox() {
      ClientOffice.req("inbox", this.pos(new CompoundTag()));
   }

   private List<CompoundTag> inboxList() {
      List<CompoundTag> out = new ArrayList<>();
      ListTag l = this.inbox.getList("Entries", 10);

      for (int i = 0; i < l.size(); i++) {
         out.add(l.getCompound(i));
      }

      return out;
   }

   private int unread() {
      int n = 0;

      for (CompoundTag e : this.inboxList()) {
         if (!e.getBoolean("Read")) {
            n++;
         }
      }

      return n;
   }

   private void openApp(App a) {
      if (!this.open.contains(a)) {
         this.open.add(a);
      }

      this.active = a;
      this.startOpen = false;
      if (a == App.INBOX) {
         this.requestInbox();
      }

      Sounds.play(Sounds.CLICK, 1.5F, 0.35F);
      this.rebuildWidgets();
   }

   private void closeApp(App a) {
      this.open.remove(a);
      if (this.active == a) {
         this.active = this.open.isEmpty() ? null : this.open.get(this.open.size() - 1);
      }

      this.rebuildWidgets();
   }

   private void act(int action, String text) {
      Net.sendToServer(new ArchivePackets.ComputerAction(this.data.pos, action, text));
   }

   private void search() {
      Sounds.play(Sounds.CLICK, 1.4F, 0.4F);
      this.act(DigitalService.SEARCH, this.query);
   }

   private void findReg(String q) {
      CompoundTag t = this.pos(new CompoundTag());
      t.putString("Query", q);
      ClientOffice.req("reg_find", t);
   }

   private int iconPitch() {
      return Math.max(44, Math.min(58, (this.sh - 18 - 12) / DESKTOP.length));
   }

   private UiButton cbtn(String label, int x, int y, int w, int h, Runnable r) {
      return this.button(label, x, y, w, h, r).classic();
   }

   private UiField lfield(int x, int y, int w, String hint, String v, int max, java.util.function.Consumer<String> on) {
      UiField f = this.field(x, y, w, hint, v, max, on);
      f.light();
      return f;
   }

   protected void init() {
      this.mw = Math.min(this.width - 8, Math.max(320, this.width - 24));
      this.mh = Math.min(this.height - 6, Math.max(220, this.height - 14));
      this.mx0 = (this.width - this.mw) / 2;
      this.my0 = (this.height - this.mh) / 2;
      this.sx0 = this.mx0 + 10;
      this.sy0 = this.my0 + 9;
      this.sw = this.mw - 20;
      this.sh = this.mh - 9 - 20;
      int tbY = this.sy0 + this.sh - 18;
      this.cbtn("▣ " + L.t("Пуск"), this.sx0 + 2, tbY + 2, 50, 14, () -> {
         this.startOpen = !this.startOpen;
         this.rebuildWidgets();
      }).selected(this.startOpen);
      int tx = this.sx0 + 56;
      int tw = Math.min(110, (this.sw - 56 - 100) / Math.max(1, this.open.size()));

      for (App a : this.open) {
         this.cbtn(L.t(a.title), tx, tbY + 2, tw - 2, 14, () -> {
            this.active = this.active == a ? null : a;
            this.rebuildWidgets();
         }).selected(this.active == a);
         tx += tw;
      }

      if (this.startOpen) {
         int my = tbY - 6 - 16 * (DESKTOP.length + 1);

         for (App a : DESKTOP) {
            this.cbtn(L.t(a.title), this.sx0 + 24, my, 130, 15, () -> this.openApp(a));
            my += 16;
         }

         this.cbtn(L.t("Выключить"), this.sx0 + 24, my + 2, 130, 15, this::onClose);
      }

      int left = this.sx0 + 78;
      this.wx = left;
      this.wy = this.sy0 + 6;
      this.ww = this.sx0 + this.sw - 6 - left;
      this.wh = tbY - 6 - this.wy;
      this.cx = this.wx + 5;
      this.cy = this.wy + 20;
      this.cw = this.ww - 10;
      this.ch = this.wh - 25;
      if (this.active != null) {
         this.cbtn("", this.wx + this.ww - 17, this.wy + 4, 13, 11, () -> this.closeApp(this.active));
         this.cbtn("", this.wx + this.ww - 31, this.wy + 4, 13, 11, () -> {
            this.active = null;
            this.rebuildWidgets();
         });
         switch (this.active) {
            case ARCHIVE -> this.initArchive();
            case REGISTRY -> this.initRegistry();
            case INBOX -> this.initInbox();
            case SEND -> this.initSend();
            case VIEWER -> this.initViewer();
            default -> {
            }
         }
      }
   }

   // ---------------------------------------------------------------- archive

   private void initArchive() {
      UiField f = this.lfield(this.cx + 2, this.cy + 4, this.cw - 74, L.t("слова для поиска: название, фамилия, дата, номер…"), this.query, 80, s -> this.query = s);
      this.setFocused(f);
      this.cbtn(L.t("Найти"), this.cx + this.cw - 66, this.cy + 4, 64, 16, this::search);
      int listH = this.ch - 28 - 26;
      this.listRows = Math.max(1, (listH - 4) / 12);
      ArchivePackets.Entry e = this.sel >= 0 && this.sel < this.data.entries.size() ? this.data.entries.get(this.sel) : null;
      int by = this.cy + this.ch - 20;
      this.cbtn(L.t("Открыть"), this.cx + 2, by, 80, 16, () -> {
         if (e != null) {
            this.act(DigitalService.VIEW, e.id);
         }
      }).enabled(e != null);
      this.cbtn(L.t("Печать копии"), this.cx + 86, by, 96, 16, () -> {
         if (e != null) {
            this.act(DigitalService.PRINT, e.id);
         }
      }).enabled(e != null && this.data.canPrint && e.type == Describe.T_DOC);
      this.cbtn(L.t("Удалить"), this.cx + 186, by, 76, 16, () -> {
         if (e != null) {
            long now = System.currentTimeMillis();
            if (now - this.deleteArm > 3000L) {
               this.deleteArm = now;
               this.info(L.t("Запись удалится из архива навсегда. Нажмите ещё раз."));
            } else {
               this.act(DigitalService.DELETE, e.id);
            }
         }
      }).enabled(e != null && this.data.manage);
   }

   private String[] meta(ArchivePackets.Entry e) {
      String[] m = e.meta.split("\u0001", -1);
      String[] out = new String[6];

      for (int i = 0; i < 6; i++) {
         out[i] = i < m.length ? m[i] : "";
      }

      return out;
   }

   private int listW() {
      return this.cw * 56 / 100;
   }

   private void renderArchive(GuiGraphics g, int mx, int my) {
      int lx = this.cx + 2;
      int ly = this.cy + 26;
      int lw = this.listW();
      int lh = this.listRows * 12 + 4;
      sunken(g, lx, ly, lw, lh);
      List<ArchivePackets.Entry> es = this.data.entries;
      if (es.isEmpty()) {
         g.drawString(this.font, this.data.total == 0 ? L.t("Архив пуст: отсканируйте документы на сканере этой сети.") : L.t("Ничего не найдено."), lx + 4, ly + 4, GREY_TXT, false);
      }

      for (int r = 0; r < this.listRows && this.scroll + r < es.size(); r++) {
         int i = this.scroll + r;
         ArchivePackets.Entry e = es.get(i);
         int ry = ly + 2 + r * 12;
         boolean s = i == this.sel;
         if (s) {
            g.fill(lx + 2, ry, lx + lw - 2, ry + 12, SEL);
         } else if (mx >= lx && mx < lx + lw && my >= ry && my < ry + 12) {
            g.fill(lx + 2, ry, lx + lw - 2, ry + 12, -3355393);
         }

         String[] m = this.meta(e);
         typeIcon(g, lx + 4, ry + 2, e.type);
         String date = m[2];
         int dw = this.font.width(date);
         g.drawString(this.font, Ui.ellipsize(this.font, e.title, lw - 30 - dw), lx + 16, ry + 2, s ? -1 : BLACK, false);
         g.drawString(this.font, date, lx + lw - 6 - dw, ry + 2, s ? -1 : GREY_TXT, false);
      }

      String cnt = L.t("Записей: ") + this.data.total + (es.size() > this.listRows ? "  ·  " + (this.scroll + 1) + "–" + Math.min(es.size(), this.scroll + this.listRows) + "/" + es.size() : "");
      g.drawString(this.font, cnt, this.cx + this.cw - 4 - this.font.width(cnt), this.cy + this.ch - 16, GREY_TXT, false);
      int dx = lx + lw + 6;
      int dw = this.cx + this.cw - 2 - dx;
      sunken(g, dx, ly, dw, lh);
      int x = dx + 5;
      int w = dw - 10;
      int y = ly + 5;
      if (this.sel >= 0 && this.sel < es.size()) {
         ArchivePackets.Entry e = es.get(this.sel);
         String[] m = this.meta(e);

         for (String l : Ui.wrap(this.font, e.title, w)) {
            g.drawString(this.font, l, x, y, SEL, false);
            y += 10;
         }

         y += 4;
         int st = 0;

         try {
            st = Integer.parseInt(m[5]);
         } catch (NumberFormatException var14) {
         }

         y = this.kv(g, L.t("Тип"), L.t(Describe.TYPE_NAMES[Math.max(0, Math.min(4, e.type))]), x, y, w);
         y = this.kv(g, L.t("Номер в архиве"), e.id, x, y, w);
         y = this.kv(g, L.t("Автор / выдал"), m[0], x, y, w);
         y = this.kv(g, L.t("Владелец / адресат"), m[1], x, y, w);
         y = this.kv(g, L.t("Дата документа"), m[2], x, y, w);
         y = this.kv(g, L.t("Оцифровал"), m[4] + ", " + m[3], x, y, w);
         this.kv(g, L.t("Подлинность при сканировании"), L.t(DigitalService.STATUS[Math.max(0, Math.min(3, st))]), x, y, w);
      } else {
         g.drawString(this.font, L.t("Выберите запись в списке."), x, y, GREY_TXT, false);
      }
   }

   private int kv(GuiGraphics g, String k, String v, int x, int y, int w) {
      if (y > this.cy + this.ch - 34) {
         return y;
      } else {
         g.drawString(this.font, k, x, y, GREY_TXT, false);
         y += 10;

         for (String l : Ui.wrap(this.font, v == null || v.isEmpty() ? "—" : v, w)) {
            if (y > this.cy + this.ch - 34) {
               break;
            }

            g.drawString(this.font, l, x, y, BLACK, false);
            y += 10;
         }

         return y + 3;
      }
   }

   // ---------------------------------------------------------------- registry

   private void initRegistry() {
      UiField f = this.lfield(this.cx + 2, this.cy + 16, Math.min(200, this.cw - 80), "RD-000042", this.regQuery, 20, s -> this.regQuery = s);
      this.setFocused(f);
      this.cbtn(L.t("Найти"), this.cx + 6 + Math.min(200, this.cw - 80), this.cy + 16, 64, 16, () -> this.findReg(this.regQuery));
      if (this.reg != null) {
         int by = this.cy + this.ch - 20;
         int bx = this.cx + 2;
         if (this.reg.contains("Doc")) {
            this.cbtn(L.t("Открыть документ"), bx, by, 120, 16, () -> {
               CompoundTag d = this.reg.getCompound("Doc");
               this.showDocument(d, -1, List.of(L.t("Из реестра: ") + this.reg.getString("Reg")), L.t("реестр"));
            });
            bx += 124;
         }

         if (this.reg.contains("By")) {
            String by2 = this.reg.getString("By");
            this.cbtn(L.t("Перейти к ") + by2, bx, by, 120, 16, () -> this.findReg(by2));
         }
      }
   }

   private void renderRegistry(GuiGraphics g) {
      g.drawString(this.font, L.t("Регистрационный номер документа"), this.cx + 2, this.cy + 5, BLACK, false);
      int px = this.cx + 2;
      int py = this.cy + 38;
      int pw = this.cw - 4;
      int ph = this.ch - 38 - 26;
      sunken(g, px, py, pw, ph);
      int x = px + 6;
      int w = pw - 12;
      int y = py + 6;
      if (this.reg == null) {
         for (String l : Ui.wrap(this.font, L.t("Введите номер вида RD-000042, чтобы узнать, действует ли документ и чем он заменён. Подробности закрытых документов видят только автор, подписанты, владелец, сотрудники организации и те, у кого документ есть в цифровом архиве этой сети."), w)) {
            g.drawString(this.font, l, x, y, GREY_TXT, false);
            y += 10;
         }

         return;
      }

      int state = this.reg.getInt("State");
      if (state < 0) {
         g.drawString(this.font, L.t("Документ с таким номером в реестре не найден."), x, y, -4776932, false);
         return;
      }

      String badge = L.t(REG_STATE[Math.max(0, Math.min(3, state))]);
      int bw = this.font.width(badge) + 12;
      g.fill(x, y, x + bw, y + 13, REG_COLOR[Math.max(0, Math.min(3, state))]);
      g.drawString(this.font, badge, x + 6, y + 3, -1, false);
      g.drawString(this.font, this.reg.getString("Reg"), x + bw + 8, y + 3, BLACK, false);
      y += 20;
      if (this.reg.contains("By")) {
         g.drawString(this.font, Ui.ellipsize(this.font, L.t("Заменён документом ") + this.reg.getString("By") + (this.reg.getString("ByDate").isEmpty() ? "" : L.t(" от ") + this.reg.getString("ByDate")), w), x, y, -2328063, false);
         y += 13;
      }

      int col2 = x + Math.min(150, w / 3);
      y = this.row2(g, L.t("Тип"), this.reg.getString("Type"), x, col2, y, w);
      y = this.row2(g, L.t("Дата"), this.reg.getString("Date"), x, col2, y, w);
      y = this.row2(g, L.t("Организация"), this.reg.getString("Org"), x, col2, y, w);
      if (this.reg.contains("Expires")) {
         y = this.row2(g, L.t("Действует до"), this.reg.getString("Expires"), x, col2, y, w);
      }

      if (this.reg.getBoolean("See")) {
         y = this.row2(g, L.t("Номер"), this.reg.getString("Number"), x, col2, y, w);
         y = this.row2(g, L.t("Тема"), this.reg.getString("Subject"), x, col2, y, w);
         y = this.row2(g, L.t("Автор"), this.reg.getString("Author"), x, col2, y, w);
         y = this.row2(g, L.t("Подписи"), this.reg.getString("Signers"), x, col2, y, w);
         if (!this.reg.getString("Holder").isEmpty()) {
            y = this.row2(g, L.t("Выдан"), this.reg.getString("Holder"), x, col2, y, w);
         }

         if (!this.reg.getString("Zones").isEmpty()) {
            y = this.row2(g, L.t("Зоны"), this.reg.getString("Zones"), x, col2, y, w);
         }

         if (!this.reg.getString("Refs").isEmpty()) {
            y = this.row2(g, L.t("Ссылается на"), this.reg.getString("Refs"), x, col2, y, w);
         }

         if (!this.reg.getString("Supersedes").isEmpty()) {
            this.row2(g, L.t("Отменяет"), this.reg.getString("Supersedes"), x, col2, y, w);
         }
      } else if (this.reg.contains("Type")) {
         y += 4;

         for (String l : Ui.wrap(this.font, L.t("Документ закрытый: содержание видят только автор, подписанты, владелец и сотрудники организации."), w)) {
            g.drawString(this.font, l, x, y, GREY_TXT, false);
            y += 10;
         }
      }
   }

   private int row2(GuiGraphics g, String k, String v, int x, int x2, int y, int w) {
      if (y > this.cy + this.ch - 40) {
         return y;
      } else {
         g.drawString(this.font, k, x, y, GREY_TXT, false);
         int yy = y;

         for (String l : Ui.wrap(this.font, v == null || v.isEmpty() ? "—" : v, x + w - x2)) {
            g.drawString(this.font, l, x2, yy, BLACK, false);
            yy += 10;
         }

         return Math.max(y + 12, yy + 2);
      }
   }

   // ---------------------------------------------------------------- inbox

   private void inboxSend(String action) {
      List<CompoundTag> l = this.inboxList();
      if (this.inSel >= 0 && this.inSel < l.size()) {
         CompoundTag t = this.pos(new CompoundTag());
         t.putString("Id", l.get(this.inSel).getString("Id"));
         if (action.equals("inbox_view") && !"sign".equals(l.get(this.inSel).getString("Kind"))) {
            l.get(this.inSel).putBoolean("Read", true);
         }

         ClientOffice.req(action, t);
      }
   }

   private void initInbox() {
      int listH = this.ch - 28 - 26 - 60;
      this.listRows = Math.max(2, (listH - 4) / 13);
      List<CompoundTag> l = this.inboxList();
      CompoundTag e = this.inSel >= 0 && this.inSel < l.size() ? l.get(this.inSel) : null;
      this.cbtn(L.t("Обновить"), this.cx + 2, this.cy + 4, 80, 16, this::requestInbox);
      this.cbtn(L.t("Написать…"), this.cx + 86, this.cy + 4, 90, 16, () -> this.openApp(App.SEND));
      int by = this.cy + this.ch - 20;
      boolean sign = e != null && "sign".equals(e.getString("Kind"));
      this.cbtn(sign ? L.t("Открыть и подписать") : L.t("Открыть"), this.cx + 2, by, sign ? 130 : 80, 16, () -> this.inboxSend("inbox_view")).enabled(e != null);
      int bx = this.cx + (sign ? 136 : 86);
      if (!sign) {
         this.cbtn(L.t("Забрать"), bx, by, 76, 16, () -> this.inboxSend("inbox_take")).enabled(e != null);
         bx += 80;
         this.cbtn(L.t("Вернуть"), bx, by, 76, 16, () -> this.inboxSend("inbox_return")).enabled(e != null && e.getBoolean("Original"));
         bx += 80;
      }

      this.cbtn(sign ? L.t("Отказаться") : L.t("Удалить"), bx, by, 80, 16, () -> {
         if (e != null && e.getBoolean("Original") && !sign) {
            long now = System.currentTimeMillis();
            if (now - this.deleteArm > 3000L) {
               this.deleteArm = now;
               this.info(L.t("Это оригинал: он пропадёт навсегда. Нажмите ещё раз."));
               return;
            }
         }

         this.inboxSend("inbox_delete");
      }).enabled(e != null);
   }

   private void renderInbox(GuiGraphics g, int mx, int my) {
      List<CompoundTag> l = this.inboxList();
      int unread = this.unread();
      String head = L.t("Писем: ") + l.size() + (unread > 0 ? " · " + L.t("новых: ") + unread : "");
      g.drawString(this.font, head, this.cx + this.cw - 4 - this.font.width(head), this.cy + 8, GREY_TXT, false);
      int lx = this.cx + 2;
      int ly = this.cy + 26;
      int lw = this.cw - 4;
      int lh = this.listRows * 13 + 4;
      sunken(g, lx, ly, lw, lh);
      if (l.isEmpty()) {
         g.drawString(this.font, L.t("Входящих нет."), lx + 4, ly + 4, GREY_TXT, false);
      }

      for (int r = 0; r < this.listRows && this.inScroll + r < l.size(); r++) {
         int i = this.inScroll + r;
         CompoundTag e = l.get(i);
         int ry = ly + 2 + r * 13;
         boolean s = i == this.inSel;
         if (s) {
            g.fill(lx + 2, ry, lx + lw - 2, ry + 13, SEL);
         } else if (mx >= lx && mx < lx + lw && my >= ry && my < ry + 13) {
            g.fill(lx + 2, ry, lx + lw - 2, ry + 13, -3355393);
         }

         int col = s ? -1 : BLACK;
         boolean bold = !e.getBoolean("Read");
         boolean sign = "sign".equals(e.getString("Kind"));
         envelope(g, lx + 5, ry + 3, sign ? -2328063 : (bold ? -12566464 : -6250336));
         String time = e.getString("Time");
         int tw2 = this.font.width(time);
         String from = e.getString("From") + (e.getString("Org").isEmpty() ? "" : " → @" + e.getString("Org"));
         int fw = Math.min(140, lw / 3);
         String fromS = Ui.ellipsize(this.font, from, fw - 4);
         g.drawString(this.font, fromS, lx + 20, ry + 3, col, false);
         if (bold) {
            g.drawString(this.font, fromS, lx + 21, ry + 3, col, false);
         }

         String title = (sign ? L.t("[на подпись] ") : (e.getBoolean("Original") ? "" : L.t("[копия] "))) + L.t(e.getString("Title"));
         g.drawString(this.font, Ui.ellipsize(this.font, title, lw - fw - 30 - tw2), lx + 20 + fw, ry + 3, col, false);
         g.drawString(this.font, time, lx + lw - 6 - tw2, ry + 3, s ? -1 : GREY_TXT, false);
      }

      int dy = ly + lh + 4;
      CompoundTag e = this.inSel >= 0 && this.inSel < l.size() ? l.get(this.inSel) : null;
      if (e != null) {
         String kind = "sign".equals(e.getString("Kind"))
            ? L.t("Запрос подписи")
            : (e.getBoolean("Original") ? L.t("Оригинал — у вас единственный экземпляр") : L.t("Заверенная копия"));
         g.drawString(this.font, Ui.ellipsize(this.font, L.t("От: ") + e.getString("From") + " · " + e.getString("Time") + " · " + kind, this.cw - 8), this.cx + 4, dy, BLACK, false);
         dy += 11;
         if (!e.getString("Note").isEmpty()) {
            for (String s : Ui.wrap(this.font, L.t("Сообщение: ") + L.t(e.getString("Note")), this.cw - 8)) {
               if (dy > this.cy + this.ch - 32) {
                  break;
               }

               g.drawString(this.font, s, this.cx + 4, dy, GREY_TXT, false);
               dy += 10;
            }
         }
      }
   }

   // ---------------------------------------------------------------- send

   private void initSend() {
      int x = this.cx + 8;
      int w = Math.min(360, this.cw - 16);
      int y = this.cy + 52;
      NameField f = this.nameField(x, y, w, L.t("ник или @организация; копии — через запятую"), this.sendTo, 300, s -> this.sendTo = s, true, false);
      f.light();
      f.label(L.t("Кому"));
      y += 24;
      int half = (w - 4) / 2;
      this.cbtn((this.sendCopy ? "○ " : "● ") + L.t("Оригинал"), x, y, half, 16, () -> {
         this.sendCopy = false;
         this.rebuildWidgets();
      }).selected(!this.sendCopy);
      this.cbtn((this.sendCopy ? "● " : "○ ") + L.t("Копия"), x + half + 4, y, w - half - 4, 16, () -> {
         this.sendCopy = true;
         this.sendSign = false;
         this.rebuildWidgets();
      }).selected(this.sendCopy);
      y += 20;
      this.cbtn((this.sendSign ? "☑ " : "☐ ") + L.t("Запросить подпись (оригинал остаётся в канцелярии)"), x, y, w, 16, () -> {
         this.sendSign = !this.sendSign;
         if (this.sendSign) {
            this.sendCopy = false;
         }

         this.rebuildWidgets();
      }).selected(this.sendSign);
      y += 32;
      UiField nf = this.lfield(x, y, w, L.t("необязательно"), this.sendNote, 120, s -> this.sendNote = s);
      nf.label(L.t("Сообщение"));
      y += 26;
      this.cbtn(L.t("Отправить"), x, y, 110, 18, () -> {
         CompoundTag t = this.pos(new CompoundTag());
         t.putInt("Hand", 0);
         t.putString("To", this.sendTo);
         t.putBoolean("Copy", this.sendCopy);
         t.putBoolean("Sign", this.sendSign);
         t.putString("Note", this.sendNote);
         ClientOffice.req("inbox_send", t);
         Sounds.play(Sounds.CLICK, 1.2F, 0.4F);
      }).enabled(!Minecraft.getInstance().player.getMainHandItem().isEmpty());
   }

   private void renderSend(GuiGraphics g) {
      int x = this.cx + 8;
      int w = Math.min(360, this.cw - 16);
      ItemStack held = Minecraft.getInstance().player.getMainHandItem();
      g.drawString(this.font, L.t("Отправляется предмет из основной руки:"), x, this.cy + 6, BLACK, false);
      sunken(g, x, this.cy + 18, w, 22);
      if (held.isEmpty()) {
         g.drawString(this.font, L.t("Рука пуста — возьмите документ."), x + 6, this.cy + 25, GREY_TXT, false);
      } else {
         g.renderItem(held, x + 3, this.cy + 21);
         g.drawString(this.font, Ui.ellipsize(this.font, held.getHoverName().getString(), w - 30), x + 24, this.cy + 25, BLACK, false);
      }

      int y = this.cy + 52 + 24 + 20 + 32 + 26 + 26;
      String[] help = new String[]{
         "Оригинал уходит из ваших рук к одному получателю.",
         "Копия заверяется и стоит 1 бумагу на каждого получателя.",
         "@Организация — документ попадёт во входящие всех её сотрудников."
      };

      for (String h : help) {
         for (String l : Ui.wrap(this.font, L.t(h), this.cw - 16)) {
            if (y > this.cy + this.ch - 10) {
               return;
            }

            g.drawString(this.font, l, x, y, GREY_TXT, false);
            y += 10;
         }
      }
   }

   // ---------------------------------------------------------------- viewer

   private void initViewer() {
      int side = Math.min(170, this.cw / 3);
      this.view.layout(this.cx + 2, this.cy + 2, this.cw - side - 8, this.ch - 4, 1.414516129032258);
      this.viewKey = 0;
   }

   private void renderViewer(GuiGraphics g) {
      if (this.viewDoc != null) {
         DocModel d = NbtCodec.readDoc(this.viewDoc);
         int key = ((this.view.w * 31 + this.view.h) * 31 + StampCache.version) * 31 + PassportCache.version + this.viewDoc.hashCode();
         if (key != this.viewKey || !this.view.tex.ready()) {
            this.viewKey = key;
            this.view.tex.set(DocRenderer.render(d, StampCache.INSTANCE, Math.max(200, Math.min(1240, this.view.w * 2))).image());
         }

         this.view.draw(g);
         int side = Math.min(170, this.cw / 3);
         int x = this.cx + this.cw - side;
         int y = this.cy + 8;
         sunken(g, x - 2, this.cy + 2, side, this.ch - 4);
         x += 4;
         int w = side - 12;
         if (this.viewStatus >= 0) {
            String[] st = new String[]{"Не документ", "Не выпущен", "Подлинный", "Подделка", "Нет в реестре", "Недействителен", "Устаревшая версия", "Заверенная копия"};
            int idx = Math.max(0, Math.min(st.length - 1, this.viewStatus));
            boolean good = idx == 2 || idx == 7;
            g.drawString(this.font, Ui.ellipsize(this.font, L.t("Проверка: ") + L.t(st[idx]), w), x, y, good ? -14375918 : -4776932, false);
            y += 14;
         }

         for (String line : this.viewLines) {
            for (String l : Ui.wrap(this.font, L.t(line), w)) {
               if (y > this.cy + this.ch - 24) {
                  break;
               }

               g.drawString(this.font, l, x, y, BLACK, false);
               y += 10;
            }

            y += 2;
         }

         g.drawString(this.font, Ui.ellipsize(this.font, L.t("Источник: ") + L.t(this.viewFrom), w), x, this.cy + this.ch - 14, GREY_TXT, false);
      }
   }

   // ---------------------------------------------------------------- about

   private void renderAbout(GuiGraphics g) {
      int x = this.cx + 12;
      int y = this.cy + 12;
      g.fill(x, y, x + 40, y + 30, -12566464);
      g.fill(x + 3, y + 3, x + 37, y + 24, -15641470);
      g.fill(x + 14, y + 30, x + 26, y + 34, -6250336);
      g.drawString(this.font, "КанцОС 98", x + 52, y + 2, BLACK, false);
      g.drawString(this.font, L.t("Операционная система для канцелярий"), x + 52, y + 14, GREY_TXT, false);
      y += 50;
      y = this.row2(g, L.t("Сеть архива"), this.data.network, x, x + 120, y, this.cw - 24);
      y = this.row2(g, L.t("Записей в архиве"), Integer.toString(this.data.total), x, x + 120, y, this.cw - 24);
      y = this.row2(g, L.t("Пользователь"), Minecraft.getInstance().player.getGameProfile().getName(), x, x + 120, y, this.cw - 24);
      this.row2(g, L.t("Дата"), LocalDateTime.now().format(DAY), x, x + 120, y, this.cw - 24);
   }

   // ---------------------------------------------------------------- drawing

   private static void sunken(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -1);
      g.fill(x, y, x + w, y + 1, -8355712);
      g.fill(x, y, x + 1, y + h, -8355712);
      g.fill(x + 1, y + 1, x + w - 1, y + 2, -12566464);
      g.fill(x + 1, y + 1, x + 2, y + h - 1, -12566464);
   }

   private static void raised(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, FACE);
      g.fill(x, y, x + w, y + 1, -1);
      g.fill(x, y, x + 1, y + h, -1);
      g.fill(x, y + h - 1, x + w, y + h, -14671840);
      g.fill(x + w - 1, y, x + w, y + h, -14671840);
      g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, -8355712);
      g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, -8355712);
   }

   private static void envelope(GuiGraphics g, int x, int y, int color) {
      g.fill(x, y, x + 11, y + 8, -1);
      g.fill(x, y, x + 11, y + 1, color);
      g.fill(x, y + 7, x + 11, y + 8, color);
      g.fill(x, y, x + 1, y + 8, color);
      g.fill(x + 10, y, x + 11, y + 8, color);

      for (int i = 0; i < 5; i++) {
         g.fill(x + 1 + i, y + 1 + i / 2 + i % 2, x + 2 + i, y + 2 + i / 2 + i % 2, color);
         g.fill(x + 9 - i, y + 1 + i / 2 + i % 2, x + 10 - i, y + 2 + i / 2 + i % 2, color);
      }
   }

   private static void typeIcon(GuiGraphics g, int x, int y, int type) {
      int c = switch (type) {
         case 1 -> -14336;
         case 2 -> -6250336;
         case 3 -> -2328063;
         case 4 -> -3628956;
         default -> -16112534;
      };
      g.fill(x, y, x + 7, y + 9, c);
      g.fill(x + 1, y + 1, x + 6, y + 8, -1);
      g.fill(x + 2, y + 3, x + 5, y + 4, c);
      g.fill(x + 2, y + 5, x + 5, y + 6, c);
   }

   private void desktopIcon(GuiGraphics g, App a, int x, int y, boolean hover) {
      int ix = x + 18;
      int iy = y + 2;
      if (hover) {
         g.fill(x + 2, y, x + 62, y + 44, 0x40FFFFFF);
      }

      switch (a) {
         case ARCHIVE -> {
            g.fill(ix, iy, ix + 28, iy + 28, -14671840);
            g.fill(ix + 1, iy + 1, ix + 27, iy + 27, -4152150);

            for (int i = 0; i < 3; i++) {
               g.fill(ix + 3, iy + 3 + i * 8, ix + 25, iy + 9 + i * 8, -5927312);
               g.fill(ix + 11, iy + 5 + i * 8, ix + 17, iy + 7 + i * 8, -9419200);
            }
         }
         case REGISTRY -> {
            g.fill(ix + 2, iy, ix + 22, iy + 27, -12566464);
            g.fill(ix + 3, iy + 1, ix + 21, iy + 26, -1);
            g.drawString(this.font, "RD", ix + 6, iy + 5, -16112534, false);

            for (int i = 0; i < 3; i++) {
               g.fill(ix + 5, iy + 16 + i * 3, ix + 19, iy + 17 + i * 3, -6250336);
            }

            g.fill(ix + 16, iy + 14, ix + 26, iy + 24, -12566464);
            g.fill(ix + 18, iy + 16, ix + 24, iy + 22, -6496257);
            g.fill(ix + 24, iy + 24, ix + 28, iy + 28, -12566464);
         }
         case INBOX -> {
            g.fill(ix, iy + 6, ix + 28, iy + 26, -12566464);
            g.fill(ix + 1, iy + 7, ix + 27, iy + 25, -1);

            for (int i = 0; i < 13; i++) {
               g.fill(ix + 1 + i, iy + 7 + i * 9 / 13, ix + 2 + i, iy + 8 + i * 9 / 13, -12566464);
               g.fill(ix + 26 - i, iy + 7 + i * 9 / 13, ix + 27 - i, iy + 8 + i * 9 / 13, -12566464);
            }

            int n = this.unread();
            if (n > 0) {
               String s = n > 99 ? "99" : Integer.toString(n);
               int bw = Math.max(10, this.font.width(s) + 4);
               g.fill(ix + 22, iy, ix + 22 + bw, iy + 10, -4776932);
               g.drawString(this.font, s, ix + 22 + (bw - this.font.width(s)) / 2, iy + 1, -1, false);
            }
         }
         case SEND -> {
            g.fill(ix, iy + 14, ix + 28, iy + 27, -9419200);
            g.fill(ix + 1, iy + 15, ix + 27, iy + 26, -5927312);
            g.fill(ix + 6, iy + 4, ix + 22, iy + 20, -1);
            Ui.outline(g, ix + 6, iy + 4, 16, 16, -12566464);
            g.fill(ix + 11, iy, ix + 17, iy + 12, -14375918);
            g.fill(ix + 8, iy + 2, ix + 20, iy + 4, -14375918);
         }
         default -> {
            g.fill(ix, iy, ix + 28, iy + 20, -12566464);
            g.fill(ix + 2, iy + 2, ix + 26, iy + 17, -15641470);
            g.fill(ix + 10, iy + 20, ix + 18, iy + 24, -6250336);
            g.fill(ix + 5, iy + 24, ix + 23, iy + 26, -6250336);
         }
      }

      String label = L.t(a.title);
      List<String> lines = Ui.wrap(this.font, label, 62);
      int ly = y + 32;

      for (int i = 0; i < Math.min(2, lines.size()); i++) {
         String s = lines.get(i);
         int lw = this.font.width(s);
         g.drawString(this.font, s, x + 32 - lw / 2, ly, -1, true);
         ly += 10;
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      g.fill(this.mx0, this.my0, this.mx0 + this.mw, this.my0 + this.mh, -2501163);
      g.fill(this.mx0, this.my0, this.mx0 + this.mw, this.my0 + 1, -1);
      g.fill(this.mx0, this.my0, this.mx0 + 1, this.my0 + this.mh, -1);
      g.fill(this.mx0, this.my0 + this.mh - 1, this.mx0 + this.mw, this.my0 + this.mh, -8355712);
      g.fill(this.mx0 + this.mw - 1, this.my0, this.mx0 + this.mw, this.my0 + this.mh, -8355712);
      g.fill(this.sx0 - 2, this.sy0 - 2, this.sx0 + this.sw + 2, this.sy0 + this.sh + 2, -14671840);
      g.fillGradient(this.sx0, this.sy0, this.sx0 + this.sw, this.sy0 + this.sh, -16748395, -16763046);
      String brand = "КанцОС";
      g.pose().pushPose();
      g.pose().translate((float)(this.sx0 + this.sw / 2), (float)(this.sy0 + this.sh / 2 - 12), 0.0F);
      g.pose().scale(3.0F, 3.0F, 1.0F);
      g.drawString(this.font, brand, -this.font.width(brand) / 2, 0, 0x30FFFFFF, false);
      g.pose().popPose();
      g.drawString(this.font, "КАНЦЕЛЯРИЯ-98", this.mx0 + 12, this.my0 + this.mh - 14, -9408400, false);
      g.fill(this.mx0 + this.mw - 18, this.my0 + this.mh - 12, this.mx0 + this.mw - 12, this.my0 + this.mh - 9, -14375918);

      for (int i = 0; i < DESKTOP.length; i++) {
         int ix = this.sx0 + 6;
         int iy = this.sy0 + 6 + i * this.iconPitch();
         boolean hov = mx >= ix + 2 && mx < ix + 62 && my >= iy && my < iy + 44 && !this.startOpen;
         this.desktopIcon(g, DESKTOP[i], ix, iy, hov);
      }

      int tbY = this.sy0 + this.sh - 18;
      raised(g, this.sx0, tbY, this.sw, 18);
      String clock = LocalDateTime.now().format(CLOCK);
      String tray = this.data.network + "  " + clock;
      int trw = this.font.width(tray) + 10;
      int trx = this.sx0 + this.sw - trw - 3;
      g.fill(trx, tbY + 3, trx + trw, tbY + 15, -3092272);
      g.fill(trx, tbY + 3, trx + trw, tbY + 4, -8355712);
      g.fill(trx, tbY + 3, trx + 1, tbY + 15, -8355712);
      g.drawString(this.font, tray, trx + 5, tbY + 6, BLACK, false);
      g.flush();
      if (this.active != null) {
         raised(g, this.wx, this.wy, this.ww, this.wh);

         for (int i = 0; i < this.ww - 6; i++) {
            float k = (float)i / (float)Math.max(1, this.ww - 6);
            g.fill(this.wx + 3 + i, this.wy + 3, this.wx + 4 + i, this.wy + 17, Ui.mix(-16112534, -5846288, k));
         }

         g.drawString(this.font, Ui.ellipsize(this.font, L.t(this.active.title), this.ww - 50), this.wx + 8, this.wy + 6, -1, false);
         switch (this.active) {
            case ARCHIVE -> this.renderArchive(g, mx, my);
            case REGISTRY -> this.renderRegistry(g);
            case INBOX -> this.renderInbox(g, mx, my);
            case SEND -> this.renderSend(g);
            case VIEWER -> this.renderViewer(g);
            case ABOUT -> this.renderAbout(g);
         }
      }

      g.flush();
      if (this.startOpen) {
         // desktop labels were drawn with text depth; clear it so the menu really lies on top
         com.mojang.blaze3d.systems.RenderSystem.clear(256, net.minecraft.client.Minecraft.ON_OSX);
         int n = DESKTOP.length + 1;
         int h = 16 * n + 10;
         int y0 = tbY - h;
         raised(g, this.sx0 + 2, y0, 156, h);
         g.fillGradient(this.sx0 + 5, y0 + 3, this.sx0 + 21, y0 + h - 3, -16112534, -14336);
         g.pose().pushPose();
         g.pose().translate((float)(this.sx0 + 9), (float)(y0 + h - 6), 0.0F);
         g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90.0F));
         g.drawString(this.font, "КанцОС", 0, 0, -1, false);
         g.pose().popPose();
      }

      super.render(g, mx, my, pt);
      if (this.active != null) {
         int bx = this.wx + this.ww - 17;
         int by = this.wy + 4;
         for (int i = 0; i < 7; i++) {
            g.fill(bx + 3 + i, by + 2 + i, bx + 5 + i, by + 3 + i, BLACK);
            g.fill(bx + 9 - i, by + 2 + i, bx + 11 - i, by + 3 + i, BLACK);
         }

         g.fill(bx - 14 + 3, by + 7, bx - 14 + 9, by + 9, BLACK);
      }

      this.renderToast(g);
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (super.mouseClicked(mx, my, button)) {
         return true;
      } else {
         if (this.startOpen) {
            this.startOpen = false;
            this.rebuildWidgets();
            return true;
         }

         for (int i = 0; i < DESKTOP.length; i++) {
            int ix = this.sx0 + 6;
            int iy = this.sy0 + 6 + i * this.iconPitch();
            if (mx >= ix + 2 && mx < ix + 62 && my >= iy && my < iy + 44) {
               this.openApp(DESKTOP[i]);
               return true;
            }
         }

         if (this.active == App.ARCHIVE) {
            int lx = this.cx + 2;
            int ly = this.cy + 28;
            if (mx >= lx && mx < lx + this.listW() && my >= ly && my < ly + this.listRows * 12) {
               int i = this.scroll + (int)((my - ly) / 12.0);
               if (i >= 0 && i < this.data.entries.size()) {
                  this.sel = i;
                  Sounds.play(Sounds.CLICK, 1.6F, 0.3F);
                  this.rebuildWidgets();
                  return true;
               }
            }
         } else if (this.active == App.INBOX) {
            int lx = this.cx + 2;
            int ly = this.cy + 28;
            if (mx >= lx && mx < lx + this.cw - 4 && my >= ly && my < ly + this.listRows * 13) {
               int i = this.inScroll + (int)((my - ly) / 13.0);
               if (i >= 0 && i < this.inboxList().size()) {
                  this.inSel = i;
                  Sounds.play(Sounds.CLICK, 1.6F, 0.3F);
                  this.rebuildWidgets();
                  return true;
               }
            }
         }

         return false;
      }
   }

   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      int d = -(int)Math.signum(sy);
      if (this.active == App.ARCHIVE) {
         this.scroll = Math.max(0, Math.min(Math.max(0, this.data.entries.size() - this.listRows), this.scroll + d));
      } else if (this.active == App.INBOX) {
         this.inScroll = Math.max(0, Math.min(Math.max(0, this.inboxList().size() - this.listRows), this.inScroll + d));
      }

      return true;
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 256) {
         if (this.startOpen) {
            this.startOpen = false;
            this.rebuildWidgets();
            return true;
         } else if (this.active != null) {
            this.active = null;
            this.rebuildWidgets();
            return true;
         } else {
            return super.keyPressed(key, scan, mods);
         }
      } else if ((key == 257 || key == 335) && this.active == App.ARCHIVE) {
         this.search();
         return true;
      } else if ((key == 257 || key == 335) && this.active == App.REGISTRY) {
         this.findReg(this.regQuery);
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   public void removed() {
      this.view.close();
      super.removed();
   }

   static boolean isDoc(ItemStack s) {
      return s.getItem() instanceof DocumentItem;
   }
}
