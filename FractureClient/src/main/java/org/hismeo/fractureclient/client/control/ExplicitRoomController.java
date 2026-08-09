package org.hismeo.fractureclient.client.control;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.room.RoomRegion;
import org.hismeo.fractureclient.client.room.RoomRegionStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolves authored room masks before the heuristic room scanner is allowed to run. */
public final class ExplicitRoomController {
    private static final int ENTER_CONFIRMATIONS = 2;
    private static final int EXIT_CONFIRMATIONS = 3;
    private static final int MAX_CACHED_WALL_BLOCKS = 65_536;
    private static final int MAX_STACK_VERTICAL_GAP = 4;
    private static final int NO_ROOF_Y = Integer.MIN_VALUE;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private static ClientLevel trackedLevel;
    private static RoomRegion activeRoom;
    private static RoomRegion pendingRoom;
    private static int enterEvidence;
    private static int exitEvidence;

    private static RoomRegion cachedRoom;
    private static Direction cachedXSide;
    private static Direction cachedZSide;
    private static int cachedExteriorDepth = -1;
    private static final LongOpenHashSet CACHED_ROOF = new LongOpenHashSet();
    private static final LongOpenHashSet CACHED_UPPER_STOREYS = new LongOpenHashSet();
    private static final Long2IntOpenHashMap CACHED_ROOF_TOPS = new Long2IntOpenHashMap();
    private static final LongOpenHashSet CACHED_WALLS = new LongOpenHashSet();
    private static final List<RoomRegion> CACHED_STACKED_ROOMS = new ArrayList<>();

    private ExplicitRoomController() {
    }

