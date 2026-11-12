package ru.realdocs.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;

/** Mailing lists (personal or an organization's) and the subscriber lists of your newspapers. */
public final class MailListScreen extends UiScreen {
   private int rows = 10;
   private final Screen parent;
   private final List<CompoundTag> entries = new ArrayList<>();
   private final List<CompoundTag> orgs = new ArrayList<>();
   private int selected = -1;
   private boolean creating;
   private int page;
   private String name = "";
   private String members = "";
   private int owner;
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int listW;

   public MailListScreen(Screen parent) {
      super(Component.literal(L.t("Списки рассылки")));
      this.parent = parent instanceof MailboxScreen ? null : parent;
      this.load();
   }

   private void load() {
      this.entries.clear();
      this.orgs.clear();
      ListTag l = ClientOffice.lists.getList("Lists", 10);

      for (int i = 0; i < l.size(); i++) {
         this.entries.add(l.getCompound(i));
      }

      ListTag o = ClientOffice.lists.getList("Orgs", 10);

      for (int i = 0; i < o.size(); i++) {
         this.orgs.add(o.getCompound(i));
      }
   }

   public void reload() {
      String keep = this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected).getString("Key") : null;
      String keepName = this.name;
      this.load();
      this.selected = -1;

      for (int i = 0; i < this.entries.size(); i++) {
         CompoundTag e = this.entries.get(i);
         if (e.getString("Key").equals(keep) || this.creating && e.getString("Name").equalsIgnoreCase(keepName.replace("@", "").trim())) {
            this.select(i);
            break;
         }
      }

