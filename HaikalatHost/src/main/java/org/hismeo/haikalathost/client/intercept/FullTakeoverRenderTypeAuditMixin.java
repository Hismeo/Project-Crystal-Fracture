package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class FullTakeoverRenderTypeAuditMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void haikalatHost$auditExclusiveDraw(MeshData meshData, CallbackInfo callback) {
        var backend = MinecraftRuntimeLifecycle.takeoverBackend();
        if (backend != null) backend.glAudit().checkMinecraftGl("RenderType.draw");
    }
}
