package ru.realdocs;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent.Nodes;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContextKey;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public final class Perms {
   public static final PermissionNode<Boolean> CREATE = node("document.create", false);
   public static final PermissionNode<Boolean> SIGN = node("document.sign", false);
   public static final PermissionNode<Boolean> VERIFY = node("document.verify", false);
   public static final PermissionNode<Boolean> APPLY = node("stamp.apply", false);
   public static final PermissionNode<Boolean> DESIGN = node("stamp.design", false);
   public static final PermissionNode<Boolean> ISSUE = node("stamp.issue", true);
   public static final PermissionNode<Boolean> PASSPORT_ISSUE = node("passport.issue", true);
   public static final PermissionNode<Boolean> PASSPORT_FILL = node("passport.fill", false);
   public static final PermissionNode<Boolean> SHOW = node("show", false);
   public static final PermissionNode<Boolean> FORGE = node("forge", false);
   public static final PermissionNode<Boolean> PRINT = node("print", false);
   public static final PermissionNode<Boolean> ADMIN = node("admin", true);
   public static final PermissionNode<Boolean> NEWSPAPER = node("newspaper.publish", false);
   public static final PermissionNode<Boolean> MAIL = node("mail.send", false);

   private Perms() {
   }

   private static PermissionNode<Boolean> node(String name, boolean opOnly) {
      return new PermissionNode(
         "signedsealed",
         name,
         PermissionTypes.BOOLEAN,
         (player, uuid, ctx) -> !opOnly || player != null && player.hasPermissions(2),
         new PermissionDynamicContextKey[0]
      );
   }

   @SubscribeEvent
   public static void onGather(Nodes event) {
      event.addNodes(new PermissionNode[]{CREATE, SIGN, VERIFY, APPLY, DESIGN, ISSUE, PASSPORT_ISSUE, PASSPORT_FILL, SHOW, ADMIN, FORGE, PRINT, NEWSPAPER, MAIL});
   }

   public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
      return (Boolean)PermissionAPI.getPermission(player, node, new PermissionDynamicContext[0]);
   }
}
