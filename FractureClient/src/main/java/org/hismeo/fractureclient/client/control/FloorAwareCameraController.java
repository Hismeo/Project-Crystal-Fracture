package org.hismeo.fractureclient.client.control;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;

/** Frames the active storey and places the orthographic camera just inside its ceiling. */
public final class FloorAwareCameraController {
    private static final double MIN_DOWNWARD_COMPONENT = 0.10;
    private static final double RAY_START_PADDING = 2.0;
    private static final double MIN_CULL_RAY_DISTANCE = 4.0;
    private static final double MAX_FRAME_SECONDS = 0.10;
    private static final double NEAR_TARGET_EPSILON = 0.01;
    private static final double SETTLE_EXPONENT = 4.605170186;
    private static final double MIN_VIEWPORT_HALF_SPAN = 1.0;
    private static final double MIN_DEPTH_SINE = 0.10;
    private static final double OUTDOOR_HEIGHT_ALLOWANCE = 32.0;
    private static final double MIN_ROOM_HEIGHT_ALLOWANCE = 8.0;
    private static final double ROOM_HEIGHT_PADDING = 4.0;
    private static final double MAX_ROOM_HEIGHT_ALLOWANCE = 32.0;
    private static final double NEAR_PLANE_PADDING = 0.25;
    private static final double MIN_ADAPTIVE_NEAR_PLANE = -128.0;
    private static final double MAX_ADAPTIVE_NEAR_PLANE = 0.05;

    private static double currentDistance;
    private static double currentFocusOffsetX;
    private static double currentFocusOffsetY;
    private static double currentFocusOffsetZ;
    private static double currentSize;
    private static float currentPitch;
    private static double currentHeightAllowance;
    private static boolean stateInitialised;
    private static long lastUpdateNanos;

    private FloorAwareCameraController() {
    }

    /**
     * Frames small rooms at their centre. In a room larger than the orthographic viewport, the
     * focus follows the player only inside the range that keeps the viewport within room bounds.
     */
    public static Vec3 apply(
            double baseX,
            double baseY,
            double baseZ,
            double playerX,
            double playerY,
            double playerZ
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        RoomCullScanner.RoomCameraBounds roomBounds = activeRoomBounds(minecraft);
        float basePitch = (float)clamp(OrthographicCameraConfig.pitch, -89.0, 89.0);
        float targetPitch = roomBounds == null
                ? basePitch
                : (float)clamp(OrthographicCameraConfig.floorCameraPitch, 25.0, 85.0);
        double baseSize = Math.max(0.1, OrthographicCameraConfig.size);
        double targetSize = roomBounds == null
                ? baseSize
                : baseSize * clamp(
                        OrthographicCameraConfig.floorCameraSizeMultiplier,
                        0.1,
                        2.0
                );
        double alpha = updateClock(basePitch, baseSize);
        currentPitch += (targetPitch - currentPitch) * (float)alpha;
        currentSize += (targetSize - currentSize) * alpha;
        double targetHeightAllowance = roomBounds == null
                ? OUTDOOR_HEIGHT_ALLOWANCE
                : clamp(
                        roomBounds.ceilingY() + 1.0 - playerY + ROOM_HEIGHT_PADDING,
                        MIN_ROOM_HEIGHT_ALLOWANCE,
                        MAX_ROOM_HEIGHT_ALLOWANCE
                );
        currentHeightAllowance += (targetHeightAllowance - currentHeightAllowance) * alpha;

        Vec3 forward = viewForward(currentPitch);
        Vec3 targetFocus = roomBounds == null
                ? new Vec3(baseX, baseY, baseZ)
                : frameRoom(
                        minecraft,
                        roomBounds,
                        playerX,
                        playerY,
                        playerZ,
                        forward
                );
        if (roomBounds != null && ExplicitRoomController.hasActiveRoom()) {
            targetFocus = ExplicitRoomController.constrainFocus(targetFocus);
        }

        double targetOffsetX = targetFocus.x - baseX;
        double targetOffsetY = targetFocus.y - baseY;
        double targetOffsetZ = targetFocus.z - baseZ;
        currentFocusOffsetX += (targetOffsetX - currentFocusOffsetX) * alpha;
        currentFocusOffsetY += (targetOffsetY - currentFocusOffsetY) * alpha;
        currentFocusOffsetZ += (targetOffsetZ - currentFocusOffsetZ) * alpha;

        Vec3 focus = new Vec3(
                baseX + currentFocusOffsetX,
                baseY + currentFocusOffsetY,
                baseZ + currentFocusOffsetZ
        );
        if (roomBounds != null && ExplicitRoomController.hasActiveRoom()) {
            focus = ExplicitRoomController.constrainFocus(focus);
            currentFocusOffsetX = focus.x - baseX;
            currentFocusOffsetY = focus.y - baseY;
            currentFocusOffsetZ = focus.z - baseZ;
        }
        double targetDistance = targetDistance(focus.y, forward, roomBounds);
        currentDistance += (targetDistance - currentDistance) * alpha;
        if (Math.abs(targetDistance - currentDistance) <= NEAR_TARGET_EPSILON) {
            currentDistance = targetDistance;
        }
        return focus.subtract(forward.scale(currentDistance));
    }

