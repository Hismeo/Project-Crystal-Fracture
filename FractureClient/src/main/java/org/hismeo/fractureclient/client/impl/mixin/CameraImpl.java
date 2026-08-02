package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.hismeo.fractureclient.client.control.FloorAwareCameraController;
import org.joml.Matrix4f;

public final class CameraImpl {
    private static final float ORTHOGRAPHIC_FAR_PLANE = 1024.0F;
    private static final CurveAnimatedDeadZone DEAD_ZONE = new CurveAnimatedDeadZone();

    /**
     * 构建正交投影矩阵。
     * <p>
     * 该矩阵以 {@link OrthographicCameraConfig#size} 作为纵向半尺寸，
     * 横向半尺寸按窗口宽高比扩展：{@code size * aspect}。
     * 当 size 过小导致画面退化时，会用 {@code minScale} 作为下限保护。
     * </p>
     *
     * @param minecraft 客户端实例，用于读取窗口宽高
     * @param minScale  投影半尺寸下限，防止 size 过小
     * @return 适配当前窗口比例的正交投影矩阵
     */
    public static Matrix4f orthoMatrix4f(Minecraft minecraft, float minScale) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        float size = FloorAwareCameraController.getRenderSize();
        float rightLeft = Math.max(minScale, size * width / height);
        float minSize = Math.max(minScale, size);
        // The pitched outdoor camera can sit on the player, placing the lower part of the ground
        // behind camera depth zero. The floor-aware controller keeps that span visible while
        // tightening the near plane again as an indoor camera rises above the active storey.
        return new Matrix4f().setOrtho(
                -rightLeft, rightLeft,
                -minSize, minSize,
                FloorAwareCameraController.getAdaptiveNearPlane(),
                ORTHOGRAPHIC_FAR_PLANE
        );
    }

    public static void resetTracking() {
        DEAD_ZONE.reset();
        FloorAwareCameraController.reset();
    }

    public static void deadZone(Camera camera, double playerX, double playerY, double playerZ) {
        Vec3 cameraPosition = camera.getPosition();
        CurveAnimatedDeadZone.Position tracked = DEAD_ZONE.update(
                cameraPosition.x,
                cameraPosition.y,
                cameraPosition.z,
                playerX,
                playerY,
                playerZ,
                OrthographicCameraConfig.deadZoneTransitionSeconds,
                System.nanoTime()
        );
        setFloorAwarePosition(camera, tracked, playerX, playerY, playerZ);
    }

    private static void setFloorAwarePosition(
            Camera camera,
            CurveAnimatedDeadZone.Position tracked,
            double playerX,
            double playerY,
            double playerZ
    ) {
        Vec3 adjusted = FloorAwareCameraController.apply(
                tracked.x(),
                tracked.y(),
                tracked.z(),
                playerX,
                playerY,
                playerZ
        );
        camera.setPosition(adjusted.x, adjusted.y, adjusted.z);
    }
}
