package org.hismeo.haikalathost.api.client.advanced;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only escape hatch for running ordinary Haikalat code inside Minecraft.
 *
 * <p>HaikalatHost owns scheduling, the embedded runtime, the render device, Minecraft's borrowed
 * target and camera, GL-state isolation and resource/lifecycle notifications. The extension owns
 * all Haikalat objects it creates and is responsible for closing them. Callbacks are serialized in
 * registration order on the Minecraft Render Thread. If a callback throws, only that extension is
 * disabled; the exception is retained for diagnostics and is not propagated into Minecraft.</p>
 *
 * <p>Extensions must not create a second window, OpenGL context or render thread, must not call
 * swap/present, and must not mutate or destroy Minecraft-owned target attachments.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface HaikalatRenderExtension {
    /**
     * Called once after the embedded Host runtime and resource catalog are ready.
     */
    default void initialize(HaikalatEngineContext context) {
    }

    /**
     * Runs once for each renderable Minecraft world frame.
     */
    void render(HaikalatFrameContext frame);

    /**
     * Called once for each newly accepted Minecraft resource generation.
     */
    default void resourcesReloaded(HaikalatReloadContext context) {
    }

    /**
     * Called when the current world closes or is replaced.
     *
     * <p>World-scoped extension objects should be released here. A later world may begin producing
     * render callbacks without another {@link #initialize(HaikalatEngineContext)} call.</p>
     */
    default void worldClosed(HaikalatEngineContext context) {
    }

    /**
     * Called exactly once during Host shutdown, in reverse registration order, while the OpenGL
     * context is still valid.
     */
    default void close(HaikalatEngineContext context) {
    }
}
