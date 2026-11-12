package ru.realdocs.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * Rotating backups of the mod's world data. signedsealed.dat holds the signing key: without it every issued
 * document, passport and stamp stops verifying, so it is copied on start, on stop, every 30 minutes of autosaves
 * and on /seal backup, and restored automatically if the main file disappears.
 */
public final class BackupService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final String[] FILES = new String[]{"signedsealed.dat", "signedsealed_office.dat"};
   private static final int KEEP = 20;
   private static final long PERIOD_MS = 30L * 60L * 1000L;
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
   private static long lastBackup;

   private BackupService() {
   }

   static Path dataDir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("data");
   }

   static Path backupDir(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve("signedsealed_backups");
   }

   @SubscribeEvent
   public static void onAboutToStart(ServerAboutToStartEvent event) {
      restoreIfMissing(event.getServer());
   }

   @SubscribeEvent
   public static void onStarted(ServerStartedEvent event) {
      MinecraftServer server = event.getServer();
      int made = backup(server, "start");
      if (made < 0) {
         LOG.warn("[Signed & Sealed] Could not back up signedsealed.dat. This file holds the signing key: if it is lost, every issued document stops verifying.");
      } else {
         LOG.info("[Signed & Sealed] Data backed up to {} ({} backups kept).", backupDir(server), countBackups(server));
      }
   }

   @SubscribeEvent
   public static void onSave(LevelEvent.Save event) {
      if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
         long now = System.currentTimeMillis();
         if (now - lastBackup >= PERIOD_MS) {
            backup(level.getServer(), "auto");
         }
      }
   }

   @SubscribeEvent
   public static void onStopped(ServerStoppedEvent event) {
      backup(event.getServer(), "stop");
   }

   @SubscribeEvent
   public static void onCommands(RegisterCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
      d.register(Commands.literal("seal").then(Commands.literal("backup").requires(s -> s.hasPermission(3)).executes(c -> {
         CommandSourceStack src = c.getSource();
         MinecraftServer server = src.getServer();
         server.overworld().getDataStorage().save();
         int made = backup(server, "manual");
         if (made < 0) {
            src.sendFailure(Component.literal("[Signed & Sealed] Backup failed, see the server log."));
            return 0;
         } else {
            src.sendSuccess(() -> Component.literal("[Signed & Sealed] Backup saved: " + made + " file(s) in signedsealed_backups (" + countBackups(server) + " kept)."), true);
            return 1;
         }
      })));
   }

   /** @return number of files copied, or -1 on error. */
   public static synchronized int backup(MinecraftServer server, String reason) {
      lastBackup = System.currentTimeMillis();
      Path data = dataDir(server);
      if (!Files.exists(data.resolve(FILES[0]))) {
         return 0;
      }

      Path dir = backupDir(server).resolve(LocalDateTime.now().format(STAMP) + "_" + reason);
      int n = 0;

      try {
         Files.createDirectories(dir);

         for (String f : FILES) {
            Path src = data.resolve(f);
            if (Files.exists(src)) {
               Files.copy(src, dir.resolve(f), StandardCopyOption.REPLACE_EXISTING);
               n++;
            }
         }

         prune(server);
         return n;
      } catch (IOException e) {
         LOG.error("[Signed & Sealed] Backup to {} failed", dir, e);
         return -1;
      }
   }

   private static List<Path> listBackups(MinecraftServer server) {
      List<Path> out = new ArrayList<>();
      Path root = backupDir(server);
      if (Files.isDirectory(root)) {
         try (Stream<Path> s = Files.list(root)) {
            s.filter(p -> Files.isRegularFile(p.resolve(FILES[0]))).forEach(out::add);
         } catch (IOException e) {
            LOG.warn("[Signed & Sealed] Cannot list backups", e);
         }
      }

      out.sort(Comparator.comparing(p -> p.getFileName().toString()));
      return out;
   }

   private static int countBackups(MinecraftServer server) {
      return listBackups(server).size();
   }

   private static void prune(MinecraftServer server) throws IOException {
      List<Path> all = listBackups(server);

      for (int i = 0; i < all.size() - KEEP; i++) {
         try (Stream<Path> s = Files.walk(all.get(i))) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(p);
            }
         }
      }
   }

   private static void restoreIfMissing(MinecraftServer server) {
      Path data = dataDir(server);
      Path key = data.resolve(FILES[0]);
      if (!Files.exists(key)) {
         List<Path> all = listBackups(server);
         if (!all.isEmpty()) {
            Path latest = all.get(all.size() - 1);

            try {
               Files.createDirectories(data);

               for (String f : FILES) {
                  Path src = latest.resolve(f);
                  if (Files.exists(src) && !Files.exists(data.resolve(f))) {
                     Files.copy(src, data.resolve(f));
                  }
               }

               LOG.warn("==============================================================");
               LOG.warn("[Signed & Sealed] data/signedsealed.dat was MISSING.");
               LOG.warn("[Signed & Sealed] Restored it from backup {}", latest.getFileName());
               LOG.warn("[Signed & Sealed] Documents issued after that backup may not verify.");
               LOG.warn("==============================================================");
            } catch (IOException e) {
               LOG.error("[Signed & Sealed] signedsealed.dat is missing and restoring {} failed. A NEW signing key will be created and existing documents will stop verifying!", latest, e);
            }
         }
      }
   }
}
