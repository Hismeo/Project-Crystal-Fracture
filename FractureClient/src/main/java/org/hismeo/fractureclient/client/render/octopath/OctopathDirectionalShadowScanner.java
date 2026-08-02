package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the CPU side of a small, stable directional shadow map.
 *
 * <p>This is purposefully a proxy scene, not a second Minecraft world renderer. Terrain is a
 * bounded grid of height-map cuboids and actors are simple interpolated living-entity boxes. It
 * gives the shadow map meaningful blockers while avoiding another full chunk mesh build or a
 * dependency on Minecraft's private render graph.</p>
 */
final class OctopathDirectionalShadowScanner {
    static final int MIN_TERRAIN_COLUMNS = 49;
    static final int MAX_TERRAIN_COLUMNS = 4_096;
    static final int MAX_ENTITY_CASTERS = 24;

    private static final float MIN_WORLD_EXTENT = 8.0F;
    private static final float MAX_WORLD_EXTENT = 128.0F;
    private static final int MIN_ENTITY_CANDIDATES = MAX_ENTITY_CASTERS * 2;
    private static final float WORLD_CENTER_Y_SNAP = 4.0F;
    private static final float MIN_TERRAIN_DEPTH = 8.0F;
    private static final float TERRAIN_DEPTH_MARGIN = 8.0F;
    private static final float LIGHT_VOLUME_PADDING = 12.0F;
    private static final float HORIZON_ELEVATION_FLOOR = 0.42F;

    private ClientLevel cachedTerrainLevel;
    private Grid cachedTerrainGrid;
    private long cachedTerrainGameTime = Long.MIN_VALUE;
    private List<OctopathDirectionalShadowCaster> cachedTerrainCasters = List.of();

    /**
     * Builds one light-space frame. {@code desiredWorldExtent} is the desired half-width in
     * blocks around the player, while {@code requestedMaxTerrainColumns} is a hard budget for
     * height-map reads and proxy cubes.
     */
    OctopathDirectionalShadowFrame scan(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            float partialTick,
            float desiredWorldExtent,
            int requestedMaxTerrainColumns,
            int terrainScanIntervalTicks,
            int shadowMapResolution
    ) {
        float worldExtent = clamp(desiredWorldExtent, MIN_WORLD_EXTENT, MAX_WORLD_EXTENT);
        int maxTerrainColumns = clamp(
                requestedMaxTerrainColumns,
                MIN_TERRAIN_COLUMNS,
                MAX_TERRAIN_COLUMNS);
        Grid grid = buildGrid(player, worldExtent, maxTerrainColumns);
        Vector3f worldCenter = new Vector3f(
                grid.centerX(),
                snap((float) player.getY(), WORLD_CENTER_Y_SNAP),
                grid.centerZ());
        Vector3f lightDirection = incomingSunDirection(level, partialTick);

        List<OctopathDirectionalShadowCaster> terrainCasters = terrainCasters(
                level,
                grid,
                Math.max(1, terrainScanIntervalTicks));
        List<OctopathDirectionalShadowCaster> entityCasters = scanVisibleEntities(
                level,
                player,
                camera,
                clamp(partialTick, 0.0F, 1.0F));

        LightMatrices matrices = createLightMatrices(
                worldCenter,
                lightDirection,
                worldExtent,
                grid.worldSpan(),
                shadowMapResolution);
        return new OctopathDirectionalShadowFrame(
                matrices.worldCenter(),
                lightDirection,
                matrices.view(),
                matrices.projection(),
                matrices.viewProjection(),
                worldExtent,
                grid.cellSize(),
                terrainCasters,
                entityCasters);
    }

    void reset() {
        cachedTerrainLevel = null;
        cachedTerrainGrid = null;
        cachedTerrainGameTime = Long.MIN_VALUE;
        cachedTerrainCasters = List.of();
    }

