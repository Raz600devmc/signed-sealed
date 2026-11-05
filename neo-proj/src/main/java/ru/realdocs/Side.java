package ru.realdocs;

import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class Side {
   private Side() {
   }

   public static void client(Supplier<Runnable> r) {
      if (FMLEnvironment.dist == Dist.CLIENT) {
         r.get().run();
      }
   }
}
