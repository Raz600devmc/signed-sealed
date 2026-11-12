package ru.realdocs.client.held;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import ru.realdocs.L;
import ru.realdocs.client.ClientHooks;
import ru.realdocs.client.ClientSetup;
import ru.realdocs.client.Sounds;
import ru.realdocs.client.ui.Ui;
import ru.realdocs.item.DocumentItem;
import ru.realdocs.item.PassportItem;

public final class InspectScreen extends Screen {
   private final InteractionHand hand;
   private float yaw = -18.0F;
   private float pitch = 12.0F;
   private float zoom = 1.0F;
   private float tYaw = -18.0F;
   private float tPitch = 12.0F;
   private float tZoom = 1.0F;
   private boolean open;
   private float openAmt;
   private long lastClick;

   public InspectScreen(InteractionHand hand) {
      super(Component.literal(L.t("Осмотр документа")));
      this.hand = hand;
   }

   private ItemStack stack() {
      return this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getItemInHand(this.hand);
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      ItemStack st = this.stack();
      if (HeldTextures.model(st, false) == null && !(st.getItem() instanceof DocumentItem) && !(st.getItem() instanceof PassportItem)) {
         this.onClose();
      } else {
         g.fillGradient(0, 0, this.width, this.height, -1879048192, -1073741824);
         HeldTextures.tick();
         this.yaw = this.yaw + (this.tYaw - this.yaw) * 0.25F;
         this.pitch = this.pitch + (this.tPitch - this.pitch) * 0.25F;
         this.zoom = this.zoom + (this.tZoom - this.zoom) * 0.25F;
         this.openAmt = this.openAmt + ((this.open ? 1.0F : 0.0F) - this.openAmt) * 0.18F;
         Doc3D.Model m = HeldTextures.model(st, this.openAmt > 0.02F);
         if (m != null) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate((float)this.width / 2.0F, (float)this.height / 2.0F, 150.0F);
            float size = (float)Math.min(this.width, this.height)
               * 0.95F
               * this.zoom
               / Math.max(m.kind == Doc3D.Kind.BOOK && this.openAmt > 0.5F ? m.w * 2.0F : m.w, m.h);
            ps.scale(size, -size, size);
            ps.mulPose(Axis.XP.rotationDegrees(this.pitch));
            ps.mulPose(Axis.YP.rotationDegrees(this.yaw));
            Lighting.setupFor3DItems();
            Doc3D.render(ps, g.bufferSource(), m, this.openAmt, 15728880, OverlayTexture.NO_OVERLAY);
            g.flush();
            Lighting.setupForFlatItems();
            ps.popPose();
         }

         String hint = L.t("Мышь — вращать · Колесо — приблизить · Пробел/ПКМ — открыть/перевернуть · Enter — просмотр · Esc — убрать");
         String shown = Ui.ellipsize(this.font, hint, this.width - 20);
         int hw = this.font.width(shown);
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 1000.0F);
         g.fill(this.width / 2 - hw / 2 - 6, 3, this.width / 2 + hw / 2 + 6, 19, -1342177280);
         g.drawCenteredString(this.font, shown, this.width / 2, 7, -3555408);
         g.pose().popPose();
      }
   }

   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (button == 0) {
         this.tYaw += (float)dx * 0.8F;
         this.tPitch = Math.max(-80.0F, Math.min(80.0F, this.tPitch + (float)dy * 0.8F));
         return true;
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   public boolean mouseScrolled(double mx, double my, double delta) {
      this.tZoom = Math.max(0.5F, Math.min(2.6F, this.tZoom * (delta > 0.0 ? 1.12F : 0.89285713F)));
      return true;
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 1) {
         this.toggle();
         return true;
      } else {
         if (button == 0) {
            long now = System.currentTimeMillis();
            if (now - this.lastClick < 300L) {
               this.openFull();
               return true;
            }

            this.lastClick = now;
         }

         return super.mouseClicked(mx, my, button);
      }
   }

   private void toggle() {
      ItemStack st = this.stack();
      Doc3D.Model m = HeldTextures.model(st, false);
      if (m != null) {
         if (m.kind == Doc3D.Kind.BOOK) {
            this.open = !this.open;
            if (this.open && Math.abs((this.tYaw % 360.0F + 360.0F) % 360.0F - 180.0F) < 90.0F) {
               this.tYaw += 180.0F;
            }

            Sounds.play(Sounds.PAGE, 1.0F);
         } else {
            this.tYaw += 180.0F;
            Sounds.play(m.kind == Doc3D.Kind.CARD ? Sounds.CARD : Sounds.PAPER, 1.0F, 0.6F);
         }
      }
   }

   private void openFull() {
      ItemStack st = this.stack();
      if (st.getItem() instanceof PassportItem) {
         ClientHooks.openPassport(this.hand);
      } else if (st.getItem() instanceof ru.realdocs.item.NewspaperItem) {
         ClientHooks.openNewspaper(this.hand);
      } else if (st.getItem() instanceof ru.realdocs.item.EnvelopeItem) {
         ClientHooks.openEnvelope(this.hand);
      } else if (st.getItem() instanceof ru.realdocs.item.FolderItem) {
         ClientHooks.openFolder(this.hand);
      } else {
         ClientHooks.openDocument(this.hand);
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 32) {
         this.toggle();
         return true;
      } else if (key == 257 || key == 335) {
         this.openFull();
         return true;
      } else if (ClientSetup.INSPECT_KEY.matches(key, scan)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }
}
