package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds exposed water in the camera's actual world footprint and condenses it into a small set of
 * stable mist anchors. The shader can then use reconstructed world position for a safe
 * water/shore mask without guessing material type from the final colour target.
 */
final class OctopathWaterMistScanner {
    static final int MAX_MISTS = 12;

    private static final int CELL_SIZE = 5;
    private static final int VIEWPORT_MARGIN = 2;
    private static final int PLAYER_HEIGHT_RADIUS = 12;
    private static final int SEA_LEVEL_BELOW = 4;
    private static final int SEA_LEVEL_ABOVE = 4;
    private static final int MAX_COLUMN_SAMPLES = 4_096;

    private ClientLevel lastLevel;
    private long lastScanTick = Long.MIN_VALUE;
    private int lastInterval = -1;
    private float lastPatchRadius = -1.0F;
    private ScanVolume lastVolume;
    private List<OctopathWaterMist> cachedMists = List.of();

    List<OctopathWaterMist> scan(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            int requestedIntervalTicks,
            float requestedPatchRadius
    ) {
        int interval = clamp(requestedIntervalTicks, 1, 100);
        float patchRadius = clamp(requestedPatchRadius, 2.0F, 12.0F);
        long gameTime = level.getGameTime();

        // Minecraft's own underwater fog owns this composition. Keeping the HD-2D shore layer
        // off here also prevents a water anchor from whitening the player's submerged view.
        if (isCameraUnderwater(level, player)) {
            resetCache();
            return List.of();
        }

        ScanVolume volume = visibleVolume(level, player, camera);
        if (level != lastLevel
                || lastScanTick == Long.MIN_VALUE
                || interval != lastInterval
                || Float.compare(patchRadius, lastPatchRadius) != 0
                || !volume.equals(lastVolume)
                || gameTime < lastScanTick
                || gameTime - lastScanTick >= interval) {
            cachedMists = findMists(level, player, camera, volume, patchRadius);
            lastLevel = level;
            lastScanTick = gameTime;
            lastInterval = interval;
            lastPatchRadius = patchRadius;
            lastVolume = volume;
        }
        return cachedMists;
    }

    void reset() {
        resetCache();
    }

    private void resetCache() {
        lastLevel = null;
        lastScanTick = Long.MIN_VALUE;
        lastInterval = -1;
        lastPatchRadius = -1.0F;
        lastVolume = null;
        cachedMists = List.of();
    }

    private static boolean isCameraUnderwater(ClientLevel level, LocalPlayer player) {
        BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.hasChunkAt(eye) && level.getFluidState(eye).is(FluidTags.WATER);
    }

