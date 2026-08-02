package org.hismeo.fractureclient.client.control;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.render.BlockCullTransitionRenderer;
import org.joml.Matrix4f;

/**
 * Finds a conservative, player-centred cutaway for the orthographic camera.
 *
 * <p>The controller deliberately answers a visibility question rather than trying to understand
 * arbitrary Minecraft architecture. Rays are parallel in camera space, test the real voxel shape
 * of an occluder, and only remove a bounded number of safe model blocks. Thick terrain and blocks
 * rendered by special paths are left intact and reported to the presentation fallback instead.</p>
 *
 * <p>Section meshes are compiled off-thread. Published cull sets are therefore immutable snapshots,
 * and each section compile captures one snapshot for its entire lifetime.</p>
 */
public final class BlockCullController {
    private static final double EPSILON = 1.0e-5;
    private static final double GRID_TIE_EPSILON = 1.0e-4;
    private static final double MIN_VIRTUAL_CAMERA_DISTANCE = 8.0;
    private static final double CULL_LENGTH_BY_SIZE = 5.0;
    private static final int MAX_EXPOSED_LIGHT_SAMPLE_DISTANCE = 16;
    public static final int NO_CUTAWAY_LIGHT = -1;

    private static final int HORIZONTAL_SAMPLES = 15;
    private static final int VERTICAL_SAMPLES = 15;
    private static final int MIN_BLOCK_RAY_HITS = 2;
    private static final double SAMPLE_ELLIPSE_LIMIT = 1.04;
    private static final double THICK_RAY_RATIO = 0.22;

    private static final boolean DEBUG_RENDER_RAYS = false;
    private static final int RAY_COLOR = 0xAA33CCFF;

    private static final ThreadLocal<CullSnapshot> COMPILATION_SNAPSHOT = new ThreadLocal<>();
    private static final Long2LongOpenHashMap LAST_SEEN_TICK = new Long2LongOpenHashMap();

    private static volatile CullSnapshot publishedSnapshot = CullSnapshot.empty(0L);
    private static volatile float fallbackStrength;

    private static ClientLevel trackedLevel;
    private static long controllerTick;
    private static boolean orthographicModeActive;

    static {
        LAST_SEEN_TICK.defaultReturnValue(Long.MIN_VALUE);
    }

    private BlockCullController() {
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            resetWithoutRenderer();
            return;
        }

        if (trackedLevel != level) {
            resetForLevel(level);
        }

        if (!CameraModeController.isOrthographic()) {
            if (orthographicModeActive
                    || !publishedSnapshot.positions().isEmpty()
                    || fallbackStrength > 0.0F) {
                disableForVanillaCamera(minecraft, level);
            }
            ExplicitRoomController.reset();
            orthographicModeActive = false;
            return;
        }

        if (!orthographicModeActive) {
            orthographicModeActive = true;
            RoomCullScanner.reset(level);
            BlockCullTransitionRenderer.reset(level);
        }

        controllerTick++;
        BlockCullTransitionRenderer.tick(level, controllerTick);
        if (!OrthographicCameraConfig.isCull) {
            ExplicitRoomController.reset();
            RoomCullScanner.disable(level);
            clearAllCulling(minecraft);
            fallbackStrength = 0.0F;
            return;
        }

        if (RoomSelectionController.isActive()) {
            ExplicitRoomController.reset();
            RoomCullScanner.disable(level);
            clearAllCulling(minecraft);
            fallbackStrength = 0.0F;
            return;
        }

        BlockPos playerBlock = BlockPos.containing(minecraft.player.getBoundingBox().getCenter());
        ExplicitRoomController.tick(minecraft, playerBlock);
        boolean explicitRoom = ExplicitRoomController.hasActiveRoom();
        if (explicitRoom) {
            RoomCullScanner.disable(level);
        } else {
            RoomCullScanner.tick(level, playerBlock, controllerTick);
        }
        RayContext context = createRayContext(minecraft);
        if (context == null) {
            clearAllCulling(minecraft);
            updateFallbackStrength(0.0F);
            return;
        }

