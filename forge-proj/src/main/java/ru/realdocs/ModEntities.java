package ru.realdocs;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.realdocs.blockentity.ArchiveBE;
import ru.realdocs.blockentity.GateBE;
import ru.realdocs.blockentity.TerminalBE;
import ru.realdocs.entity.PosterEntity;

public final class ModEntities {
   public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "signedsealed");
   public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "signedsealed");
   public static final RegistryObject<BlockEntityType<ArchiveBE>> ARCHIVE = BLOCK_ENTITIES.register(
      "archive", () -> BlockEntityType.Builder.of(ArchiveBE::new, new Block[]{ModBlocks.ARCHIVE_SHELF.get(), ModBlocks.FILING_CABINET.get()}).build(null)
   );
   public static final RegistryObject<BlockEntityType<GateBE>> GATE = BLOCK_ENTITIES.register(
      "ticket_gate", () -> BlockEntityType.Builder.of(GateBE::new, new Block[]{ModBlocks.TICKET_GATE.get()}).build(null)
   );
   public static final RegistryObject<BlockEntityType<TerminalBE>> TERMINAL = BLOCK_ENTITIES.register(
      "terminal", () -> BlockEntityType.Builder.of(TerminalBE::new, new Block[]{ModBlocks.SCANNER.get(), ModBlocks.COMPUTER.get()}).build(null)
   );
   public static final RegistryObject<BlockEntityType<ru.realdocs.blockentity.ReaderBE>> READER = BLOCK_ENTITIES.register(
      "id_reader", () -> BlockEntityType.Builder.of(ru.realdocs.blockentity.ReaderBE::new, new Block[]{ModBlocks.ID_READER.get()}).build(null)
   );
   public static final RegistryObject<EntityType<ru.realdocs.entity.NoticeBoardEntity>> NOTICE_BOARD = ENTITIES.register(
      "notice_board",
      () -> EntityType.Builder.<ru.realdocs.entity.NoticeBoardEntity>of(ru.realdocs.entity.NoticeBoardEntity::new, MobCategory.MISC)
            .sized(0.5F, 0.5F)
            .clientTrackingRange(10)
            .updateInterval(20)
            .build("notice_board")
   );
   public static final RegistryObject<EntityType<PosterEntity>> POSTER = ENTITIES.register(
      "poster",
      () -> EntityType.Builder.<PosterEntity>of(PosterEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(20).build("poster")
   );

   private ModEntities() {
   }
}
