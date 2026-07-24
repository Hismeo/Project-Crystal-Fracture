package org.hismeo.haikalathost.client.intercept;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.joml.Matrix4f;
import org.hismeo.haikalathost.client.extraction.DirectDrawStateTracker;
import org.hismeo.haikalathost.client.submission.PassKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$submitChunkLayer(
            RenderType renderType,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4f modelView,
            Matrix4f projection,
            CallbackInfo callback
    ) {
        MinecraftRuntimeLifecycle.captureChunkLayer(
                renderType, visibleSections, cameraX, cameraY, cameraZ, modelView, projection);
        callback.cancel();
    }

    /** The vanilla rain/snow path shares the particle shader, so its pass needs call-site context. */
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"))
    private void haikalatHost$beginWeatherPass(
            LightTexture lightTexture,
            float partialTick,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo callback
    ) {
        DirectDrawStateTracker.pushPassOverride(PassKey.WEATHER);
    }

    @Inject(method = "renderSnowAndRain", at = @At("RETURN"))
    private void haikalatHost$endWeatherPass(
            LightTexture lightTexture,
            float partialTick,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfo callback
    ) {
        DirectDrawStateTracker.popPassOverride(PassKey.WEATHER);
    }

    /** Position/color and position/texture shaders are also used by UI; scope sky explicitly. */
    @Inject(method = "renderSky", at = @At("HEAD"))
    private void haikalatHost$beginSkyPass(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean foggy,
            Runnable skyFogSetup,
            CallbackInfo callback
    ) {
        DirectDrawStateTracker.pushPassOverride(PassKey.SKY);
    }

    @Inject(method = "renderSky", at = @At("RETURN"))
    private void haikalatHost$endSkyPass(
            Matrix4f frustumMatrix,
            Matrix4f projectionMatrix,
            float partialTick,
            Camera camera,
            boolean foggy,
            Runnable skyFogSetup,
            CallbackInfo callback
    ) {
        DirectDrawStateTracker.popPassOverride(PassKey.SKY);
    }
}
