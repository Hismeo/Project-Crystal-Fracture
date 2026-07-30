package org.hismeo.haikalathost.internal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

import java.util.Objects;

/**
 * Per-frame borrowed view of a Minecraft render target.
 *
 * <p>The numeric names remain owned by Minecraft. HaikalatHost must never delete, resize, or
 * reallocate them.</p>
 */
public record MinecraftRenderTargetDescriptor(
        int framebufferId,
        int colorTextureId,
        int depthTextureId,
        int viewWidth,
        int viewHeight,
        int allocationWidth,
        int allocationHeight,
        boolean hasDepth,
        boolean hasStencil
) {
    public MinecraftRenderTargetDescriptor {
        if (framebufferId < 0) {
            throw new IllegalArgumentException("framebufferId must be non-negative");
        }
        if (colorTextureId < 0) {
            throw new IllegalArgumentException("colorTextureId must be non-negative");
        }
        if (viewWidth < 0 || viewHeight < 0) {
            throw new IllegalArgumentException("view dimensions must be non-negative");
        }
        if (allocationWidth <= 0 || allocationHeight <= 0) {
            throw new IllegalArgumentException("allocation dimensions must be positive");
        }
        if (hasDepth && depthTextureId < 0) {
            throw new IllegalArgumentException("depthTextureId must be available when hasDepth");
        }
    }

    public static MinecraftRenderTargetDescriptor capture(RenderTarget target) {
        Objects.requireNonNull(target, "target");
        return new MinecraftRenderTargetDescriptor(
                target.frameBufferId,
                target.getColorTextureId(),
                target.getDepthTextureId(),
                target.viewWidth,
                target.viewHeight,
                target.width,
                target.height,
                target.useDepth,
                target.isStencilEnabled());
    }

    public boolean minimized() {
        return viewWidth == 0 || viewHeight == 0;
    }
}
