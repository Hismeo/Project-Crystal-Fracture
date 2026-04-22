package org.hismeo.fractureclient.client.impl.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.hismeo.fractureclient.client.config.OrthographicCameraConfig;
import org.joml.Matrix4f;

public interface CameraImpl {
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
