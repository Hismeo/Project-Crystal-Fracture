package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.hismeo.haikalathost.client.staticmesh.StaticVertexConsumerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class FullTakeoverBufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void haikalatHost$observeRenderType(
            RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        StaticVertexConsumerRegistry.observe(cir.getReturnValue(), renderType);
    }

    @Inject(
            method = "endBatch(Lnet/minecraft/client/renderer/RenderType;"
                    + "Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At("RETURN"))
    private void haikalatHost$releaseRenderType(
            RenderType renderType, BufferBuilder builder, CallbackInfo ci) {
        StaticVertexConsumerRegistry.release(builder);
    }

    @Redirect(
            method = "endBatch(Lnet/minecraft/client/renderer/RenderType;"
                    + "Lcom/mojang/blaze3d/vertex/BufferBuilder;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;"
                            + "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void haikalatHost$captureMeshData(RenderType renderType, MeshData meshData) {
        var backend = MinecraftRuntimeLifecycle.takeoverBackend();
        if (backend == null) {
            meshData.close();
            throw new IllegalStateException(
                    "Haikalat capture occurred before the exclusive backend was initialized");
        }
        backend.capture(renderType, meshData);
    }
}
