package ru.realdocs.server;

import net.minecraft.server.level.ServerPlayer;
import ru.realdocs.L;
import ru.realdocs.blockentity.GateBE;
import ru.realdocs.blockentity.OfficeBE;
import ru.realdocs.blockentity.TerminalBE;
import ru.realdocs.net.ArchivePackets;

/** Applies settings sent from the block settings screen. */
public final class BlockConfigService {
   private BlockConfigService() {
   }

   public static void apply(ServerPlayer sp, ArchivePackets.BlockConfig c) {
      if (sp.distanceToSqr(c.pos.getX() + 0.5, c.pos.getY() + 0.5, c.pos.getZ() + 0.5) <= 64.0) {
         if (sp.level().getBlockEntity(c.pos) instanceof OfficeBE be) {
            if (!be.isOwner(sp)) {
               DocService.err(sp, L.k("Настраивать может только владелец: ") + be.ownerName);
            } else {
               be.label = DocService.clean(c.label, 40, false);
               be.locked = c.locked;
               be.setMembers(c.members);
               if (be instanceof GateBE g) {
                  g.filter = DocService.clean(c.text, 80, false);
                  g.mode = Math.max(0, Math.min(2, c.mode));
                  g.pulse = Math.max(1, Math.min(10, c.n));
               } else if (be instanceof TerminalBE t) {
                  t.network = DocService.clean(c.text, 40, false);
               }

               be.sync();
               DocService.ok(sp, L.k("Настройки сохранены."));
            }
         }
      }
   }
}
