package org.hismeo.haikalathost.client.intercept;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.hismeo.haikalathost.client.chunk.ChunkMeshRegistry;
import org.hismeo.haikalathost.client.runtime.MinecraftRuntimeLifecycle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;

@Mixin(SectionRenderDispatcher.class)
public abstract class SectionRenderDispatcherMixin {
    @Shadow @Final private Queue<Runnable> toUpload;
    @Shadow private volatile boolean closed;

    @Inject(method = "uploadSectionLayer", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$takeOwnership(
            MeshData meshData,
            VertexBuffer vertexBuffer,
            CallbackInfoReturnable<CompletableFuture<Void>> callback
    ) {
        ChunkMeshRegistry.Capture capture = ChunkMeshRegistry.captureSupported(vertexBuffer);
        if (capture == null || closed) return;

        CompletableFuture<Void> upload = CompletableFuture.runAsync(() -> {
            try (meshData) {
                if (vertexBuffer.isInvalid()
                        || !ChunkMeshRegistry.isCurrent(vertexBuffer, capture.generation())) {
                    return;
                }
                SectionRenderDispatcher.RenderSection section =
                        (SectionRenderDispatcher.RenderSection) capture.section();
                BlockPos origin = section.getOrigin();
                MinecraftRuntimeLifecycle.uploadChunkMesh(
                        capture, origin.getX(), origin.getY(), origin.getZ(), meshData);
            }
        }, toUpload::add);
        callback.setReturnValue(upload);
    }

    @Inject(method = "uploadSectionIndexBuffer", at = @At("HEAD"), cancellable = true)
    private void haikalatHost$discardVanillaIndexResort(
            ByteBufferBuilder.Result indexBuffer,
            VertexBuffer vertexBuffer,
            CallbackInfoReturnable<CompletableFuture<Void>> callback
    ) {
        if (!ChunkMeshRegistry.isTracked(vertexBuffer) || closed) return;
        callback.setReturnValue(CompletableFuture.runAsync(
                indexBuffer::close,
                toUpload::add));
    }
}
