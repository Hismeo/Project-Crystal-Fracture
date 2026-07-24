package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.hismeo.haikalathost.client.chunk.ChunkMeshRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class RenderSectionMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void haikalatHost$observeBuffer(
            RenderType renderType, CallbackInfoReturnable<VertexBuffer> callback) {
        ChunkMeshRegistry.observe(this, renderType, callback.getReturnValue());
    }

    @Inject(method = "setOrigin", at = @At("HEAD"))
    private void haikalatHost$releaseMovedSection(int x, int y, int z, CallbackInfo callback) {
        ChunkMeshRegistry.releaseSection(this);
    }

    @Inject(method = "releaseBuffers", at = @At("HEAD"))
    private void haikalatHost$releaseSectionBuffers(CallbackInfo callback) {
        ChunkMeshRegistry.releaseSection(this);
    }
    @Inject(method = "setCompiled", at = @At("TAIL"))
    private void haikalatHost$retainCompiledLayers(
            SectionRenderDispatcher.CompiledSection compiled,
            CallbackInfo callback
    ) {
        Set<RenderType> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RenderType renderType : RenderType.chunkBufferLayers()) {
            if (!compiled.isEmpty(renderType)) retained.add(renderType);
        }
        MinecraftRuntimeLifecycle.retainChunkLayers(this, retained);
    }

}
