package org.hismeo.hazedungeon.worldgen.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.hismeo.hazedungeon.HazeDungeon;

public final class ModStructures {
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, HazeDungeon.MODID);
    public static final RegistryObject<StructurePieceType> GRID_PIECE = PIECE_TYPES.register("grid_piece", ()-> (context, tag) -> new GridPiece(tag));
    public static final RegistryObject<StructurePieceType> SIMPLE_TEMPLATE_PIECE = PIECE_TYPES.register("simple_template_piece", () -> (context, tag) -> new SimpleTemplatePiece(context.structureTemplateManager(), tag));
}
