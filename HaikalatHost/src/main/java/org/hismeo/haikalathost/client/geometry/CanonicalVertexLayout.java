package org.hismeo.haikalathost.client.geometry;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BYTE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;

/** H1 canonical superset layout used by generic Minecraft geometry capture. */
public final class CanonicalVertexLayout {
    public static final int POSITION_OFFSET = 0;
    public static final int COLOR_OFFSET = 12;
    public static final int UV_OFFSET = 16;
    public static final int OVERLAY_OFFSET = 24;
    public static final int LIGHT_OFFSET = 28;
    public static final int NORMAL_OFFSET = 32;
    public static final int STRIDE_BYTES = 36;

    public static final MinecraftVertexLayout LAYOUT = new MinecraftVertexLayout(
            STRIDE_BYTES,
            List.of(
                    new MinecraftVertexAttribute(
                            0, 3, GL_FLOAT, false, POSITION_OFFSET, VertexInputClass.FLOATING),
                    new MinecraftVertexAttribute(
                            1, 4, GL_UNSIGNED_BYTE, true, COLOR_OFFSET, VertexInputClass.FLOATING),
                    new MinecraftVertexAttribute(
                            2, 2, GL_FLOAT, false, UV_OFFSET, VertexInputClass.FLOATING),
                    new MinecraftVertexAttribute(
                            3, 2, GL_UNSIGNED_SHORT, false, OVERLAY_OFFSET, VertexInputClass.INTEGER),
                    new MinecraftVertexAttribute(
                            4, 2, GL_UNSIGNED_SHORT, false, LIGHT_OFFSET, VertexInputClass.INTEGER),
                    new MinecraftVertexAttribute(
                            5, 3, GL_BYTE, true, NORMAL_OFFSET, VertexInputClass.FLOATING)));

    private CanonicalVertexLayout() {
    }
}
