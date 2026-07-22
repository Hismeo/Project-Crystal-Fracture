package org.hismeo.fracture_loader.render;

import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;

/**
 * Shared Haikalat renderer for both sides of the Minecraft window hand-off.
 *
 * <p>Callers retain ownership of progress collection, framebuffer selection and presentation.
 * Each loading phase creates its own renderer so no GL state cache is carried across the module
 * and lifecycle boundary.</p>
 */
public final class HaikalatLoadingFrameRenderer {
    private static final int MAX_PROGRESS_BARS = 3;

    private final GlRenderDevice renderDevice = new GlRenderDevice();
    private final long startedAtNanos = System.nanoTime();

    public void render(int width, int height, FramebufferPolicy framebufferPolicy,
                       float... progressValues) {
        if (width <= 0 || height <= 0) {
            return;
        }

        renderDevice.invalidateState();
        CommandBuffer commands = renderDevice.createCommandBuffer();
        if (framebufferPolicy == FramebufferPolicy.BIND_DEFAULT) {
            commands.bindDefaultFramebuffer();
        }
        commands.viewport(0, 0, width, height)
                .enableScissor(false)
                .clearColor(0.018F, 0.023F, 0.045F, 1.0F)
                .clear(true, false);

        int stripeHeight = Math.max(1, height / 12);
        for (int stripe = 0; stripe < 8; stripe++) {
            float shade = stripe / 7.0F;
            clearRectangle(commands, 0, stripe * stripeHeight, width, stripeHeight,
                    0.025F + shade * 0.012F,
                    0.035F + shade * 0.016F,
                    0.070F + shade * 0.035F,
                    1.0F);
        }

        int contentWidth = Math.max(1, Math.min(Math.max(1, width - 80), 640));
        int left = (width - contentWidth) / 2;
        int centerY = height / 2;
        int markWidth = Math.max(1, Math.min(contentWidth, Math.max(64, contentWidth / 5)));
        int markLeft = (width - markWidth) / 2;
        float pulse = pulse();

        clearRectangle(commands, markLeft, centerY + 32, markWidth, 4,
                0.26F + pulse * 0.16F, 0.78F, 1.0F, 1.0F);
        clearRectangle(commands, markLeft + markWidth / 4, centerY + 20, markWidth / 2, 4,
                0.58F, 0.35F + pulse * 0.18F, 1.0F, 1.0F);

        int shownProgressBars = Math.min(MAX_PROGRESS_BARS, progressValues.length);
        for (int index = 0; index < shownProgressBars; index++) {
            float progress = clamp(progressValues[index]);
            int y = centerY - 32 - index * 18;
            clearRectangle(commands, left, y, contentWidth, 6,
                    0.09F, 0.11F, 0.18F, 1.0F);
            clearRectangle(commands, left, y,
                    Math.min(contentWidth, Math.max(2, Math.round(contentWidth * progress))), 6,
                    index == 0 ? 0.22F : 0.44F,
                    index == 0 ? 0.76F : 0.34F,
                    0.98F, 1.0F);
        }

        commands.enableScissor(false);
        renderDevice.execute(commands);
    }

    public float indeterminateProgress() {
        return 0.15F + pulse() * 0.70F;
    }

    public void invalidateState() {
        renderDevice.invalidateState();
    }

    private float pulse() {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        return 0.5F + 0.5F * (float) Math.sin(elapsedNanos / 450_000_000.0D);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void clearRectangle(CommandBuffer commands, int x, int y, int width, int height,
                                       float red, float green, float blue, float alpha) {
        if (width <= 0 || height <= 0) {
            return;
        }
        commands.scissor(x, y, width, height)
                .enableScissor(true)
                .clearColor(red, green, blue, alpha)
                .clear(true, false);
    }

    public enum FramebufferPolicy {
        BIND_DEFAULT,
        PRESERVE_CURRENT
    }
}
