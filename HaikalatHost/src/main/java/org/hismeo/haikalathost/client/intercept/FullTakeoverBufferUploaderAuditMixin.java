package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.MeshData;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferUploader.class)
public abstract class FullTakeoverBufferUploaderAuditMixin {
    @Inject(method = {"draw", "_drawWithShader"}, at = @At("HEAD"), cancellable = true)
    private static void haikalatHost$captureExclusiveUploadDraw(
            MeshData meshData, CallbackInfo callback) {
        var backend = MinecraftRuntimeLifecycle.takeoverBackend();
        if (backend == null || !backend.glAudit().frameActive()) return;
        backend.captureDirect(meshData);
        callback.cancel();
    }
}
