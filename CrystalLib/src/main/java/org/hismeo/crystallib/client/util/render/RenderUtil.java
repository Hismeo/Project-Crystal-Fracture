package org.hismeo.crystallib.client.util.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

public class RenderUtil {
    public static void drawQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                float x1, float x2, float x3, float x4,
                                float y1, float y2, float y3, float y4,
                                float z1, float z2, float z3, float z4,
                                float red1, float green1, float blue1, float alpha1,
                                float red2, float green2, float blue2, float alpha2,
                                float red3, float green3, float blue3, float alpha3,
                                float red4, float green4, float blue4, float alpha4) {
        vertexConsumer.vertex(matrix4f, x1, y1, z1)
                .color(red1, green1, blue1, alpha1)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x2, y2, z2)
                .color(red2, green2, blue2, alpha2)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x3, y3, z3)
                .color(red3, green3, blue3, alpha3)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x4, y4, z4)
                .color(red4, green4, blue4, alpha4)
                .endVertex();
    }

    public static void drawQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                float x1, float x2, float x3, float x4,
                                float y1, float y2, float y3, float y4,
                                float z1, float z2, float z3, float z4,
                                float red, float green, float blue, float alpha) {
        drawQuad(vertexConsumer, matrix4f, x1, x2, x3, x4, y1, y2, y3, y4, z1, z2, z3, z4, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha);
    }

    public static void drawSameXQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                     float x,
                                     float y1, float y2, float y3, float y4,
                                     float z1, float z2, float z3, float z4,
                                     float red, float green, float blue, float alpha) {
        drawQuad(vertexConsumer, matrix4f, x, x, x, x, y1, y2, y3, y4, z1, z2, z3, z4, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha);
    }

    public static void drawSameYQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                     float x1, float x2, float x3, float x4,
                                     float y,
                                     float z1, float z2, float z3, float z4,
                                     float red, float green, float blue, float alpha) {
        drawQuad(vertexConsumer, matrix4f, x1, x2, x3, x4, y, y, y, y, z1, z2, z3, z4, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha);
    }

    public static void drawSameZQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                     float x1, float x2, float x3, float x4,
                                     float y1, float y2, float y3, float y4,
                                     float z,
                                     float red, float green, float blue, float alpha) {
        drawQuad(vertexConsumer, matrix4f, x1, x2, x3, x4, y1, y2, y3, y4, z, z, z, z, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha, red, green, blue, alpha);
    }

    public static void drawQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                float x1, float x2, float x3, float x4,
                                float y1, float y2, float y3, float y4,
                                float z1, float z2, float z3, float z4,
                                int colorARGB1, int colorARGB2, int colorARGB3, int colorARGB4) {
        vertexConsumer.vertex(matrix4f, x1, y1, z1)
                .color(colorARGB1)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x2, y2, z2)
                .color(colorARGB2)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x3, y3, z3)
                .color(colorARGB3)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x4, y4, z4)
                .color(colorARGB4)
                .endVertex();
    }

    public static void drawQuad(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                float x1, float x2, float x3, float x4,
                                float y1, float y2, float y3, float y4,
                                float z1, float z2, float z3, float z4,
                                int colorARGB) {
        vertexConsumer.vertex(matrix4f, x1, y1, z1)
                .color(colorARGB)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x2, y2, z2)
                .color(colorARGB)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x3, y3, z3)
                .color(colorARGB)
                .endVertex();
        vertexConsumer.vertex(matrix4f, x4, y4, z4)
                .color(colorARGB)
                .endVertex();
    }
}
