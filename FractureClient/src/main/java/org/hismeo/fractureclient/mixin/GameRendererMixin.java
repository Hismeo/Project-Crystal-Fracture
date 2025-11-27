package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.hismeo.crystallib.util.MatrixUtil;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

//thanks for https://github.com/DimasKama/OrthoCamera
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    //=====================================OVERLOOK CAMERA=====================================
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 2
    )
    public Matrix4f frustumProjection(Matrix4f projectionMatrix) {
        return MatrixUtil.orthoMatrix4f(minecraft, 20.0F);
    }
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 6
    )
    public Matrix4f renderProjection(Matrix4f projectionMatrix, @Local(argsOnly = true) DeltaTracker tickCounter) {
        Matrix4f orthoMatrix = MatrixUtil.orthoMatrix4f(minecraft, 0.0F);
        RenderSystem.setProjectionMatrix(orthoMatrix, VertexSorting.ORTHOGRAPHIC_Z);
        return orthoMatrix;
    }
}
