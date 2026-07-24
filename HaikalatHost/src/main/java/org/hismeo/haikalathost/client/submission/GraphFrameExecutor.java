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
import com.kaleblangley.haikalat.core.graph.FrameProfile;
import com.kaleblangley.haikalat.core.graph.PassResources;
import com.kaleblangley.haikalat.core.graph.RenderGraph;
import com.kaleblangley.haikalat.subsystems.postprocess.BloomPass;
import com.kaleblangley.haikalat.subsystems.postprocess.FxaaPostProcessor;
import com.kaleblangley.haikalat.subsystems.postprocess.ToneMappingPass;
import org.hismeo.haikalathost.client.extraction.RenderTypeMaterialRegistry;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;
import org.hismeo.haikalathost.client.gpu.DrawDataPageTable;
import org.hismeo.haikalathost.client.gpu.FrameArenaAllocation;
import org.hismeo.haikalathost.client.gpu.GeometryPageTable;
import org.hismeo.haikalathost.client.gpu.HostTextureManager;
import org.hismeo.haikalathost.client.graph.HiZPyramidPass;
import org.hismeo.haikalathost.client.graph.MinecraftFrameGraphPlan;
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
import static org.lwjgl.opengl.GL42.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL46.GL_PARAMETER_BUFFER;
import static org.lwjgl.opengl.GL46.glMultiDrawElementsIndirectCount;

/**
 * H5 execution path. RenderGraph owns every frame target and records each H4 coverage group once.
 */
public final class GraphFrameExecutor implements AutoCloseable {
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

    private static final String CULLING_COMPUTE_SHADER = """
            #version 460 core
            layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;

            struct IndirectCommand {
                uint count;
                uint instanceCount;
                uint firstIndex;
                int baseVertex;
                uint baseInstance;
            };
            struct CullingMetadata {
                vec4 sphere;
                ivec4 control;
            };
            struct TransformData {
                mat4 modelView;
                mat4 projection;
            };

            layout(std430, binding = 0) readonly buffer SourceCommandTable {
                IndirectCommand sourceCommands[];
            };
            layout(std430, binding = 1) writeonly buffer CompactCommandTable {
                IndirectCommand compactCommands[];
            };
            layout(std430, binding = 2) readonly buffer CullingTable {
                CullingMetadata culling[];
            };
            layout(std430, binding = 3) readonly buffer TransformTable {
                TransformData transforms[];
            };
            layout(std430, binding = 4) buffer CountTable {
                uint counts[];
            };

            uniform int uDrawCount;

            bool outsidePlane(vec4 plane, vec4 center, float radius) {
                return dot(plane, center) < -radius * length(plane.xyz);
            }

            bool sphereVisible(CullingMetadata metadata) {
                const uint FRUSTUM_SPHERE = 1u;
                uint flags = uint(metadata.control.w);
                if ((flags & FRUSTUM_SPHERE) == 0u) return true;

                TransformData transform = transforms[metadata.control.x];
                mat4 clip = transform.projection * transform.modelView;
                vec4 row0 = vec4(clip[0][0], clip[1][0], clip[2][0], clip[3][0]);
                vec4 row1 = vec4(clip[0][1], clip[1][1], clip[2][1], clip[3][1]);
                vec4 row2 = vec4(clip[0][2], clip[1][2], clip[2][2], clip[3][2]);
                vec4 row3 = vec4(clip[0][3], clip[1][3], clip[2][3], clip[3][3]);
                vec4 center = vec4(metadata.sphere.xyz, 1.0);
                float radius = metadata.sphere.w;
                return !outsidePlane(row3 + row0, center, radius)
                        && !outsidePlane(row3 - row0, center, radius)
                        && !outsidePlane(row3 + row1, center, radius)
                        && !outsidePlane(row3 - row1, center, radius)
                        && !outsidePlane(row3 + row2, center, radius)
                        && !outsidePlane(row3 - row2, center, radius);
            }

            void main() {
                const uint COMPACT = 2u;
                uint sourceIndex = gl_GlobalInvocationID.x;
                if (sourceIndex >= uint(uDrawCount)) return;
                CullingMetadata metadata = culling[sourceIndex];
                if ((uint(metadata.control.w) & COMPACT) == 0u) return;
                if (!sphereVisible(metadata)) return;

                uint batchIndex = uint(metadata.control.y);
                uint outputIndex = uint(metadata.control.z) + atomicAdd(counts[batchIndex], 1u);
                compactCommands[outputIndex] = sourceCommands[sourceIndex];
            }
            """;

