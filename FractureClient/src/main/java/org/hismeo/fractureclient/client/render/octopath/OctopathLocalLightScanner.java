package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Samples loaded emissive blocks from the actual visible world footprint rather than a fixed
 * player-radius disk. Dense lamps first merge into stable spatial clusters, then the previous
 * selection is retained with hysteresis. This keeps a small camera movement from repeatedly
 * swapping similarly scored glowstone/torch blocks in and out of the post-light budget.
 */
final class OctopathLocalLightScanner {
    private static final int MAX_LIGHTS = 16;
    private static final int VERTICAL_RADIUS = 10;
    private static final int VIEWPORT_MARGIN = 2;
    private static final int VIEWPORT_SCAN_GUARD = 6;
    private static final int CLUSTER_CELL_SIZE = 4;
    private static final float RETAIN_SCORE_FRACTION = 0.72F;
    private static final Comparator<OctopathLocalLight> SCORE_ORDER =
            Comparator.comparingDouble(OctopathLocalLight::score)
                    .reversed()
                    .thenComparingLong(OctopathLocalLight::stableKey);

    private ClientLevel lastLevel;
    private long lastScanTick = Long.MIN_VALUE;
    private int lastPlayerRadius = -1;
    private ScanBounds lastCoverageBounds;
    private List<OctopathLocalLight> cachedLights = List.of();

    List<OctopathLocalLight> scan(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            int requestedPlayerRadius,
            int requestedInterval
    ) {
        int playerRadius = clamp(requestedPlayerRadius, 2, 48);
        int interval = clamp(requestedInterval, 1, 100);
        ScanBounds bounds = visibleBounds(level, player, camera, playerRadius);
        long gameTime = level.getGameTime();

        if (level != lastLevel
                || lastScanTick == Long.MIN_VALUE
                || playerRadius != lastPlayerRadius
                || lastCoverageBounds == null
                || !lastCoverageBounds.contains(bounds)
                || gameTime < lastScanTick
                || gameTime - lastScanTick >= interval) {
            List<OctopathLocalLight> previousLights = level == lastLevel
                    ? cachedLights
                    : List.of();
            cachedLights = findLights(level, player, bounds, previousLights);
            lastLevel = level;
            lastScanTick = gameTime;
            lastPlayerRadius = playerRadius;
            // Camera rotation changes the unprojected footprint by one block surprisingly often.
            // Keep a guard band so that does not bypass the normal scan interval every frame.
            lastCoverageBounds = bounds.expand(VIEWPORT_SCAN_GUARD);
        }
        return cachedLights;
    }

    void reset() {
        lastLevel = null;
        lastScanTick = Long.MIN_VALUE;
        lastPlayerRadius = -1;
        lastCoverageBounds = null;
        cachedLights = List.of();
    }

    private static ScanBounds visibleBounds(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            int playerRadius
    ) {
        int centerX = (int) Math.floor(player.getX());
        int centerY = (int) Math.floor(player.getY());
        int centerZ = (int) Math.floor(player.getZ());
        int minY = Math.max(level.getMinBuildHeight(), centerY - VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + VERTICAL_RADIUS);
        Matrix4f inverseViewProjection = camera.inverseViewProjection();
        BoundsBuilder visible = new BoundsBuilder();

        int[] sampleHeights = {minY, centerY, maxY};
        float[] corners = {-1.0F, 1.0F};
        for (int height : sampleHeights) {
            for (float ndcX : corners) {
                for (float ndcY : corners) {
                    includePlaneIntersection(
                            visible,
                            inverseViewProjection,
                            ndcX,
                            ndcY,
                            height + 0.5F);
                }
            }
        }

        ScanBounds playerBounds = ScanBounds.around(centerX, centerZ, playerRadius);
        if (visible.isEmpty()) {
            return playerBounds;
        }
        return visible.build(VIEWPORT_MARGIN).union(playerBounds);
    }

    private static void includePlaneIntersection(
            BoundsBuilder bounds,
            Matrix4f inverseViewProjection,
            float ndcX,
            float ndcY,
            float planeY
    ) {
        Vector3f near = unproject(inverseViewProjection, ndcX, ndcY, -1.0F);
        Vector3f far = unproject(inverseViewProjection, ndcX, ndcY, 1.0F);
        if (near == null || far == null) {
            return;
        }

        float directionY = far.y - near.y;
        if (Math.abs(directionY) < 0.00001F) {
            return;
        }
        float t = (planeY - near.y) / directionY;
        // The plane must be within the camera depth interval. Ignoring intersections outside that
        // interval prevents a nearly horizontal camera from producing an enormous scan area.
        if (!Float.isFinite(t) || t < -0.02F || t > 1.02F) {
            return;
        }
        float x = near.x + (far.x - near.x) * t;
        float z = near.z + (far.z - near.z) * t;
        if (Float.isFinite(x) && Float.isFinite(z)) {
            bounds.include(x, z);
        }
    }

