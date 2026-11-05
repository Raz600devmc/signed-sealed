package ru.realdocs.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

public class UiTextArea extends MultiLineEditBox {
   public UiTextArea(Font font, int x, int y, int w, int h, String placeholder) {
      super(font, x, y, w, h, Component.literal(placeholder).withStyle(s -> s.withColor(7233874)), Component.literal(placeholder));
   }

   protected void renderBorder(GuiGraphics g, int x, int y, int w, int h) {
      Ui.round(g, x, y, w, h, -15396595);
      Ui.outline(g, x, y, w, h, this.isFocused() ? -2641559 : -11912404);
   }
}
