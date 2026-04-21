package org.hismeo.crystallib.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public class MatrixUtil {
    public static Matrix4f orthoMatrix4f(Minecraft minecraft, float minScale) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();

        float size = 10.0f;
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