    private static ScanVolume visibleVolume(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera
    ) {
        int centerX = (int) Math.floor(player.getX());
        int centerY = (int) Math.floor(player.getY());
        int centerZ = (int) Math.floor(player.getZ());
        int seaLevel = level.getSeaLevel();
        int minimumY = Math.max(
                level.getMinBuildHeight(),
                Math.min(centerY - PLAYER_HEIGHT_RADIUS, seaLevel - SEA_LEVEL_BELOW));
        int maximumY = Math.min(
                level.getMaxBuildHeight() - 1,
                Math.max(centerY + PLAYER_HEIGHT_RADIUS, seaLevel + SEA_LEVEL_ABOVE));

        Matrix4f inverseViewProjection = camera.inverseViewProjection();
        BoundsBuilder visible = new BoundsBuilder();
        int[] sampleHeights = {minimumY, seaLevel, centerY, maximumY};
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

        // A conservative fallback keeps the effect functional during a transient invalid camera
        // matrix (for example while a world is being entered) without scanning a whole dimension.
        ScanBounds bounds = visible.isEmpty()
                ? ScanBounds.around(centerX, centerZ, 16)
                : visible.build(VIEWPORT_MARGIN);
        return new ScanVolume(bounds, minimumY, maximumY, centerY, seaLevel);
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

    private static List<OctopathWaterMist> findMists(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            ScanVolume volume,
            float requestedPatchRadius
    ) {
        ScanBounds bounds = volume.bounds();
        int sampleStep = sampleStep(bounds);
        int cellSize = CELL_SIZE * sampleStep;
        Map<CellKey, PatchAccumulator> cells = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

        for (int z = bounds.minZ(); z <= bounds.maxZ(); z += sampleStep) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x += sampleStep) {
                for (int y = volume.minimumY(); y <= volume.maximumY(); y++) {
                    if (!volume.includesWaterSearchHeight(y)) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }

                    FluidState fluid = level.getFluidState(cursor);
                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }

                    above.set(x, y + 1, z);
                    if (y + 1 >= level.getMaxBuildHeight()
                            || !level.getBlockState(above).isAir()
                            || level.getFluidState(above).is(FluidTags.WATER)) {
                        continue;
                    }

                    float surfaceHeight = fluid.getHeight(level, cursor);
                    if (surfaceHeight <= 0.02F) {
                        continue;
                    }
                    int cellX = Math.floorDiv(x, cellSize);
                    int cellY = Math.floorDiv(y, 4);
                    int cellZ = Math.floorDiv(z, cellSize);
                    cells.computeIfAbsent(new CellKey(cellX, cellY, cellZ), ignored -> new PatchAccumulator())
                            .add(x + 0.5F, y + surfaceHeight, z + 0.5F);
                }
            }
        }

        if (cells.isEmpty()) {
            return List.of();
        }

        float minimumRadius = Math.max(requestedPatchRadius, cellSize * 0.90F);
        Matrix4f viewProjection = camera.viewProjection();
        List<Candidate> candidates = new ArrayList<>(cells.size());
        for (PatchAccumulator cell : cells.values()) {
            OctopathWaterMist mist = cell.toMist(minimumRadius, cellSize, sampleStep);
            boolean visible = isVisible(viewProjection, mist.position());
            float playerDistance = horizontalDistanceSquared(
                    mist.position(),
                    (float) player.getX(),
                    (float) player.getZ());
            float score = mist.coverage() * 2.0F
                    + (visible ? 10.0F : 0.0F)
                    + 1.0F / (1.0F + playerDistance * 0.035F);
            candidates.add(new Candidate(mist, score));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        return selectSpacedMists(candidates);
    }

    private static List<OctopathWaterMist> selectSpacedMists(List<Candidate> candidates) {
        List<OctopathWaterMist> selected = new ArrayList<>(MAX_MISTS);
        for (Candidate candidate : candidates) {
            if (selected.size() >= MAX_MISTS) {
                break;
            }
            if (isSeparated(candidate.mist(), selected)) {
                selected.add(candidate.mist());
            }
        }
        // A narrow river can legitimately produce adjacent cells. Use them after the broad
        // coverage pass rather than leaving an unnecessarily sparse shore layer.
        if (selected.size() < MAX_MISTS) {
            for (Candidate candidate : candidates) {
                if (selected.size() >= MAX_MISTS) {
                    break;
                }
                if (!selected.contains(candidate.mist())) {
                    selected.add(candidate.mist());
                }
            }
        }
        return List.copyOf(selected);
    }

    private static boolean isSeparated(OctopathWaterMist candidate, List<OctopathWaterMist> selected) {
        for (OctopathWaterMist existing : selected) {
            float minimumDistance = (candidate.radius() + existing.radius()) * 0.62F;
            if (horizontalDistanceSquared(candidate.position(), existing.position().x, existing.position().z)
                    < minimumDistance * minimumDistance) {
                return false;
            }
        }
        return true;
    }

    private static boolean isVisible(Matrix4f viewProjection, Vector3f position) {
        Vector4f clip = new Vector4f(position.x, position.y, position.z, 1.0F);
        viewProjection.transform(clip);
        if (Math.abs(clip.w) < 0.00001F) {
            return false;
        }
        float inverseW = 1.0F / clip.w;
        float ndcX = clip.x * inverseW;
        float ndcY = clip.y * inverseW;
        float ndcZ = clip.z * inverseW;
        return ndcX >= -1.08F && ndcX <= 1.08F
                && ndcY >= -1.08F && ndcY <= 1.08F
                && ndcZ >= -1.08F && ndcZ <= 1.08F;
    }

    private static int sampleStep(ScanBounds bounds) {
        long columnCount = (long) bounds.width() * bounds.depth();
        if (columnCount <= MAX_COLUMN_SAMPLES) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(Math.sqrt(columnCount / (double) MAX_COLUMN_SAMPLES)));
    }

    private static float horizontalDistanceSquared(Vector3f position, float x, float z) {
        float deltaX = position.x - x;
        float deltaZ = position.z - z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * The sea-level band and the player-height band may be far apart in a mountain view. Keep
     * them in one camera-footprint volume for cache comparison, but do not perform material reads
     * through every intermediate cave layer.
     */
    private record ScanVolume(
            ScanBounds bounds,
            int minimumY,
            int maximumY,
            int playerY,
            int seaLevel
    ) {
        private boolean includesWaterSearchHeight(int y) {
            return Math.abs(y - playerY) <= PLAYER_HEIGHT_RADIUS
                    || (y >= seaLevel - SEA_LEVEL_BELOW && y <= seaLevel + SEA_LEVEL_ABOVE);
        }
    }

    private record ScanBounds(int minX, int maxX, int minZ, int maxZ) {
        private ScanBounds {
            if (minX > maxX || minZ > maxZ) {
                throw new IllegalArgumentException("Invalid water-mist scan bounds");
            }
        }

        private static ScanBounds around(int centerX, int centerZ, int radius) {
            return new ScanBounds(
                    centerX - radius,
                    centerX + radius,
                    centerZ - radius,
                    centerZ + radius);
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }
    }

    private record CellKey(int x, int y, int z) {
    }

    private record Candidate(OctopathWaterMist mist, float score) {
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

    private static final class PatchAccumulator {
        private float totalX;
        private float totalY;
        private float totalZ;
        private int samples;

        private void add(float x, float y, float z) {
            totalX += x;
            totalY += y;
            totalZ += z;
            samples++;
        }

        private OctopathWaterMist toMist(float radius, int cellSize, int sampleStep) {
            float inverseSamples = 1.0F / Math.max(1, samples);
            float maximumSamples = Math.max(1.0F, (cellSize / (float) sampleStep) * (cellSize / (float) sampleStep));
            // A one-block-wide river should still receive a visible, but restrained, mist
            // anchor. Square-root coverage keeps broad water bodies stronger without making
            // narrow streams mathematically disappear inside a five-by-five cluster cell.
            float sampledCoverage = Math.min(1.0F, samples / maximumSamples);
            float coverage = 0.35F + 0.65F * (float) Math.sqrt(sampledCoverage);
            return new OctopathWaterMist(
                    new Vector3f(totalX * inverseSamples, totalY * inverseSamples, totalZ * inverseSamples),
                    radius,
                    coverage);
        }
    }
}
