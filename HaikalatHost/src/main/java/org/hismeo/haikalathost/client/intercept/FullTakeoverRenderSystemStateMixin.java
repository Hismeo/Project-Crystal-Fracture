package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.client.extraction.DirectDrawStateTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors resource identities used by direct MeshData producers without consulting GL names. */
@Mixin(RenderSystem.class)
public abstract class FullTakeoverRenderSystemStateMixin {
    @Inject(
            method = "_setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"))
    private static void haikalatHost$trackResourceTexture(
            int slot, ResourceLocation texture, CallbackInfo callback) {
        DirectDrawStateTracker.setTexture(slot, texture);
    }

    @Inject(method = "_setShaderTexture(II)V", at = @At("HEAD"))
    private static void haikalatHost$trackUnresolvedTexture(
            int slot, int textureId, CallbackInfo callback) {
        DirectDrawStateTracker.setUnresolvedTexture(slot);
    }
}