      this.rebuildWidgets();
   }

   private void select(int i) {
      this.selected = i;
      this.creating = false;
      CompoundTag e = this.entries.get(i);
      this.name = e.getString("Name");
      this.members = e.getString("Members");
      this.owner = 0;
      String org = e.getString("Owner");

      for (int k = 0; k < this.orgs.size(); k++) {
         if (this.orgs.get(k).getString("Id").equals(org)) {
            this.owner = k + 1;
         }
      }
   }

   private CompoundTag current() {
      return this.selected >= 0 && this.selected < this.entries.size() ? this.entries.get(this.selected) : null;
   }

   private boolean subs() {
      CompoundTag c = this.current();
      return c != null && "subs".equals(c.getString("Kind"));
   }

   private String ownerLabel() {
      return this.owner == 0 ? L.t("Личный список") : L.t("Организация: ") + this.orgs.get(this.owner - 1).getString("Name");
   }

   protected void init() {
      this.w0 = Math.min(560, this.width - 30);
      this.h0 = Math.min(300, this.height - 20);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      this.rows = Math.max(3, (this.h0 - 28 - 22 - 22 - 40) / 20);
      this.listW = Math.min(190, this.w0 / 3);
      int lx = this.x0 + 10;
      int ly = this.y0 + 28;
      this.button(L.t("+ Новый список"), lx, ly, this.listW, 18, () -> {
         this.selected = -1;
         this.creating = true;
         this.name = "";
         this.members = "";
         this.owner = 0;
         this.rebuildWidgets();
      }).selected(this.creating);
      int pages = Math.max(1, (this.entries.size() + this.rows - 1) / this.rows);
      this.page = Math.min(this.page, pages - 1);

      for (int r = 0; r < this.rows; r++) {
         int i = this.page * this.rows + r;
         if (i >= this.entries.size()) {
            break;
         }

         CompoundTag e = this.entries.get(i);
         boolean sub = "subs".equals(e.getString("Kind"));
         String label = (sub ? "≡ " + e.getString("Name") : "@" + e.getString("Name")) + " (" + e.getInt("Count") + ")";
         int ii = i;
         this.button(label, lx, ly + 22 + r * 20, this.listW, 18, () -> {
            this.select(ii);
            this.rebuildWidgets();
         }).selected(i == this.selected).ghost();
      }

      if (pages > 1) {
         int py = ly + 22 + this.rows * 20;
         this.button("◀", lx, py, 20, 16, () -> {
            this.page = Math.max(0, this.page - 1);
            this.rebuildWidgets();
         }).enabled(this.page > 0);
         this.button("▶", lx + this.listW - 20, py, 20, 16, () -> {
            this.page = Math.min(pages - 1, this.page + 1);
            this.rebuildWidgets();
         }).enabled(this.page < pages - 1);
      }

      int fx = this.x0 + this.listW + 22;
      int fw = this.x0 + this.w0 - 12 - fx;
      int y = this.y0 + 40;
      if (this.creating || this.current() != null) {
         if (!this.subs()) {
            UiField nf = this.field(fx, y, fw, L.t("например, Подписчики, Отдел кадров"), this.name, 32, s -> this.name = s);
            nf.label(L.t("Название (пишется как @название)"));
            y += 30;
            this.button(this.ownerLabel(), fx, y, fw, 18, () -> {
               this.owner = (this.owner + 1) % (this.orgs.size() + 1);
               this.rebuildWidgets();
            }).enabled(!this.orgs.isEmpty());
            y += 34;
         } else {
            y += 30;
         }

         NameField mf = this.nameField(fx, y, fw, L.t("ники через запятую"), this.members, 4000, s -> this.members = s, true, false);
         mf.label(this.subs() ? L.t("Подписчики газеты") : L.t("Адресаты"));
         y += 30;
         this.button(L.t("Сохранить"), fx, y, 110, 20, this::save).primary();
         if (!this.creating) {
            this.button(L.t("Удалить"), fx + 116, y, 90, 20, () -> {
               CompoundTag t = new CompoundTag();
               t.putString("Key", this.current().getString("Key"));
               ClientOffice.req("list_delete", t);
            }).danger().enabled(!this.subs());
         }
      }

      this.button(L.t("Организации"), this.x0 + this.w0 - 184, this.y0 + this.h0 - 28, 100, 20, () -> {
         ClientOffice.req("orgs");
         this.minecraft.setScreen(new OrgScreen());
      });
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 78, this.y0 + this.h0 - 28, 66, 20, this::onClose).ghost();
   }

   private void save() {
      CompoundTag t = new CompoundTag();
      CompoundTag c = this.current();
      t.putString("Key", c == null ? "" : c.getString("Key"));
      t.putString("Kind", this.subs() ? "subs" : "list");
      t.putString("Name", this.name);
      t.putString("Members", this.members);
      t.putString("Owner", this.owner == 0 ? "" : this.orgs.get(this.owner - 1).getString("Id"));
      ClientOffice.req("list_save", t);
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Списки рассылки"));
      int fx = this.x0 + this.listW + 22;
      int fw = this.x0 + this.w0 - 12 - fx;
      if (this.entries.isEmpty() && !this.creating) {
         g.drawString(this.font, L.t("Списков пока нет."), this.x0 + 10, this.y0 + 52, -6253953, false);
      }

      if (this.subs()) {
         Ui.section(g, this.font, L.t("Газета «") + this.current().getString("Name") + "»", fx, this.y0 + 30, fw);
         g.drawString(this.font, Ui.ellipsize(this.font, L.t("Выпуск рассылается подписчикам из почтового ящика: «@subscribers»."), fw), fx, this.y0 + 46, -6253953, false);
      } else if (!this.creating && this.current() == null) {
         int y = this.y0 + 40;

         for (String line : new String[]{
            "Список рассылки — это несколько адресатов под одним именем.",
            "Напишите на конверте «@имя_списка», и письмо получит каждый.",
            "Списки организации видят и используют все её участники.",
            "Подписаться на газету: /seal paper subscribe <газета>."
         }) {
            g.drawString(this.font, Ui.ellipsize(this.font, L.t(line), fw), fx, y, -6253953, false);
            y += 12;
         }
      }

      List<String> mine = new ArrayList<>();
      ListTag ms = ClientOffice.lists.getList("MySubs", 8);

      for (int i = 0; i < ms.size(); i++) {
         mine.add(ms.getString(i));
      }

      String s = L.t("Мои подписки: ") + (mine.isEmpty() ? L.t("нет") : String.join(", ", mine));
      g.drawString(this.font, Ui.ellipsize(this.font, s, this.w0 - 210), this.x0 + 10, this.y0 + this.h0 - 22, -6253953, false);
      super.render(g, mx, my, pt);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
