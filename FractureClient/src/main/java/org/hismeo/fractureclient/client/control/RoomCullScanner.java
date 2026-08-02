package org.hismeo.fractureclient.client.control;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;

import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Recognises enclosed rooms from air connectivity and exposes per-storey camera bounds, walls and
 * roofs as cutaway candidates. World reads remain on the client thread; the flood fills only see
 * an immutable boolean snapshot and can therefore run on Minecraft's background executor.
 */
public final class RoomCullScanner {
    public static final int NO_ROOM_CEILING = Integer.MIN_VALUE;

    private static final int ENTER_CONFIRMATIONS = 2;
    private static final int EXIT_CONFIRMATIONS = 3;
    private static final int PERIODIC_SCAN_INTERVALS = 20;
    private static final int PLAYER_SEED_SEARCH_RADIUS = 2;
    private static final int PLAYER_ROOM_MEMBERSHIP_RADIUS = 1;
    private static final int VERTICAL_RESCAN_DISTANCE = 2;
    private static final int MAX_RESULT_PLAYER_DRIFT = 4;
    private static final int MAX_ADAPTIVE_HORIZONTAL_SIZE = 80;
    private static final int MAX_ADAPTIVE_VERTICAL_SIZE = 48;
    private static final int MIN_STRUCTURAL_CEILING_SEEDS = 4;
    private static final int LOCAL_CEILING_SEARCH_RADIUS = 2;
    private static final double LOCAL_FLAT_CEILING_RATIO = 0.25;
    private static final double LOW_CEILING_OUTLIER_RATIO = 0.05;
    private static final double CAMERA_CEILING_PERCENTILE = 0.98;
    private static final double SIGNIFICANT_UPPER_BAND_RATIO = 0.10;
    private static final int MAX_SIGNIFICANT_UPPER_BAND_SEEDS = 8;

    private static ClientLevel trackedLevel;
    private static boolean enabled;
    private static long epoch;
    private static long lastScanTick = Long.MIN_VALUE;
    private static CompletableFuture<ScanResult> pendingScan;
    private static BlockPos observedPlayerPos;
    private static ScanBounds monitoredBounds;
    private static boolean nearbyDirty = true;
    private static boolean confirmationPending;
    private static int adaptiveHorizontalSize;
    private static int adaptiveVerticalSize;

    private static RoomResult activeRoom;
    private static RoomResult pendingRoom;
    private static int enterEvidence;
    private static int exitEvidence;

    private RoomCullScanner() {
    }

    public static void tick(ClientLevel level, BlockPos playerPos, long controllerTick) {
        if (trackedLevel != level || !enabled) {
            initialise(level);
        }

        BlockPos immutablePlayerPos = playerPos.immutable();
        if (!immutablePlayerPos.equals(observedPlayerPos)) {
            observedPlayerPos = immutablePlayerPos;
            boolean movedToAnotherHeightBand = activeRoom != null
                    && Math.abs(
                            immutablePlayerPos.getY()
                                    - BlockPos.getY(activeRoom.seedPos())
                    ) >= VERTICAL_RESCAN_DISTANCE;
            if (activeRoom == null
                    || !roomContainsPlayer(activeRoom, immutablePlayerPos)
                    || movedToAnotherHeightBand) {
                nearbyDirty = true;
            }
        }

        collectCompletedScan(immutablePlayerPos);
        if (pendingScan != null) {
            return;
        }

        int interval = clamp(OrthographicCameraConfig.cullRoomScanIntervalTicks, 4, 40);
        boolean firstScan = lastScanTick == Long.MIN_VALUE;
        boolean periodicScan = !firstScan
                && controllerTick - lastScanTick >= (long)interval * PERIODIC_SCAN_INTERVALS;
        if (!nearbyDirty && !confirmationPending && !firstScan && !periodicScan) {
            return;
        }
        if (!firstScan && controllerTick - lastScanTick < interval) {
            return;
        }

        Snapshot snapshot = captureSnapshot(level, immutablePlayerPos, epoch);
        monitoredBounds = snapshot.bounds();
        lastScanTick = controllerTick;
        nearbyDirty = false;
        confirmationPending = false;
        pendingScan = CompletableFuture.supplyAsync(
                () -> analyse(snapshot),
                Util.backgroundExecutor()
        );
    }

    /** Marks a completed world snapshot stale when a relevant client-side block changes. */
    public static void markDirty(ClientLevel level, BlockPos changedPos) {
        if (!enabled || trackedLevel != level) {
            return;
        }
        if (monitoredBounds == null || monitoredBounds.contains(changedPos)) {
            nearbyDirty = true;
        }
    }

