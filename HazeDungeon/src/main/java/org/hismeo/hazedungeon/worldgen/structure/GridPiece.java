package org.hismeo.hazedungeon.worldgen.structure;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.hismeo.crystallib.common.util.CodecUtil;
import org.hismeo.hazedungeon.HazeDungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GridPiece extends StructurePiece {
    public static final Codec<List<Tuple<Integer, LongArrayList>>> BLOCK_MAP_CODEC = CodecUtil.tupleCodec(Codec.INT, Codec.LONG.listOf().xmap(LongArrayList::new, Function.identity())).listOf();
    public static final Codec<List<Tuple<BlockPos, ResourceLocation>>> FEATURES_CODEC = CodecUtil.tupleCodec(BlockPos.CODEC, ResourceLocation.CODEC).listOf();

    private final ChunkPos startPos;
    private final int y;
    private final List<Tuple<Integer, LongArrayList>> blockMap;
    public List<BlockState> blockList;
    private final List<Tuple<BlockPos, ResourceLocation>> features;

    public GridPiece(ChunkPos startPos, int y, Object2IntMap<BlockPos> blockMap, Map<BlockPos, ResourceLocation> features) {
        super(ModStructures.GRID_PIECE.get(), 0, new BoundingBox(
                startPos.getMinBlockX() - 24, y - 12, startPos.getMinBlockZ() - 24,
                startPos.getMaxBlockX() + 23, y + 11, startPos.getMaxBlockZ() + 23
        ));
        this.startPos = startPos;
        this.y = y;
        this.blockMap = new ArrayList<>();
        this.features = new ArrayList<>();
        convertToList(blockMap, features);
    }

    private void convertToList(Object2IntMap<BlockPos> blockMap, Map<BlockPos, ResourceLocation> features) {
        Map<Integer, LongArrayList> map = new HashMap<>();
        for (Object2IntMap.Entry<BlockPos> posEntry : blockMap.object2IntEntrySet()) {
            map.computeIfAbsent(posEntry.getIntValue(), index -> new LongArrayList()).add(posEntry.getKey().asLong());
        }
        for (Map.Entry<Integer, LongArrayList> entry : map.entrySet()) {
            this.blockMap.add(new Tuple<>(entry.getKey(), entry.getValue()));
        }
        features.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .forEachOrdered(entry -> this.features.add(new Tuple<>(entry.getKey(), entry.getValue())));
    }

    public GridPiece(CompoundTag tag) {
        super(ModStructures.GRID_PIECE.get(), tag);
        this.startPos = new ChunkPos(tag.getLong("StartPos"));
        this.y = tag.getInt("Y");
        this.blockMap = BLOCK_MAP_CODEC.parse(NbtOps.INSTANCE, tag.get("BlockMap")).getOrThrow(false, HazeDungeon.LOGGER::error);
        this.blockList = BlockState.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("BlockList")).getOrThrow(false, HazeDungeon.LOGGER::error);
        this.features = FEATURES_CODEC.parse(NbtOps.INSTANCE, tag.get("Features")).getOrThrow(false, HazeDungeon.LOGGER::error);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("StartPos", startPos.toLong());
        tag.putInt("Y", y);
        tag.put("BlockMap", BLOCK_MAP_CODEC.encodeStart(NbtOps.INSTANCE, blockMap).getOrThrow(false, HazeDungeon.LOGGER::error));
        tag.put("BlockList", BlockState.CODEC.listOf().encodeStart(NbtOps.INSTANCE, blockList).getOrThrow(false, HazeDungeon.LOGGER::error));
        tag.put("Features", FEATURES_CODEC.encodeStart(NbtOps.INSTANCE, features).getOrThrow(false, HazeDungeon.LOGGER::error));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        if (blockList == null || !chunkPos.equals(startPos)) return;
        for (Tuple<Integer, LongArrayList> pair : blockMap) {
            BlockState blockState = blockList.get(pair.getA());
            for (long blockPos : pair.getB()) {
                level.setBlock(BlockPos.of(blockPos), blockState, 2);
            }
            pair.getB().clear();
        }
        Registry<ConfiguredFeature<?, ?>> configuredFeatures = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (Tuple<BlockPos, ResourceLocation> pair : features) {
            configuredFeatures.getOptional(pair.getB()).ifPresent(feature -> feature.place(level, generator, random, pair.getA()));
        }
    }

    public static void addPieces(Object2IntMap<BlockPos> blockMap, ChunkPos startChunk, int y, List<BlockState> blockList, StructurePiecesBuilder builder) {
        addPieces(blockMap, startChunk, y, blockList, Map.of(), builder);
    }

    public static void addPieces(Object2IntMap<BlockPos> blockMap, ChunkPos startChunk, int y, List<BlockState> blockList, Map<BlockPos, ResourceLocation> featureMap, StructurePiecesBuilder builder) {
        Map<ChunkPos, Map<BlockPos, ResourceLocation>> sliceMap = new HashMap<>();
        for (Map.Entry<BlockPos, ResourceLocation> posEntry : featureMap.entrySet()) {
            BlockPos blockPos = posEntry.getKey();
            sliceMap.computeIfAbsent(sliceChunk(blockPos, startChunk), map -> new HashMap<>()).put(blockPos, posEntry.getValue());
        }

        Map<ChunkPos, Object2IntMap<BlockPos>> gridMap = new HashMap<>();
        for (Object2IntMap.Entry<BlockPos> posEntry : blockMap.object2IntEntrySet()) {
            BlockPos blockPos = posEntry.getKey();
            gridMap.computeIfAbsent(sliceChunk(blockPos, startChunk), map -> new Object2IntOpenHashMap<>()).put(blockPos, posEntry.getIntValue());
        }

        for (Map.Entry<ChunkPos, Object2IntMap<BlockPos>> entry : gridMap.entrySet()) {
            GridPiece piece = new GridPiece(entry.getKey(), y, entry.getValue(), sliceMap.getOrDefault(entry.getKey(), Map.of()));
            piece.blockList = blockList;
            builder.addPiece(piece);
        }
    }

    private static ChunkPos sliceChunk(BlockPos blockPos, ChunkPos startChunk) {
        int cx = SectionPos.blockToSectionCoord(blockPos.getX());
        int cz = SectionPos.blockToSectionCoord(blockPos.getZ());
        int regionX = (cx - startChunk.x + 1) / 3;
        int regionZ = (cz - startChunk.z + 1) / 3;
        return new ChunkPos(cx + regionX, cz + regionZ);
    }
}
