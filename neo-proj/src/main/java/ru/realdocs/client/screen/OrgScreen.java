package ru.realdocs.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;
import ru.realdocs.client.ClientOffice;
import ru.realdocs.client.ui.NameField;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.client.ui.UiField;
import ru.realdocs.client.ui.UiScreen;

/** Organizations you belong to: owner, managers and members. */
public final class OrgScreen extends UiScreen {
   private static final String[] ROLES = new String[]{"владелец", "руководитель", "участник", "администратор"};
   private int rows = 10;
   private final List<CompoundTag> orgs = new ArrayList<>();
   private int selected = -1;
   private boolean creating;
   private int page;
   private String name = "";
   private String newOwner = "";
   private String managers = "";
   private String members = "";
   private int x0;
   private int y0;
   private int w0;
   private int h0;
   private int listW;
   private int formBottom;

   public OrgScreen() {
      super(Component.literal(L.t("Организации")));
      this.load();
      String sel = ClientOffice.orgData.getString("Select");

      for (int i = 0; i < this.orgs.size(); i++) {
         if (this.orgs.get(i).getString("Id").equals(sel) || sel.isEmpty() && this.selected < 0 && i == 0) {
            this.select(i);
         }
      }
   }

   private void load() {
      this.orgs.clear();
      ListTag l = ClientOffice.orgData.getList("Orgs", 10);

      for (int i = 0; i < l.size(); i++) {
         this.orgs.add(l.getCompound(i));
      }
   }

   public void reload() {
      String keep = this.current() == null ? "" : this.current().getString("Id");
      String sel = ClientOffice.orgData.getString("Select");
      this.load();
      this.selected = -1;
      this.creating = false;

      for (int i = 0; i < this.orgs.size(); i++) {
         String id = this.orgs.get(i).getString("Id");
         if (id.equals(sel.isEmpty() ? keep : sel)) {
            this.select(i);
         }
      }

      this.rebuildWidgets();
   }

   private void select(int i) {
      this.selected = i;
      this.creating = false;
      CompoundTag o = this.orgs.get(i);
      this.name = o.getString("Name");
      this.newOwner = o.getString("Owner");
      this.managers = o.getString("Managers");
      this.members = o.getString("Members");
   }

   private CompoundTag current() {
      return this.selected >= 0 && this.selected < this.orgs.size() ? this.orgs.get(this.selected) : null;
   }

   private int role() {
      CompoundTag c = this.current();
      if (c == null) {
         return 3;
      } else {
         int r = c.getInt("Role");
         return r == 3 && ClientOffice.orgData.getBoolean("Admin") ? 0 : r;
      }
   }

