package org.hismeo.haikalathost.client.submission;

import com.kaleblangley.haikalat.backend.RenderFormat;
import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.backend.framebuffer.Framebuffer;
import com.kaleblangley.haikalat.backend.shader.ShaderProgram;
import com.kaleblangley.haikalat.backend.vertex.VertexArray;
import com.kaleblangley.haikalat.backend.vertex.VertexAttribute;
import com.kaleblangley.haikalat.backend.vertex.VertexLayout;
import com.kaleblangley.haikalat.core.command.CommandBuffer;
import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import org.hismeo.haikalathost.client.extraction.RenderTypeMaterialRegistry;
import com.kaleblangley.haikalat.core.graph.FrameProfile;
import com.kaleblangley.haikalat.core.graph.PassResources;
import com.kaleblangley.haikalat.core.graph.RenderGraph;
import com.kaleblangley.haikalat.subsystems.postprocess.BloomPass;
import com.kaleblangley.haikalat.subsystems.postprocess.FxaaPostProcessor;
import com.kaleblangley.haikalat.subsystems.postprocess.ToneMappingPass;
import org.hismeo.haikalathost.client.graph.HiZPyramidPass;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;
import org.hismeo.haikalathost.client.gpu.DrawDataPageTable;
import org.hismeo.haikalathost.client.gpu.FrameArenaAllocation;
import org.hismeo.haikalathost.client.graph.MinecraftFrameGraphPlan;
import org.hismeo.haikalathost.client.gpu.GeometryPageTable;
import org.hismeo.haikalathost.client.gpu.HostTextureManager;
import org.hismeo.haikalathost.client.material.MaterialFeature;
import org.hismeo.haikalathost.client.material.MaterialKey;
import org.hismeo.haikalathost.client.material.MaterialRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import java.util.Set;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.glDrawElementsInstancedBaseVertexBaseInstance;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;

/**
 * H1 execution bridge. Frame data remains in Host-owned mapped buffers and is submitted in
 * material/geometry batches; Minecraft ShaderInstance and VertexBuffer never participate.
 */
public final class TakeoverFrameExecutor implements AutoCloseable {
    private static final String VERTEX_SHADER = """
            #version 460 core
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec4 aColor;
            layout(location = 2) in vec2 aUv;

            struct DrawData {
                ivec4 identifiers;
                vec4 origin;
            };
            struct TransformData {
                mat4 modelView;
                mat4 projection;
            };
            layout(std430, binding = 0) readonly buffer DrawTable {
                DrawData draws[];
            };
            layout(std430, binding = 1) readonly buffer TransformTable {
                TransformData transforms[];
            };

            out vec4 vColor;
            out vec2 vUv;

            void main() {
                DrawData draw = draws[gl_BaseInstance];
                TransformData transform = transforms[draw.identifiers.x];
                vec3 position = aPosition + draw.origin.xyz;
                gl_Position = transform.projection * transform.modelView * vec4(position, 1.0);
                vColor = aColor;
                vUv = aUv;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 460 core
            in vec4 vColor;
            in vec2 vUv;
            layout(location = 0) out vec4 outColor;

            uniform sampler2D uBaseColor;
            uniform int uTextured;
            uniform float uAlphaCutoff;

            void main() {
                vec4 color = vColor;
                if (uTextured != 0) color *= texture(uBaseColor, vUv);
                if (color.a < uAlphaCutoff) discard;
                outColor = color;
            }
            """;

    private final GlRenderDevice renderDevice;
    private final ShaderProgram shader;
    private final VertexArray vertexArray = new VertexArray();
    private final VertexLayout vertexLayout = VertexLayout.interleaved(
            CanonicalVertexLayout.STRIDE_BYTES,
            VertexAttribute.builder()
                    .index(0)
                    .size(3)
                    .type(GL_FLOAT)
                    .offsetBytes(CanonicalVertexLayout.POSITION_OFFSET)
                    .build(),
            VertexAttribute.builder()
                    .index(1)
                    .size(4)
                    .type(GL_UNSIGNED_BYTE)
                    .normalized(true)
                    .offsetBytes(CanonicalVertexLayout.COLOR_OFFSET)
                    .build(),
            VertexAttribute.builder()
                    .index(2)
                    .size(2)
                    .type(GL_FLOAT)
                    .offsetBytes(CanonicalVertexLayout.UV_OFFSET)
                    .build());
    private final HostTextureManager textures = new HostTextureManager();
    private final CommandBuffer commands = new CommandBuffer();
    private final List<IndirectBatchCommand> indirectCommands = new ArrayList<>();
    private final List<DirectBatchCommand> directCommands = new ArrayList<>();
    private final boolean multiDrawIndirect = Boolean.parseBoolean(
            System.getProperty("haikalathost.mdi", "true"));
    private boolean closed;

