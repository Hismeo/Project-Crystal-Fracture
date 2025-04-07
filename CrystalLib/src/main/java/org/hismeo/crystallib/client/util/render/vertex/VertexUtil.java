package org.hismeo.crystallib.client.util.render.vertex;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;


public class VertexUtil {
    public static void drawVertex(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).endVertex();
    }

    public static void drawVertexColor(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }

    public static void drawVertexColorUV(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).endVertex();
    }

    public static void drawVertexColorUVLight(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int l) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).uv2(l).endVertex();
    }

    public static void drawVertexColorUVLight(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int lU, int lV) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).uv2(lU, lV).endVertex();
    }

    public static void drawVertexColorUVOverlay(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int o) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).overlayCoords(o).endVertex();
    }

    public static void drawVertexColorUVOverlay(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int oU, int oV) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).overlayCoords(oU, oV).endVertex();
    }

    public static void drawVertexColorUVOverlayLight(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int o, int l) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).overlayCoords(o).uv2(l).endVertex();
    }

    public static void drawVertexColorUVOverlayLight(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int o, int lU, int lV) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).color(r, g, b, a).uv(u, v).overlayCoords(o).uv2(lU, lV).endVertex();
    }

    public static void drawVertexColorUVOverlayLightNormal(VertexConsumer vertex, Matrix4f matrix4f, float x1, float y1, float z1, float r, float g, float b, float a, float u, float v, int o, int lU, int lV, Matrix3f matrix3f, float x2, float y2, float z2) {
        vertex.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).uv(u, v).overlayCoords(o).uv2(lU, lV).normal(matrix3f, x2, y2, z2).endVertex();
    }

    public static void drawVertexColorNormal(VertexConsumer vertex, Matrix4f matrix4f, float x1, float y1, float z1, float r, float g, float b, float a, Matrix3f matrix3f, float x2, float y2, float z2) {
        vertex.vertex(matrix4f, x1, y1, z1).color(r, g, b, a).normal(matrix3f, x2, y2, z2).endVertex();
    }

    public static void drawVertexUV(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float u, float v) {
        vertex.vertex(matrix4f, (float) x, (float) y, (float) z).uv(u, v).endVertex();
    }

    public static void drawVertexUVLightNormal(VertexConsumer vertex, Matrix4f matrix4f, float x1, float y1, float z1, float u, float v, int l, Matrix3f matrix3f, float x2, float y2, float z2) {
        vertex.vertex(matrix4f, x1, y1, z1).uv(u, v).uv2(l).normal(matrix3f, x2, y2, z2).endVertex();
    }

    public static void drawVertexUVLightNormal(VertexConsumer vertex, Matrix4f matrix4f, float x1, float y1, float z1, float u, float v, int lU, int lV, Matrix3f matrix3f, float x2, float y2, float z2) {
        vertex.vertex(matrix4f, x1, y1, z1).uv(u, v).uv2(lU, lV).normal(matrix3f, x2, y2, z2).endVertex();
    }
}
