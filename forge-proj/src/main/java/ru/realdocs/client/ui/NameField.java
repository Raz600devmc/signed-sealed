package ru.realdocs.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import ru.realdocs.client.ClientOffice;

/**
 * Text field with player name suggestions: everyone who has joined the server, online or not,
 * and (where allowed) "@" mailing lists and organizations. Tab or Enter takes the highlighted
 * name, arrows move the highlight. With {@code multi} each comma-separated part is completed.
 */
public class NameField extends UiField {
   private static final int ROWS = 6;
   private static final int ROW_H = 11;
   private final boolean multi;
   private final boolean targets;
   private List<String> matches = List.of();
   private int sel;
   private String lastValue;
   private int lastCursor = -1;
   private String dismissed;

   public NameField(Font font, int x, int y, int w, int h, String hint, boolean multi, boolean targets) {
      super(font, x, y, w, h, hint);
      this.multi = multi;
      this.targets = targets;
   }

   private int tokenStart() {
      if (!this.multi) {
         return 0;
      } else {
         String v = this.getValue();
         int c = Math.min(this.getCursorPosition(), v.length());
         int a = Math.max(v.lastIndexOf(',', c - 1), v.lastIndexOf(';', c - 1));
         int s = a + 1;

         while (s < c && v.charAt(s) == ' ') {
            s++;
         }

         return s;
      }
   }

   private String token() {
      String v = this.getValue();
      int c = Math.min(this.getCursorPosition(), v.length());
      int s = Math.min(this.tokenStart(), c);
      return v.substring(s, c);
   }

   private void refresh() {
      String v = this.getValue();
      int c = this.getCursorPosition();
      if (!v.equals(this.lastValue) || c != this.lastCursor) {
         this.lastValue = v;
         this.lastCursor = c;
         this.sel = 0;
         String tok = this.token();
         if (tok.isEmpty() || v.equals(this.dismissed)) {
            this.matches = List.of();
         } else {
            String low = tok.toLowerCase(Locale.ROOT);
            List<String> pool = new ArrayList<>();
            if (tok.startsWith("@")) {
               if (this.targets) {
                  pool.addAll(ClientOffice.targets);
               } else {
                  for (String o : ClientOffice.orgs) {
                     pool.add("@" + o);
                  }
               }
            } else {
               pool.addAll(ClientOffice.names);
            }

            List<String> starts = new ArrayList<>();
            List<String> contains = new ArrayList<>();

            for (String n : pool) {
               String nl = n.toLowerCase(Locale.ROOT);
               if (!nl.equals(low)) {
                  if (nl.startsWith(low)) {
                     starts.add(n);
                  } else if (nl.contains(low)) {
                     contains.add(n);
                  }
               }
            }

            starts.addAll(contains);
            this.matches = starts.size() > 40 ? starts.subList(0, 40) : starts;
         }
      }
   }

   private boolean open() {
      return this.isFocused() && this.isVisible() && !this.matches.isEmpty();
   }

   private int dropY() {
      return this.outerY() + this.outerH() + 1;
   }

   private int shown() {
      return Math.min(ROWS, this.matches.size());
   }

   private int first() {
      return Math.max(0, Math.min(this.sel - ROWS + 1, this.matches.size() - ROWS));
   }

   private void accept(int i) {
      if (i >= 0 && i < this.matches.size()) {
         String pick = this.matches.get(i);
         String v = this.getValue();
         int c = Math.min(this.getCursorPosition(), v.length());
         int s = Math.min(this.tokenStart(), c);
         int e = c;
         if (this.multi) {
            int next = v.length();

            for (int k = c; k < v.length(); k++) {
               if (v.charAt(k) == ',' || v.charAt(k) == ';') {
                  next = k;
                  break;
               }
            }

            e = next;
         } else {
            e = v.length();
         }

         String tail = v.substring(e);
         String add = pick + (this.multi && tail.isEmpty() ? ", " : "");
         String nv = v.substring(0, s) + add + tail;
         if (nv.length() <= 4000) {
            this.setValue(nv);
            this.setCursorPosition(s + add.length());
            this.setHighlightPos(this.getCursorPosition());
         }

         this.dismissed = this.multi ? null : nv;
         this.lastValue = null;
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      this.refresh();
      if (this.open()) {
         if (key == InputConstants.KEY_TAB || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            this.accept(this.sel);
            return true;
         }

         if (key == InputConstants.KEY_DOWN) {
            this.sel = (this.sel + 1) % this.matches.size();
            return true;
         }

         if (key == InputConstants.KEY_UP) {
            this.sel = (this.sel - 1 + this.matches.size()) % this.matches.size();
            return true;
         }

         if (key == InputConstants.KEY_ESCAPE) {
            this.dismissed = this.getValue();
            this.matches = List.of();
            return true;
         }
      }

      return super.keyPressed(key, scan, mods);
   }

   private boolean inDrop(double mx, double my) {
      int y0 = this.dropY();
      return mx >= (double)this.outerX() && mx < (double)(this.outerX() + this.outerW()) && my >= (double)y0 && my < (double)(y0 + this.shown() * ROW_H + 2);
   }

   public boolean isMouseOver(double mx, double my) {
      return super.isMouseOver(mx, my) || this.open() && this.inDrop(mx, my);
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (this.open() && this.inDrop(mx, my)) {
         int row = (int)((my - (double)this.dropY() - 1.0) / ROW_H);
         this.accept(this.first() + Math.max(0, Math.min(this.shown() - 1, row)));
         return true;
      } else {
         return super.mouseClicked(mx, my, button);
      }
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      super.renderWidget(g, mx, my, pt);
      this.refresh();
      if (this.open()) {
         Font font = Minecraft.getInstance().font;
         int x = this.outerX();
         int y = this.dropY();
         int w = this.outerW();
         int n = this.shown();
         int f = this.first();
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 400.0F);
         boolean lt = this.isLight();
         if (lt) {
            g.fill(x, y, x + w, y + n * ROW_H + 2, -1);
            Ui.outline(g, x, y, w, n * ROW_H + 2, -16777216);
         } else {
            Ui.round(g, x, y, w, n * ROW_H + 2, -15791853);
            Ui.outline(g, x, y, w, n * ROW_H + 2, -9808320);
         }

         for (int i = 0; i < n; i++) {
            int idx = f + i;
            int ry = y + 1 + i * ROW_H;
            boolean hot = idx == this.sel || mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            if (hot) {
               g.fill(x + 1, ry, x + w - 1, ry + ROW_H, lt ? (idx == this.sel ? -16762518 : -3355393) : (idx == this.sel ? -12897502 : 587202559));
            }

            String s = this.matches.get(idx);
            g.drawString(font, Ui.ellipsize(font, s, w - 30), x + 5, ry + 2, lt ? (idx == this.sel ? -1 : -16777216) : (idx == this.sel ? -2641559 : -1183755), false);
            if (idx == this.sel) {
               g.drawString(font, "Tab", x + w - font.width("Tab") - 5, ry + 2, -6253953, false);
            }
         }

         g.pose().popPose();
      }
   }
}
