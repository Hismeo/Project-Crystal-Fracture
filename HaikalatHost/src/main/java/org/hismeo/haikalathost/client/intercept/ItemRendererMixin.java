package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.hismeo.haikalathost.client.staticmesh.ItemModelStaticMeshCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoids rebuilding common untinted baked item vertices every frame. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderModelLists", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$captureStaticItemModel(
            BakedModel model,
            ItemStack stack,
            int combinedLight,
            int combinedOverlay,
            PoseStack poseStack,
            VertexConsumer buffer,
            CallbackInfo ci
    ) {
        if (ItemModelStaticMeshCache.capture(
                model, poseStack.last(), buffer, combinedLight, combinedOverlay)) {
            ci.cancel();
        }
    }
}
