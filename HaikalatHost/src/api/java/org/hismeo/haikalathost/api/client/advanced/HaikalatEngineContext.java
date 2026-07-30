package org.hismeo.haikalathost.api.client.advanced;

import com.kaleblangley.haikalat.core.device.RenderDevice;
import com.kaleblangley.haikalat.subsystems.resources.ResourceCatalog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Host-owned Haikalat services available during an extension lifecycle callback.
 *
 * <p>This is an advanced, client-only API. The device and resource catalog are borrowed from
 * HaikalatHost. An extension must never close the device, create another OpenGL context or render
 * thread, or call a window swap/present operation. GPU objects may only be created and released
 * while HaikalatHost is invoking one of the extension callbacks.</p>
 *
 * <p>Extensions own every engine object they create, including pipelines, scenes, meshes,
 * materials, textures, VFX and UI objects. Those objects must be released from
 * {@link HaikalatRenderExtension#worldClosed(HaikalatEngineContext)} or
 * {@link HaikalatRenderExtension#close(HaikalatEngineContext)} as appropriate.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface HaikalatEngineContext {
    /**
     * Returns the borrowed Host render device.
     *
     * <p>The returned device must not be closed or retained for use outside Host callbacks.</p>
     */
    RenderDevice device();

    /**
     * Returns the current Minecraft-backed resource catalog.
     *
     * <p>The catalog itself is Host-owned. Resources created from its data remain
     * extension-owned.</p>
     */
    ResourceCatalog resources();

    /**
     * Returns the latest Minecraft resource generation accepted by the Host.
     */
    long resourceGeneration();
}
