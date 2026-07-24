package org.hismeo.haikalathost.client.graph;

import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.subsystems.render3d.ScreenQuad;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

/**
 * H6 single-accumulator weighted blended OIT composite.
 *
 * <p>Translucent entity draws add premultiplied color and alpha into an RGBA16F attachment. The
 * resolve is order independent and intentionally conservative: it favors stable batching and
 * predictable cost over exact layer-by-layer alpha composition.</p>
 */
public final class WeightedOitPass implements AutoCloseable {
    private static final String VERTEX_SHADER = """
            #version 460 core
            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aUv;
            out vec2 vUv;

            void main() {
                vUv = aUv;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 460 core
            in vec2 vUv;
            layout(location = 0) out vec4 outColor;

            uniform sampler2D uScene;
            uniform sampler2D uAccumulation;

            void main() {
                vec4 scene = texture(uScene, vUv);
                vec4 accumulation = texture(uAccumulation, vUv);
                if (accumulation.a <= 0.00001) {
                    outColor = scene;
                    return;
                }
                vec3 translucent = accumulation.rgb / accumulation.a;
                float alpha = 1.0 - exp(-accumulation.a);
                outColor = vec4(mix(scene.rgb, translucent, clamp(alpha, 0.0, 1.0)), scene.a);
            }
            """;

    private final ShaderProgram program =
            ShaderProgram.fromSources(VERTEX_SHADER, FRAGMENT_SHADER);
    private final ScreenQuad quad = new ScreenQuad();
    private boolean closed;

    public void record(
            CommandBuffer commands,
            int sceneTexture,
            int accumulationTexture
    ) {
        ensureOpen();
        commands.enableBlend(false)
                .enableDepthTest(false)
                .depthMask(false)
                .enableCullFace(false)
                .enableFramebufferSrgb(false)
                .bindShader(program)
                .bindTexture(0, sceneTexture)
                .bindTexture(1, accumulationTexture)
                .setUniformInt(program, "uScene", 0)
                .setUniformInt(program, "uAccumulation", 1)
                .bindVertexArray(quad.id())
                .drawArrays(GL_TRIANGLES, 0, 6)
                .depthMask(true)
                .enableDepthTest(true);
    }

    @Override
    public void close() {
        if (closed) return;
        quad.close();
        program.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("weighted OIT pass is closed");
    }
}