    private List<OctopathDirectionalShadowCaster> terrainCasters(
            ClientLevel level,
            Grid grid,
            int intervalTicks
    ) {
        long gameTime = level.getGameTime();
        boolean unchangedGrid = cachedTerrainLevel == level && grid.equals(cachedTerrainGrid);
        boolean cacheFresh = unchangedGrid
                && gameTime >= cachedTerrainGameTime
                && gameTime - cachedTerrainGameTime < intervalTicks;
        if (cacheFresh) {
            return cachedTerrainCasters;
        }

        List<TerrainSample> terrain = scanTerrain(level, grid);
        cachedTerrainLevel = level;
        cachedTerrainGrid = grid;
        cachedTerrainGameTime = gameTime;
        cachedTerrainCasters = buildTerrainCasters(level, terrain, grid.cellSize());
        return cachedTerrainCasters;
    }

    private static Grid buildGrid(LocalPlayer player, float worldExtent, int maxTerrainColumns) {
        int desiredDiameter = Math.max(1, (int) Math.ceil(worldExtent * 2.0F));
        int maxSide = Math.max(1, (int) Math.floor(Math.sqrt(maxTerrainColumns)));
        int cellSize = Math.max(1, (int) Math.ceil(desiredDiameter / (double) maxSide));
        int side = Math.max(1, (int) Math.ceil(desiredDiameter / (double) cellSize));
        // The ceiling operations above can only decrease the final number of cells below the
        // requested budget. Keep this guard in case either clamp changes in the future.
        while ((long) side * side > maxTerrainColumns) {
            cellSize++;
            side = Math.max(1, (int) Math.ceil(desiredDiameter / (double) cellSize));
        }

        int playerCellX = Math.floorDiv((int) Math.floor(player.getX()), cellSize);
        int playerCellZ = Math.floorDiv((int) Math.floor(player.getZ()), cellSize);
        int minCellX = playerCellX - side / 2;
        int minCellZ = playerCellZ - side / 2;
        int minX = minCellX * cellSize;
        int minZ = minCellZ * cellSize;
        return new Grid(minX, minZ, side, cellSize);
    }

    private static List<TerrainSample> scanTerrain(ClientLevel level, Grid grid) {
        List<TerrainSample> samples = new ArrayList<>(grid.cellCount());
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int probeY = clamp(level.getSeaLevel(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        for (int row = 0; row < grid.side(); row++) {
            int sampleZ = grid.minZ() + row * grid.cellSize() + grid.cellSize() / 2;
            for (int column = 0; column < grid.side(); column++) {
                int sampleX = grid.minX() + column * grid.cellSize() + grid.cellSize() / 2;
                probe.set(sampleX, probeY, sampleZ);
                // Heightmap queries do not force a chunk load, but this explicit guard also
                // avoids emitting a false min-height wall during chunk-edge streaming.
                if (!level.hasChunkAt(probe)) {
                    continue;
                }
                // Include leaves here: a tree canopy must enter the light-space depth map or
                // its shadow turns into an implausible thin trunk line.
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sampleX, sampleZ);
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }
                int opaqueTopY = OctopathShadowOcclusion.opaqueSurfaceTopY(
                        level,
                        probe,
                        sampleX,
                        sampleZ,
                        topY);
                // Water is a receiver, not an opaque solar caster. More importantly, the helper
                // walks past glass/panes so they no longer turn into an artificial solid wall in
                // the map: sun shafts can continue through windows and glass railings.
                if (opaqueTopY != OctopathShadowOcclusion.NO_OPAQUE_SURFACE) {
                    samples.add(new TerrainSample(sampleX, sampleZ, opaqueTopY));
                }
            }
        }
        return samples;
    }

    private static List<OctopathDirectionalShadowCaster> buildTerrainCasters(
            ClientLevel level,
            List<TerrainSample> samples,
            int cellSize
    ) {
        if (samples.isEmpty()) {
            return List.of();
        }
        int minimumTop = Integer.MAX_VALUE;
        for (TerrainSample sample : samples) {
            minimumTop = Math.min(minimumTop, sample.topY());
        }
        int baseY = Math.max(
                level.getMinBuildHeight(),
                (int) Math.floor(minimumTop - Math.max(MIN_TERRAIN_DEPTH, cellSize * 2.0F + TERRAIN_DEPTH_MARGIN)));
        List<OctopathDirectionalShadowCaster> casters = new ArrayList<>(samples.size());
        for (TerrainSample sample : samples) {
            float height = Math.max(0.10F, sample.topY() - baseY);
            casters.add(OctopathDirectionalShadowCaster.cube(
                    sample.x(),
                    baseY + height * 0.5F,
                    sample.z(),
                    cellSize,
                    height,
                    cellSize));
        }
        return List.copyOf(casters);
    }

