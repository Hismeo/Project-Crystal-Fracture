package org.hismeo.haikalathost.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

public final class MinecraftShaderBridge {
    private MinecraftShaderBridge() {
    }

    public static ShaderInstance currentShader() {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            throw new MissingShaderException("RenderType did not provide a Minecraft shader");
        }
        return shader;
    }

    public static void apply(ShaderInstance shader, VertexFormat.Mode mode) {
        shader.setDefaultUniforms(
                mode,
                RenderSystem.getModelViewMatrix(),
                RenderSystem.getProjectionMatrix(),
                Minecraft.getInstance().getWindow());
        shader.apply();
    }

    public static void clear(ShaderInstance shader) {
        shader.clear();
    }
}
