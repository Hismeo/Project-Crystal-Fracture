package org.hismeo.fractureclient.client.render.octopath;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a compact height-field and actor proxy around the selected glowing block. The six-face
 * renderer consumes this instead of trying to intercept Minecraft's private block mesh pass.
 */
final class OctopathPointLightShadowScanner {
    private static final int MAX_TERRAIN_COLUMNS = 1_024;
    private static final int MAX_ENTITY_CASTERS = 24;
    private static final float MIN_RANGE = 2.0F;
    private static final float MAX_RANGE = 12.0F;

    OctopathPointLightShadowFrame scan(
            ClientLevel level,
            LocalPlayer player,
            OctopathLocalLight light,
            float requestedRange
    ) {
        float range = clamp(requestedRange, MIN_RANGE, MAX_RANGE);
        Vector3f lightPosition = light.position();
        List<TerrainSample> terrain = scanTerrain(level, lightPosition, range);
        List<OctopathDirectionalShadowCaster> casters = new ArrayList<>(terrain.size() + MAX_ENTITY_CASTERS);
        casters.addAll(buildTerrainCasters(terrain, lightPosition, range));
        casters.addAll(scanEntityCasters(level, player, lightPosition, range));
        return new OctopathPointLightShadowFrame(lightPosition, range, casters);
    }

    private static List<TerrainSample> scanTerrain(
            ClientLevel level,
            Vector3f lightPosition,
            float range
    ) {
        int radius = Math.max(2, (int) Math.ceil(range));
        int centerX = (int) Math.floor(lightPosition.x);
        int centerZ = (int) Math.floor(lightPosition.z);
        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int probeY = clamp(level.getSeaLevel(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        float scanRadiusSquared = (range + 1.0F) * (range + 1.0F);
        List<TerrainSample> samples = new ArrayList<>();
        for (int z = minZ; z <= maxZ && samples.size() < MAX_TERRAIN_COLUMNS; z++) {
            for (int x = minX; x <= maxX && samples.size() < MAX_TERRAIN_COLUMNS; x++) {
                float blockCenterX = x + 0.5F;
                float blockCenterZ = z + 0.5F;
                float deltaX = blockCenterX - lightPosition.x;
                float deltaZ = blockCenterZ - lightPosition.z;
                if (deltaX * deltaX + deltaZ * deltaZ > scanRadiusSquared) {
                    continue;
                }
                probe.set(x, probeY, z);
                if (!level.hasChunkAt(probe)) {
                    continue;
                }
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (topY <= level.getMinBuildHeight()) {
                    continue;
                }
                int opaqueTopY = OctopathShadowOcclusion.opaqueSurfaceTopY(
                        level,
                        probe,
                        x,
                        z,
                        topY);
                if (opaqueTopY == OctopathShadowOcclusion.NO_OPAQUE_SURFACE) {
                    continue;
                }
                // The glowing block itself is the origin of the point light, not an opaque
                // blocker placed around that origin.
                if (Math.abs(blockCenterX - lightPosition.x) <= 0.55F
                        && Math.abs(blockCenterZ - lightPosition.z) <= 0.55F) {
                    continue;
                }
                samples.add(new TerrainSample(blockCenterX, blockCenterZ, opaqueTopY));
            }
        }
        return samples;
    }

    private static List<OctopathDirectionalShadowCaster> buildTerrainCasters(
            List<TerrainSample> samples,
            Vector3f lightPosition,
            float range
    ) {
        if (samples.isEmpty()) {
            return List.of();
        }
        List<OctopathDirectionalShadowCaster> casters = new ArrayList<>(samples.size());
        float radialLimitSquared = (range + 1.2F) * (range + 1.2F);
        for (TerrainSample sample : samples) {
            float deltaX = sample.x() - lightPosition.x;
            float deltaZ = sample.z() - lightPosition.z;
            if (deltaX * deltaX + deltaZ * deltaZ > radialLimitSquared) {
                continue;
            }
            // A directional map can safely use a solid height-field column. A point light
            // cannot: those fake columns become giant vertical walls and project an obvious
            // wedge across the ground. Represent only the real top block layer instead. It
            // still lets steps, banks and tree canopies occlude a lamp, without inventing a
            // deep blocker below every heightmap sample.
            float height = 0.90F;
            casters.add(OctopathDirectionalShadowCaster.cube(
                    sample.x(),
                    sample.topY() - height * 0.5F,
                    sample.z(),
                    1.0F,
                    height,
                    1.0F));
        }
        return List.copyOf(casters);
    }

    private static List<OctopathDirectionalShadowCaster> scanEntityCasters(
            ClientLevel level,
            LocalPlayer player,
            Vector3f lightPosition,
            float range
    ) {
        float rangeSquared = (range + 0.8F) * (range + 0.8F);
        List<OctopathDirectionalShadowCaster> casters = new ArrayList<>();
        appendEntityCaster(casters, player, lightPosition, rangeSquared, 0.0F);
        for (Entity entity : level.entitiesForRendering()) {
            if (casters.size() >= MAX_ENTITY_CASTERS) {
                break;
            }
            if (entity != player) {
                appendEntityCaster(casters, entity, lightPosition, rangeSquared, 0.0F);
            }
        }
        return List.copyOf(casters);
    }

    private static void appendEntityCaster(
            List<OctopathDirectionalShadowCaster> casters,
            Entity entity,
            Vector3f lightPosition,
            float rangeSquared,
            float partialTick
    ) {
        if (!(entity instanceof LivingEntity)
                || entity.isRemoved()
                || entity.isInvisible()
                || entity.isSpectator()
                || casters.size() >= MAX_ENTITY_CASTERS) {
            return;
        }
        Vec3 position = entity.getPosition(partialTick);
        // A full-height Minecraft AABB is correct for sunlight, but under a nearby point lamp
        // it projects into a long, hard wedge that reads as a fan from the HD-2D camera. Use a
        // deliberately compact lower-body proxy here: it keeps the actor anchored to the ground
        // while leaving the long silhouette work to the directional sun shadow map.
        float width = Math.max(0.18F, entity.getBbWidth() * 0.78F);
        float height = Math.max(0.45F, Math.min(1.05F, entity.getBbHeight() * 0.58F));
        float centerX = (float) position.x;
        float centerY = (float) position.y + height * 0.5F;
        float centerZ = (float) position.z;
        float deltaX = centerX - lightPosition.x;
        float deltaY = centerY - lightPosition.y;
        float deltaZ = centerZ - lightPosition.z;
        if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > rangeSquared) {
            return;
        }
        casters.add(OctopathDirectionalShadowCaster.cube(
                centerX,
                centerY,
                centerZ,
                width,
                height,
                width));
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

    private record TerrainSample(float x, float z, int topY) {
    }
}