    private static List<OctopathDirectionalShadowCaster> scanVisibleEntities(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            float partialTick
    ) {
        Matrix4f viewProjection = camera.viewProjection();
        Set<Integer> visitedIds = new HashSet<>();
        List<EntityCandidate> candidates = new ArrayList<>(MIN_ENTITY_CANDIDATES);
        addEntityCandidate(candidates, visitedIds, player, player, viewProjection, partialTick);
        for (Entity entity : level.entitiesForRendering()) {
            addEntityCandidate(candidates, visitedIds, player, entity, viewProjection, partialTick);
        }
        candidates.sort(Comparator.comparingDouble(EntityCandidate::score).reversed());

        List<OctopathDirectionalShadowCaster> casters = new ArrayList<>(Math.min(MAX_ENTITY_CASTERS, candidates.size()));
        for (EntityCandidate candidate : candidates) {
            if (casters.size() >= MAX_ENTITY_CASTERS) {
                break;
            }
            casters.add(OctopathDirectionalShadowCaster.cube(
                    candidate.center().x,
                    candidate.center().y,
                    candidate.center().z,
                    candidate.width(),
                    candidate.height(),
                    candidate.depth()));
        }
        return List.copyOf(casters);
    }

    private static void addEntityCandidate(
            List<EntityCandidate> candidates,
            Set<Integer> visitedIds,
            LocalPlayer player,
            Entity entity,
            Matrix4f viewProjection,
            float partialTick
    ) {
        if (!(entity instanceof LivingEntity)
                || entity.isRemoved()
                || entity.isInvisible()
                || entity.isSpectator()
                || !visitedIds.add(entity.getId())) {
            return;
        }

        Vec3 position = entity.getPosition(partialTick);
        float width = Math.max(0.15F, entity.getBbWidth());
        float height = Math.max(0.15F, entity.getBbHeight());
        float depth = width;
        Vector3f center = new Vector3f(
                (float) position.x,
                (float) position.y + height * 0.5F,
                (float) position.z);
        if (!isFinite(center)) {
            return;
        }

        boolean visible = isVisible(viewProjection, center, width * 0.5F, height * 0.5F, depth * 0.5F);
        // The local player anchors the HD-2D composition. Retain their caster through one-frame
        // camera transitions even if the orthographic frustum reports a transient miss.
        if (!visible && entity != player) {
            return;
        }

        float deltaX = center.x - (float) player.getX();
        float deltaZ = center.z - (float) player.getZ();
        float distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
        float score = entity == player ? 10_000.0F : 100.0F;
        score += visible ? 25.0F : 0.0F;
        score += 12.0F / (1.0F + distanceSquared * 0.08F);
        score += Math.min(4.0F, width + height * 0.15F);
        candidates.add(new EntityCandidate(center, width, height, depth, score));
    }

    private static LightMatrices createLightMatrices(
            Vector3f worldCenter,
            Vector3f lightDirection,
            float requestedWorldExtent,
            float actualWorldSpan,
            int requestedResolution
    ) {
        float sceneHalfSpan = Math.max(requestedWorldExtent, actualWorldSpan * 0.5F);
        // A cube in world space projects to at most sqrt(2) times its half width in a rotated
        // light view. The extra vertical band covers the heightfield and elevated actors without
        // re-fitting the projection every frame (which would make the map shimmer).
        float lightHalfExtent = sceneHalfSpan * 2.15F + LIGHT_VOLUME_PADDING;
        float eyeDistance = lightHalfExtent * 2.25F;
        Vector3f up = Math.abs(lightDirection.y) > 0.92F
                ? new Vector3f(0.0F, 0.0F, 1.0F)
                : new Vector3f(0.0F, 1.0F, 0.0F);
        int resolution = clamp(requestedResolution, 256, 2_048);
        Vector3f snappedWorldCenter = snapLightSpaceCenter(
                worldCenter,
                lightDirection,
                up,
                lightHalfExtent * 2.0F / resolution);
        Vector3f eye = new Vector3f(lightDirection).mul(-eyeDistance).add(snappedWorldCenter);
        Matrix4f view = new Matrix4f().lookAt(eye, snappedWorldCenter, up);
        Matrix4f projection = new Matrix4f().ortho(
                -lightHalfExtent,
                lightHalfExtent,
                -lightHalfExtent,
                lightHalfExtent,
                0.10F,
                eyeDistance + lightHalfExtent * 2.50F);
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        return new LightMatrices(snappedWorldCenter, view, projection, viewProjection);
    }

