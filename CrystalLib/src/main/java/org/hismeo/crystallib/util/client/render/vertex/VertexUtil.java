package org.hismeo.crystallib.util.client.render.vertex;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;


public class VertexUtil {
    public static void drawVertex(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z) {
        vertex.addVertex(matrix4f, (float) x, (float) y, (float) z);
    }

    public static void drawVertexColor(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a) {
        vertex.addVertex(matrix4f, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }

    public static void drawVertexColorUV(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v) {
        vertex.addVertex(matrix4f, (float) x, (float) y, (float) z).setColor(r, g, b, a).setUv(u, v);
    }

    public static void drawVertexColorUVLight(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float r, float g, float b, float a, float u, float v, int lU, int lV) {
        vertex.addVertex(matrix4f, (float) x, (float) y, (float) z).setColor(r, g, b, a).setUv(u, v).setUv2(lU, lV);
    }

    public static void drawVertexColorNormal(VertexConsumer vertex, Matrix4f matrix4f, float x1, float y1, float z1, float r, float g, float b, float a, PoseStack.Pose pose, float x2, float y2, float z2) {
        vertex.addVertex(matrix4f, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, x2, y2, z2);
    }

    public static void drawVertexUV(VertexConsumer vertex, Matrix4f matrix4f, double x, double y, double z, float u, float v) {
        vertex.addVertex(matrix4f, (float) x, (float) y, (float) z).setUv(u, v);
    }
}