    private static Vector3f unproject(
            Matrix4f inverseViewProjection,
            float ndcX,
            float ndcY,
            float ndcZ
    ) {
        Vector4f homogeneous = new Vector4f(ndcX, ndcY, ndcZ, 1.0F);
        inverseViewProjection.transform(homogeneous);
        if (Math.abs(homogeneous.w) < 0.00001F) {
            return null;
        }
        float inverseW = 1.0F / homogeneous.w;
        float x = homogeneous.x * inverseW;
        float y = homogeneous.y * inverseW;
        float z = homogeneous.z * inverseW;
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                ? new Vector3f(x, y, z)
                : null;
    }

    private static List<OctopathLocalLight> findLights(
            ClientLevel level,
            LocalPlayer player,
            ScanBounds bounds,
            List<OctopathLocalLight> previousLights
    ) {
        int centerY = (int) Math.floor(player.getY());
        int minY = Math.max(level.getMinBuildHeight(), centerY - VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + VERTICAL_RADIUS);
        double playerX = player.getX();
        double playerY = player.getY() + player.getBbHeight() * 0.45D;
        double playerZ = player.getZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        Map<Long, LightCluster> clusters = new HashMap<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    int emission = state.getLightEmission();
                    if (emission <= 0) {
                        continue;
                    }

                    double lightX = x + 0.5D;
                    double lightY = y + 0.5D;
                    double lightZ = z + 0.5D;
                    double distanceSquared = squaredDistance(
                            playerX,
                            playerY,
                            playerZ,
                            lightX,
                            lightY,
                            lightZ);
                    float score = (float) (emission * emission / (1.0D + distanceSquared * 0.10D));
                    Vector3f position = new Vector3f((float) lightX, (float) lightY, (float) lightZ);
                    long clusterKey = clusterKey(position);
                    LightCluster cluster = clusters.computeIfAbsent(clusterKey, LightCluster::new);
                    cluster.add(
                            position,
                            colorFor(state),
                            emission / 15.0F,
                            score);
                }
            }
        }

        List<OctopathLocalLight> candidates = new ArrayList<>(clusters.size());
        for (LightCluster cluster : clusters.values()) {
            candidates.add(cluster.toLight());
        }
        return selectStableLights(candidates, previousLights);
    }

    private static List<OctopathLocalLight> selectStableLights(
            List<OctopathLocalLight> candidates,
            List<OctopathLocalLight> previousLights
    ) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(SCORE_ORDER);
        Map<Long, OctopathLocalLight> byKey = new HashMap<>(candidates.size());
        for (OctopathLocalLight candidate : candidates) {
            byKey.put(candidate.stableKey(), candidate);
        }

        float retainThreshold = candidates.size() > MAX_LIGHTS
                ? candidates.get(MAX_LIGHTS - 1).score() * RETAIN_SCORE_FRACTION
                : 0.0F;
        List<OctopathLocalLight> selected = new ArrayList<>(Math.min(MAX_LIGHTS, candidates.size()));
        Set<Long> selectedKeys = new HashSet<>();
        // Preserve existing slot order whenever a source remains reasonably competitive. The
        // atlas cache is keyed by this list order, so stability here also prevents shadow tiles
        // from briefly being assigned to another lamp during a camera pan.
        for (OctopathLocalLight previous : previousLights) {
            if (selected.size() >= MAX_LIGHTS) {
                break;
            }
            OctopathLocalLight candidate = byKey.get(previous.stableKey());
            if (candidate != null
                    && candidate.score() >= retainThreshold
                    && selectedKeys.add(candidate.stableKey())) {
                selected.add(candidate);
            }
        }
        for (OctopathLocalLight candidate : candidates) {
            if (selected.size() >= MAX_LIGHTS) {
                break;
            }
            if (selectedKeys.add(candidate.stableKey())) {
                selected.add(candidate);
            }
        }
        return List.copyOf(selected);
    }

    private static long clusterKey(Vector3f position) {
        int cellX = (int) Math.floor(position.x / CLUSTER_CELL_SIZE);
        int cellY = (int) Math.floor(position.y / CLUSTER_CELL_SIZE);
        int cellZ = (int) Math.floor(position.z / CLUSTER_CELL_SIZE);
        return BlockPos.asLong(cellX, cellY, cellZ);
    }

    private static Vector3f colorFor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        if (path.contains("soul")) {
            return new Vector3f(0.26F, 0.78F, 1.00F);
        }
        if (path.contains("redstone")) {
            return new Vector3f(1.00F, 0.16F, 0.08F);
        }
        if (path.contains("sea_lantern") || path.contains("end_rod")) {
            return new Vector3f(0.54F, 0.90F, 1.00F);
        }
        if (path.contains("lava") || path.contains("fire") || path.contains("magma")) {
            return new Vector3f(1.00F, 0.24F, 0.05F);
        }
        if (path.contains("glowstone") || path.contains("shroomlight")) {
            return new Vector3f(1.00F, 0.65F, 0.23F);
        }
        return new Vector3f(1.00F, 0.48F, 0.16F);
    }

    private static double squaredDistance(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz
    ) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScanBounds(int minX, int maxX, int minZ, int maxZ) {
        private ScanBounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid local-light scan bounds");
            }
        }

        private static ScanBounds around(int centerX, int centerZ, int radius) {
            return new ScanBounds(
                    centerX - radius,
                    centerX + radius,
                    centerZ - radius,
                    centerZ + radius);
        }

        private ScanBounds union(ScanBounds other) {
            return new ScanBounds(
                    Math.min(minX, other.minX),
                    Math.max(maxX, other.maxX),
                    Math.min(minZ, other.minZ),
                    Math.max(maxZ, other.maxZ));
        }

        private ScanBounds expand(int margin) {
            return new ScanBounds(
                    minX - margin,
                    maxX + margin,
                    minZ - margin,
                    maxZ + margin);
        }

        private boolean contains(ScanBounds other) {
            return minX <= other.minX
                    && maxX >= other.maxX
                    && minZ <= other.minZ
                    && maxZ >= other.maxZ;
        }
    }

    private static final class BoundsBuilder {
        private float minX = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        private void include(float x, float z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        private boolean isEmpty() {
            return !Float.isFinite(minX)
                    || !Float.isFinite(maxX)
                    || !Float.isFinite(minZ)
                    || !Float.isFinite(maxZ);
        }

        private ScanBounds build(int margin) {
            return new ScanBounds(
                    (int) Math.floor(minX) - margin,
                    (int) Math.ceil(maxX) + margin,
                    (int) Math.floor(minZ) - margin,
                    (int) Math.ceil(maxZ) + margin);
        }
    }

    private static final class LightCluster {
        private final long stableKey;
        private float positionWeight;
        private float weightedX;
        private float weightedY;
        private float weightedZ;
        private float weightedRed;
        private float weightedGreen;
        private float weightedBlue;
        private float totalPower;
        private float strongestPower;
        private float score;

        private LightCluster(long stableKey) {
            this.stableKey = stableKey;
        }

        private void add(Vector3f position, Vector3f color, float power, float sourceScore) {
            float weight = Math.max(0.05F, power);
            positionWeight += weight;
            weightedX += position.x * weight;
            weightedY += position.y * weight;
            weightedZ += position.z * weight;
            weightedRed += color.x * weight;
            weightedGreen += color.y * weight;
            weightedBlue += color.z * weight;
            totalPower += power;
            strongestPower = Math.max(strongestPower, power);
            score += sourceScore;
        }

        private OctopathLocalLight toLight() {
            float inverseWeight = 1.0F / Math.max(positionWeight, 0.00001F);
            // A cluster should acknowledge multiple lamps, but avoid treating every adjacent
            // glowstone block as an independent full-intensity additive post light.
            float clusteredPower = strongestPower + (totalPower - strongestPower) * 0.16F;
            return new OctopathLocalLight(
                    new Vector3f(
                            weightedX * inverseWeight,
                            weightedY * inverseWeight,
                            weightedZ * inverseWeight),
                    new Vector3f(
                            weightedRed * inverseWeight,
                            weightedGreen * inverseWeight,
                            weightedBlue * inverseWeight),
                    Math.min(2.0F, clusteredPower),
                    score,
                    stableKey);
        }
    }
}