        ProbeResult probe = probeOcclusion(
                level,
                context,
                explicitRoom || RoomCullScanner.isInsideActiveRoom(playerBlock)
        );
        LongOpenHashSet resolvedCandidates = explicitRoom
                ? ExplicitRoomController.resolveCullCandidates(
                probe.hardCullBlocks(),
                playerBlock
        )
                : RoomCullScanner.resolveRoofCandidates(
                probe.hardCullBlocks(),
                playerBlock
        );
        probe = new ProbeResult(
                retainSafeHardCullStates(
                        level,
                        resolvedCandidates
                ),
                probe.fallbackStrength(),
                probe.releaseImmediately()
        );
        LongOpenHashSet stableSet = stabilizeCullSet(
                level,
                probe.hardCullBlocks(),
                probe.releaseImmediately()
        );
        publish(minecraft, stableSet);
        updateFallbackStrength(probe.fallbackStrength());
    }

    /** Captures one immutable state for all block calls made by a section compile. */
    public static void beginSectionCompilation() {
        COMPILATION_SNAPSHOT.set(publishedSnapshot);
    }

    public static void endSectionCompilation() {
        COMPILATION_SNAPSHOT.remove();
    }

    public static boolean shouldCullDuringCompilation(BlockPos pos, BlockState state) {
        CullSnapshot snapshot = COMPILATION_SNAPSHOT.get();
        return CameraModeController.isOrthographic()
                && snapshot != null
                && isSafeHardCullState(state)
                && snapshot.contains(pos.asLong());
    }

    /** Used by the section visibility graph, where the state has already been classified. */
    public static boolean isCulledDuringCompilation(BlockPos pos) {
        CullSnapshot snapshot = COMPILATION_SNAPSHOT.get();
        return CameraModeController.isOrthographic()
                && snapshot != null
                && snapshot.contains(pos.asLong());
    }

    /**
     * A non-culled block next to a culled block must emit its boundary face. Vanilla face culling
     * still sees the real world state, so without this override a cutaway has missing inner faces.
     */
    public static boolean shouldExposeFaceDuringCompilation(BlockPos neighborPos) {
        return isCulledDuringCompilation(neighborPos);
    }

    /**
     * Returns the minimum light for a block directly below the virtual cutaway. Environment mode
     * samples the first real cell above the complete culled column, while full-bright mode forces
     * both lightmap channels to level 15.
     */
    public static int getExposedLightDuringCompilation(
            BlockAndTintGetter level,
            BlockPos renderedPos
    ) {
        CullSnapshot snapshot = COMPILATION_SNAPSHOT.get();
        if (!CameraModeController.isOrthographic() || snapshot == null) {
            return NO_CUTAWAY_LIGHT;
        }

        OrthographicCameraConfig.CullExposedLightMode mode =
                OrthographicCameraConfig.cullExposedLightMode;
        if (mode == null || mode == OrthographicCameraConfig.CullExposedLightMode.VANILLA) {
            return NO_CUTAWAY_LIGHT;
        }

        BlockPos.MutableBlockPos samplePos = renderedPos.mutable().move(Direction.UP);
        if (!snapshot.contains(samplePos.asLong())) {
            return NO_CUTAWAY_LIGHT;
        }
        if (mode == OrthographicCameraConfig.CullExposedLightMode.FULL_BRIGHT) {
            return LightTexture.FULL_BRIGHT;
        }

        int distance = 0;
        while (snapshot.contains(samplePos.asLong())
                && distance < MAX_EXPOSED_LIGHT_SAMPLE_DISTANCE) {
            samplePos.move(Direction.UP);
            distance++;
        }
        if (snapshot.contains(samplePos.asLong())) {
            return NO_CUTAWAY_LIGHT;
        }
        if (samplePos.getY() >= level.getMaxBuildHeight()) {
            return LightTexture.pack(0, level.getMaxLightLevel());
        }
        return LevelRenderer.getLightColor(level, samplePos);
    }

    /** Strength requested from the Haikalat presentation fallback for unsafe/thick obstruction. */
    public static float getFallbackStrength() {
        return fallbackStrength;
    }

    private static RayContext createRayContext(Minecraft minecraft) {
        Vec3 viewForward = normalizedOrNull(Vec3.directionFromRotation(
                FloorAwareCameraController.getRenderPitch(),
                CameraRotateController.getRenderYaw()
        ));
        if (viewForward == null) {
            return null;
        }

        Vec3 helper = Math.abs(viewForward.y) < 0.98
                ? new Vec3(0.0, 1.0, 0.0)
                : new Vec3(1.0, 0.0, 0.0);
        Vec3 horizontalAxis = normalizedOrNull(viewForward.cross(helper));
        if (horizontalAxis == null) {
            return null;
        }
        Vec3 verticalAxis = normalizedOrNull(horizontalAxis.cross(viewForward));
        if (verticalAxis == null) {
            return null;
        }

        AABB bounds = minecraft.player.getBoundingBox();
        Vec3 center = bounds.getCenter();
        double halfX = (bounds.maxX - bounds.minX) * 0.5;
        double halfY = (bounds.maxY - bounds.minY) * 0.5;
        double halfZ = (bounds.maxZ - bounds.minZ) * 0.5;
        double horizontalExtent = projectedHalfExtent(horizontalAxis, halfX, halfY, halfZ)
                + Math.max(0.0, OrthographicCameraConfig.cullHorizontalMargin);
        double verticalExtent = projectedHalfExtent(verticalAxis, halfX, halfY, halfZ)
                + Math.max(0.0, OrthographicCameraConfig.cullVerticalMargin);
        // A player-shaped ray bundle becomes a thin keyhole after crossing a sloped roof. Keep a
        // circular minimum aperture; room detection still limits the result to the current roof.
        double minimumOpeningRadius = clamp(
                OrthographicCameraConfig.cullOpeningRadius,
                0.0,
                6.0
        );
        horizontalExtent = Math.max(horizontalExtent, minimumOpeningRadius);
        verticalExtent = Math.max(verticalExtent, minimumOpeningRadius);
        double rayLength = clamp(
                FloorAwareCameraController.getRenderSize() * CULL_LENGTH_BY_SIZE,
                MIN_VIRTUAL_CAMERA_DISTANCE,
                Math.max(MIN_VIRTUAL_CAMERA_DISTANCE, OrthographicCameraConfig.cullMaximumDistance)
        );
        rayLength = FloorAwareCameraController.limitOcclusionRayDistance(rayLength);

        return new RayContext(
                center,
                viewForward,
                horizontalAxis,
                verticalAxis,
                horizontalExtent,
                verticalExtent,
                rayLength,
                ceilToInt(bounds.minY)
        );
    }

    private static ProbeResult probeOcclusion(
            ClientLevel level,
            RayContext context,
            boolean insideConfirmedRoom
    ) {
        Long2IntOpenHashMap rayHits = new Long2IntOpenHashMap();
        int maximumLayers = Math.max(1, OrthographicCameraConfig.cullMaximumSolidLayers);
        if (insideConfirmedRoom) {
            maximumLayers = Math.max(
                    maximumLayers,
                    Math.max(
                            1,
                            Math.min(32, OrthographicCameraConfig.cullRoomMaximumSolidLayers)
                    )
            );
        }
        int sampleCount = 0;
        int occludedRays = 0;
        int unsafeRays = 0;
        int thickRays = 0;
        boolean centerRayThick = false;

        for (int horizontal = 0; horizontal < HORIZONTAL_SAMPLES; horizontal++) {
            double normalizedHorizontal = normalizedSample(horizontal, HORIZONTAL_SAMPLES);
            for (int vertical = 0; vertical < VERTICAL_SAMPLES; vertical++) {
                double normalizedVertical = normalizedSample(vertical, VERTICAL_SAMPLES);
                if (normalizedHorizontal * normalizedHorizontal
                        + normalizedVertical * normalizedVertical > SAMPLE_ELLIPSE_LIMIT) {
                    continue;
                }

                boolean centerSample = horizontal == HORIZONTAL_SAMPLES / 2
                        && vertical == VERTICAL_SAMPLES / 2;
                Vec3 target = context.playerCenter()
                        .add(context.horizontalAxis().scale(normalizedHorizontal * context.horizontalExtent()))
                        .add(context.verticalAxis().scale(normalizedVertical * context.verticalExtent()));
                Vec3 start = target.subtract(context.viewForward().scale(context.rayLength()));
                RayTrace trace = traceRay(level, start, target, context.minCullY());

                sampleCount++;
                if (trace.isOccluded()) {
                    occludedRays++;
                }
                if (trace.unsafeOccluder()) {
                    unsafeRays++;
                }

                boolean thick = trace.hardLayers() > maximumLayers;
                if (thick) {
                    thickRays++;
                    centerRayThick |= centerSample;
                    continue;
                }

                LongIterator iterator = trace.hardBlocks().iterator();
                while (iterator.hasNext()) {
                    rayHits.addTo(iterator.nextLong(), 1);
                }
            }
        }

        int thickThreshold = Math.max(2, (int)Math.ceil(sampleCount * THICK_RAY_RATIO));
        boolean thickBarrier = centerRayThick || thickRays >= thickThreshold;
        LongOpenHashSet hardCullBlocks = new LongOpenHashSet();
        if (!thickBarrier) {
            for (Long2IntMap.Entry entry : rayHits.long2IntEntrySet()) {
                if (entry.getIntValue() >= MIN_BLOCK_RAY_HITS) {
                    hardCullBlocks.add(entry.getLongKey());
                }
            }
        }

        float inverseSamples = sampleCount == 0 ? 0.0F : 1.0F / sampleCount;
        float unsafeRatio = unsafeRays * inverseSamples;
        float thickRatio = thickRays * inverseSamples;
        float occludedRatio = occludedRays * inverseSamples;
        float requestedFallback = clamp01(Math.max(unsafeRatio * 3.0F, thickRatio * 3.5F));
        if (thickBarrier) {
            requestedFallback = Math.max(requestedFallback, clamp01(occludedRatio * 1.6F));
        }

        return new ProbeResult(hardCullBlocks, requestedFallback, thickBarrier);
    }

    private static RayTrace traceRay(ClientLevel level, Vec3 start, Vec3 end, int minCullY) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        if (dx * dx + dy * dy + dz * dz < EPSILON * EPSILON) {
            return RayTrace.EMPTY;
        }

        int x = floorToInt(start.x);
        int y = floorToInt(start.y);
        int z = floorToInt(start.z);
        int endX = floorToInt(end.x);
        int endY = floorToInt(end.y);
        int endZ = floorToInt(end.z);

        int stepX = step(dx);
        int stepY = step(dy);
        int stepZ = step(dz);
        double tMaxX = initialBoundaryT(start.x, dx, x, stepX);
        double tMaxY = initialBoundaryT(start.y, dy, y, stepY);
        double tMaxZ = initialBoundaryT(start.z, dz, z, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);

        LongArrayList hardBlocks = new LongArrayList();
        int hardLayers = 0;
        boolean unsafeOccluder = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (x != endX || y != endY || z != endZ) {
            if (y >= minCullY) {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                OccluderKind kind = classifyIntersection(level, pos, state, start, end);
                if (kind == OccluderKind.HARD_CULL) {
                    hardBlocks.add(BlockPos.asLong(x, y, z));
                    hardLayers++;
                } else if (kind == OccluderKind.UNSAFE) {
                    unsafeOccluder = true;
                }
            }

            double nextT = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (nextT >= 1.0) {
                break;
            }
            if (tMaxX <= nextT + GRID_TIE_EPSILON) {
                x += stepX;
                tMaxX += tDeltaX;
            }
            if (tMaxY <= nextT + GRID_TIE_EPSILON) {
                y += stepY;
                tMaxY += tDeltaY;
            }
            if (tMaxZ <= nextT + GRID_TIE_EPSILON) {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }

        return new RayTrace(hardBlocks, hardLayers, unsafeOccluder);
    }

    private static OccluderKind classifyIntersection(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            Vec3 start,
            Vec3 end
    ) {
        if (state.isAir() || state.is(BlockTags.LEAVES)) {
            return OccluderKind.NONE;
        }

        boolean structuralOccluder = state.canOcclude()
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS);
        boolean specialRenderer = state.hasBlockEntity()
                || state.getRenderShape() != RenderShape.MODEL
                || !state.getFluidState().isEmpty();
        if (!structuralOccluder && !state.hasBlockEntity()) {
            return OccluderKind.NONE;
        }

        VoxelShape shape = state.canOcclude()
                ? state.getOcclusionShape(level, pos)
                : state.getShape(level, pos);
        if (shape.isEmpty() || shape.clip(start, end, pos) == null) {
            return OccluderKind.NONE;
        }
        return structuralOccluder && !specialRenderer
                ? OccluderKind.HARD_CULL
                : OccluderKind.UNSAFE;
    }

    /**
     * Exact chunk-mesh removal safety classifier, also exposed to the debug scan command. Model
     * decorations and glass are safe to omit from a rebuilt section even when they do not count
     * as hard ray occluders; block entities, fluids and non-model renderers are kept.
     */
    public static boolean isSafeHardCullState(BlockState state) {
        return !state.isAir()
                && !state.is(BlockTags.LEAVES)
                && !state.hasBlockEntity()
                && state.getRenderShape() == RenderShape.MODEL
                && state.getFluidState().isEmpty();
    }

    private static LongOpenHashSet retainSafeHardCullStates(
            ClientLevel level,
            LongOpenHashSet candidates
    ) {
        LongOpenHashSet safeCandidates = new LongOpenHashSet(candidates.size());
        LongIterator iterator = candidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (isSafeHardCullState(level.getBlockState(BlockPos.of(packedPos)))) {
                safeCandidates.add(packedPos);
            }
        }
        return safeCandidates;
    }

    private static LongOpenHashSet stabilizeCullSet(
            ClientLevel level,
            LongOpenHashSet requested,
            boolean releaseImmediately
    ) {
        LongOpenHashSet stable = new LongOpenHashSet(requested);
        LongIterator requestedIterator = requested.iterator();
        while (requestedIterator.hasNext()) {
            long packedPos = requestedIterator.nextLong();
            LAST_SEEN_TICK.put(packedPos, controllerTick);
            if (publishedSnapshot.contains(packedPos)) {
                BlockState state = level.getBlockState(BlockPos.of(packedPos));
                BlockCullTransitionRenderer.hideIfTransitioning(
                        level,
                        packedPos,
                        state,
                        controllerTick
                );
            }
        }

        int releaseDelay = releaseImmediately
                ? 0
                : Math.max(0, OrthographicCameraConfig.cullReleaseDelayTicks);
        int fadeDuration = releaseImmediately
                ? 0
                : Math.max(0, Math.min(40, OrthographicCameraConfig.cullFadeDurationTicks));
        LongIterator activeIterator = publishedSnapshot.positions().iterator();
        while (activeIterator.hasNext()) {
            long packedPos = activeIterator.nextLong();
            if (stable.contains(packedPos)) {
                continue;
            }

            long lastSeen = LAST_SEEN_TICK.get(packedPos);
            long elapsed = lastSeen == Long.MIN_VALUE
                    ? Long.MAX_VALUE
                    : controllerTick - lastSeen;
            if (lastSeen != Long.MIN_VALUE && elapsed <= releaseDelay + fadeDuration) {
                BlockState currentState = level.getBlockState(BlockPos.of(packedPos));
                if (isSafeHardCullState(currentState)) {
                    stable.add(packedPos);
                    if (elapsed > releaseDelay) {
                        BlockCullTransitionRenderer.beginReveal(
                                level,
                                packedPos,
                                currentState,
                                controllerTick
                        );
                    }
                    continue;
                }
            }
            BlockCullTransitionRenderer.discard(packedPos);
            LAST_SEEN_TICK.remove(packedPos);
        }
        return stable;
    }

    private static void publish(Minecraft minecraft, LongOpenHashSet positions) {
        CullSnapshot oldSnapshot = publishedSnapshot;
        if (sameSet(oldSnapshot.positions(), positions)) {
            return;
        }

        CullSnapshot nextSnapshot = new CullSnapshot(
                oldSnapshot.generation() + 1L,
                new LongOpenHashSet(positions)
        );
        updateTransitions(
                minecraft.level,
                oldSnapshot.positions(),
                nextSnapshot.positions()
        );
        // Publish first. Every rebuild scheduled below must observe the new generation.
        publishedSnapshot = nextSnapshot;
        markDirtySections(minecraft, oldSnapshot.positions(), nextSnapshot.positions());
    }

    private static void updateTransitions(
            ClientLevel level,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        if (level == null) {
            return;
        }

        LongIterator added = newSet.iterator();
        while (added.hasNext()) {
            long packedPos = added.nextLong();
            if (!oldSet.contains(packedPos)) {
                BlockCullTransitionRenderer.beginCull(
                        level,
                        packedPos,
                        level.getBlockState(BlockPos.of(packedPos)),
                        controllerTick
                );
            }
        }

        LongIterator restored = oldSet.iterator();
        while (restored.hasNext()) {
            long packedPos = restored.nextLong();
            if (!newSet.contains(packedPos)) {
                BlockState state = level.getBlockState(BlockPos.of(packedPos));
                if (isSafeHardCullState(state)) {
                    BlockCullTransitionRenderer.chunkRestorePublished(
                            level,
                            packedPos,
                            state,
                            controllerTick
                    );
                } else {
                    BlockCullTransitionRenderer.discard(packedPos);
                }
            }
        }
    }

    private static void markDirtySections(
            Minecraft minecraft,
            LongOpenHashSet oldSet,
            LongOpenHashSet newSet
    ) {
        if (minecraft.levelRenderer == null) {
            return;
        }

        LongOpenHashSet dirtySections = new LongOpenHashSet();
        collectChangedSections(oldSet, newSet, dirtySections);
        collectChangedSections(newSet, oldSet, dirtySections);
        LongIterator iterator = dirtySections.iterator();
        while (iterator.hasNext()) {
            long section = iterator.nextLong();
            minecraft.levelRenderer.setSectionDirty(
                    SectionPos.x(section),
                    SectionPos.y(section),
                    SectionPos.z(section)
            );
        }
    }

    private static void collectChangedSections(
            LongOpenHashSet source,
            LongOpenHashSet other,
            LongOpenHashSet out
    ) {
        LongIterator iterator = source.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (other.contains(packedPos)) {
                continue;
            }

            int blockX = BlockPos.getX(packedPos);
            int blockY = BlockPos.getY(packedPos);
            int blockZ = BlockPos.getZ(packedPos);
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        out.add(SectionPos.asLong(
                                SectionPos.blockToSectionCoord(blockX + xOffset),
                                SectionPos.blockToSectionCoord(blockY + yOffset),
                                SectionPos.blockToSectionCoord(blockZ + zOffset)
                        ));
                    }
                }
            }
        }
    }

    private static void clearAllCulling(Minecraft minecraft) {
        LAST_SEEN_TICK.clear();
        publish(minecraft, new LongOpenHashSet());
    }

    private static void disableForVanillaCamera(Minecraft minecraft, ClientLevel level) {
        controllerTick = 0L;
        LAST_SEEN_TICK.clear();
        fallbackStrength = 0.0F;
        RoomCullScanner.disable(level);
        ExplicitRoomController.reset();
        BlockCullTransitionRenderer.reset(level);
        COMPILATION_SNAPSHOT.remove();

        CullSnapshot oldSnapshot = publishedSnapshot;
        if (oldSnapshot.positions().isEmpty()) {
            return;
        }
        CullSnapshot emptySnapshot = CullSnapshot.empty(oldSnapshot.generation() + 1L);
        publishedSnapshot = emptySnapshot;
        markDirtySections(minecraft, oldSnapshot.positions(), emptySnapshot.positions());
    }

    private static void resetForLevel(ClientLevel level) {
        trackedLevel = level;
        controllerTick = 0L;
        orthographicModeActive = false;
        LAST_SEEN_TICK.clear();
        fallbackStrength = 0.0F;
        FloorAwareCameraController.reset();
        CullSnapshot old = publishedSnapshot;
        publishedSnapshot = CullSnapshot.empty(old.generation() + 1L);
        COMPILATION_SNAPSHOT.remove();
        RoomCullScanner.reset(level);
        ExplicitRoomController.reset();
        BlockCullTransitionRenderer.reset(level);
    }

    private static void resetWithoutRenderer() {
        trackedLevel = null;
        controllerTick = 0L;
        orthographicModeActive = false;
        LAST_SEEN_TICK.clear();
        fallbackStrength = 0.0F;
        FloorAwareCameraController.reset();
        CullSnapshot old = publishedSnapshot;
        if (!old.positions().isEmpty()) {
            publishedSnapshot = CullSnapshot.empty(old.generation() + 1L);
        }
        COMPILATION_SNAPSHOT.remove();
        RoomCullScanner.reset();
        ExplicitRoomController.reset();
        BlockCullTransitionRenderer.reset();
    }

    private static void updateFallbackStrength(float target) {
        target = clamp01(target);
        float alpha = target > fallbackStrength ? 0.55F : 0.18F;
        fallbackStrength += (target - fallbackStrength) * alpha;
        if (fallbackStrength < 0.005F) {
            fallbackStrength = 0.0F;
        }
    }

    public static void renderDebugCullBoxWorld(
            PoseStack poseStack,
            Vec3 cameraRenderPos,
            MultiBufferSource.BufferSource buffer,
            Minecraft minecraft
    ) {
        if (!DEBUG_RENDER_RAYS
                || !CameraModeController.isOrthographic()
                || !OrthographicCameraConfig.isCull
                || minecraft.player == null) {
            return;
        }

        RayContext context = createRayContext(minecraft);
        if (context == null) {
            return;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        for (int horizontal = 0; horizontal < HORIZONTAL_SAMPLES; horizontal++) {
            double normalizedHorizontal = normalizedSample(horizontal, HORIZONTAL_SAMPLES);
            for (int vertical = 0; vertical < VERTICAL_SAMPLES; vertical++) {
                double normalizedVertical = normalizedSample(vertical, VERTICAL_SAMPLES);
                if (normalizedHorizontal * normalizedHorizontal
                        + normalizedVertical * normalizedVertical > SAMPLE_ELLIPSE_LIMIT) {
                    continue;
                }

                Vec3 target = context.playerCenter()
                        .add(context.horizontalAxis().scale(normalizedHorizontal * context.horizontalExtent()))
                        .add(context.verticalAxis().scale(normalizedVertical * context.verticalExtent()));
                Vec3 start = target.subtract(context.viewForward().scale(context.rayLength()));
                drawLine3D(
                        poseStack,
                        consumer,
                        start.subtract(cameraRenderPos),
                        target.subtract(cameraRenderPos),
                        RAY_COLOR
                );
            }
        }
    }

    private static void drawLine3D(PoseStack poseStack, VertexConsumer consumer, Vec3 a, Vec3 b, int color) {
        float nx = (float)(b.x - a.x);
        float ny = (float)(b.y - a.y);
        float nz = (float)(b.z - a.z);
        float length = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;

        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        consumer.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z)
                .setColor(color)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z)
                .setColor(color)
                .setNormal(pose, nx, ny, nz);
        poseStack.popPose();
    }

    private static double projectedHalfExtent(Vec3 axis, double halfX, double halfY, double halfZ) {
        return Math.abs(axis.x) * halfX + Math.abs(axis.y) * halfY + Math.abs(axis.z) * halfZ;
    }

    private static double normalizedSample(int index, int count) {
        return count <= 1 ? 0.0 : index * 2.0 / (count - 1.0) - 1.0;
    }

    private static int step(double delta) {
        return delta > 0.0 ? 1 : delta < 0.0 ? -1 : 0;
    }

    private static double initialBoundaryT(double position, double delta, int cell, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - position) / delta;
    }

    private static Vec3 normalizedOrNull(Vec3 vector) {
        double length = vector.length();
        return length < EPSILON ? null : vector.scale(1.0 / length);
    }

    private static boolean sameSet(LongOpenHashSet first, LongOpenHashSet second) {
        if (first.size() != second.size()) {
            return false;
        }
        LongIterator iterator = first.iterator();
        while (iterator.hasNext()) {
            if (!second.contains(iterator.nextLong())) {
                return false;
            }
        }
        return true;
    }

    private static int floorToInt(double value) {
        int integer = (int)value;
        return value < integer ? integer - 1 : integer;
    }

    private static int ceilToInt(double value) {
        int integer = (int)value;
        return value > integer ? integer + 1 : integer;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private enum OccluderKind {
        NONE,
        HARD_CULL,
        UNSAFE
    }

    private record RayContext(
            Vec3 playerCenter,
            Vec3 viewForward,
            Vec3 horizontalAxis,
            Vec3 verticalAxis,
            double horizontalExtent,
            double verticalExtent,
            double rayLength,
            int minCullY
    ) {
    }

    private record RayTrace(LongArrayList hardBlocks, int hardLayers, boolean unsafeOccluder) {
        private static final RayTrace EMPTY = new RayTrace(new LongArrayList(), 0, false);

        boolean isOccluded() {
            return hardLayers > 0 || unsafeOccluder;
        }
    }

    private record ProbeResult(
            LongOpenHashSet hardCullBlocks,
            float fallbackStrength,
            boolean releaseImmediately
    ) {
    }

    private record CullSnapshot(long generation, LongOpenHashSet positions) {
        static CullSnapshot empty(long generation) {
            return new CullSnapshot(generation, new LongOpenHashSet());
        }

        boolean contains(long packedPos) {
            return positions.contains(packedPos);
        }
    }
}
