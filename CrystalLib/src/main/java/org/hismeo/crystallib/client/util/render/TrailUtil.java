package org.hismeo.crystallib.client.util.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class TrailUtil {
    public void renderTrail(Vec3[] trails, Entity entity, Camera camera, float width, PoseStack poseStack, MultiBufferSource multiBufferSource, RenderType renderType) {
        renderTrail(trails, entity.position(), camera.getPosition(), width, poseStack, multiBufferSource, renderType);
    }

    public void renderTrail(Vec3[] trails, Vec3 entityPosition, Vec3 cameraPosition, float width, PoseStack poseStack, MultiBufferSource multiBufferSource, RenderType renderType) {
//        poseStack.pushPose();
//        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
//        poseStack.translate(entityPosition.x, entityPosition.y, entityPosition.z());
        Matrix4f matrix4f = poseStack.last().pose();
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(renderType);
        renderTrail(trails, entityPosition, width, vertexConsumer, matrix4f);
//        poseStack.popPose();
    }

    public void renderTrail(Vec3[] trails, Vec3 position, float width, VertexConsumer vertexConsumer, Matrix4f matrix4f) {
        if (trails == null) return;
        Vec3 pos0;
        Vec3 pos1;

        for (int i = 1; i < trails.length; i++) {
            pos0 = trails[i - 1].subtract(position);
            pos1 = trails[i].subtract(position);

            double x1 = pos0.x;
            double y1 = pos0.y;
            double z1 = pos0.z;
            double x2 = pos1.x;
            double y2 = pos1.y;
            double z2 = pos1.z;
            float width0 = width / trails.length * (i - 1);
            float width1 = width / trails.length * i;

            vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) z1 - width0)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x1, (float) y1, (float) z1 + width0)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) z2 + width1)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x2, (float) y2, (float) z2 - width1)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();

            vertexConsumer.vertex(matrix4f, (float) x1 - width0, (float) y1, (float) z1)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x1 + width0, (float) y1, (float) z1)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x2 + width1, (float) y2, (float) z2)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
            vertexConsumer.vertex(matrix4f, (float) x2 - width1, (float) y2, (float) z2)
                    .color(1, 1, 1, 0.5f)
                    .endVertex();
        }
    }
}
