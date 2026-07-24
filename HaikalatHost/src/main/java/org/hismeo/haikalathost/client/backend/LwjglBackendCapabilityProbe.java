package org.hismeo.haikalathost.client.backend;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class LwjglBackendCapabilityProbe {
    private LwjglBackendCapabilityProbe() {
    }

    public static BackendCapabilities detect() {
        GLCapabilities capabilities = GL.getCapabilities();
        return new BackendCapabilities(
                capabilities.OpenGL46,
                capabilities.OpenGL43 || capabilities.GL_ARB_compute_shader,
                capabilities.OpenGL43 || capabilities.GL_ARB_shader_storage_buffer_object,
                capabilities.OpenGL44 || capabilities.GL_ARB_buffer_storage,
                capabilities.OpenGL43 || capabilities.GL_ARB_multi_draw_indirect,
                capabilities.OpenGL46 || capabilities.GL_ARB_shader_draw_parameters,
                capabilities.OpenGL46 || capabilities.GL_ARB_indirect_parameters,
                capabilities.OpenGL43 || capabilities.GL_KHR_debug,
                capabilities.GL_ARB_bindless_texture);
    }
}