    /**
     * A confirmed floor-aware room opens its current ceiling directly. Rays still select nearby
     * wall faces and provide the conservative fallback used outside floor-aware mode.
     */
    public static LongOpenHashSet resolveRoofCandidates(
            LongOpenHashSet rayCandidates,
            BlockPos playerPos
    ) {
        RoomResult room = activeRoom;
        if (room == null
                || !room.bounds().contains(playerPos)
                || !roomContainsPlayer(room, playerPos)) {
            return rayCandidates;
        }
        boolean useFloorCameraCutaway = room.cameraCeilingY() != NO_ROOM_CEILING
                && FloorAwareCameraController.isEnabledForCurrentView();
        if (useFloorCameraCutaway && !OrthographicCameraConfig.cullNearestRoomWalls) {
            return resolveRoofOnlyCandidates(room.floorCutawayBlocks(), rayCandidates);
        }
        if (useFloorCameraCutaway
                && OrthographicCameraConfig.cullNearestRoomWalls
                && !room.wallCutaway().isEmpty()) {
            return resolveFloorCameraCandidates(room, rayCandidates, playerPos);
        }
        if (room.structuralBlocks().isEmpty()) {
            return rayCandidates;
        }

        LongOpenHashSet rayOverheadIntersection = new LongOpenHashSet();
        LongIterator iterator = rayCandidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (room.structuralBlocks().contains(packedPos)) {
                rayOverheadIntersection.add(packedPos);
            }
        }
        LongOpenHashSet wholeRoomCutaway = useFloorCameraCutaway
                ? room.floorCutawayBlocks()
                : room.wholeCutawayBlocks();
        boolean canCullWholeRoom = OrthographicCameraConfig.cullWholeRoomRoof
                && wholeRoomCutaway.size() <= clamp(
                OrthographicCameraConfig.cullWholeRoofMaximumBlocks,
                64,
                8192
        );
        if (!canCullWholeRoom
                || (!useFloorCameraCutaway && rayOverheadIntersection.isEmpty())) {
            return rayOverheadIntersection;
        }
        if (wholeRoomCutaway.isEmpty()) {
            return rayOverheadIntersection;
        }
        LongOpenHashSet result = new LongOpenHashSet(wholeRoomCutaway);
        result.addAll(rayOverheadIntersection);
        return result;
    }

    /**
     * With wall cutaway disabled, never fall back to the broad connected-structure ray mask: it
     * also contains wall blocks beside the player. Only the extracted roof shell is eligible.
     */
    private static LongOpenHashSet resolveRoofOnlyCandidates(
            LongOpenHashSet roofBlocks,
            LongOpenHashSet rayCandidates
    ) {
        if (roofBlocks.isEmpty()) {
            return new LongOpenHashSet();
        }
        int maximumWholeCutaway = clamp(
                OrthographicCameraConfig.cullWholeRoofMaximumBlocks,
                64,
                8192
        );
        if (OrthographicCameraConfig.cullWholeRoomRoof
                && roofBlocks.size() <= maximumWholeCutaway) {
            return new LongOpenHashSet(roofBlocks);
        }

        LongOpenHashSet result = new LongOpenHashSet();
        LongIterator iterator = rayCandidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (roofBlocks.contains(packedPos)) {
                result.add(packedPos);
            }
        }
        return result;
    }

    private static LongOpenHashSet resolveFloorCameraCandidates(
            RoomResult room,
            LongOpenHashSet rayCandidates,
            BlockPos playerPos
    ) {
        double yawRadians = Math.toRadians(CameraRotateController.getRenderYaw());
        double forwardX = -Math.sin(yawRadians);
        double forwardZ = Math.cos(yawRadians);
        RoomCameraBounds cameraBounds = room.cameraBounds();

        WallSide xSide;
        if (Math.abs(forwardX) > 1.0e-4) {
            xSide = forwardX > 0.0 ? WallSide.WEST : WallSide.EAST;
        } else {
            xSide = playerPos.getX() + 0.5 <= cameraBounds.centerX()
                    ? WallSide.WEST
                    : WallSide.EAST;
        }

        WallSide zSide;
        if (Math.abs(forwardZ) > 1.0e-4) {
            zSide = forwardZ > 0.0 ? WallSide.NORTH : WallSide.SOUTH;
        } else {
            zSide = playerPos.getZ() + 0.5 <= cameraBounds.centerZ()
                    ? WallSide.NORTH
                    : WallSide.SOUTH;
        }

        LongOpenHashSet selectedWalls = new LongOpenHashSet(
                room.wallCutaway().forSide(xSide)
        );
        selectedWalls.addAll(room.wallCutaway().forSide(zSide));

        LongOpenHashSet rayWallIntersection = new LongOpenHashSet();
        LongOpenHashSet rayRoofIntersection = new LongOpenHashSet();
        LongIterator iterator = rayCandidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (selectedWalls.contains(packedPos)) {
                rayWallIntersection.add(packedPos);
            }
            if (room.structuralBlocks().contains(packedPos)) {
                rayRoofIntersection.add(packedPos);
            }
        }

        int maximumWholeCutaway = clamp(
                OrthographicCameraConfig.cullWholeRoofMaximumBlocks,
                64,
                8192
        );
        boolean canCullWholeFloor = OrthographicCameraConfig.cullWholeRoomRoof
                && !room.floorCutawayBlocks().isEmpty()
                && room.floorCutawayBlocks().size() <= maximumWholeCutaway;
        if (rayWallIntersection.isEmpty()
                && rayRoofIntersection.isEmpty()
                && !canCullWholeFloor) {
            return new LongOpenHashSet();
        }

        LongOpenHashSet result = new LongOpenHashSet(rayWallIntersection);
        result.addAll(rayRoofIntersection);
        if (!rayWallIntersection.isEmpty()
                && selectedWalls.size() <= maximumWholeCutaway) {
            result.addAll(selectedWalls);
        }
        // A flat intermediate floor can be missed by shallow camera rays that enter through a
        // wall first. Once the room and storey are confirmed, the extracted floor surface itself
        // is sufficient evidence and must not depend on a roof-ray intersection.
        if (canCullWholeFloor) {
            result.addAll(room.floorCutawayBlocks());
        }
        return result;
    }

    /** Whether the player is currently covered by a confirmed, stable room snapshot. */
    public static boolean isInsideActiveRoom(BlockPos playerPos) {
        RoomResult room = activeRoom;
        return room != null
                && room.bounds().contains(playerPos)
                && roomContainsPlayer(room, playerPos);
    }

    /** Returns the block Y of the current storey's ceiling, ignoring tiny chimney outliers. */
    public static int getActiveRoomCameraCeilingY(BlockPos playerPos) {
        RoomResult room = activeRoom;
        if (room == null
                || !room.bounds().contains(playerPos)
                || !roomContainsPlayer(room, playerPos)) {
            return NO_ROOM_CEILING;
        }
        return room.cameraCeilingY();
    }

    /** Rectangle of the current storey's walkable air, used to frame the orthographic camera. */
    public static RoomCameraBounds getActiveRoomCameraBounds(BlockPos playerPos) {
        RoomResult room = activeRoom;
        if (room == null
                || !room.bounds().contains(playerPos)
                || !roomContainsPlayer(room, playerPos)
                || room.cameraCeilingY() == NO_ROOM_CEILING) {
            return null;
        }
        return room.cameraBounds();
    }

    public static void disable(ClientLevel level) {
        if (trackedLevel == level && !enabled) {
            return;
        }
        clearState();
        trackedLevel = level;
        enabled = false;
    }

    public static void reset(ClientLevel level) {
        clearState();
        trackedLevel = level;
        enabled = false;
    }

    public static void reset() {
        clearState();
        trackedLevel = null;
        enabled = false;
    }

    private static void initialise(ClientLevel level) {
        clearState();
        trackedLevel = level;
        enabled = true;
        nearbyDirty = true;
    }

    private static void clearState() {
        epoch++;
        if (pendingScan != null) {
            pendingScan.cancel(false);
            pendingScan = null;
        }
        lastScanTick = Long.MIN_VALUE;
        observedPlayerPos = null;
        monitoredBounds = null;
        nearbyDirty = true;
        confirmationPending = false;
        adaptiveHorizontalSize = 0;
        adaptiveVerticalSize = 0;
        activeRoom = null;
        pendingRoom = null;
        enterEvidence = 0;
        exitEvidence = 0;
    }

    private static void collectCompletedScan(BlockPos currentPlayerPos) {
        CompletableFuture<ScanResult> future = pendingScan;
        if (future == null || !future.isDone()) {
            return;
        }

        pendingScan = null;
        ScanResult result;
        try {
            result = future.join();
        } catch (RuntimeException ignored) {
            nearbyDirty = true;
            return;
        }

        if (result.epoch() != epoch) {
            return;
        }
        monitoredBounds = result.bounds();

        int driftX = Math.abs(currentPlayerPos.getX() - result.scanPlayerPos().getX());
        int driftY = Math.abs(currentPlayerPos.getY() - result.scanPlayerPos().getY());
        int driftZ = Math.abs(currentPlayerPos.getZ() - result.scanPlayerPos().getZ());
        if (driftX > MAX_RESULT_PLAYER_DRIFT
                || driftY > MAX_RESULT_PLAYER_DRIFT
                || driftZ > MAX_RESULT_PLAYER_DRIFT) {
            nearbyDirty = true;
            return;
        }

        if (requestLargerSnapshot(result)) {
            nearbyDirty = true;
            confirmationPending = true;
            return;
        }

        applyEvidence(result);
    }

    /**
     * A player-centred box may slice straight through a long room or through an upper storey.
     * Such cells are not evidence of outdoors. Re-scan once with the maximum bounded snapshot so
     * the complete room and its overhead structure can be recovered without paying that cost for
     * ordinary houses.
     */
    private static boolean requestLargerSnapshot(ScanResult result) {
        if (result.status() != ScanStatus.ENCLOSED || result.room() == null) {
            return false;
        }

        RoomResult room = result.room();
        boolean expanded = false;
        if (room.clippedHorizontally()
                && result.bounds().width() < MAX_ADAPTIVE_HORIZONTAL_SIZE) {
            adaptiveHorizontalSize = MAX_ADAPTIVE_HORIZONTAL_SIZE;
            expanded = true;
        }
        if (room.clippedVertically()
                && result.bounds().height() < MAX_ADAPTIVE_VERTICAL_SIZE) {
            adaptiveVerticalSize = MAX_ADAPTIVE_VERTICAL_SIZE;
            expanded = true;
        }
        return expanded;
    }

    private static void applyEvidence(ScanResult result) {
        if (result.status() == ScanStatus.ENCLOSED) {
            RoomResult scannedRoom = result.room();
            exitEvidence = 0;

            if (activeRoom != null && sameRoom(activeRoom, scannedRoom)) {
                activeRoom = scannedRoom;
                pendingRoom = null;
                enterEvidence = 0;
                return;
            }

            if (pendingRoom != null && sameRoom(pendingRoom, scannedRoom)) {
                pendingRoom = scannedRoom;
                enterEvidence++;
            } else {
                pendingRoom = scannedRoom;
                enterEvidence = 1;
            }

            if (enterEvidence >= ENTER_CONFIRMATIONS) {
                activeRoom = scannedRoom;
                pendingRoom = null;
                enterEvidence = 0;
            } else {
                confirmationPending = true;
            }
            return;
        }

        pendingRoom = null;
        enterEvidence = 0;
        if (result.status() == ScanStatus.OUTDOOR && activeRoom != null) {
            exitEvidence++;
            if (exitEvidence >= EXIT_CONFIRMATIONS) {
                activeRoom = null;
                exitEvidence = 0;
            } else {
                confirmationPending = true;
            }
        } else if (result.status() == ScanStatus.OUTDOOR) {
            exitEvidence = 0;
        }
    }

    private static boolean sameRoom(RoomResult first, RoomResult second) {
        return first.roomAir().contains(second.seedPos())
                || second.roomAir().contains(first.seedPos());
    }

    /** Keeps room membership stable while the player's centre occupies stairs or another solid. */
    private static boolean roomContainsPlayer(RoomResult room, BlockPos playerPos) {
        if (room.roomAir().contains(playerPos.asLong())) {
            return true;
        }
        for (int yOffset = -PLAYER_ROOM_MEMBERSHIP_RADIUS;
             yOffset <= PLAYER_ROOM_MEMBERSHIP_RADIUS;
             yOffset++) {
            for (int zOffset = -PLAYER_ROOM_MEMBERSHIP_RADIUS;
                 zOffset <= PLAYER_ROOM_MEMBERSHIP_RADIUS;
                 zOffset++) {
                for (int xOffset = -PLAYER_ROOM_MEMBERSHIP_RADIUS;
                     xOffset <= PLAYER_ROOM_MEMBERSHIP_RADIUS;
                     xOffset++) {
                    int distanceSquared = xOffset * xOffset
                            + yOffset * yOffset
                            + zOffset * zOffset;
                    if (distanceSquared > PLAYER_ROOM_MEMBERSHIP_RADIUS
                            * PLAYER_ROOM_MEMBERSHIP_RADIUS) {
                        continue;
                    }
                    long nearbyPos = BlockPos.asLong(
                            playerPos.getX() + xOffset,
                            playerPos.getY() + yOffset,
                            playerPos.getZ() + zOffset
                    );
                    if (room.roomAir().contains(nearbyPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Copies all world-dependent data on the client thread. */
    private static Snapshot captureSnapshot(ClientLevel level, BlockPos playerPos, long scanEpoch) {
        int width = Math.max(
                clamp(OrthographicCameraConfig.cullRoomScanHorizontalSize, 16, 80),
                adaptiveHorizontalSize
        );
        int requestedHeight = Math.max(
                clamp(OrthographicCameraConfig.cullRoomScanVerticalSize, 12, 48),
                adaptiveVerticalSize
        );
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight();
        int height = Math.min(requestedHeight, worldMaxY - worldMinY);
        int belowPlayer = Math.min(6, Math.max(3, height / 4));

        int minX = playerPos.getX() - width / 2;
        int minZ = playerPos.getZ() - width / 2;
        int minY = clamp(playerPos.getY() - belowPlayer, worldMinY, worldMaxY - height);
        ScanBounds bounds = new ScanBounds(minX, minY, minZ, width, height, width);
        int cellCount = width * height * width;
        boolean[] roomPassable = new boolean[cellCount];
        boolean[] outsideFloodPassable = new boolean[cellCount];
        boolean[] outsideSeeds = new boolean[cellCount];
        boolean[] hardCullSafe = new boolean[cellCount];

        IdentityHashMap<BlockState, CellPassability> passabilityCache = new IdentityHashMap<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int localY = 0; localY < height; localY++) {
            int worldY = minY + localY;
            for (int localZ = 0; localZ < width; localZ++) {
                int worldZ = minZ + localZ;
                for (int localX = 0; localX < width; localX++) {
                    mutablePos.set(minX + localX, worldY, worldZ);
                    BlockState state = level.getBlockState(mutablePos);
                    CellPassability passability = passabilityCache.get(state);
                    if (passability == null) {
                        passability = classifyPassability(level, mutablePos, state);
                        passabilityCache.put(state, passability);
                    }
                    int packedIndex = index(localX, localY, localZ, width, width);
                    roomPassable[packedIndex] = passability.roomPassable();
                    outsideFloodPassable[packedIndex] = passability.outsideFloodPassable();
                    hardCullSafe[packedIndex] = BlockCullController.isSafeHardCullState(state);
                    boolean onSnapshotBoundary = localX == 0
                            || localX == width - 1
                            || localY == 0
                            || localY == height - 1
                            || localZ == 0
                            || localZ == width - 1;
                    // Only real sky-connected boundary cells seed the outside flood. Blindly
                    // seeding all six artificial faces cuts open long rooms and tall upper floors.
                    outsideSeeds[packedIndex] = onSnapshotBoundary
                            && passability.outsideFloodPassable()
                            && level.canSeeSky(mutablePos);
                }
            }
        }

        int roofLayers = clamp(OrthographicCameraConfig.cullRoomRoofExpansionLayers, 1, 5);
        int roofHorizontalExpansion = clamp(
                OrthographicCameraConfig.cullRoomRoofHorizontalExpansion,
                0,
                6
        );
        int wallExteriorDepth = clamp(
                OrthographicCameraConfig.cullRoomWallExteriorDepth,
                0,
                6
        );
        return new Snapshot(
                scanEpoch,
                bounds,
                playerPos,
                roofLayers,
                roofHorizontalExpansion,
                wallExteriorDepth,
                roomPassable,
                outsideFloodPassable,
                outsideSeeds,
                hardCullSafe
        );
    }

    /** Exact passability classifier used by both room snapshots and the block-scan command. */
    public static boolean isRoomPassable(ClientLevel level, BlockPos pos, BlockState state) {
        return classifyPassability(level, pos, state).roomPassable();
    }

    /**
     * Passability used only by the flood that enters from the scan-box boundary. Open doors are
     * traversable room portals, but they remain a topological boundary so exterior air cannot
     * erase an otherwise enclosed room.
     */
    public static boolean isOutsideFloodPassable(
            ClientLevel level,
            BlockPos pos,
            BlockState state
    ) {
        return classifyPassability(level, pos, state).outsideFloodPassable();
    }

    private static CellPassability classifyPassability(
            ClientLevel level,
            BlockPos pos,
            BlockState state
    ) {
        if (state.isAir()) {
            return CellPassability.OPEN_AIR;
        }
        if (!state.getFluidState().isEmpty()) {
            return CellPassability.BLOCKED;
        }

        boolean openRoomPortal = state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN)
                && (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock);
        boolean roomPassable = openRoomPortal || state.getCollisionShape(level, pos).isEmpty();
        return new CellPassability(roomPassable, roomPassable && !openRoomPortal);
    }

    /** Pure background work: outside fill, player-component fill, then roof extraction. */
    private static ScanResult analyse(Snapshot snapshot) {
        ScanBounds bounds = snapshot.bounds();
        int width = bounds.width();
        int height = bounds.height();
        int depth = bounds.depth();
        int cellCount = width * height * depth;
        boolean[] outside = new boolean[cellCount];
        int[] queue = new int[cellCount];
        int tail = 0;

        for (int packedIndex = 0; packedIndex < cellCount; packedIndex++) {
            if (snapshot.outsideSeeds()[packedIndex]) {
                tail = enqueue(
                        snapshot.outsideFloodPassable(),
                        outside,
                        queue,
                        tail,
                        packedIndex
                );
            }
        }

        flood(snapshot.outsideFloodPassable(), outside, queue, tail, width, height, depth);

        int seed = findPlayerSeed(snapshot, outside);
        if (seed < 0) {
            return new ScanResult(
                    snapshot.epoch(),
                    bounds,
                    snapshot.playerPos(),
                    ScanStatus.UNKNOWN,
                    null
            );
        }
        if (outside[seed]) {
            return new ScanResult(
                    snapshot.epoch(),
                    bounds,
                    snapshot.playerPos(),
                    ScanStatus.OUTDOOR,
                    null
            );
        }

        boolean[] roomCells = new boolean[cellCount];
        int roomSize = floodRoom(
                snapshot.roomPassable(),
                outside,
                roomCells,
                queue,
                seed,
                width,
                height,
                depth
        );
        int seedX = seed % width;
        int seedYZ = seed / width;
        int seedZ = seedYZ % depth;
        int seedY = seedYZ / depth;
        LongOpenHashSet roomAir = new LongOpenHashSet(Math.max(16, roomSize));
        boolean[] ceilingSeeds = new boolean[cellCount];
        int[] ceilingSeedsByY = new int[height];
        int[] roomCellsByY = new int[height];
        int ceilingSeedCount = 0;
        boolean roomTouchesHorizontalBoundary = false;
        boolean roomTouchesVerticalBoundary = false;
        for (int queueIndex = 0; queueIndex < roomSize; queueIndex++) {
            int packedIndex = queue[queueIndex];
            int x = packedIndex % width;
            int yz = packedIndex / width;
            int z = yz % depth;
            int y = yz / depth;
            roomCellsByY[y]++;
            roomAir.add(BlockPos.asLong(
                    bounds.minX() + x,
                    bounds.minY() + y,
                    bounds.minZ() + z
            ));
            roomTouchesHorizontalBoundary |= x == 0
                    || x == width - 1
                    || z == 0
                    || z == depth - 1;
            roomTouchesVerticalBoundary |= y == 0 || y == height - 1;

            int ceilingY = y + 1;
            if (ceilingY >= height
                    || snapshot.roomPassable()[index(x, ceilingY, z, width, depth)]) {
                continue;
            }
            // A chandelier, beam or floating platform has room air above it that is connected
            // around the object. A genuine ceiling/roof separates this component from that air.
            if (roomContinuesAbove(
                    roomCells,
                    x,
                    y,
                    z,
                    width,
                    height,
                    depth,
                    snapshot.roofExpansionLayers() + 2
            )) {
                continue;
            }
            int ceilingIndex = index(x, ceilingY, z, width, depth);
            if (!ceilingSeeds[ceilingIndex]) {
                ceilingSeeds[ceilingIndex] = true;
                ceilingSeedsByY[ceilingY]++;
                ceilingSeedCount++;
            }
        }
        int structuralCeilingY = findStructuralCeilingY(
                roomCells,
                ceilingSeeds,
                ceilingSeedsByY,
                ceilingSeedCount,
                seedX,
                seedY,
                seedZ,
                width,
                depth
        );
        int cameraCeilingY = findCameraCeilingY(
                ceilingSeedsByY,
                structuralCeilingY,
                snapshot.roofExpansionLayers(),
                roomCellsByY[seedY]
        );
        int storeyBottomY = findStoreyBottomY(roomCellsByY, seedY);
        RoomGeometry roomGeometry = extractRoomGeometry(
                snapshot,
                roomCells,
                storeyBottomY,
                structuralCeilingY,
                cameraCeilingY,
                seedY,
                seedX,
                seedZ
        );
        OverheadResult overhead = expandOverheadFromRoomSeeds(
                snapshot,
                ceilingSeeds,
                structuralCeilingY,
                cameraCeilingY,
                queue
        );

        long seedPos = BlockPos.asLong(
                bounds.minX() + seedX,
                bounds.minY() + seedY,
                bounds.minZ() + seedZ
        );
        RoomResult room = new RoomResult(
                bounds,
                seedPos,
                roomAir,
                overhead.structuralBlocks(),
                overhead.wholeCutawayBlocks(),
                overhead.floorCutawayBlocks(),
                roomGeometry.wallCutaway(),
                roomGeometry.cameraBounds(),
                cameraCeilingY < 0
                        ? NO_ROOM_CEILING
                        : bounds.minY() + cameraCeilingY,
                roomTouchesHorizontalBoundary || overhead.touchesHorizontalBoundary(),
                roomTouchesVerticalBoundary || overhead.touchesTopBoundary()
        );
        return new ScanResult(
                snapshot.epoch(),
                bounds,
                snapshot.playerPos(),
                ScanStatus.ENCLOSED,
                room
        );
    }

    /**
     * A staircase may connect multiple storeys into one 3-D air component. A storey boundary is
     * still visible as a sharp loss of passable cells at the floor slab, so stop descending when
     * the horizontal slice becomes a small stairwell-sized bottleneck.
     */
    private static int findStoreyBottomY(int[] roomCellsByY, int playerSeedY) {
        int referenceCells = Math.max(1, roomCellsByY[playerSeedY]);
        int minimumStoreySlice = Math.max(8, (int)Math.ceil(referenceCells * 0.20));
        int bottomY = playerSeedY;
        for (int y = playerSeedY - 1; y >= 0; y--) {
            if (roomCellsByY[y] < minimumStoreySlice) {
                break;
            }
            bottomY = y;
        }
        return bottomY;
    }

    /** Extracts a floor rectangle and four directional wall masks from the room-air component. */
    private static RoomGeometry extractRoomGeometry(
            Snapshot snapshot,
            boolean[] roomCells,
            int storeyBottomY,
            int structuralCeilingY,
            int cameraCeilingY,
            int playerSeedY,
            int playerSeedX,
            int playerSeedZ
    ) {
        ScanBounds bounds = snapshot.bounds();
        int width = bounds.width();
        int height = bounds.height();
        int depth = bounds.depth();
        int wallTopY = cameraCeilingY >= 0
                ? Math.min(height - 1, cameraCeilingY - 1)
                : structuralCeilingY >= 0
                ? Math.min(height - 1, structuralCeilingY - 1)
                : Math.min(height - 1, playerSeedY + 4);
        wallTopY = Math.max(storeyBottomY, wallTopY);

        int frameMinY = Math.max(storeyBottomY, playerSeedY - 1);
        int frameMaxY = Math.min(wallTopY, playerSeedY + 2);
        int frameMinX = width;
        int frameMaxX = -1;
        int frameMinZ = depth;
        int frameMaxZ = -1;

        int[] minXByY = new int[height];
        int[] maxXByY = new int[height];
        int[] minZByY = new int[height];
        int[] maxZByY = new int[height];
        java.util.Arrays.fill(minXByY, width);
        java.util.Arrays.fill(maxXByY, -1);
        java.util.Arrays.fill(minZByY, depth);
        java.util.Arrays.fill(maxZByY, -1);

        for (int y = storeyBottomY; y <= wallTopY; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    if (!roomCells[index(x, y, z, width, depth)]) {
                        continue;
                    }
                    minXByY[y] = Math.min(minXByY[y], x);
                    maxXByY[y] = Math.max(maxXByY[y], x);
                    minZByY[y] = Math.min(minZByY[y], z);
                    maxZByY[y] = Math.max(maxZByY[y], z);
                    if (y >= frameMinY && y <= frameMaxY) {
                        frameMinX = Math.min(frameMinX, x);
                        frameMaxX = Math.max(frameMaxX, x);
                        frameMinZ = Math.min(frameMinZ, z);
                        frameMaxZ = Math.max(frameMaxZ, z);
                    }
                }
            }
        }

        if (frameMaxX < frameMinX || frameMaxZ < frameMinZ) {
            frameMinX = frameMaxX = playerSeedX;
            frameMinZ = frameMaxZ = playerSeedZ;
        }
        int worldCeilingY = cameraCeilingY >= 0
                ? bounds.minY() + cameraCeilingY
                : bounds.minY() + wallTopY + 1;
        RoomCameraBounds cameraBounds = new RoomCameraBounds(
                bounds.minX() + frameMinX,
                bounds.minX() + frameMaxX + 1.0,
                bounds.minZ() + frameMinZ,
                bounds.minZ() + frameMaxZ + 1.0,
                bounds.minY() + storeyBottomY,
                worldCeilingY
        );

        LongOpenHashSet north = new LongOpenHashSet();
        LongOpenHashSet south = new LongOpenHashSet();
        LongOpenHashSet west = new LongOpenHashSet();
        LongOpenHashSet east = new LongOpenHashSet();
        for (int y = storeyBottomY; y <= wallTopY; y++) {
            if (maxXByY[y] < 0 || maxZByY[y] < 0) {
                continue;
            }
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    if (!roomCells[index(x, y, z, width, depth)]) {
                        continue;
                    }
                    if (x <= minXByY[y] + 1) {
                        addWallExterior(snapshot, west, x - 1, y, z, -1, 0);
                    }
                    if (x >= maxXByY[y] - 1) {
                        addWallExterior(snapshot, east, x + 1, y, z, 1, 0);
                    }
                    if (z <= minZByY[y] + 1) {
                        addWallExterior(snapshot, north, x, y, z - 1, 0, -1);
                    }
                    if (z >= maxZByY[y] - 1) {
                        addWallExterior(snapshot, south, x, y, z + 1, 0, 1);
                    }
                }
            }
        }

        return new RoomGeometry(
                cameraBounds,
                new WallCutaway(north, south, west, east)
        );
    }

    /** Adds the wall skin, its thickness and one tangential decoration cell on each side. */
    private static void addWallExterior(
            Snapshot snapshot,
            LongOpenHashSet target,
            int wallX,
            int wallY,
            int wallZ,
            int outwardX,
            int outwardZ
    ) {
        ScanBounds bounds = snapshot.bounds();
        int tangentX = outwardZ;
        int tangentZ = -outwardX;
        for (int outward = 0; outward <= snapshot.wallExteriorDepth(); outward++) {
            for (int tangent = -1; tangent <= 1; tangent++) {
                int x = wallX + outwardX * outward + tangentX * tangent;
                int z = wallZ + outwardZ * outward + tangentZ * tangent;
                if (x < 0 || x >= bounds.width()
                        || wallY < 0 || wallY >= bounds.height()
                        || z < 0 || z >= bounds.depth()) {
                    continue;
                }
                int packedIndex = index(x, wallY, z, bounds.width(), bounds.depth());
                if (!snapshot.hardCullSafe()[packedIndex]) {
                    continue;
                }
                target.add(BlockPos.asLong(
                        bounds.minX() + x,
                        bounds.minY() + wallY,
                        bounds.minZ() + z
                ));
            }
        }
    }

    /**
     * Prefers the nearest verified ceiling in a 5x5 area around the player. This lets a tiny
     * intermediate floor win over a much larger roof above it; the global outlier filter remains
     * as a fallback when the player stands directly under a stairwell opening.
     */
    private static int findStructuralCeilingY(
            boolean[] roomCells,
            boolean[] ceilingSeeds,
            int[] seedsByY,
            int totalSeeds,
            int playerSeedX,
            int playerSeedY,
            int playerSeedZ,
            int width,
            int depth
    ) {
        if (totalSeeds <= 0) {
            return -1;
        }
        int minX = Math.max(0, playerSeedX - LOCAL_CEILING_SEARCH_RADIUS);
        int maxX = Math.min(width - 1, playerSeedX + LOCAL_CEILING_SEARCH_RADIUS);
        int minZ = Math.max(0, playerSeedZ - LOCAL_CEILING_SEARCH_RADIUS);
        int maxZ = Math.min(depth - 1, playerSeedZ + LOCAL_CEILING_SEARCH_RADIUS);
        int localRoomColumns = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (roomCells[index(x, playerSeedY, z, width, depth)]) {
                    localRoomColumns++;
                }
            }
        }
        int requiredLocalSeeds = Math.max(
                1,
                (int)Math.ceil(localRoomColumns * LOCAL_FLAT_CEILING_RATIO)
        );
        for (int y = playerSeedY + 1; y < seedsByY.length; y++) {
            if (seedsByY[y] <= 0) {
                continue;
            }
            int localSeeds = 0;
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (ceilingSeeds[index(x, y, z, width, depth)]) {
                        localSeeds++;
                    }
                }
            }
            if (localSeeds >= requiredLocalSeeds) {
                return y;
            }
        }

        int eligibleSeeds = 0;
        for (int y = playerSeedY + 1; y < seedsByY.length; y++) {
            eligibleSeeds += seedsByY[y];
        }
        if (eligibleSeeds <= 0) {
            return -1;
        }
        int requiredSeeds = Math.min(
                eligibleSeeds,
                Math.max(
                        MIN_STRUCTURAL_CEILING_SEEDS,
                        (int)Math.ceil(eligibleSeeds * LOW_CEILING_OUTLIER_RATIO)
                )
        );
        int accumulated = 0;
        for (int y = playerSeedY + 1; y < seedsByY.length; y++) {
            accumulated += seedsByY[y];
            if (accumulated >= requiredSeeds) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Flat floor slabs stop at their first layer. A sloped roof can also have a wide lowest row,
     * so that row is only considered flat when it has no significant, immediately adjacent upper
     * band. Stepped roof bands use an upper percentile so their ridge is kept while a one-block
     * chimney is ignored.
     */
    private static int findCameraCeilingY(
            int[] seedsByY,
            int structuralCeilingY,
            int maximumBandGap,
            int playerRoomSliceCells
    ) {
        if (structuralCeilingY < 0) {
            return -1;
        }
        int flatSlabThreshold = Math.max(
                1,
                (int)Math.ceil(Math.max(1, playerRoomSliceCells) * 0.25)
        );
        int bandMaximumY = structuralCeilingY;
        int lastOccupiedY = structuralCeilingY;
        int firstUpperOccupiedY = -1;
        for (int y = structuralCeilingY + 1; y < seedsByY.length; y++) {
            if (seedsByY[y] <= 0) {
                continue;
            }
            if (y - lastOccupiedY > Math.max(2, maximumBandGap)) {
                break;
            }
            if (firstUpperOccupiedY < 0) {
                firstUpperOccupiedY = y;
            }
            bandMaximumY = y;
            lastOccupiedY = y;
        }

        int retainedSeeds = 0;
        for (int y = structuralCeilingY; y <= bandMaximumY; y++) {
            retainedSeeds += seedsByY[y];
        }
        int upperBandSeeds = retainedSeeds - seedsByY[structuralCeilingY];
        int significantUpperBandSeeds = Math.min(
                MAX_SIGNIFICANT_UPPER_BAND_SEEDS,
                Math.max(
                        2,
                        (int)Math.ceil(
                                seedsByY[structuralCeilingY]
                                        * SIGNIFICANT_UPPER_BAND_RATIO
                        )
                )
        );
        boolean adjacentUpperBand = firstUpperOccupiedY == structuralCeilingY + 1;
        if (seedsByY[structuralCeilingY] >= flatSlabThreshold
                && (!adjacentUpperBand || upperBandSeeds < significantUpperBandSeeds)) {
            return structuralCeilingY;
        }

        int requiredSeeds = Math.max(
                1,
                (int)Math.ceil(retainedSeeds * CAMERA_CEILING_PERCENTILE)
        );
        int accumulated = 0;
        for (int y = structuralCeilingY; y <= bandMaximumY; y++) {
            accumulated += seedsByY[y];
            if (accumulated >= requiredSeeds) {
                return y;
            }
        }
        return structuralCeilingY;
    }

    /**
     * Follows the connected solid structure above the current room instead of assuming that the
     * first ceiling is also the building's final roof. This reaches a second-storey wall and its
     * high roof through the intervening floor. The full connected set is retained only as a safe
     * membership mask for ray hits; whole-roof mode removes its air-backed ceiling surfaces,
     * avoiding a several-thousand-block solid-volume transition.
     */
    private static OverheadResult expandOverheadFromRoomSeeds(
            Snapshot snapshot,
            boolean[] ceilingSeeds,
            int structuralCeilingY,
            int cameraCeilingY,
            int[] queue
    ) {
        if (structuralCeilingY < 0) {
            return OverheadResult.empty();
        }

        ScanBounds bounds = snapshot.bounds();
        int width = bounds.width();
        int height = bounds.height();
        int depth = bounds.depth();
        int plane = width * depth;
        int minSeedX = width;
        int minSeedZ = depth;
        int maxSeedX = -1;
        int maxSeedZ = -1;
        int tail = 0;
        boolean[] structuralCells = new boolean[ceilingSeeds.length];

        for (int packedIndex = 0; packedIndex < ceilingSeeds.length; packedIndex++) {
            if (!ceilingSeeds[packedIndex]) {
                continue;
            }
            int x = packedIndex % width;
            int yz = packedIndex / width;
            int z = yz % depth;
            int y = yz / depth;
            if (y < structuralCeilingY) {
                continue;
            }
            minSeedX = Math.min(minSeedX, x);
            minSeedZ = Math.min(minSeedZ, z);
            maxSeedX = Math.max(maxSeedX, x);
            maxSeedZ = Math.max(maxSeedZ, z);
            structuralCells[packedIndex] = true;
            queue[tail++] = packedIndex;
        }
        if (tail == 0) {
            return OverheadResult.empty();
        }

        int horizontalExpansion = snapshot.roofHorizontalExpansion();
        int minX = Math.max(0, minSeedX - horizontalExpansion);
        int maxX = Math.min(width - 1, maxSeedX + horizontalExpansion);
        int minZ = Math.max(0, minSeedZ - horizontalExpansion);
        int maxZ = Math.min(depth - 1, maxSeedZ + horizontalExpansion);
        int minY = Math.max(0, structuralCeilingY - 1);

        int head = 0;
        while (head < tail) {
            int current = queue[head++];
            int currentX = current % width;
            int currentYZ = current / width;
            int currentZ = currentYZ % depth;
            int currentY = currentYZ / depth;
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                int candidateY = currentY + yOffset;
                if (candidateY < minY || candidateY >= height) {
                    continue;
                }
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    int candidateZ = currentZ + zOffset;
                    if (candidateZ < minZ || candidateZ > maxZ) {
                        continue;
                    }
                    for (int xOffset = -1; xOffset <= 1; xOffset++) {
                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                            continue;
                        }
                        int candidateX = currentX + xOffset;
                        if (candidateX < minX || candidateX > maxX) {
                            continue;
                        }
                        int candidate = index(
                                candidateX,
                                candidateY,
                                candidateZ,
                                width,
                                depth
                        );
                        if (structuralCells[candidate]
                                || snapshot.roomPassable()[candidate]) {
                            continue;
                        }
                        structuralCells[candidate] = true;
                        queue[tail++] = candidate;
                    }
                }
            }
        }

        LongOpenHashSet structuralBlocks = new LongOpenHashSet(Math.max(16, tail));
        LongOpenHashSet wholeCutawayBlocks = new LongOpenHashSet(Math.max(16, tail / 2));
        LongOpenHashSet floorCutawayBlocks = new LongOpenHashSet(Math.max(16, tail / 4));
        boolean touchesHorizontalBoundary = false;
        boolean touchesTopBoundary = false;
        for (int queueIndex = 0; queueIndex < tail; queueIndex++) {
            int packedIndex = queue[queueIndex];
            int x = packedIndex % width;
            int yz = packedIndex / width;
            int z = yz % depth;
            int y = yz / depth;
            touchesHorizontalBoundary |= x == 0
                    || x == width - 1
                    || z == 0
                    || z == depth - 1;
            touchesTopBoundary |= y == height - 1;
            if (!snapshot.hardCullSafe()[packedIndex]) {
                continue;
            }

            long packedPos = BlockPos.asLong(
                    bounds.minX() + x,
                    bounds.minY() + y,
                    bounds.minZ() + z
            );
            structuralBlocks.add(packedPos);
            // The conservative non-floor view only removes air-backed undersides. Once a floor
            // camera is confirmed, remove the complete connected shell between the first ceiling
            // and the resolved ridge; otherwise stair blocks resting on walls or the previous roof
            // step remain as striped fragments.
            if (y > 0 && snapshot.roomPassable()[packedIndex - plane]) {
                wholeCutawayBlocks.add(packedPos);
            }
            if (cameraCeilingY >= 0
                    && y >= structuralCeilingY
                    && y <= cameraCeilingY) {
                floorCutawayBlocks.add(packedPos);
            }
        }
        return new OverheadResult(
                structuralBlocks,
                wholeCutawayBlocks,
                floorCutawayBlocks,
                touchesHorizontalBoundary,
                touchesTopBoundary
        );
    }

    private static boolean roomContinuesAbove(
            boolean[] roomCells,
            int x,
            int y,
            int z,
            int width,
            int height,
            int depth,
            int searchHeight
    ) {
        int highestY = Math.min(height - 1, y + Math.max(2, searchHeight));
        for (int candidateY = y + 2; candidateY <= highestY; candidateY++) {
            if (roomCells[index(x, candidateY, z, width, depth)]) {
                return true;
            }
        }
        return false;
    }

    private static int enqueue(
            boolean[] passable,
            boolean[] visited,
            int[] queue,
            int tail,
            int candidate
    ) {
        if (passable[candidate] && !visited[candidate]) {
            visited[candidate] = true;
            queue[tail++] = candidate;
        }
        return tail;
    }

    private static void flood(
            boolean[] passable,
            boolean[] visited,
            int[] queue,
            int tail,
            int width,
            int height,
            int depth
    ) {
        int head = 0;
        int plane = width * depth;
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int yz = current / width;
            int z = yz % depth;
            int y = yz / depth;
            if (x > 0) {
                tail = enqueue(passable, visited, queue, tail, current - 1);
            }
            if (x + 1 < width) {
                tail = enqueue(passable, visited, queue, tail, current + 1);
            }
            if (z > 0) {
                tail = enqueue(passable, visited, queue, tail, current - width);
            }
            if (z + 1 < depth) {
                tail = enqueue(passable, visited, queue, tail, current + width);
            }
            if (y > 0) {
                tail = enqueue(passable, visited, queue, tail, current - plane);
            }
            if (y + 1 < height) {
                tail = enqueue(passable, visited, queue, tail, current + plane);
            }
        }
    }

    private static int floodRoom(
            boolean[] passable,
            boolean[] outside,
            boolean[] roomCells,
            int[] queue,
            int seed,
            int width,
            int height,
            int depth
    ) {
        int head = 0;
        int tail = 1;
        int plane = width * depth;
        queue[0] = seed;
        roomCells[seed] = true;
        while (head < tail) {
            int current = queue[head++];
            int x = current % width;
            int yz = current / width;
            int z = yz % depth;
            int y = yz / depth;
            if (x > 0) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current - 1);
            }
            if (x + 1 < width) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current + 1);
            }
            if (z > 0) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current - width);
            }
            if (z + 1 < depth) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current + width);
            }
            if (y > 0) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current - plane);
            }
            if (y + 1 < height) {
                tail = enqueueRoom(passable, outside, roomCells, queue, tail, current + plane);
            }
        }
        return tail;
    }

    private static int enqueueRoom(
            boolean[] passable,
            boolean[] outside,
            boolean[] roomCells,
            int[] queue,
            int tail,
            int candidate
    ) {
        if (passable[candidate] && !outside[candidate] && !roomCells[candidate]) {
            roomCells[candidate] = true;
            queue[tail++] = candidate;
        }
        return tail;
    }

    private static int findPlayerSeed(Snapshot snapshot, boolean[] outside) {
        ScanBounds bounds = snapshot.bounds();
        int centerX = snapshot.playerPos().getX() - bounds.minX();
        int centerY = snapshot.playerPos().getY() - bounds.minY();
        int centerZ = snapshot.playerPos().getZ() - bounds.minZ();
        if (centerX >= 0 && centerX < bounds.width()
                && centerY >= 0 && centerY < bounds.height()
                && centerZ >= 0 && centerZ < bounds.depth()) {
            int centerIndex = index(
                    centerX,
                    centerY,
                    centerZ,
                    bounds.width(),
                    bounds.depth()
            );
            // A genuine air cell is authoritative. The enclosed-first fallback below is only for
            // a player centre occupying a door, stair or another collision cell at a threshold.
            if (snapshot.roomPassable()[centerIndex]) {
                return centerIndex;
            }
        }

        int bestEnclosedIndex = -1;
        int bestEnclosedDistanceSquared = Integer.MAX_VALUE;
        int bestAnyIndex = -1;
        int bestAnyDistanceSquared = Integer.MAX_VALUE;

        for (int yOffset = -PLAYER_SEED_SEARCH_RADIUS;
             yOffset <= PLAYER_SEED_SEARCH_RADIUS;
             yOffset++) {
            int y = centerY + yOffset;
            if (y < 0 || y >= bounds.height()) {
                continue;
            }
            for (int zOffset = -PLAYER_SEED_SEARCH_RADIUS;
                 zOffset <= PLAYER_SEED_SEARCH_RADIUS;
                 zOffset++) {
                int z = centerZ + zOffset;
                if (z < 0 || z >= bounds.depth()) {
                    continue;
                }
                for (int xOffset = -PLAYER_SEED_SEARCH_RADIUS;
                     xOffset <= PLAYER_SEED_SEARCH_RADIUS;
                     xOffset++) {
                    int x = centerX + xOffset;
                    if (x < 0 || x >= bounds.width()) {
                        continue;
                    }
                    int distanceSquared = xOffset * xOffset
                            + yOffset * yOffset
                            + zOffset * zOffset;
                    int candidate = index(x, y, z, bounds.width(), bounds.depth());
                    if (!snapshot.roomPassable()[candidate]) {
                        continue;
                    }
                    if (distanceSquared < bestAnyDistanceSquared) {
                        bestAnyIndex = candidate;
                        bestAnyDistanceSquared = distanceSquared;
                    }
                    if (!outside[candidate]
                            && distanceSquared < bestEnclosedDistanceSquared) {
                        bestEnclosedIndex = candidate;
                        bestEnclosedDistanceSquared = distanceSquared;
                    }
                }
            }
        }
        return bestEnclosedIndex >= 0 ? bestEnclosedIndex : bestAnyIndex;
    }

    private static int index(int x, int y, int z, int width, int depth) {
        return (y * depth + z) * width + x;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum ScanStatus {
        ENCLOSED,
        OUTDOOR,
        UNKNOWN
    }

    private enum WallSide {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    /** Inclusive floor slab Y and exclusive horizontal block edges for camera framing. */
    public record RoomCameraBounds(
            double minX,
            double maxX,
            double minZ,
            double maxZ,
            int floorY,
            int ceilingY
    ) {
        public double centerX() {
            return (minX + maxX) * 0.5;
        }

        public double centerZ() {
            return (minZ + maxZ) * 0.5;
        }
    }

    private record ScanBounds(int minX, int minY, int minZ, int width, int height, int depth) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX
                    && pos.getX() < minX + width
                    && pos.getY() >= minY
                    && pos.getY() < minY + height
                    && pos.getZ() >= minZ
                    && pos.getZ() < minZ + depth;
        }
    }

    private record Snapshot(
            long epoch,
            ScanBounds bounds,
            BlockPos playerPos,
            int roofExpansionLayers,
            int roofHorizontalExpansion,
            int wallExteriorDepth,
            boolean[] roomPassable,
            boolean[] outsideFloodPassable,
            boolean[] outsideSeeds,
            boolean[] hardCullSafe
    ) {
    }

    private record CellPassability(boolean roomPassable, boolean outsideFloodPassable) {
        private static final CellPassability OPEN_AIR = new CellPassability(true, true);
        private static final CellPassability BLOCKED = new CellPassability(false, false);
    }

    private record RoomResult(
            ScanBounds bounds,
            long seedPos,
            LongOpenHashSet roomAir,
            LongOpenHashSet structuralBlocks,
            LongOpenHashSet wholeCutawayBlocks,
            LongOpenHashSet floorCutawayBlocks,
            WallCutaway wallCutaway,
            RoomCameraBounds cameraBounds,
            int cameraCeilingY,
            boolean clippedHorizontally,
            boolean clippedVertically
    ) {
    }

    private record RoomGeometry(
            RoomCameraBounds cameraBounds,
            WallCutaway wallCutaway
    ) {
    }

    private record WallCutaway(
            LongOpenHashSet north,
            LongOpenHashSet south,
            LongOpenHashSet west,
            LongOpenHashSet east
    ) {
        LongOpenHashSet forSide(WallSide side) {
            return switch (side) {
                case NORTH -> north;
                case SOUTH -> south;
                case WEST -> west;
                case EAST -> east;
            };
        }

        boolean isEmpty() {
            return north.isEmpty() && south.isEmpty() && west.isEmpty() && east.isEmpty();
        }
    }

    private record OverheadResult(
            LongOpenHashSet structuralBlocks,
            LongOpenHashSet wholeCutawayBlocks,
            LongOpenHashSet floorCutawayBlocks,
            boolean touchesHorizontalBoundary,
            boolean touchesTopBoundary
    ) {
        static OverheadResult empty() {
            return new OverheadResult(
                    new LongOpenHashSet(),
                    new LongOpenHashSet(),
                    new LongOpenHashSet(),
                    false,
                    false
            );
        }
    }

    private record ScanResult(
            long epoch,
            ScanBounds bounds,
            BlockPos scanPlayerPos,
            ScanStatus status,
            RoomResult room
    ) {
    }
}