    public static void tick(Minecraft minecraft, BlockPos playerPos) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || RoomSelectionController.isActive()) {
            reset();
            return;
        }
        if (trackedLevel != level) {
            reset();
            trackedLevel = level;
        }

        RoomRegion candidate = RoomRegionStore.findContaining(minecraft, playerPos);
        if (candidate != null) {
            exitEvidence = 0;
            if (sameRoom(activeRoom, candidate)) {
                if (activeRoom != candidate) {
                    activeRoom = candidate;
                    invalidateCaches();
                }
                pendingRoom = null;
                enterEvidence = 0;
                return;
            }

            if (sameRoom(pendingRoom, candidate)) {
                pendingRoom = candidate;
                enterEvidence++;
            } else {
                pendingRoom = candidate;
                enterEvidence = 1;
            }
            if (enterEvidence >= ENTER_CONFIRMATIONS) {
                activeRoom = candidate;
                pendingRoom = null;
                enterEvidence = 0;
                invalidateCaches();
            }
            return;
        }

        pendingRoom = null;
        enterEvidence = 0;
        if (activeRoom != null) {
            exitEvidence++;
            if (exitEvidence >= EXIT_CONFIRMATIONS) {
                activeRoom = null;
                exitEvidence = 0;
                invalidateCaches();
            }
        }
    }

    public static boolean hasActiveRoom() {
        return activeRoom != null;
    }

    public static RoomCullScanner.RoomCameraBounds getActiveCameraBounds() {
        RoomRegion room = activeRoom;
        if (room == null || room.isEmpty()) {
            return null;
        }
        return new RoomCullScanner.RoomCameraBounds(
                room.minX(),
                room.maxX() + 1.0,
                room.minZ(),
                room.maxZ() + 1.0,
                room.floorY(),
                room.ceilingY()
        );
    }

    /** Prevents an AABB-framed camera from settling into the missing corner of an L/U room. */
    public static Vec3 constrainFocus(Vec3 desired) {
        RoomRegion room = activeRoom;
        if (room == null) {
            return desired;
        }
        RoomRegion.HorizontalPoint point = room.closestInteriorPoint(desired.x, desired.z);
        return new Vec3(point.x(), desired.y, point.z());
    }

    public static LongOpenHashSet resolveCullCandidates(
            LongOpenHashSet rayCandidates,
            BlockPos playerPos
    ) {
        RoomRegion room = activeRoom;
        if (room == null) {
            return rayCandidates;
        }
        ensureCaches(room, playerPos);

        int maximumLayers = Math.max(
                1,
                Math.min(32, OrthographicCameraConfig.cullRoomMaximumSolidLayers)
        );
        boolean floorCamera = FloorAwareCameraController.isEnabledForCurrentView();
        boolean wallCutaway = floorCamera && OrthographicCameraConfig.cullNearestRoomWalls;
        if (floorCamera && !wallCutaway) {
            return resolveRoofOnlyCandidates(rayCandidates);
        }
        LongOpenHashSet wholeFloorCutaway = floorCamera
                ? createWholeFloorCutaway()
                : CACHED_ROOF;
        LongOpenHashSet rayRoofIntersection = new LongOpenHashSet();
        LongOpenHashSet rayWallIntersection = new LongOpenHashSet();
        LongIterator iterator = rayCandidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            int x = BlockPos.getX(packedPos);
            int y = BlockPos.getY(packedPos);
            int z = BlockPos.getZ(packedPos);
            int roofTopY = CACHED_ROOF_TOPS.getOrDefault(
                    RoomRegion.packColumn(x, z),
                    NO_ROOF_Y
            );
            boolean roofCandidate = floorCamera
                    ? wholeFloorCutaway.contains(packedPos)
                    : roofTopY != NO_ROOF_Y
                    && y <= roofTopY
                    && y > roofTopY - maximumLayers
                    && y >= minimumRoofSearchY(room);
            if (roofCandidate) {
                rayRoofIntersection.add(packedPos);
            }
            if (wallCutaway && CACHED_WALLS.contains(packedPos)) {
                rayWallIntersection.add(packedPos);
            }
        }

        int maximumWholeCutaway = Math.max(
                64,
                Math.min(8192, OrthographicCameraConfig.cullWholeRoofMaximumBlocks)
        );
        LongOpenHashSet result = new LongOpenHashSet(rayRoofIntersection);
        result.addAll(rayWallIntersection);

        boolean canCullWholeRoof = OrthographicCameraConfig.cullWholeRoomRoof
                && !wholeFloorCutaway.isEmpty()
                && wholeFloorCutaway.size() <= maximumWholeCutaway
                && (floorCamera || !rayRoofIntersection.isEmpty());
        if (canCullWholeRoof) {
            result.addAll(wholeFloorCutaway);
        }
        if (wallCutaway
                && !CACHED_WALLS.isEmpty()
                && CACHED_WALLS.size() <= maximumWholeCutaway) {
            result.addAll(CACHED_WALLS);
        }
        return result;
    }

    private static LongOpenHashSet resolveRoofOnlyCandidates(LongOpenHashSet rayCandidates) {
        LongOpenHashSet floorCutaway = createWholeFloorCutaway();
        int maximumWholeCutaway = Math.max(
                64,
                Math.min(8192, OrthographicCameraConfig.cullWholeRoofMaximumBlocks)
        );
        if (OrthographicCameraConfig.cullWholeRoomRoof
                && !floorCutaway.isEmpty()
                && floorCutaway.size() <= maximumWholeCutaway) {
            return floorCutaway;
        }

        LongOpenHashSet result = new LongOpenHashSet();
        LongIterator iterator = rayCandidates.iterator();
        while (iterator.hasNext()) {
            long packedPos = iterator.nextLong();
            if (floorCutaway.contains(packedPos)) {
                result.add(packedPos);
            }
        }
        return result;
    }

    private static LongOpenHashSet createWholeFloorCutaway() {
        LongOpenHashSet result = new LongOpenHashSet(
                CACHED_ROOF.size() + CACHED_UPPER_STOREYS.size()
        );
        result.addAll(CACHED_ROOF);
        result.addAll(CACHED_UPPER_STOREYS);
        return result;
    }

    public static void reset() {
        trackedLevel = null;
        activeRoom = null;
        pendingRoom = null;
        enterEvidence = 0;
        exitEvidence = 0;
        invalidateCaches();
    }

    private static void ensureCaches(RoomRegion room, BlockPos playerPos) {
        Direction xSide = resolveXSide(room, playerPos);
        Direction zSide = resolveZSide(room, playerPos);
        int exteriorDepth = Math.max(
                0,
                Math.min(6, OrthographicCameraConfig.cullRoomWallExteriorDepth)
        );
        if (cachedRoom == room
                && cachedXSide == xSide
                && cachedZSide == zSide
                && cachedExteriorDepth == exteriorDepth) {
            return;
        }

        cachedRoom = room;
        cachedXSide = xSide;
        cachedZSide = zSide;
        cachedExteriorDepth = exteriorDepth;
        CACHED_ROOF.clear();
        CACHED_UPPER_STOREYS.clear();
        CACHED_ROOF_TOPS.clear();
        CACHED_WALLS.clear();
        CACHED_STACKED_ROOMS.clear();

        ClientLevel level = trackedLevel;
        int roofShellDepth = Math.max(
                1,
                Math.min(5, OrthographicCameraConfig.cullRoomRoofExpansionLayers)
        );
        BlockPos.MutableBlockPos roofPos = new BlockPos.MutableBlockPos();
        LongIterator roofIterator = room.columnIterator();
        while (roofIterator.hasNext()) {
            long packedColumn = roofIterator.nextLong();
            int x = RoomRegion.unpackX(packedColumn);
            int z = RoomRegion.unpackZ(packedColumn);
            cacheRoofColumn(level, room, roofPos, x, z, roofShellDepth);
        }

        List<RoomRegion> stackedRooms = findStackedRoomsAbove(room);
        CACHED_STACKED_ROOMS.addAll(stackedRooms);
        for (RoomRegion stackedRoom : stackedRooms) {
            cacheUpperStorey(level, stackedRoom, roofPos, exteriorDepth);
        }

        Direction[] selectedSides = {xSide, zSide};
        LongIterator wallIterator = room.columnIterator();
        while (wallIterator.hasNext() && CACHED_WALLS.size() <= MAX_CACHED_WALL_BLOCKS) {
            long packedColumn = wallIterator.nextLong();
            int x = RoomRegion.unpackX(packedColumn);
            int z = RoomRegion.unpackZ(packedColumn);
            for (Direction side : selectedSides) {
                int neighborX = x + side.getStepX();
                int neighborZ = z + side.getStepZ();
                if (room.containsColumn(neighborX, neighborZ)) {
                    continue;
                }
                for (int outward = 0; outward <= exteriorDepth; outward++) {
                    int wallX = neighborX + side.getStepX() * outward;
                    int wallZ = neighborZ + side.getStepZ() * outward;
                    for (int y = room.floorY(); y < room.ceilingY(); y++) {
                        CACHED_WALLS.add(BlockPos.asLong(wallX, y, wallZ));
                        if (CACHED_WALLS.size() > MAX_CACHED_WALL_BLOCKS) {
                            break;
                        }
                    }
                    if (CACHED_WALLS.size() > MAX_CACHED_WALL_BLOCKS) {
                        break;
                    }
                }
            }
        }
    }

    private static Direction resolveXSide(RoomRegion room, BlockPos playerPos) {
        double yawRadians = Math.toRadians(CameraRotateController.getRenderYaw());
        double forwardX = -Math.sin(yawRadians);
        if (Math.abs(forwardX) > 1.0e-4) {
            return forwardX > 0.0 ? Direction.WEST : Direction.EAST;
        }
        double centerX = (room.minX() + room.maxX() + 1.0) * 0.5;
        return playerPos.getX() + 0.5 <= centerX ? Direction.WEST : Direction.EAST;
    }

    private static Direction resolveZSide(RoomRegion room, BlockPos playerPos) {
        double yawRadians = Math.toRadians(CameraRotateController.getRenderYaw());
        double forwardZ = Math.cos(yawRadians);
        if (Math.abs(forwardZ) > 1.0e-4) {
            return forwardZ > 0.0 ? Direction.NORTH : Direction.SOUTH;
        }
        double centerZ = (room.minZ() + room.maxZ() + 1.0) * 0.5;
        return playerPos.getZ() + 0.5 <= centerZ ? Direction.NORTH : Direction.SOUTH;
    }

    private static boolean sameRoom(RoomRegion first, RoomRegion second) {
        return first != null && second != null && first.id().equals(second.id());
    }

    /**
     * Resolves an authored vertical room chain without adding linkage fields to room_regions.json.
     * A room is the next storey when its footprint overlaps and its floor begins just above the
     * lower room's authored ceiling. Repeating the search supports towers with more than two floors.
     */
    private static List<RoomRegion> findStackedRoomsAbove(RoomRegion baseRoom) {
        List<RoomRegion> candidates = RoomRegionStore.roomsForCurrentWorld(Minecraft.getInstance());
        List<RoomRegion> frontier = new ArrayList<>();
        List<RoomRegion> result = new ArrayList<>();
        Set<UUID> included = new HashSet<>();
        included.add(baseRoom.id());
        frontier.add(baseRoom);

        for (int head = 0; head < frontier.size(); head++) {
            RoomRegion lowerRoom = frontier.get(head);
            for (RoomRegion candidate : candidates) {
                if (included.contains(candidate.id())
                        || !isDirectlyStackedAbove(lowerRoom, candidate)) {
                    continue;
                }
                included.add(candidate.id());
                frontier.add(candidate);
                result.add(candidate);
            }
        }
        result.sort(Comparator
                .comparingInt(RoomRegion::floorY)
                .thenComparing(RoomRegion::name));
        return result;
    }

    private static boolean isDirectlyStackedAbove(RoomRegion lowerRoom, RoomRegion upperRoom) {
        int verticalGap = upperRoom.floorY() - lowerRoom.ceilingY();
        return verticalGap >= 0
                && verticalGap <= MAX_STACK_VERTICAL_GAP
                && footprintsOverlap(lowerRoom, upperRoom);
    }

    private static boolean footprintsOverlap(RoomRegion first, RoomRegion second) {
        RoomRegion smaller = first.columnCount() <= second.columnCount() ? first : second;
        RoomRegion larger = smaller == first ? second : first;
        LongIterator iterator = smaller.columnIterator();
        while (iterator.hasNext()) {
            long packedColumn = iterator.nextLong();
            if (larger.containsColumn(
                    RoomRegion.unpackX(packedColumn),
                    RoomRegion.unpackZ(packedColumn)
            )) {
                return true;
            }
        }
        return false;
    }

    /** Caches the complete solid upper storey, including its floor, boundary walls and roof. */
    private static void cacheUpperStorey(
            ClientLevel level,
            RoomRegion room,
            BlockPos.MutableBlockPos pos,
            int exteriorDepth
    ) {
        if (level == null) {
            return;
        }
        int minimumY = Math.max(level.getMinBuildHeight(), room.floorY() - 1);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, room.ceilingY());
        if (maximumY < minimumY) {
            return;
        }

        LongIterator iterator = room.columnIterator();
        while (iterator.hasNext()) {
            long packedColumn = iterator.nextLong();
            int x = RoomRegion.unpackX(packedColumn);
            int z = RoomRegion.unpackZ(packedColumn);
            cacheUpperStoreyColumn(level, pos, x, z, minimumY, maximumY);

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                int neighborX = x + direction.getStepX();
                int neighborZ = z + direction.getStepZ();
                if (room.containsColumn(neighborX, neighborZ)) {
                    continue;
                }
                cacheUpperStoreyBoundary(
                        level,
                        pos,
                        neighborX,
                        neighborZ,
                        direction,
                        minimumY,
                        maximumY,
                        exteriorDepth
                );
            }
        }
    }

    private static void cacheUpperStoreyBoundary(
            ClientLevel level,
            BlockPos.MutableBlockPos pos,
            int startX,
            int startZ,
            Direction outwardDirection,
            int minimumY,
            int maximumY,
            int exteriorDepth
    ) {
        for (int y = minimumY; y <= maximumY; y++) {
            for (int outward = 0; outward <= exteriorDepth; outward++) {
                int x = startX + outwardDirection.getStepX() * outward;
                int z = startZ + outwardDirection.getStepZ() * outward;
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!BlockCullController.isSafeHardCullState(state)) {
                    // Never jump across an air gap into an adjacent building.
                    break;
                }
                CACHED_UPPER_STOREYS.add(BlockPos.asLong(x, y, z));
            }
        }
    }

    private static void cacheUpperStoreyColumn(
            ClientLevel level,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int minimumY,
            int maximumY
    ) {
        for (int y = minimumY; y <= maximumY; y++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (BlockCullController.isSafeHardCullState(state)) {
                CACHED_UPPER_STOREYS.add(BlockPos.asLong(x, y, z));
            }
        }
    }

    /** Invalidates a derived sloped-roof shell when the authored room is edited in-world. */
    public static void markDirty(ClientLevel level, BlockPos changedPos) {
        RoomRegion room = activeRoom;
        if (room == null
                || level != trackedLevel) {
            return;
        }
        if (mayAffectCachedRoom(room, changedPos, room.floorY())) {
            invalidateCaches();
            return;
        }
        for (RoomRegion stackedRoom : CACHED_STACKED_ROOMS) {
            if (mayAffectCachedRoom(stackedRoom, changedPos, stackedRoom.floorY() - 1)) {
                invalidateCaches();
                return;
            }
        }
    }

    private static boolean mayAffectCachedRoom(
            RoomRegion room,
            BlockPos changedPos,
            int minimumY
    ) {
        int expansion = 1 + Math.max(0, cachedExteriorDepth);
        return changedPos.getY() >= minimumY
                && changedPos.getY() <= room.ceilingY()
                && changedPos.getX() >= room.minX() - expansion
                && changedPos.getX() <= room.maxX() + expansion
                && changedPos.getZ() >= room.minZ() - expansion
                && changedPos.getZ() <= room.maxZ() + expansion;
    }

    private static void cacheRoofColumn(
            ClientLevel level,
            RoomRegion room,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            int shellDepth
    ) {
        if (level == null) {
            return;
        }
        int minimumY = minimumRoofSearchY(room);
        int roofTopY = NO_ROOF_Y;
        for (int y = room.ceilingY(); y >= minimumY; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (RoomCullScanner.isRoomPassable(level, pos, state)) {
                continue;
            }
            if (!BlockCullController.isSafeHardCullState(state)) {
                return;
            }
            roofTopY = y;
            break;
        }
        if (roofTopY == NO_ROOF_Y) {
            return;
        }

        CACHED_ROOF_TOPS.put(RoomRegion.packColumn(x, z), roofTopY);
        int cachedLayers = 0;
        for (int y = roofTopY; y >= minimumY && cachedLayers < shellDepth; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (RoomCullScanner.isRoomPassable(level, pos, state)
                    || !BlockCullController.isSafeHardCullState(state)) {
                break;
            }
            CACHED_ROOF.add(BlockPos.asLong(x, y, z));
            cachedLayers++;
        }
    }

    private static int minimumRoofSearchY(RoomRegion room) {
        return Math.min(room.ceilingY(), room.floorY() + 2);
    }

    private static void invalidateCaches() {
        cachedRoom = null;
        cachedXSide = null;
        cachedZSide = null;
        cachedExteriorDepth = -1;
        CACHED_ROOF.clear();
        CACHED_UPPER_STOREYS.clear();
        CACHED_ROOF_TOPS.clear();
        CACHED_WALLS.clear();
        CACHED_STACKED_ROOMS.clear();
    }
}