    /** Pitch shared by rendering, camera placement and cutaway rays. */
    public static float getRenderPitch() {
        return stateInitialised ? currentPitch : OrthographicCameraConfig.pitch;
    }

    /** Orthographic half-height shared by projection, framing and cutaway ray length. */
    public static float getRenderSize() {
        return (float)(stateInitialised
                ? currentSize
                : Math.max(0.1, OrthographicCameraConfig.size));
    }

    /**
     * Near depth for the orthographic projection. A ground plane spans camera depth when the
     * view is pitched, so a fixed positive near plane cuts away the lower part of an outdoor
     * viewport. Keep that projected ground span in range, then progressively reduce the extra
     * height allowance as the camera settles into an authored/scanned room.
     */
    public static float getAdaptiveNearPlane() {
        double pitchRadians = Math.toRadians(getRenderPitch());
        double verticalComponent = Math.abs(Math.sin(pitchRadians));
        double horizontalComponent = Math.abs(Math.cos(pitchRadians));
        double groundDepthSpan = getRenderSize()
                * horizontalComponent
                / Math.max(MIN_DEPTH_SINE, verticalComponent);
        double nearPlane = currentDistance
                - groundDepthSpan
                - currentHeightAllowance * verticalComponent
                - NEAR_PLANE_PADDING;
        return (float)clamp(
                nearPlane,
                MIN_ADAPTIVE_NEAR_PLANE,
                MAX_ADAPTIVE_NEAR_PLANE
        );
    }

    /** Keeps cutaway rays on the same near-to-player segment as the physical floor camera. */
    public static double limitOcclusionRayDistance(double configuredDistance) {
        if (currentDistance <= NEAR_TARGET_EPSILON) {
            return configuredDistance;
        }
        return Math.min(
                configuredDistance,
                Math.max(MIN_CULL_RAY_DISTANCE, currentDistance + RAY_START_PADDING)
        );
    }

    public static boolean isEnabledForCurrentView() {
        if (!OrthographicCameraConfig.floorAwareCamera) {
            return false;
        }
        return -viewForward(getRenderPitch()).y >= MIN_DOWNWARD_COMPONENT;
    }

    public static void reset() {
        currentDistance = 0.0;
        currentFocusOffsetX = 0.0;
        currentFocusOffsetY = 0.0;
        currentFocusOffsetZ = 0.0;
        currentSize = Math.max(0.1, OrthographicCameraConfig.size);
        currentPitch = OrthographicCameraConfig.pitch;
        currentHeightAllowance = OUTDOOR_HEIGHT_ALLOWANCE;
        stateInitialised = false;
        lastUpdateNanos = 0L;
    }

    private static RoomCullScanner.RoomCameraBounds activeRoomBounds(Minecraft minecraft) {
        if (!OrthographicCameraConfig.floorAwareCamera
                || minecraft.player == null
                || RoomSelectionController.isActive()) {
            return null;
        }
        RoomCullScanner.RoomCameraBounds explicitBounds =
                ExplicitRoomController.getActiveCameraBounds();
        if (explicitBounds != null) {
            return explicitBounds;
        }
        BlockPos playerPos = BlockPos.containing(
                minecraft.player.getBoundingBox().getCenter()
        );
        return RoomCullScanner.getActiveRoomCameraBounds(playerPos);
    }

