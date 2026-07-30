package org.hismeo.haikalathost.internal.render;

import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import org.hismeo.haikalathost.internal.interop.GlStateFootprint;
import org.hismeo.haikalathost.internal.interop.MinecraftGlInteropScope;

import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;

/**
 * Opt-in low-level nonzero-FBO marker, retained separately from the 0.20.1 embedded pipeline
 * probe for diagnosing failures below RenderGraph.
 */
public final class HostDebugRenderProbe {
    public static final String ENABLE_PROPERTY = "haikalathost.renderProbe";

    private HostDebugRenderProbe() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static void render(
            GlRenderDevice device,
            MinecraftRenderTargetDescriptor target
    ) {
        if (target == null || target.viewWidth() <= 0 || target.viewHeight() <= 0) {
            return;
        }

        try (MinecraftGlInteropScope ignored =
                     MinecraftGlInteropScope.capture(GlStateFootprint.MINIMAL_PROBE)) {
            device.invalidateState();
            int markerSize = Math.max(4, Math.min(16, Math.min(
                    target.viewWidth(),
                    target.viewHeight())));
            CommandBuffer commands = device.createCommandBuffer()
                    .bindFramebuffer(GL_FRAMEBUFFER, target.framebufferId())
                    .viewport(0, 0, target.viewWidth(), target.viewHeight())
                    .scissor(4, 4, markerSize, markerSize)
                    .enableScissor(true)
                    .clearColor(0.12F, 0.85F, 1.0F, 1.0F)
                    .clear(true, false)
                    .enableScissor(false);
            device.execute(commands);
        } finally {
            device.invalidateState();
        }
    }
}
