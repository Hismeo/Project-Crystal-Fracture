package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.hismeo.haikalathost.client.staticmesh.ModelPartStaticMeshCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replaces common untinted ModelPart vertex generation with static cube instances. */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin {
    @Redirect(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart$Cube;"
                            + "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void haikalatHost$captureStaticCube(
            ModelPart.Cube cube,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        if (!ModelPartStaticMeshCache.capture(
                cube, pose, buffer, packedLight, packedOverlay, color)) {
            cube.compile(pose, buffer, packedLight, packedOverlay, color);
        }
    }
}
