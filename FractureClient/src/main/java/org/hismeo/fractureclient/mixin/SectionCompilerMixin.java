package org.hismeo.fractureclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import org.hismeo.fractureclient.client.control.BlockCullController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {
    @WrapMethod(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;"
    )
    private SectionCompiler.Results fractureClient$captureCullSnapshot(
            SectionPos sectionPos,
            RenderChunkRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack bufferPack,
            List<AddSectionGeometryEvent.AdditionalSectionRenderer> additionalRenderers,
            Operation<SectionCompiler.Results> original
    ) {
        BlockCullController.beginSectionCompilation();
        try {
            return original.call(sectionPos, region, vertexSorting, bufferPack, additionalRenderers);
        } finally {
            BlockCullController.endSectionCompilation();
        }
    }

    @WrapOperation(
            method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void fractureClient$skipCutawayInVisibilityGraph(
            VisGraph graph,
            BlockPos pos,
            Operation<Void> original
    ) {
        if (!BlockCullController.isCulledDuringCompilation(pos)) {
            original.call(graph, pos);
        }
    }
}
