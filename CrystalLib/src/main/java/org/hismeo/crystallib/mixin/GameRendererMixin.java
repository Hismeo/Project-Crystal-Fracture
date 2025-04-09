package org.hismeo.crystallib.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.hismeo.crystallib.client.util.render.shader.BlurUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "close", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;shutdownShaders()V"))
    public void closeBlur(CallbackInfo ci){
        BlurUtil.closeBlur();
    }
}
