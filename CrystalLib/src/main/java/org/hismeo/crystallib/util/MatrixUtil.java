package org.hismeo.crystallib.util;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public class MatrixUtil {
    public static Matrix4f orthoMatrix4f(Minecraft minecraft, float minScale) {
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();

        float size = 12.0f;
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
