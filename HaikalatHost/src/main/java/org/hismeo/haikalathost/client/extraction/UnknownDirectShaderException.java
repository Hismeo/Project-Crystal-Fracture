package org.hismeo.haikalathost.client.extraction;

import net.minecraft.client.renderer.ShaderInstance;

/** Raised when a direct BufferUploader submission has no explicit Host shader-family route. */
public final class UnknownDirectShaderException extends IllegalStateException {
    public UnknownDirectShaderException(ShaderInstance shader) {
        super(shader == null
                ? "Direct Minecraft MeshData submission has no active ShaderInstance"
                : "Unregistered direct Minecraft shader: " + shader.getName());
    }
}
