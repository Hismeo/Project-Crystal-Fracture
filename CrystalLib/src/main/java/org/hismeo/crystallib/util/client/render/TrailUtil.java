package org.hismeo.crystallib.util.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.hismeo.crystallib.CrystalLib;
import org.hismeo.crystallib.util.client.render.vertex.VertexUtil;
import org.joml.Matrix4f;

public class TrailUtil {
    public void renderTrail(Vec3[] trails, Entity entity, float width, PoseStack poseStack, MultiBufferSource multiBufferSource, RenderType renderType) {
        renderTrail(trails, entity.position(), width, poseStack, multiBufferSource, renderType);
    }

    public void renderTrail(Vec3[] trails, Vec3 entityPosition, float width, PoseStack poseStack, MultiBufferSource multiBufferSource, RenderType renderType) {
        Matrix4f matrix4f = poseStack.last().pose();
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(renderType);
        renderTrail(trails, entityPosition, width, vertexConsumer, matrix4f);
    }

    // TODO 将枪械的新轨迹应用于此
    public void renderTrail(Vec3[] trails, Vec3 position, float width, VertexConsumer vertexConsumer, Matrix4f matrix4f) {
        if (trails == null) {
            CrystalLib.LOGGER.warn("Trails is null!");
            return;
        }

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

            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1, y1, z1 - width0, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1, y1, z1 + width0, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1, y2, z2 + width1, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1, y2, z2 - width1, 1, 1, 1, 0.5f);

            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1 - width0, y1, z1, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x1 + width0, y1, z1, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x2 + width1, y2, z1, 1, 1, 1, 0.5f);
            VertexUtil.drawVertexColor(vertexConsumer, matrix4f, x2 + width1, y2, z1, 1, 1, 1, 0.5f);
        }
    }
}
