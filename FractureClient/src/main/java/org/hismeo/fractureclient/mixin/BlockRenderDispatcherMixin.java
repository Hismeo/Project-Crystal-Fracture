package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.hismeo.fractureclient.client.render.CutawayLightBlockAndTintGetter;
import org.hismeo.fractureclient.client.render.CutawayLightVertexConsumer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {
    @WrapMethod(
            method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"
    )
    private void fractureClient$renderCutawayAware(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter level,
            PoseStack poseStack,
            VertexConsumer consumer,
            boolean checkSides,
            RandomSource random,
            ModelData modelData,
            RenderType renderType,
            Operation<Void> original
    ) {
        if (BlockCullController.shouldCullDuringCompilation(pos, state)) {
            return;
        }

        int exposedLight = BlockCullController.getExposedLightDuringCompilation(level, pos);
        if (exposedLight == BlockCullController.NO_CUTAWAY_LIGHT) {
            original.call(
                    state,
                    pos,
                    level,
                    poseStack,
                    consumer,
                    checkSides,
                    random,
                    modelData,
                    renderType
            );
            return;
        }

        BlockAndTintGetter virtualLevel = new CutawayLightBlockAndTintGetter(level, exposedLight);
        VertexConsumer lightConsumer = new CutawayLightVertexConsumer(consumer, exposedLight);
        ModelBlockRenderer.clearCache();
        ModelBlockRenderer.enableCaching();
        try {
            original.call(
                    state,
                    pos,
                    virtualLevel,
                    poseStack,
                    lightConsumer,
                    checkSides,
                    random,
                    modelData,
                    renderType
            );
        } finally {
            // Vanilla's AO cache is keyed by position only, so proxy-world samples must not leak
            // into the next normally rendered block in this section compile.
            ModelBlockRenderer.clearCache();
            ModelBlockRenderer.enableCaching();
        }
    }
}
