package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.joml.Matrix4f;

public interface CameraImpl {
    /**
     * 构建正交投影矩阵。
     * <p>
     * 该矩阵以 {@link OrthographicCameraConfig#size} 作为纵向半尺寸，
     * 横向半尺寸按窗口宽高比扩展：{@code size * aspect}。
     * 当 size 过小导致画面退化时，会用 {@code minScale} 作为下限保护。
     * </p>
     *
     * @param minecraft 客户端实例，用于读取窗口宽高
     * @param minScale 投影半尺寸下限，防止 size 过小
     * @return 适配当前窗口比例的正交投影矩阵
     */
    default Matrix4f orthoMatrix4f(Minecraft minecraft, float minScale) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        float size = OrthographicCameraConfig.size;
        float aspect = (float) width / height;
        float rightLeft = Math.max(minScale, size * aspect);
        float minSize = Math.max(minScale, size);
        return new Matrix4f().setOrtho(
                -rightLeft, rightLeft,
                -minSize, minSize,
                -1000, 1000
        );
    }
}
