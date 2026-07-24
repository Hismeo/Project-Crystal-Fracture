package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.routing.DrawRoute;
import org.hismeo.haikalathost.client.routing.MinecraftDrawRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public abstract class RenderTypeMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$routeDraw(MeshData meshData, CallbackInfo callback) {
        RenderType renderType = (RenderType) (Object) this;
        MinecraftDrawRouter.DrawDecision decision =
                MinecraftDrawRouter.route(renderType, meshData.drawState());
        if (decision.route() != DrawRoute.HAIKALAT_COMPAT) return;

        callback.cancel();
        decision.runtime().drawOwned(renderType, meshData);
    }
}
