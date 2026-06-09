package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.hismeo.crystallib.util.MatrixUtil;
import org.hismeo.fractureclient.client.impl.mixin.CameraImpl;
import org.hismeo.fractureclient.client.impl.mixin.GlobalRender;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        return  CameraImpl.orthoMatrix4f(minecraft, 20.0F);
    }
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 6
    )
    public Matrix4f renderProjection(Matrix4f projectionMatrix, @Local(argsOnly = true) DeltaTracker tickCounter) {
        Matrix4f orthoMatrix =  CameraImpl.orthoMatrix4f(minecraft, 0.0F);
        RenderSystem.setProjectionMatrix(orthoMatrix, VertexSorting.ORTHOGRAPHIC_Z);
        return orthoMatrix;
    }

    //=====================================NORMAL RENDER=====================================
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V",
                    shift = At.Shift.AFTER
            )
    )
    public void globalRender(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci, @Local(type = GuiGraphics.class)GuiGraphics guiGraphics, @Local Window window) {
        GlobalRender.globalRender(minecraft, deltaTracker, renderLevel, guiGraphics, window);
    }
}
