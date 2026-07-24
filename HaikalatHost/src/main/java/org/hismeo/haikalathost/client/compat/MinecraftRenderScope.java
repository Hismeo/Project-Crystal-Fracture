package org.hismeo.haikalathost.client.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;

public final class MinecraftRenderScope implements AutoCloseable {
    private final RenderType renderType;
    private ShaderInstance shader;
    private boolean renderStateApplied;
    private boolean closed;

    private MinecraftRenderScope(RenderType renderType, VertexFormat.Mode mode) {
        this.renderType = renderType;
        try {
            renderType.setupRenderState();
            renderStateApplied = true;
            shader = MinecraftShaderBridge.currentShader();
            MinecraftShaderBridge.apply(shader, mode);
        } catch (Throwable throwable) {
            closeAfterFailedOpen(throwable);
            throw throwable;
        }
    }

    public static MinecraftRenderScope open(RenderType renderType, VertexFormat.Mode mode) {
        return new MinecraftRenderScope(renderType, mode);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (shader != null) MinecraftShaderBridge.clear(shader);
        } finally {
            if (renderStateApplied) renderType.clearRenderState();
        }
    }

    private void closeAfterFailedOpen(Throwable failure) {
        try {
            close();
        } catch (Throwable closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