    private final GlRenderDevice renderDevice;
    private final ShaderProgram shader;
    private final ShaderProgram cullingShader;
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
    private final HiZPyramidPass hiZ;
    private final BloomPass bloom;
    private final ToneMappingPass toneMapping;
    private final FxaaPostProcessor fxaa;
    private final RenderGraph frameGraph;
    private final List<IndirectBatchCommand> indirectCommands = new ArrayList<>();
    private final List<CountedIndirectBatchCommand> countedIndirectCommands = new ArrayList<>();
    private final List<DirectBatchCommand> directCommands = new ArrayList<>();
    private final boolean multiDrawIndirect = Boolean.parseBoolean(
            System.getProperty("haikalathost.mdi", "true"));
    private final boolean gpuDriven;
    private final boolean bloomEnabled = Boolean.parseBoolean(
            System.getProperty("haikalathost.bloom", "true"));
    private final boolean fxaaEnabled = Boolean.parseBoolean(
            System.getProperty("haikalathost.fxaa", "true"));
    private final float exposure = positiveProperty("haikalathost.exposure", 1.0F);
    private final float bloomThreshold = nonNegativeProperty(
            "haikalathost.bloomThreshold", 1.0F);
    private final float bloomSoftKnee = unitProperty("haikalathost.bloomSoftKnee", 0.5F);
    private final float bloomIntensity = nonNegativeProperty(
            "haikalathost.bloomIntensity", 0.08F);
    private FrameContext activeFrame;
    private boolean closed;

    public GraphFrameExecutor(GlRenderDevice renderDevice, int width, int height) {
        this(renderDevice, width, height, true);
    }

    public GraphFrameExecutor(
            GlRenderDevice renderDevice, int width, int height, boolean gpuDriven) {
        this.renderDevice = Objects.requireNonNull(renderDevice, "renderDevice");
        this.gpuDriven = gpuDriven;
        shader = ShaderProgram.fromSources(VERTEX_SHADER, FRAGMENT_SHADER);
        cullingShader = gpuDriven ? ShaderProgram.fromComputeSource(CULLING_COMPUTE_SHADER) : null;
        hiZ = new HiZPyramidPass();
        bloom = new BloomPass();
        toneMapping = new ToneMappingPass();
        fxaa = new FxaaPostProcessor();
        frameGraph = new RenderGraph(positive(width, "width"), positive(height, "height"));
        buildFrameGraph();
        frameGraph.compile();
        frameGraph.sealTopology();
    }

    public void execute(
            TakeoverDrawBuffer draws,
            GeometryPageTable geometryPages,
            DrawDataPageTable drawDataPages,
            FrameArenaAllocation transformTable,
            FrameArenaAllocation indirectTable,
            FrameArenaAllocation compactIndirectTable,
            FrameArenaAllocation indirectCountTable,
            FrameArenaAllocation cullingMetadataTable,
            MaterialRegistry materials,
            RenderTypeMaterialRegistry renderTypes
    ) {
        ensureOpen();
        Objects.requireNonNull(draws, "draws");
        Objects.requireNonNull(geometryPages, "geometryPages");
        Objects.requireNonNull(drawDataPages, "drawDataPages");
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(renderTypes, "renderTypes");
        if (draws.size() > 0) {
            Objects.requireNonNull(transformTable, "transformTable");
            Objects.requireNonNull(indirectTable, "indirectTable");
            if (gpuDriven) {
                Objects.requireNonNull(compactIndirectTable, "compactIndirectTable");
                Objects.requireNonNull(indirectCountTable, "indirectCountTable");
                Objects.requireNonNull(cullingMetadataTable, "cullingMetadataTable");
            }
        }
        if (activeFrame != null) {
            throw new IllegalStateException("takeover frame graph is already executing");
        }
        activeFrame = new FrameContext(
                draws, geometryPages, drawDataPages, transformTable, indirectTable,
                compactIndirectTable, indirectCountTable, cullingMetadataTable,
                materials, renderTypes);
        try {
            frameGraph.execute(renderDevice);
        } finally {
            activeFrame = null;
        }
    }

