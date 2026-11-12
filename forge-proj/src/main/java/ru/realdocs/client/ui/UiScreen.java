package ru.realdocs.client.ui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.realdocs.L;

public abstract class UiScreen extends Screen {
   /** Screen to go back to when the next viewer closes (e.g. the computer that opened it). */
   public static net.minecraft.client.gui.screens.Screen returnTo;

   /** Captured from {@link #returnTo} when this screen was created. */
   private net.minecraft.client.gui.screens.Screen back;

   public void onClose() {
      if (this.back != null && this.back != this) {
         net.minecraft.client.gui.screens.Screen r = this.back;
         this.back = null;
         this.minecraft.setScreen(r);
      } else {
         super.onClose();
      }
   }

   private String toast = "";
   private int toastColor = -1183755;
   private long toastAt;

   protected UiScreen(Component title) {
      super(title);
      this.back = returnTo;
      returnTo = null;
   }

   protected UiButton button(String label, int x, int y, int w, int h, Runnable action) {
      return (UiButton)this.addRenderableWidget(new UiButton(x, y, w, h, label, action));
   }

   protected UiButton button(String label, int x, int y, int w, Runnable action) {
      return this.button(label, x, y, w, 20, action);
   }

   protected UiField field(int x, int y, int w, String hint, String value, int max, Consumer<String> onChange) {
      UiField f = new UiField(this.font, x, y, w, 18, hint);
      f.setMaxLength(max);
      f.setValue(value == null ? "" : value);
      if (onChange != null) {
         f.setResponder(onChange);
      }

      return (UiField)this.addRenderableWidget(f);
   }

   /** Field with player name suggestions; {@code multi} for comma-separated lists, {@code targets} adds @lists. */
   protected NameField nameField(int x, int y, int w, String hint, String value, int max, Consumer<String> onChange, boolean multi, boolean targets) {
      NameField f = new NameField(this.font, x, y, w, 18, hint, multi, targets);
      f.setMaxLength(max);
      f.setValue(value == null ? "" : value);
      if (onChange != null) {
         f.setResponder(onChange);
      }

      return (NameField)this.addRenderableWidget(f);
   }

   public void toast(String text, int color) {
      this.toast = text == null ? "" : L.t(text);
      this.toastColor = color;
      this.toastAt = System.currentTimeMillis();
   }

   public void ok(String text) {
      this.toast(text, -10628481);
   }

   public void error(String text) {
      this.toast(text, -1088669);
   }

   public void info(String text) {
      this.toast(text, -3555408);
   }

   protected void renderToast(GuiGraphics g) {
      if (!this.toast.isEmpty()) {
         long t = System.currentTimeMillis() - this.toastAt;
         if (t > 6000L) {
            this.toast = "";
         } else {
            float a = t < 150L ? (float)t / 150.0F : (t > 5000L ? 1.0F - (float)(t - 5000L) / 1000.0F : 1.0F);
            String s = Ui.ellipsize(this.font, this.toast, this.width - 60);
            int w = this.font.width(s) + 24;
            int h = 18;
            int x = (this.width - w) / 2;
            int y = this.height - h - 8 + (int)((1.0F - Math.min(1.0F, (float)t / 150.0F)) * 6.0F);
            Ui.round(g, x, y, w, h, Ui.alpha(-267052765, a));
            Ui.outline(g, x, y, w, h, Ui.alpha(this.toastColor, 0.6F * a));
            g.fill(x + 7, y + 7, x + 11, y + 11, Ui.alpha(this.toastColor, a));
            g.drawString(this.font, s, x + 15, y + 5, Ui.alpha(-1183755, Math.max(0.05F, a)), false);
         }
      }
   }

   public void renderBackground(GuiGraphics g) {
      Ui.background(g, this.width, this.height);
   }

   protected void header(GuiGraphics g, String title, String subtitle, int x, int y) {
      g.fill(x, y + 1, x + 3, y + 9, -2641559);
      g.drawString(this.font, title, x + 8, y + 1, -1183755, false);
      if (subtitle != null && !subtitle.isEmpty()) {
         g.drawString(this.font, subtitle, x + 14 + this.font.width(title), y + 1, -6253953, false);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
