package org.hismeo.haikalathost.client.intercept;

import net.minecraft.client.Minecraft;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void haikalatHost$closeRuntime(CallbackInfo callback) {
        MinecraftRuntimeLifecycle.close();
    }
}
