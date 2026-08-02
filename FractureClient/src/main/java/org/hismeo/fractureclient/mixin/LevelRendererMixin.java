package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.hismeo.fractureclient.client.control.PlayerOcclusionOutlineController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapOperation(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getTeamColor()I"
            )
    )
    private int fractureClient$useOcclusionOutlineColor(
            Entity entity,
            Operation<Integer> original
    ) {
        if (PlayerOcclusionOutlineController.shouldOutline(entity)) {
            return PlayerOcclusionOutlineController.getOutlineColor();
        }
        return original.call(entity);
    }
}
