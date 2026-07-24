package org.hismeo.haikalathost.client.graph;

import com.kaleblangley.haikalat.backend.GlException;
import com.kaleblangley.haikalat.backend.GlResource;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.subsystems.render3d.ScreenQuad;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;

/** Records one conservative 2x2 max-depth reduction level of the Host-owned Hi-Z pyramid. */
public final class HiZPyramidPass implements GlResource {
    private static final String VERTEX_SHADER = """
            #version 460 core
            out vec2 vUv;
            const vec2 POSITIONS[6] = vec2[](
                vec2(-1.0, -1.0), vec2( 1.0, -1.0), vec2( 1.0,  1.0),
                vec2(-1.0, -1.0), vec2( 1.0,  1.0), vec2(-1.0,  1.0)
            );
            void main() {
                vec2 position = POSITIONS[gl_VertexID];
                gl_Position = vec4(position, 0.0, 1.0);
                vUv = position * 0.5 + 0.5;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 460 core
            in vec2 vUv;
            layout(location = 0) out float outDepth;
            uniform sampler2D uSource;
            uniform vec2 uSourceTexelSize;
            void main() {
                vec2 halfTexel = uSourceTexelSize * 0.5;
                float d0 = texture(uSource, vUv + vec2(-halfTexel.x, -halfTexel.y)).r;
                float d1 = texture(uSource, vUv + vec2( halfTexel.x, -halfTexel.y)).r;
                float d2 = texture(uSource, vUv + vec2(-halfTexel.x,  halfTexel.y)).r;
                float d3 = texture(uSource, vUv + vec2( halfTexel.x,  halfTexel.y)).r;
                outDepth = max(max(d0, d1), max(d2, d3));
            }
            """;

    private final ShaderProgram program = ShaderProgram.fromSources(VERTEX_SHADER, FRAGMENT_SHADER);
    private final ScreenQuad quad = new ScreenQuad();
    private boolean closed;

    public CommandBuffer record(
            CommandBuffer commands,
            int sourceTexture,
            int sourceWidth,
            int sourceHeight
    ) {
        ensureOpen();
        if (sourceTexture == 0) {
            throw new IllegalArgumentException("Hi-Z source texture must be non-zero");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException("Hi-Z source dimensions must be positive");
        }
        return commands.enableBlend(false)
                .enableDepthTest(false)
                .depthMask(false)
                .enableCullFace(false)
                .enableFramebufferSrgb(false)
                .bindShader(program)
                .bindTexture(0, sourceTexture)
                .setUniformInt(program, "uSource", 0)
                .setUniformVec2(
                        program, "uSourceTexelSize", 1.0F / sourceWidth, 1.0F / sourceHeight)
                .bindVertexArray(quad.id())
                .drawArrays(GL_TRIANGLES, 0, 6);
    }

    @Override
    public int id() {
        return program.id();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        quad.close();
        program.close();
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new GlException("Hi-Z pyramid pass is closed");
    }
}
