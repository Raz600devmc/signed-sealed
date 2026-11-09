package ru.realdocs;

import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Loader/version specific helpers (NeoForge 1.21.1). Shared code calls only these. */
public final class Plat {
   private Plat() {
   }

   public static ResourceLocation rl(String path) {
      return ResourceLocation.fromNamespaceAndPath("signedsealed", path);
   }

   public static CompoundTag saveStack(ItemStack stack, Level level) {
      if (stack.isEmpty()) {
         return new CompoundTag();
      } else {
         Tag t = stack.save(level.registryAccess());
         return t instanceof CompoundTag c ? c : new CompoundTag();
      }
   }

   public static ItemStack loadStack(CompoundTag tag, Level level) {
      return tag != null && !tag.isEmpty() ? ItemStack.parseOptional(level.registryAccess(), tag) : ItemStack.EMPTY;
   }

   public static byte[] pack(CompoundTag tag) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      NbtIo.writeCompressed(tag, out);
      return out.toByteArray();
   }

   public static CompoundTag unpack(byte[] data, long quota) throws IOException {
      return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.create(quota));
   }

   public static Optional<GameProfile> profile(MinecraftServer server, String name) {
      GameProfileCache cache = server.getProfileCache();
      return cache == null ? Optional.empty() : cache.get(name);
   }
}
