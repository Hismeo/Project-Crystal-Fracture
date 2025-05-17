package org.hismeo.hazedungeon.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.hismeo.hazedungeon.HazeDungeon;

import java.util.function.Supplier;

public final class ModStructures {
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, HazeDungeon.MODID);
    public static final Supplier<StructurePieceType> GRID_PIECE = PIECE_TYPES.register("grid_piece", () -> (context, tag) -> new GridPiece(tag));
    public static final Supplier<StructurePieceType> SIMPLE_TEMPLATE_PIECE = PIECE_TYPES.register("simple_template_piece", () -> (context, tag) -> new SimpleTemplatePiece(context.structureTemplateManager(), tag));

    /**
     * 将绝对坐标压缩为相对坐标
     */
    public static int compressRelativePos(BlockPos pos) {
        return ((pos.getX() & 0xF) << 16) | ((pos.getY() + 2048) << 4) | (pos.getZ() & 0xF);
    }

    /**
     * 将相对坐标解压为绝对坐标
     */
    public static BlockPos decompressRelativePos(ChunkPos chunkPos, int compressed) {
        int x = (compressed >>> 16) & 0xF;
        int y = ((compressed >>> 4) & 0xFFF) - 2048;
        int z = compressed & 0xF;
        return chunkPos.getBlockAt(x, y, z);
    }
}