    public void resize(int width, int height) {
        ensureOpen();
        frameGraph.resize(positive(width, "width"), positive(height, "height"));
    }

    public void reloadResources() {
        ensureOpen();
        textures.reload();
    }

    public FrameProfile frameProfile() {
        ensureOpen();
        return frameGraph.lastFrameProfile();
    }

    public RenderGraph.Description graphDescription() {
        ensureOpen();
        return frameGraph.description();
    }

    public int recordedGraphCommandCount() {
        ensureOpen();
        return frameGraph.lastRecordedCommandCount();
    }

    @Override
    public void close() {
        if (closed) return;
        frameGraph.close();
        fxaa.close();
        toneMapping.close();
        bloom.close();
        hiZ.close();
        if (cullingShader != null) cullingShader.close();
        textures.close();
        vertexArray.close();
        shader.close();
        countedIndirectCommands.clear();
        indirectCommands.clear();
        directCommands.clear();
        closed = true;
    }

    private void buildFrameGraph() {
        frameGraph.addPass(MinecraftFrameGraphPlan.GPU_CULL_COMPACT)
                .writeToExternalTarget()
                .noClear()
                .execute((resources, commands) -> recordGpuCulling(commands));

        frameGraph.addPass(MinecraftFrameGraphPlan.DEPTH_OPAQUE)
                .createColor(MinecraftFrameGraphPlan.SCENE_COLOR, RenderFormat.RGBA16F)
                .createDepthTexture(MinecraftFrameGraphPlan.SCENE_DEPTH)
                .clearColor(0.0F, 0.0F, 0.0F, 1.0F)
                .dependsOn(MinecraftFrameGraphPlan.GPU_CULL_COMPACT)
                .execute((resources, commands) -> recordBatches(
                        commands, coverage(MinecraftFrameGraphPlan.DEPTH_OPAQUE)));

        frameGraph.addPass(MinecraftFrameGraphPlan.HIZ_HALF)
                .createColor(MinecraftFrameGraphPlan.HIZ_HALF_TEXTURE, RenderFormat.R16F)
                .relativeSize(0.5F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.DEPTH_OPAQUE)
                .execute((resources, commands) -> recordHiZ(
                        resources, commands,
                        MinecraftFrameGraphPlan.SCENE_DEPTH,
                        MinecraftFrameGraphPlan.DEPTH_OPAQUE));
        frameGraph.addPass(MinecraftFrameGraphPlan.HIZ_QUARTER)
                .createColor(MinecraftFrameGraphPlan.HIZ_QUARTER_TEXTURE, RenderFormat.R16F)
                .relativeSize(0.25F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.HIZ_HALF)
                .execute((resources, commands) -> recordHiZ(
                        resources, commands,
                        MinecraftFrameGraphPlan.HIZ_HALF_TEXTURE,
                        MinecraftFrameGraphPlan.HIZ_HALF));
        frameGraph.addPass(MinecraftFrameGraphPlan.HIZ_EIGHTH)
                .createColor(MinecraftFrameGraphPlan.HIZ_EIGHTH_TEXTURE, RenderFormat.R16F)
                .relativeSize(0.125F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.HIZ_QUARTER)
                .execute((resources, commands) -> recordHiZ(
                        resources, commands,
                        MinecraftFrameGraphPlan.HIZ_QUARTER_TEXTURE,
                        MinecraftFrameGraphPlan.HIZ_QUARTER));

        addScenePass(MinecraftFrameGraphPlan.WORLD_CUTOUT);
        addScenePass(MinecraftFrameGraphPlan.ENTITIES);
        addScenePass(MinecraftFrameGraphPlan.SKY_CLOUD_WEATHER);
        addScenePass(MinecraftFrameGraphPlan.TRANSLUCENT);
        addScenePass(MinecraftFrameGraphPlan.PARTICLES);
        addScenePass(MinecraftFrameGraphPlan.OUTLINE_FIRST_PERSON_DEBUG);

        frameGraph.addPass(MinecraftFrameGraphPlan.EXPOSURE)
                .createColor(MinecraftFrameGraphPlan.EXPOSURE_TEXTURE, RenderFormat.R16F)
                .fixedSize(1, 1)
                .clearColor(exposure, 0.0F, 0.0F, 1.0F)
                .dependsOn(MinecraftFrameGraphPlan.OUTLINE_FIRST_PERSON_DEBUG)
                .execute((resources, commands) -> {
                });

        frameGraph.addPass(MinecraftFrameGraphPlan.BLOOM_EXTRACT)
                .createColor(MinecraftFrameGraphPlan.BLOOM_HALF, RenderFormat.RGBA16F)
                .relativeSize(0.5F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.OUTLINE_FIRST_PERSON_DEBUG)
                .execute((resources, commands) -> {
                    if (bloomEnabled) {
                        bloom.recordExtract(
                                commands,
                                resources.colorAttachment(MinecraftFrameGraphPlan.SCENE_COLOR),
                                frameGraph.width(),
                                frameGraph.height(),
                                bloomThreshold,
                                bloomSoftKnee);
                    }
                });
        frameGraph.addPass(MinecraftFrameGraphPlan.BLOOM_DOWNSAMPLE)
                .createColor(MinecraftFrameGraphPlan.BLOOM_QUARTER, RenderFormat.RGBA16F)
                .relativeSize(0.25F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.BLOOM_EXTRACT)
                .execute((resources, commands) -> {
                    if (bloomEnabled) {
                        Framebuffer source = resources.framebufferOfPass(
                                MinecraftFrameGraphPlan.BLOOM_EXTRACT);
                        bloom.recordDownsample(
                                commands,
                                resources.colorAttachment(MinecraftFrameGraphPlan.BLOOM_HALF),
                                source.width(),
                                source.height());
                    }
                });
        frameGraph.addPass(MinecraftFrameGraphPlan.BLOOM_UPSAMPLE)
                .createColor(MinecraftFrameGraphPlan.BLOOM_COMBINED, RenderFormat.RGBA16F)
                .relativeSize(0.5F)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.BLOOM_DOWNSAMPLE)
                .execute((resources, commands) -> {
                    if (bloomEnabled) {
                        Framebuffer low = resources.framebufferOfPass(
                                MinecraftFrameGraphPlan.BLOOM_DOWNSAMPLE);
                        bloom.recordUpsample(
                                commands,
                                resources.colorAttachment(MinecraftFrameGraphPlan.BLOOM_HALF),
                                resources.colorAttachment(MinecraftFrameGraphPlan.BLOOM_QUARTER),
                                low.width(),
                                low.height());
                    }
                });