    private static Vec3 frameRoom(
            Minecraft minecraft,
            RoomCullScanner.RoomCameraBounds room,
            double playerX,
            double playerY,
            double playerZ,
            Vec3 forward
    ) {
        double horizontalLength = Math.hypot(forward.x, forward.z);
        if (horizontalLength < 1.0e-5) {
            return new Vec3(room.centerX(), playerY, room.centerZ());
        }

        double depthX = forward.x / horizontalLength;
        double depthZ = forward.z / horizontalLength;
        double rightX = -depthZ;
        double rightZ = depthX;

        AxisInterval rightInterval = projectRoom(
                room,
                rightX,
                rightZ
        );
        AxisInterval depthInterval = projectRoom(
                room,
                depthX,
                depthZ
        );

        int windowWidth = Math.max(1, minecraft.getWindow().getWidth());
        int windowHeight = Math.max(1, minecraft.getWindow().getHeight());
        double aspect = clamp((double)windowWidth / windowHeight, 0.25, 4.0);
        double size = Math.max(0.1, currentSize);
        double margin = clamp(OrthographicCameraConfig.floorCameraRoomMargin, 0.0, 4.0);
        double halfScreenWidth = Math.max(
                MIN_VIEWPORT_HALF_SPAN,
                size * aspect - margin
        );
        // On a level floor, screen-space vertical displacement is ground depth times sin(pitch).
        double downwardComponent = Math.max(MIN_DOWNWARD_COMPONENT, -forward.y);
        double halfGroundDepth = Math.max(
                MIN_VIEWPORT_HALF_SPAN,
                (size - margin) / downwardComponent
        );

        double playerRight = playerX * rightX + playerZ * rightZ;
        double playerDepth = playerX * depthX + playerZ * depthZ;
        double framedRight = frameAxis(rightInterval, playerRight, halfScreenWidth);
        double framedDepth = frameAxis(depthInterval, playerDepth, halfGroundDepth);
        return new Vec3(
                rightX * framedRight + depthX * framedDepth,
                playerY,
                rightZ * framedRight + depthZ * framedDepth
        );
    }

    private static AxisInterval projectRoom(
            RoomCullScanner.RoomCameraBounds room,
            double axisX,
            double axisZ
    ) {
        double first = room.minX() * axisX + room.minZ() * axisZ;
        double second = room.minX() * axisX + room.maxZ() * axisZ;
        double third = room.maxX() * axisX + room.minZ() * axisZ;
        double fourth = room.maxX() * axisX + room.maxZ() * axisZ;
        return new AxisInterval(
                Math.min(Math.min(first, second), Math.min(third, fourth)),
                Math.max(Math.max(first, second), Math.max(third, fourth))
        );
    }

    private static double frameAxis(
            AxisInterval roomInterval,
            double playerCoordinate,
            double viewportHalfSpan
    ) {
        if (roomInterval.maximum() - roomInterval.minimum() <= viewportHalfSpan * 2.0) {
            return (roomInterval.minimum() + roomInterval.maximum()) * 0.5;
        }
        return clamp(
                playerCoordinate,
                roomInterval.minimum() + viewportHalfSpan,
                roomInterval.maximum() - viewportHalfSpan
        );
    }

    private static double targetDistance(
            double focusY,
            Vec3 forward,
            RoomCullScanner.RoomCameraBounds roomBounds
    ) {
        if (roomBounds == null) {
            return 0.0;
        }

        double downwardComponent = -forward.y;
        if (downwardComponent < MIN_DOWNWARD_COMPONENT) {
            return 0.0;
        }

        double clearance = clamp(
                OrthographicCameraConfig.floorCameraCeilingClearance,
                0.05,
                6.0
        );
        // A block at ceilingY occupies [ceilingY, ceilingY + 1]. The camera deliberately rises
        // above it so the current floor slab/roof becomes a visible cutaway again.
        double targetCameraY = roomBounds.ceilingY() + 1.0 + clearance;
        double verticalLift = Math.max(0.0, targetCameraY - focusY);
        double maximumDistance = clamp(
                OrthographicCameraConfig.floorCameraMaximumDistance,
                4.0,
                96.0
        );
        return clamp(verticalLift / downwardComponent, 0.0, maximumDistance);
    }

    private static double updateClock(float basePitch, double baseSize) {
        long now = System.nanoTime();
        if (!stateInitialised) {
            currentPitch = basePitch;
            currentSize = baseSize;
            stateInitialised = true;
            lastUpdateNanos = now;
            return 0.0;
        }

        double elapsedSeconds = Math.min(
                MAX_FRAME_SECONDS,
                Math.max(0.0, (now - lastUpdateNanos) * 1.0e-9)
        );
        lastUpdateNanos = now;
        double transitionSeconds = clamp(
                OrthographicCameraConfig.floorCameraTransitionSeconds,
                0.05,
                3.0
        );
        return 1.0 - Math.exp(
                -SETTLE_EXPONENT * elapsedSeconds / transitionSeconds
        );
    }

    private static Vec3 viewForward(float pitch) {
        return Vec3.directionFromRotation(
                pitch,
                CameraRotateController.getRenderYaw()
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record AxisInterval(double minimum, double maximum) {
    }
}
