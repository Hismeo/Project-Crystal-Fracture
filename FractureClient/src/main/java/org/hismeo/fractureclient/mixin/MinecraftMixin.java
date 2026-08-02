package org.hismeo.fractureclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.hismeo.fractureclient.client.control.PlayerOcclusionOutlineController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void fractureClient$outlineOccludedPlayer(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (PlayerOcclusionOutlineController.shouldOutline(entity)) {
            cir.setReturnValue(true);
        }
    }
}