    private static Vector3f snapLightSpaceCenter(
            Vector3f worldCenter,
            Vector3f lightDirection,
            Vector3f upHint,
            float texelWorldSize
    ) {
        if (!Float.isFinite(texelWorldSize) || texelWorldSize <= 0.00001F) {
            return new Vector3f(worldCenter);
        }
        Vector3f lightRight = new Vector3f(lightDirection).cross(upHint).normalize();
        Vector3f lightUp = new Vector3f(lightRight).cross(lightDirection).normalize();
        float centerRight = worldCenter.dot(lightRight);
        float centerUp = worldCenter.dot(lightUp);
        float snappedRight = nearestMultiple(centerRight, texelWorldSize);
        float snappedUp = nearestMultiple(centerUp, texelWorldSize);
        return new Vector3f(worldCenter)
                .add(lightRight.mul(snappedRight - centerRight))
                .add(lightUp.mul(snappedUp - centerUp));
    }

    /**
     * Incoming sky-light direction. Noon is nearly vertical; near the horizon the vertical
     * component is clamped so the intentionally low-resolution proxy map never produces an
     * unbounded, screen-spanning sliver. This follows Minecraft's single-axis celestial motion
     * instead of injecting a permanent diagonal component: a fixed artistic Z offset made
     * small terrain blockers paint arbitrary slashes across otherwise flat paths.
     */
    private static Vector3f incomingSunDirection(ClientLevel level, float partialTick) {
        float sunAngle = level.getSunAngle(partialTick);
        float horizontalX = -((float) Math.sin(sunAngle));
        float vertical = -Math.max(HORIZON_ELEVATION_FLOOR, Math.abs((float) Math.cos(sunAngle)));
        Vector3f direction = new Vector3f(horizontalX, vertical, 0.0F);
        if (direction.lengthSquared() < 0.00001F) {
            return new Vector3f(-0.45F, -0.78F, 0.43F).normalize();
        }
        return direction.normalize();
    }

    private static boolean isVisible(
            Matrix4f viewProjection,
            Vector3f center,
            float halfWidth,
            float halfHeight,
            float halfDepth
    ) {
        for (int xSign = -1; xSign <= 1; xSign += 2) {
            for (int ySign = -1; ySign <= 1; ySign += 2) {
                for (int zSign = -1; zSign <= 1; zSign += 2) {
                    if (isVisible(viewProjection,
                            center.x + halfWidth * xSign,
                            center.y + halfHeight * ySign,
                            center.z + halfDepth * zSign)) {
                        return true;
                    }
                }
            }
        }
        return isVisible(viewProjection, center.x, center.y, center.z);
    }

    private static boolean isVisible(Matrix4f viewProjection, float x, float y, float z) {
        Vector4f clip = new Vector4f(x, y, z, 1.0F);
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

    private static float snap(float value, float increment) {
        return (float) Math.floor(value / increment) * increment + increment * 0.5F;
    }

    private static float nearestMultiple(float value, float increment) {
        return (float) (Math.round(value / (double) increment) * increment);
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x) && Float.isFinite(value.y) && Float.isFinite(value.z);
    }

    private record Grid(int minX, int minZ, int side, int cellSize) {
        private int cellCount() {
            return side * side;
        }

        private float worldSpan() {
            return side * (float) cellSize;
        }

        private float centerX() {
            return minX + worldSpan() * 0.5F;
        }

        private float centerZ() {
            return minZ + worldSpan() * 0.5F;
        }
    }

    private record TerrainSample(float x, float z, int topY) {
    }

    private record EntityCandidate(Vector3f center, float width, float height, float depth, float score) {
    }

    private record LightMatrices(
            Vector3f worldCenter,
            Matrix4f view,
            Matrix4f projection,
            Matrix4f viewProjection
    ) {
    }
}
