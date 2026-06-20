package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.joml.Matrix4f;

public final class CameraImpl {
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

        float size = OrthographicCameraConfig.size;
        float rightLeft = Math.max(minScale, size * width / height);
        float minSize = Math.max(minScale, size);
        return new Matrix4f().setOrtho(
                -rightLeft, rightLeft,
                -minSize, minSize,
                -100, 100
        );
    }

    static boolean followingX = false;
    static boolean followingZ = false;
    static boolean followingY = false;
    static final double ENTER_X = 3;
    static final double EXIT_X = 11.5;
    static final double ENTER_Z = 2;
    static final double EXIT_Z = 6.4;
    static final double ENTER_Y = 0.1;
    static final double EXIT_Y = 6;
    //TODO 集合函数
    public static void deadZone(Camera camera, double playerX, double playerY, double playerZ) {
        Vec3 camPos = camera.getPosition();
        double dx = playerX - camPos.x;
        double dz = playerZ - camPos.z;
        double dy = playerY - camPos.y;

        // 瞬移保护
        if (Mth.length(dx, dz, dy) > 40) {
            camera.setPosition(playerX, playerY, playerZ);
            followingX = false;
            followingZ = false;
            followingY = false;
            return;
        }

        double absX = Math.abs(dx);
        if (!followingX && absX > ENTER_X) followingX = true;
        else if (followingX && absX < EXIT_X) followingX = false;

        double absY = Math.abs(dy);
        if (!followingY && absY > ENTER_Y) followingY = true;
        else if (followingY && absY < EXIT_Y) followingY = false;

        double absZ = Math.abs(dz);
        if (!followingZ && absZ > ENTER_Z) followingZ = true;
        else if (followingZ && absZ < EXIT_Z) followingZ = false;

        double newX = followingX ? move(playerX, dx, ENTER_X, camPos.x, 0.01) : camPos.x;
        double newY = followingY ? move(playerY, dy, ENTER_Y, camPos.y, 0.005) : camPos.y;
        double newZ = followingZ ? move(playerZ, dz, ENTER_Z, camPos.z, 0.01) : camPos.z;

        camera.setPosition(newX, newY, newZ);
    }

    private static double move(double player, double d, double enter, double cam, double lerp) {
        double targetY = player - Math.signum(d) * enter * 0.5;
        double move = Mth.lerp(lerp, cam, targetY) - cam;
        if (Math.abs(move) > 0.01) {
            return cam + move;
        } else {
            return cam;
        }
    }
}
