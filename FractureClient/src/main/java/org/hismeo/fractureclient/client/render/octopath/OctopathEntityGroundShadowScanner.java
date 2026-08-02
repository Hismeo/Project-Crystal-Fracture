package org.hismeo.fractureclient.client.render.octopath;

import com.kaleblangley.haikalat.subsystems.render3d.ExternalCamera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * Builds a compact, visible set of ground receivers for the custom entity-shadow post pass.
 *
 * <p>Vanilla's entity-shadow decal is deliberately disabled by the HD-2D presentation because
 * it becomes a hard black sticker from the orthographic camera. This scanner only finds the
 * physical receiver surface. The final target/depth pass then draws the soft footprint, so a
 * wall, an occluded floor, or the entity's own pixels are not directly painted by CPU geometry.</p>
 */
final class OctopathEntityGroundShadowScanner {
    static final int MAX_SHADOWS = 12;

    private static final int MAX_RAYCAST_CANDIDATES = 24;
    private static final float MINIMUM_DROP = 0.5F;
    private static final float MAXIMUM_DROP = 16.0F;
    private static final float MINIMUM_RADIUS_SCALE = 0.25F;
    private static final float MAXIMUM_RADIUS_SCALE = 3.0F;
    private static final float RAY_START_OFFSET = 0.08F;
    private static final float GROUND_CLEARANCE = 0.04F;

    List<OctopathEntityGroundShadow> scan(
            ClientLevel level,
            LocalPlayer player,
            ExternalCamera camera,
            float partialTick,
            float requestedMaxDrop,
            float requestedRadiusScale
    ) {
        float maxDrop = clamp(requestedMaxDrop, MINIMUM_DROP, MAXIMUM_DROP);
        float radiusScale = clamp(requestedRadiusScale, MINIMUM_RADIUS_SCALE, MAXIMUM_RADIUS_SCALE);
        float interpolation = clamp(partialTick, 0.0F, 1.0F);
        Matrix4f viewProjection = camera.viewProjection();

        List<EntityCandidate> visibleEntities = new ArrayList<>(MAX_RAYCAST_CANDIDATES * 2);
        Set<Integer> visitedEntityIds = new HashSet<>();
        addVisibleEntity(visibleEntities, visitedEntityIds, player, player, viewProjection, interpolation);
        for (Entity entity : level.entitiesForRendering()) {
            addVisibleEntity(visibleEntities, visitedEntityIds, player, entity, viewProjection, interpolation);
        }

        visibleEntities.sort(Comparator.comparingDouble(EntityCandidate::score).reversed());
        List<Candidate> candidates = new ArrayList<>(MAX_SHADOWS);
        int raycastCount = Math.min(MAX_RAYCAST_CANDIDATES, visibleEntities.size());
        for (int index = 0; index < raycastCount; index++) {
            EntityCandidate entity = visibleEntities.get(index);
            addGroundCandidate(candidates, level, entity, maxDrop, radiusScale);
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<OctopathEntityGroundShadow> shadows = new ArrayList<>(Math.min(MAX_SHADOWS, candidates.size()));
        for (Candidate candidate : candidates) {
            if (shadows.size() >= MAX_SHADOWS) {
                break;
            }
            shadows.add(candidate.shadow());
        }
        return List.copyOf(shadows);
    }

    void reset() {
        // This scanner intentionally traces from current interpolated entity positions each frame.
        // A cache would make a moving actor's shadow visibly lag behind its feet.
    }

    private static void addVisibleEntity(
            List<EntityCandidate> visibleEntities,
            Set<Integer> visitedEntityIds,
            LocalPlayer player,
            Entity entity,
            Matrix4f viewProjection,
            float partialTick
    ) {
        if (entity == null
                || entity.isRemoved()
                || entity.isInvisible()
                || entity.isSpectator()
                || entity.isPassenger()
                || !(entity instanceof LivingEntity)
                || !visitedEntityIds.add(entity.getId())) {
            return;
        }

        Vec3 feet = entity.getPosition(partialTick);
        if (!isFinite(feet)) {
            return;
        }
        float visualHeight = Math.max(0.1F, entity.getBbHeight());
        boolean visible = isVisible(viewProjection, feet.x, feet.y + visualHeight * 0.45D, feet.z);
        // The local actor is the focal point of this renderer. Keep its grounding shadow even
        // when the orthographic frustum is between frames during a camera transition.
        if (!visible && entity != player) {
            return;
        }

        float width = Math.max(0.20F, entity.getBbWidth());
        float distanceSquared = horizontalDistanceSquared(
                (float) player.getX(),
                (float) player.getZ(),
                (float) feet.x,
                (float) feet.z);
        float score = entity == player ? 10_000.0F : 100.0F;
        score += 8.0F / (1.0F + distanceSquared * 0.06F);
        score += Math.min(2.0F, width);
        visibleEntities.add(new EntityCandidate(entity, feet, width, score));
    }

    private static void addGroundCandidate(
            List<Candidate> candidates,
            ClientLevel level,
            EntityCandidate entity,
            float maxDrop,
            float radiusScale
    ) {
        BlockHitResult hit = traceGround(level, entity.entity(), entity.feet(), maxDrop);
        if (hit == null) {
            return;
        }

        Vec3 ground = hit.getLocation();
        double verticalGap = Math.max(0.0D, entity.feet().y - ground.y);
        if (verticalGap > maxDrop + GROUND_CLEARANCE || !isFinite(ground)) {
            return;
        }

        float radius = clamp((entity.width() * 0.82F + 0.15F) * radiusScale, 0.20F, 2.4F);
        float lift = smoothstep(0.18F, maxDrop, (float) verticalGap);
        float opacity = 0.88F * (1.0F - lift);
        if (opacity <= 0.01F) {
            return;
        }

        candidates.add(new Candidate(
                new OctopathEntityGroundShadow(
                        new Vector3f((float) ground.x, (float) ground.y, (float) ground.z),
                        radius,
                        opacity),
                entity.score()));
    }

    private static BlockHitResult traceGround(ClientLevel level, Entity entity, Vec3 feet, float maxDrop) {
        Vec3 start = feet.add(0.0D, RAY_START_OFFSET, 0.0D);
        Vec3 end = start.add(0.0D, -(maxDrop + RAY_START_OFFSET + GROUND_CLEARANCE), 0.0D);
        HitResult result = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity));
        return result instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && blockHit.getDirection() == Direction.UP
                ? blockHit
                : null;
    }

    private static boolean isVisible(Matrix4f viewProjection, double x, double y, double z) {
        Vector4f clip = new Vector4f((float) x, (float) y, (float) z, 1.0F);
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

    private static float horizontalDistanceSquared(float ax, float az, float bx, float bz) {
        float deltaX = ax - bx;
        float deltaZ = az - bz;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float range = Math.max(0.0001F, edge1 - edge0);
        float t = clamp((value - edge0) / range, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record Candidate(OctopathEntityGroundShadow shadow, float score) {
    }

    private record EntityCandidate(Entity entity, Vec3 feet, float width, float score) {
    }
}
