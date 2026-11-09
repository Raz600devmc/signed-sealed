package ru.realdocs.net;

import net.minecraft.server.level.ServerPlayer;

public final class Ctx {
   private final ServerPlayer sender;

   public Ctx(ServerPlayer sender) {
      this.sender = sender;
   }

   public ServerPlayer getSender() {
      return this.sender;
   }
}
