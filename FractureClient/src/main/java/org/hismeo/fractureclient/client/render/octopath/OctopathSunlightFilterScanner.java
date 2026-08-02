package org.hismeo.fractureclient.client.render.octopath;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a few nearby stained-glass apertures for the directional Tyndall pass.
 *
 * <p>The main directional map intentionally omits the glass so the sun can pass through. This
 * scanner separately preserves its dye colour, grouping adjacent blocks into stable 3x3x3
 * apertures rather than turning every window block into an individual shader uniform.</p>
 */
final class OctopathSunlightFilterScanner {
    static final int MAX_FILTERS = 12;

    private static final int HORIZONTAL_RADIUS = 32;
    private static final int BELOW_PLAYER = 12;
    private static final int ABOVE_PLAYER = 28;
    private static final int CLUSTER_SIZE = 3;
    private static final int SCAN_INTERVAL_TICKS = 12;
    private static final Comparator<OctopathSunlightFilter> SCORE_ORDER =
            Comparator.comparingDouble(OctopathSunlightFilter::score).reversed();

    private ClientLevel lastLevel;
    private long lastScanTick = Long.MIN_VALUE;
    private List<OctopathSunlightFilter> cachedFilters = List.of();

    List<OctopathSunlightFilter> scan(ClientLevel level, LocalPlayer player) {
        long gameTime = level.getGameTime();
        if (level != lastLevel
                || lastScanTick == Long.MIN_VALUE
                || gameTime < lastScanTick
                || gameTime - lastScanTick >= SCAN_INTERVAL_TICKS) {
            cachedFilters = findFilters(level, player);
            lastLevel = level;
            lastScanTick = gameTime;
        }
        return cachedFilters;
    }

    void reset() {
        lastLevel = null;
        lastScanTick = Long.MIN_VALUE;
        cachedFilters = List.of();
    }

    private static List<OctopathSunlightFilter> findFilters(ClientLevel level, LocalPlayer player) {
        int centerX = (int) Math.floor(player.getX());
        int centerY = (int) Math.floor(player.getY());
        int centerZ = (int) Math.floor(player.getZ());
        int minY = Math.max(level.getMinBuildHeight(), centerY - BELOW_PLAYER);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + ABOVE_PLAYER);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Map<FilterClusterKey, FilterCluster> clusters = new HashMap<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = centerZ - HORIZONTAL_RADIUS; z <= centerZ + HORIZONTAL_RADIUS; z++) {
                for (int x = centerX - HORIZONTAL_RADIUS; x <= centerX + HORIZONTAL_RADIUS; x++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    DyeColor color = stainedGlassColor(level.getBlockState(cursor));
                    if (color == null) {
                        continue;
                    }

                    double deltaX = x + 0.5D - player.getX();
                    double deltaY = y + 0.5D - (player.getY() + player.getBbHeight() * 0.45D);
                    double deltaZ = z + 0.5D - player.getZ();
                    float score = (float) (1.0D / (1.0D + (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 0.035D));
                    FilterClusterKey key = new FilterClusterKey(
                            Math.floorDiv(x, CLUSTER_SIZE),
                            Math.floorDiv(y, CLUSTER_SIZE),
                            Math.floorDiv(z, CLUSTER_SIZE),
                            color);
                    clusters.computeIfAbsent(key, ignored -> new FilterCluster(color)).add(
                            x + 0.5F,
                            y + 0.5F,
                            z + 0.5F,
                            score);
                }
            }
        }

        List<OctopathSunlightFilter> filters = new ArrayList<>(clusters.size());
        for (FilterCluster cluster : clusters.values()) {
            filters.add(cluster.toFilter());
        }
        filters.sort(SCORE_ORDER);
        if (filters.size() > MAX_FILTERS) {
            filters = new ArrayList<>(filters.subList(0, MAX_FILTERS));
        }
        return List.copyOf(filters);
    }

    private static DyeColor stainedGlassColor(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StainedGlassBlock stainedGlass) {
            return stainedGlass.getColor();
        }
        if (block instanceof StainedGlassPaneBlock stainedGlassPane) {
            return stainedGlassPane.getColor();
        }
        return null;
    }

    private static Vector3f linearColor(DyeColor dyeColor) {
        int rgb = dyeColor.getTextureDiffuseColor();
        return new Vector3f(
                toLinear((rgb >> 16) & 0xFF),
                toLinear((rgb >> 8) & 0xFF),
                toLinear(rgb & 0xFF));
    }

    private static float toLinear(int component) {
        return (float) Math.pow(component / 255.0D, 2.0D);
    }

    private record FilterClusterKey(int x, int y, int z, DyeColor color) {
    }

    private static final class FilterCluster {
        private final DyeColor dyeColor;
        private float weightedX;
        private float weightedY;
        private float weightedZ;
        private float score;
        private int blockCount;

        private FilterCluster(DyeColor dyeColor) {
            this.dyeColor = dyeColor;
        }

        private void add(float x, float y, float z, float sourceScore) {
            weightedX += x;
            weightedY += y;
            weightedZ += z;
            score += sourceScore;
            blockCount++;
        }

        private OctopathSunlightFilter toFilter() {
            float inverseCount = 1.0F / Math.max(1, blockCount);
            float radius = Math.min(2.25F, 0.38F + (float) Math.sqrt(blockCount) * 0.34F);
            return new OctopathSunlightFilter(
                    new Vector3f(weightedX * inverseCount, weightedY * inverseCount, weightedZ * inverseCount),
                    linearColor(dyeColor),
                    radius,
                    score);
        }
    }
}