    public TakeoverFrameExecutor(GlRenderDevice renderDevice) {
        this.renderDevice = Objects.requireNonNull(renderDevice, "renderDevice");
        shader = ShaderProgram.fromSources(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public void execute(
            Framebuffer target,
            TakeoverDrawBuffer draws,
            GeometryPageTable geometryPages,
            DrawDataPageTable drawDataPages,
            FrameArenaAllocation transformTable,
            FrameArenaAllocation indirectTable,
            MaterialRegistry materials,
            RenderTypeMaterialRegistry renderTypes
    ) {
        ensureOpen();
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(draws, "draws");
        if (draws.size() == 0) {
            presentCleared(target);
            return;
        }
        Objects.requireNonNull(transformTable, "transformTable");
        Objects.requireNonNull(indirectTable, "indirectTable");

        commands.reset();
        commands.bindFramebuffer(target)
                .viewport(0, 0, target.width(), target.height())
                .enableFramebufferSrgb(true)
                .clearColor(0.0F, 0.0F, 0.0F, 1.0F)
                .depthMask(true)
                .clear(true, true)
                .bindShader(shader)
                .setUniformInt(shader, "uBaseColor", 0)
                .bindStorageBuffer(
                        1,
                        transformTable.buffer(),
                        transformTable.offsetBytes(),
                        transformTable.lengthBytes())
                .memoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
        for (int batch = 0; batch < draws.batchCount(); batch++) {
            int start = draws.batchStart(batch);
            int drawDataPage = draws.orderedDrawDataPage(start);
            commands.bindStorageBuffer(
                    0,
                    drawDataPages.buffer(drawDataPage),
                    0L,
                    drawDataPages.capacityBytes(drawDataPage));
            MaterialKey material = materials.get(draws.orderedMaterialId(start));
            boolean textured = MaterialFeature.contains(
                    material.features(), MaterialFeature.TEXTURED);
            configurePass(draws.orderedPass(start));
            commands.setUniformInt(shader, "uTextured", textured ? 1 : 0)
                    .setUniformFloat(shader, "uAlphaCutoff", material.alphaCutoff());
            if (textured) {
                if (material.texturePage() < 0) {
                    throw new IllegalStateException(
                            "Textured Host material has no resource texture page");
                }
                commands.bindTexture(
                        0, textures.resolve(renderTypes.textureLocation(material.texturePage())));
            }

            int geometryPage = draws.orderedGeometryPage(start);
            GlBuffer vertexBuffer = geometryPages.vertexBuffer(geometryPage);
            GlBuffer indexBuffer = geometryPages.indexBuffer(geometryPage);
            if (multiDrawIndirect) {
                IndirectBatchCommand indirect = indirectCommand(batch);
                indirect.configure(
                        vertexBuffer,
                        indexBuffer,
                        indirectTable.buffer(),
                        draws.batchGlMode(batch),
                        draws.batchGlIndexType(batch),
                        Math.addExact(
                                indirectTable.offsetBytes(),
                                Math.multiplyExact(start, DrawElementsIndirectCommand.BYTES)),
                        draws.batchLength(batch));
                commands.custom(indirect);
            } else {
                DirectBatchCommand direct = directCommand(batch);
                direct.configure(
                        draws,
                        vertexBuffer,
                        indexBuffer,
                        draws.batchGlMode(batch),
                        draws.batchGlIndexType(batch),
                        start,
                        draws.batchLength(batch));
                commands.custom(direct);
            }
        }
        commands.blitToDefault(target, target.width(), target.height());
        renderDevice.execute(commands);
    }

    public void reloadResources() {
        ensureOpen();
        textures.reload();
    }

    @Override
    public void close() {
        if (closed) return;
        textures.close();
        vertexArray.close();
        shader.close();
        indirectCommands.clear();
        directCommands.clear();
        closed = true;
    }

    private void presentCleared(Framebuffer target) {
        commands.reset();
        commands.bindFramebuffer(target)
                .viewport(0, 0, target.width(), target.height())
                .clearColor(0.0F, 0.0F, 0.0F, 1.0F)
                .depthMask(true)
                .clear(true, true)
                .blitToDefault(target, target.width(), target.height());
        renderDevice.execute(commands);
    }

    private void configurePass(PassKey pass) {
        boolean blended = switch (pass) {
            case WORLD_TRANSLUCENT, ENTITY_TRANSLUCENT, PARTICLE, OUTLINE,
                    FIRST_PERSON, POSTPROCESS, TEXT, UI -> true;
            default -> false;
        };
        boolean depthTest = switch (pass) {
            case POSTPROCESS, TEXT, UI -> false;
            default -> true;
        };
        commands.enableBlend(blended)
                .blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                .enableDepthTest(depthTest)
                .depthMask(depthTest && !blended)
                .enableCullFace(pass.cullsBackFaces());
    }

    private IndirectBatchCommand indirectCommand(int index) {
        while (indirectCommands.size() <= index) {
            indirectCommands.add(new IndirectBatchCommand());
        }
        return indirectCommands.get(index);
    }

    private DirectBatchCommand directCommand(int index) {
        while (directCommands.size() <= index) {
            directCommands.add(new DirectBatchCommand());
        }
        return directCommands.get(index);
    }

    private static int indexTypeBytes(int indexType) {
        return switch (indexType) {
            case GL_UNSIGNED_BYTE -> Byte.BYTES;
            case GL_UNSIGNED_SHORT -> Short.BYTES;
            case GL_UNSIGNED_INT -> Integer.BYTES;
            default -> throw new IllegalArgumentException(
                    "unsupported OpenGL index type " + indexType);
        };
    }


    private void ensureOpen() {
        if (closed) throw new IllegalStateException("takeover frame executor is closed");
    }

    private final class DirectBatchCommand implements Runnable {
        private TakeoverDrawBuffer draws;
        private GlBuffer vertexBuffer;
        private GlBuffer indexBuffer;
        private int mode;
        private int indexType;
        private int start;
        private int drawCount;

        private void configure(
                TakeoverDrawBuffer draws,
                GlBuffer vertexBuffer,
                GlBuffer indexBuffer,
                int mode,
                int indexType,
                int start,
                int drawCount
        ) {
            this.draws = Objects.requireNonNull(draws, "draws");
            this.vertexBuffer = Objects.requireNonNull(vertexBuffer, "vertexBuffer");
            this.indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
            if (start < 0 || drawCount < 0
                    || Math.addExact(start, drawCount) > draws.size()) {
                throw new IndexOutOfBoundsException(
                        "direct batch start=" + start + ", count=" + drawCount
                                + ", draws=" + draws.size());
            }
            indexTypeBytes(indexType);
            this.mode = mode;
            this.indexType = indexType;
            this.start = start;
            this.drawCount = drawCount;
        }

        @Override
        public void run() {
            vertexArray.bindVertexBuffer(vertexBuffer, vertexLayout)
                    .bindElementBuffer(indexBuffer);
            int bytesPerIndex = indexTypeBytes(indexType);
            int end = start + drawCount;
            for (int position = start; position < end; position++) {
                long indexOffsetBytes = Math.multiplyExact(
                        (long) draws.orderedFirstIndex(position), bytesPerIndex);
                glDrawElementsInstancedBaseVertexBaseInstance(
                        mode,
                        draws.orderedIndexCount(position),
                        indexType,
                        indexOffsetBytes,
                        draws.orderedInstanceCount(position),
                        draws.orderedBaseVertex(position),
                        draws.orderedBaseInstance(position));
            }
        }
    }
    private final class IndirectBatchCommand implements Runnable {
        private GlBuffer vertexBuffer;
        private GlBuffer indexBuffer;
        private GlBuffer indirectBuffer;
        private int mode;
        private int indexType;
        private long offsetBytes;
        private int drawCount;

        private void configure(
                GlBuffer vertexBuffer,
                GlBuffer indexBuffer,
                GlBuffer indirectBuffer,
                int mode,
                int indexType,
                long offsetBytes,
                int drawCount
        ) {
            this.vertexBuffer = Objects.requireNonNull(vertexBuffer, "vertexBuffer");
            this.indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
            this.indirectBuffer = Objects.requireNonNull(indirectBuffer, "indirectBuffer");
            this.mode = mode;
            this.indexType = indexType;
            this.offsetBytes = offsetBytes;
            this.drawCount = drawCount;
        }

        @Override
        public void run() {
            vertexArray.bindVertexBuffer(vertexBuffer, vertexLayout)
                    .bindElementBuffer(indexBuffer);
            indirectBuffer.bind();
            try {
                glMultiDrawElementsIndirect(mode, indexType, offsetBytes, drawCount, 0);
            } finally {
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            }
        }
    }
}