        frameGraph.addPass(MinecraftFrameGraphPlan.TONE_MAPPING)
                .createColor(MinecraftFrameGraphPlan.TONEMAPPED_COLOR, RenderFormat.RGBA8)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.EXPOSURE)
                .dependsOn(MinecraftFrameGraphPlan.BLOOM_UPSAMPLE)
                .execute((resources, commands) -> toneMapping.recordIntoCurrentTarget(
                        commands,
                        resources.colorAttachment(MinecraftFrameGraphPlan.SCENE_COLOR),
                        bloomEnabled
                                ? resources.colorAttachment(MinecraftFrameGraphPlan.BLOOM_COMBINED)
                                : 0,
                        exposure,
                        resources.colorAttachment(MinecraftFrameGraphPlan.EXPOSURE_TEXTURE),
                        bloomEnabled ? bloomIntensity : 0.0F));

        frameGraph.addPass(MinecraftFrameGraphPlan.FXAA)
                .createColor(MinecraftFrameGraphPlan.FXAA_COLOR, RenderFormat.RGBA8)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.TONE_MAPPING)
                .execute((resources, commands) -> {
                    Framebuffer source = resources.framebufferOfPass(
                            MinecraftFrameGraphPlan.TONE_MAPPING);
                    if (fxaaEnabled) {
                        fxaa.recordIntoCurrentTarget(
                                commands,
                                resources.colorAttachment(
                                        MinecraftFrameGraphPlan.TONEMAPPED_COLOR),
                                source.width(),
                                source.height());
                    } else {
                        Framebuffer target = resources.currentTarget();
                        commands.blitColor(source, target)
                                .bindFramebuffer(target)
                                .viewport(0, 0, target.width(), target.height());
                    }
                });

        frameGraph.addPass(MinecraftFrameGraphPlan.UI_TEXT)
                .createColor(MinecraftFrameGraphPlan.UI_COLOR, RenderFormat.RGBA8)
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.FXAA)
                .execute((resources, commands) -> {
                    Framebuffer source = resources.framebufferOfPass(MinecraftFrameGraphPlan.FXAA);
                    Framebuffer target = resources.currentTarget();
                    commands.blitColor(source, target)
                            .bindFramebuffer(target)
                            .viewport(0, 0, target.width(), target.height());
                    recordBatches(commands, coverage(MinecraftFrameGraphPlan.UI_TEXT));
                });

        frameGraph.addPass(MinecraftFrameGraphPlan.PRESENT)
                .writeToBackbuffer()
                .noClear()
                .dependsOn(MinecraftFrameGraphPlan.UI_TEXT)
                .execute((resources, commands) -> {
                    Framebuffer source = resources.framebufferOfPass(
                            MinecraftFrameGraphPlan.UI_TEXT);
                    commands.blitToDefault(source, frameGraph.width(), frameGraph.height());
                });
    }

    private void addScenePass(String name) {
        MinecraftFrameGraphPlan.Node node = MinecraftFrameGraphPlan.node(name);
        RenderGraph.PassBuilder builder = frameGraph.addPass(name)
                .writeToExternalTarget()
                .noClear();
        for (String dependency : node.dependencies()) {
            builder.dependsOn(dependency);
        }
        builder.execute((resources, commands) -> {
            Framebuffer scene = resources.framebufferOfPass(MinecraftFrameGraphPlan.DEPTH_OPAQUE);
            commands.bindFramebuffer(scene)
                    .viewport(0, 0, scene.width(), scene.height())
                    .enableFramebufferSrgb(false);
            recordBatches(commands, node.coverage());
        });
    }

    private void recordHiZ(
            PassResources resources,
            CommandBuffer commands,
            String sourceTexture,
            String sourcePass
    ) {
        Framebuffer source = resources.framebufferOfPass(sourcePass);
        int texture = sourceTexture.equals(MinecraftFrameGraphPlan.SCENE_DEPTH)
                ? resources.depthAttachment(sourceTexture)
                : resources.colorAttachment(sourceTexture);
        hiZ.record(commands, texture, source.width(), source.height());
    }

    private void recordGpuCulling(CommandBuffer commands) {
        FrameContext frame = activeFrame();
        TakeoverDrawBuffer draws = frame.draws();
        if (!gpuDriven || !multiDrawIndirect || draws.size() == 0) return;

        FrameArenaAllocation source = Objects.requireNonNull(
                frame.indirectTable(), "indirectTable");
        FrameArenaAllocation compact = Objects.requireNonNull(
                frame.compactIndirectTable(), "compactIndirectTable");
        FrameArenaAllocation metadata = Objects.requireNonNull(
                frame.cullingMetadataTable(), "cullingMetadataTable");
        FrameArenaAllocation transforms = Objects.requireNonNull(
                frame.transformTable(), "transformTable");
        FrameArenaAllocation counts = Objects.requireNonNull(
                frame.indirectCountTable(), "indirectCountTable");
        ShaderProgram compute = Objects.requireNonNull(cullingShader, "cullingShader");

        commands.bindShader(compute)
                .setUniformInt(compute, "uDrawCount", draws.size())
                .bindStorageBuffer(
                        0, source.buffer(), source.offsetBytes(), source.lengthBytes())
                .bindStorageBuffer(
                        1, compact.buffer(), compact.offsetBytes(), compact.lengthBytes())
                .bindStorageBuffer(
                        2, metadata.buffer(), metadata.offsetBytes(), metadata.lengthBytes())
                .bindStorageBuffer(
                        3, transforms.buffer(), transforms.offsetBytes(), transforms.lengthBytes())
                .bindStorageBuffer(
                        4, counts.buffer(), counts.offsetBytes(), counts.lengthBytes())
                .memoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT)
                .dispatchCompute((draws.size() + 63) / 64, 1, 1)
                .memoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
    }

    private void recordBatches(CommandBuffer commands, Set<PassKey> coveredPasses) {
        FrameContext frame = activeFrame();
        TakeoverDrawBuffer draws = frame.draws();
        if (draws.size() == 0 || coveredPasses.isEmpty()) return;

        boolean initialized = false;
        for (int batch = 0; batch < draws.batchCount(); batch++) {
            int start = draws.batchStart(batch);
            PassKey pass = draws.orderedPass(start);
            if (!coveredPasses.contains(pass)) continue;
            if (!initialized) {
                FrameArenaAllocation transforms = Objects.requireNonNull(
                        frame.transformTable(), "transformTable");
                commands.bindShader(shader)
                        .setUniformInt(shader, "uBaseColor", 0)
                        .bindStorageBuffer(
                                1,
                                transforms.buffer(),
                                transforms.offsetBytes(),
                                transforms.lengthBytes())
                        .memoryBarrier(GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);
                initialized = true;
            }
            int drawDataPage = draws.orderedDrawDataPage(start);
            commands.bindStorageBuffer(
                    0,
                    frame.drawDataPages().buffer(drawDataPage),
                    0L,
                    frame.drawDataPages().capacityBytes(drawDataPage));
            MaterialKey material = frame.materials().get(draws.orderedMaterialId(start));
            boolean textured = MaterialFeature.contains(
                    material.features(), MaterialFeature.TEXTURED);
            configurePass(commands, pass);
            commands.setUniformInt(shader, "uTextured", textured ? 1 : 0)
                    .setUniformFloat(shader, "uAlphaCutoff", material.alphaCutoff());
            if (textured) {
                if (material.texturePage() < 0) {
                    throw new IllegalStateException(
                            "Textured Host material has no resource texture page");
                }
                commands.bindTexture(
                        0, textures.resolve(
                                frame.renderTypes().textureLocation(material.texturePage())));
            }

            int geometryPage = draws.orderedGeometryPage(start);
            GlBuffer vertexBuffer = frame.geometryPages().vertexBuffer(geometryPage);
            GlBuffer indexBuffer = frame.geometryPages().indexBuffer(geometryPage);
            if (multiDrawIndirect) {
                if (gpuDriven && draws.batchGpuCompactable(batch)) {
                    FrameArenaAllocation compact = Objects.requireNonNull(
                            frame.compactIndirectTable(), "compactIndirectTable");
                    FrameArenaAllocation counts = Objects.requireNonNull(
                            frame.indirectCountTable(), "indirectCountTable");
                    CountedIndirectBatchCommand indirect = countedIndirectCommand(batch);
                    indirect.configure(
                            vertexBuffer,
                            indexBuffer,
                            compact.buffer(),
                            counts.buffer(),
                            draws.batchGlMode(batch),
                            draws.batchGlIndexType(batch),
                            Math.addExact(compact.offsetBytes(), Math.multiplyExact(
                                    start, DrawElementsIndirectCommand.BYTES)),
                            Math.addExact(counts.offsetBytes(), Math.multiplyExact(
                                    batch, Integer.BYTES)),
                            draws.batchLength(batch));
                    commands.custom(indirect);
                } else {
                    FrameArenaAllocation indirectTable = Objects.requireNonNull(
                            frame.indirectTable(), "indirectTable");
                    IndirectBatchCommand indirect = indirectCommand(batch);
                    indirect.configure(
                            vertexBuffer, indexBuffer, indirectTable.buffer(),
                            draws.batchGlMode(batch), draws.batchGlIndexType(batch),
                            Math.addExact(indirectTable.offsetBytes(), Math.multiplyExact(
                                    start, DrawElementsIndirectCommand.BYTES)),
                            draws.batchLength(batch));
                    commands.custom(indirect);
                }
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
    }

    private static Set<PassKey> coverage(String nodeName) {
        return MinecraftFrameGraphPlan.node(nodeName).coverage();
    }

    private static void configurePass(CommandBuffer commands, PassKey pass) {
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

    private FrameContext activeFrame() {
        if (activeFrame == null) {
            throw new IllegalStateException("frame-graph pass executed without active frame data");
        }
        return activeFrame;
    }

    private IndirectBatchCommand indirectCommand(int index) {
        while (indirectCommands.size() <= index) {
            indirectCommands.add(new IndirectBatchCommand());
        }
        return indirectCommands.get(index);
    }

    private CountedIndirectBatchCommand countedIndirectCommand(int index) {
        while (countedIndirectCommands.size() <= index) {
            countedIndirectCommands.add(new CountedIndirectBatchCommand());
        }
        return countedIndirectCommands.get(index);
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

    private static int positive(int value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
        return value;
    }

    private static float positiveProperty(String name, float defaultValue) {
        float value = Float.parseFloat(System.getProperty(name, Float.toString(defaultValue)));
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static float nonNegativeProperty(String name, float defaultValue) {
        float value = Float.parseFloat(System.getProperty(name, Float.toString(defaultValue)));
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static float unitProperty(String name, float defaultValue) {
        float value = nonNegativeProperty(name, defaultValue);
        if (value > 1.0F) throw new IllegalArgumentException(name + " must be at most 1");
        return value;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("graph frame executor is closed");
    }

    private record FrameContext(
            TakeoverDrawBuffer draws,
            GeometryPageTable geometryPages,
            DrawDataPageTable drawDataPages,
            FrameArenaAllocation transformTable,
            FrameArenaAllocation indirectTable,
            FrameArenaAllocation compactIndirectTable,
            FrameArenaAllocation indirectCountTable,
            FrameArenaAllocation cullingMetadataTable,
            MaterialRegistry materials,
            RenderTypeMaterialRegistry renderTypes
    ) {
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

    private final class CountedIndirectBatchCommand implements Runnable {
        private GlBuffer vertexBuffer;
        private GlBuffer indexBuffer;
        private GlBuffer indirectBuffer;
        private GlBuffer countBuffer;
        private int mode;
        private int indexType;
        private long indirectOffsetBytes;
        private long countOffsetBytes;
        private int maxDrawCount;

        private void configure(
                GlBuffer vertexBuffer,
                GlBuffer indexBuffer,
                GlBuffer indirectBuffer,
                GlBuffer countBuffer,
                int mode,
                int indexType,
                long indirectOffsetBytes,
                long countOffsetBytes,
                int maxDrawCount
        ) {
            this.vertexBuffer = Objects.requireNonNull(vertexBuffer, "vertexBuffer");
            this.indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
            this.indirectBuffer = Objects.requireNonNull(indirectBuffer, "indirectBuffer");
            this.countBuffer = Objects.requireNonNull(countBuffer, "countBuffer");
            if (indirectOffsetBytes < 0L || countOffsetBytes < 0L || maxDrawCount < 0) {
                throw new IllegalArgumentException("indirect-count offsets/count must be non-negative");
            }
            indexTypeBytes(indexType);
            this.mode = mode;
            this.indexType = indexType;
            this.indirectOffsetBytes = indirectOffsetBytes;
            this.countOffsetBytes = countOffsetBytes;
            this.maxDrawCount = maxDrawCount;
        }

        @Override
        public void run() {
            vertexArray.bindVertexBuffer(vertexBuffer, vertexLayout)
                    .bindElementBuffer(indexBuffer);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectBuffer.id());
            glBindBuffer(GL_PARAMETER_BUFFER, countBuffer.id());
            try {
                glMultiDrawElementsIndirectCount(
                        mode,
                        indexType,
                        indirectOffsetBytes,
                        countOffsetBytes,
                        maxDrawCount,
                        0);
            } finally {
                glBindBuffer(GL_PARAMETER_BUFFER, 0);
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
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