   protected void init() {
      this.w0 = Math.min(580, this.width - 30);
      this.h0 = Math.min(310, this.height - 20);
      this.x0 = (this.width - this.w0) / 2;
      this.y0 = (this.height - this.h0) / 2;
      this.rows = Math.max(3, (this.h0 - 28 - 22 - 22 - 40) / 20);
      this.listW = Math.min(180, this.w0 / 3);
      int lx = this.x0 + 10;
      int ly = this.y0 + 28;
      this.button(L.t("+ Создать организацию"), lx, ly, this.listW, 18, () -> {
         this.selected = -1;
         this.creating = true;
         this.name = "";
         this.rebuildWidgets();
      }).selected(this.creating).enabled(ClientOffice.orgData.getBoolean("CanCreate"));
      int pages = Math.max(1, (this.orgs.size() + this.rows - 1) / this.rows);
      this.page = Math.min(this.page, pages - 1);

      for (int r = 0; r < this.rows; r++) {
         int i = this.page * this.rows + r;
         if (i >= this.orgs.size()) {
            break;
         }

         CompoundTag o = this.orgs.get(i);
         int ii = i;
         this.button(o.getString("Name") + " (" + o.getInt("Count") + ")", lx, ly + 22 + r * 20, this.listW, 18, () -> {
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
      if (this.creating) {
         UiField nf = this.field(fx, y, fw, L.t("например, Мэрия, ООО «Кубострой»"), this.name, 40, s -> this.name = s);
         nf.label(L.t("Название организации"));
         this.button(L.t("Создать"), fx, y + 26, 110, 20, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("Name", this.name);
            ClientOffice.req("org_create", t);
         }).primary();
      } else if (this.current() != null) {
         int role = this.role();
         boolean head = role == 0;
         boolean manage = role <= 1;
         y += 12;
         UiField nf = this.field(fx, y, fw, "", this.name, 40, s -> this.name = s);
         nf.label(L.t("Название"));
         nf.setEditable(head);
         y += 30;
         if (head) {
            NameField of = this.nameField(fx, y, fw, L.t("ник нового владельца"), this.newOwner, 16, s -> this.newOwner = s, false, false);
            of.label(L.t("Владелец (можно передать)"));
            y += 30;
         }

         NameField mg = this.nameField(fx, y, fw, L.t("ники через запятую"), this.managers, 4000, s -> this.managers = s, true, false);
         mg.label(L.t("Руководители: управляют списками, шаблонами и блоками"));
         mg.setEditable(head);
         y += 30;
         NameField mb = this.nameField(fx, y, fw, L.t("ники через запятую"), this.members, 4000, s -> this.members = s, true, false);
         mb.label(L.t("Участники"));
         mb.setEditable(manage);
         y += 28;
         this.button(L.t("Сохранить"), fx, y, 100, 20, () -> {
            CompoundTag t = new CompoundTag();
            t.putString("Id", this.current().getString("Id"));
            t.putString("Name", this.name);
            t.putString("NewOwner", this.newOwner);
            t.putString("Managers", this.managers);
            t.putString("Members", this.members);
            ClientOffice.req("org_save", t);
         }).primary().enabled(manage);
         if (role == 1 || role == 2) {
            this.button(L.t("Выйти"), fx + 106, y, 80, 20, () -> {
               CompoundTag t = new CompoundTag();
               t.putString("Id", this.current().getString("Id"));
               ClientOffice.req("org_leave", t);
            });
         }

         if (head) {
            this.button(L.t("Удалить"), fx + fw - 80, y, 80, 20, () -> {
               CompoundTag t = new CompoundTag();
               t.putString("Id", this.current().getString("Id"));
               ClientOffice.req("org_delete", t);
            }).danger();
         }
      }

      this.formBottom = y + 24;
      this.button(L.t("Закрыть"), this.x0 + this.w0 - 78, this.y0 + this.h0 - 28, 66, 20, this::onClose).ghost();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderBackground(g);
      Ui.card(g, this.font, this.x0, this.y0, this.w0, this.h0, L.t("Организации"));
      int fx = this.x0 + this.listW + 22;
      int fw = this.x0 + this.w0 - 12 - fx;
      CompoundTag c = this.current();
      if (this.orgs.isEmpty() && !this.creating) {
         g.drawString(this.font, L.t("Вы пока не состоите в организациях."), this.x0 + 10, this.y0 + 52, -6253953, false);
      }

      if (c != null) {
         String info = L.t("Владелец: ") + c.getString("Owner") + " · " + L.t("создана ") + c.getString("Created") + " · " + L.t("ваша роль: ") + L.t(ROLES[Math.min(3, c.getInt("Role"))]);
         g.drawString(this.font, Ui.ellipsize(this.font, info, fw), fx, this.y0 + 30, -6253953, false);
      }

      int y = this.y0 + this.h0 - 46;
      int listBottom = this.y0 + 28 + 22 + Math.min(this.rows, this.orgs.size()) * 20 + (this.orgs.size() > this.rows ? 18 : 0);

      for (String line : new String[]{
         "Доступ к шкафам, турникетам и компьютерам: впишите @Название в список доступа.",
         "Письмо всем участникам: «@Название» на конверте."
      }) {
         if (y > this.formBottom && y > listBottom) {
            g.drawString(this.font, Ui.ellipsize(this.font, L.t(line), this.w0 - 100), this.x0 + 10, y, -7633519, false);
         }

         y += 11;
      }

      super.render(g, mx, my, pt);
   }
}
