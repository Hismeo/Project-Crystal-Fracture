package org.hismeo.haikalathost.client.submission;

import com.kaleblangley.haikalat.core.device.GlRenderDevice;
import com.kaleblangley.haikalat.core.graph.FrameProfile;
import com.kaleblangley.haikalat.core.graph.PassProfile;
import com.kaleblangley.haikalat.core.graph.RenderGraph;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.hismeo.haikalathost.client.chunk.LongLivedChunkMesh;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.diagnostics.FramePerformanceSnapshot;
import org.hismeo.haikalathost.client.diagnostics.FramePerformanceTracker;
import org.hismeo.haikalathost.client.extraction.MaterialBinding;
import org.hismeo.haikalathost.client.extraction.RenderTypeMaterialRegistry;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;
import org.hismeo.haikalathost.client.geometry.MinecraftCanonicalVertexEncoder;
import org.hismeo.haikalathost.client.geometry.MinecraftSequentialIndexEncoder;
import org.hismeo.haikalathost.client.gpu.DrawDataPageTable;
import org.hismeo.haikalathost.client.gpu.FrameArenaAllocation;
import org.hismeo.haikalathost.client.gpu.FrameArenaRegion;
import org.hismeo.haikalathost.client.gpu.FrameTransformTable;
import org.hismeo.haikalathost.client.gpu.GeometryPageTable;
import org.hismeo.haikalathost.client.gpu.GpuDrawData;
import org.hismeo.haikalathost.client.gpu.GpuCullingMetadata;
import org.hismeo.haikalathost.client.gpu.PersistentFrameArena;
import org.hismeo.haikalathost.client.gpu.PersistentFrameArenaConfiguration;
import org.hismeo.haikalathost.client.material.MaterialRegistry;
import org.hismeo.haikalathost.client.scene.MaterialId;
import org.hismeo.haikalathost.client.staticmesh.LongLivedStaticMesh;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT;

/**
 * H1 generic capture path: Minecraft produces CPU MeshData; Host owns every byte after endBatch.
 */
public final class TakeoverFrameSubmission implements AutoCloseable {
    private final PersistentFrameArena arena;
    private static final float CHUNK_BOUNDING_SPHERE_RADIUS = (float) (Math.sqrt(3.0) * 8.0);
    private final RenderTypeMaterialRegistry renderTypes = new RenderTypeMaterialRegistry();
    private final MaterialRegistry materials = new MaterialRegistry();
    private final FrameTransformTable transforms = new FrameTransformTable(64);
    private final GraphFrameExecutor executor;
    private final MinecraftCanonicalVertexEncoder vertices = new MinecraftCanonicalVertexEncoder();
    private FrameArenaAllocation transformTableAllocation;
    private final boolean gpuDriven;
    private final int storageBufferOffsetAlignment;
    private final TakeoverDrawBuffer draws = new TakeoverDrawBuffer(4096);
    private final GeometryPageTable geometryPages = new GeometryPageTable(4);
    private final DrawDataPageTable drawDataPages = new DrawDataPageTable(4);
    private final FramePerformanceTracker performance = new FramePerformanceTracker(240);
    private final int performanceLogInterval = Math.max(
            0, Integer.getInteger("haikalathost.performanceLogInterval", 0));

    private FrameArenaAllocation indirectAllocation;
    private long nextSequence;
    private long frameStartedNanos;
    private boolean frameActive;
    private FrameArenaAllocation compactIndirectAllocation;
    private FrameArenaAllocation indirectCountAllocation;
    private FrameArenaAllocation cullingMetadataAllocation;
    private boolean closed;

    public TakeoverFrameSubmission(GlRenderDevice renderDevice, int width, int height) {
        this(renderDevice, width, height, true);
    }

    public TakeoverFrameSubmission(
            GlRenderDevice renderDevice, int width, int height, boolean gpuDriven) {
        RenderSystem.assertOnRenderThread();
        this.gpuDriven = gpuDriven;
        storageBufferOffsetAlignment = glGetInteger(
                GL_SHADER_STORAGE_BUFFER_OFFSET_ALIGNMENT);
        if (storageBufferOffsetAlignment <= 0) {
            throw new IllegalStateException("OpenGL reported an invalid SSBO offset alignment");
        }
        arena = new PersistentFrameArena(
                PersistentFrameArenaConfiguration.fromSystemProperties());
        try {
            executor = new GraphFrameExecutor(renderDevice, width, height, gpuDriven);
        } catch (Throwable failure) {
            arena.close();
            throw failure;
        }
    }

    public void beginFrame() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (frameActive) throw new IllegalStateException("submission frame is already active");
        frameStartedNanos = System.nanoTime();
        arena.beginFrame();
        draws.clear();
        geometryPages.clear();
        drawDataPages.clear();
        transforms.clear();
        indirectAllocation = null;
        compactIndirectAllocation = null;
        indirectCountAllocation = null;
        cullingMetadataAllocation = null;
        transformTableAllocation = null;
        nextSequence = 0L;
        frameActive = true;
    }

    public void capture(RenderType renderType, MeshData meshData) {
        capture(renderType, meshData, 0.0F,
                transforms.resolve(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix()), 0);
    }

    public void capture(
            RenderType renderType,
            MeshData meshData,
            float viewDepth,
            int transformIndex,
            int objectFlags
    ) {
        ensureActive();
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(meshData, "meshData");
        try (meshData) {
            MaterialBinding binding = renderTypes.resolve(renderType);
            captureResolved(binding, meshData, viewDepth, transformIndex, objectFlags);
        }
    }

    /** Adds a long-lived chunk slice without copying its vertex or index payload this frame. */
    public void captureChunk(
            LongLivedChunkMesh mesh,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc modelView,
            Matrix4fc projection
    ) {
        ensureActive();
        Objects.requireNonNull(mesh, "mesh");
        MaterialBinding binding = renderTypes.resolve(mesh.renderType());
        MaterialId materialId = materials.resolve(binding.material());
        int transformIndex = transforms.resolve(modelView, projection);
        float originX = (float) (mesh.originX() - cameraX);
        float originY = (float) (mesh.originY() - cameraY);
        float originZ = (float) (mesh.originZ() - cameraZ);

        FrameArenaAllocation drawDataAllocation = arena.allocate(
                FrameArenaRegion.DRAW_DATA, GpuDrawData.BYTES, GpuDrawData.BYTES);
        new GpuDrawData(transformIndex, materialId.value(), 1, originX, originY, originZ)
                .writeTo(drawDataAllocation.mappedSlice());
        int drawDataPage = drawDataPages.resolve(
                drawDataAllocation.buffer(), drawDataAllocation.bufferCapacityBytes());
        int geometryPage = geometryPages.resolve(mesh.vertexBuffer(), mesh.indexBuffer());
        int baseInstance = drawDataAllocation.offsetBytes() / GpuDrawData.BYTES;
        int draw = draws.append(
                binding.pass(),
                binding.pipelineId(),
                materialId.value(),
                geometryPage,
                drawDataPage,
                mesh.glMode(),
                mesh.glIndexType(),
                mesh.indexCount(),
                1,
                mesh.firstIndex(),
                mesh.baseVertex(),
                baseInstance,
                mesh.squaredDistanceTo(cameraX, cameraY, cameraZ),
                nextSequence++);
        draws.setCullingSphere(
                draw,
                transformIndex,
                originX + 8.0F,
                originY + 8.0F,
                originZ + 8.0F,
                CHUNK_BOUNDING_SPHERE_RADIUS);
    }

    /** Submits one visible chunk layer with a single draw-data arena allocation. */
    public void captureChunkLayer(
            RenderType renderType,
            LongLivedChunkMesh[] meshes,
            int meshCount,
            double cameraX,
            double cameraY,
            double cameraZ,
            Matrix4fc modelView,
            Matrix4fc projection
    ) {
        ensureActive();
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(meshes, "meshes");
        if (meshCount < 0 || meshCount > meshes.length) {
            throw new IndexOutOfBoundsException(
                    "meshCount=" + meshCount + ", capacity=" + meshes.length);
        }
        if (meshCount == 0) return;

        MaterialBinding binding = renderTypes.resolve(renderType);
        MaterialId materialId = materials.resolve(binding.material());
        int transformIndex = transforms.resolve(modelView, projection);
        int drawDataBytes = Math.multiplyExact(meshCount, GpuDrawData.BYTES);
        FrameArenaAllocation drawDataAllocation = arena.allocate(
                FrameArenaRegion.DRAW_DATA, drawDataBytes, GpuDrawData.BYTES);
        ByteBuffer drawData = drawDataAllocation.mappedSlice();
        int drawDataPage = drawDataPages.resolve(
                drawDataAllocation.buffer(), drawDataAllocation.bufferCapacityBytes());
        int firstBaseInstance = drawDataAllocation.offsetBytes() / GpuDrawData.BYTES;

        for (int index = 0; index < meshCount; index++) {
            LongLivedChunkMesh mesh = Objects.requireNonNull(meshes[index], "mesh");
            float originX = (float) (mesh.originX() - cameraX);
            float originY = (float) (mesh.originY() - cameraY);
            float originZ = (float) (mesh.originZ() - cameraZ);
            GpuDrawData.writeTo(
                    drawData, transformIndex, materialId.value(), 1, originX, originY, originZ);
            int geometryPage = geometryPages.resolve(mesh.vertexBuffer(), mesh.indexBuffer());
            int draw = draws.append(
                    binding.pass(),
                    binding.pipelineId(),
                    materialId.value(),
                    geometryPage,
                    drawDataPage,
                    mesh.glMode(),
                    mesh.glIndexType(),
                    mesh.indexCount(),
                    1,
                    mesh.firstIndex(),
                    mesh.baseVertex(),
                    firstBaseInstance + index,
                    mesh.squaredDistanceTo(cameraX, cameraY, cameraZ),
                    nextSequence++);
            draws.setCullingSphere(
                    draw,
                    transformIndex,
                    originX + 8.0F,
                    originY + 8.0F,
                    originZ + 8.0F,
                    CHUNK_BOUNDING_SPHERE_RADIUS);
        }
    }


    /** Adds a long-lived fixed/model mesh as a transform-only frame instance. */
    public void captureStatic(
            LongLivedStaticMesh mesh,
            Matrix4fc modelView,
            Matrix4fc projection,
            ShaderInstance shader,
            ResourceLocation texture
    ) {
        ensureActive();
        Objects.requireNonNull(mesh, "mesh");
        MaterialBinding binding =
                renderTypes.resolveDirect(shader, texture, mesh.sourceFormat());
        captureStaticResolved(mesh, modelView, projection, binding);
    }

    public void captureStatic(
            LongLivedStaticMesh mesh,
            Matrix4fc modelView,
            Matrix4fc projection,
            RenderType renderType
    ) {
        ensureActive();
        Objects.requireNonNull(mesh, "mesh");
        captureStaticResolved(
                mesh, modelView, projection,
                renderTypes.resolve(Objects.requireNonNull(renderType, "renderType")));
    }

    private void captureStaticResolved(
            LongLivedStaticMesh mesh,
            Matrix4fc modelView,
            Matrix4fc projection,
            MaterialBinding binding
    ) {
        int transformIndex = transforms.resolve(modelView, projection);
        MaterialId materialId = materials.resolve(binding.material());
        FrameArenaAllocation drawDataAllocation = arena.allocate(
                FrameArenaRegion.DRAW_DATA, GpuDrawData.BYTES, GpuDrawData.BYTES);
        GpuDrawData.writeTo(
                drawDataAllocation.mappedSlice(),
                transformIndex,
                materialId.value(),
                1,
                0.0F,
                0.0F,
                0.0F);
        int drawDataPage = drawDataPages.resolve(
                drawDataAllocation.buffer(), drawDataAllocation.bufferCapacityBytes());
        int geometryPage = geometryPages.resolve(mesh.vertexBuffer(), mesh.indexBuffer());
        int baseInstance = drawDataAllocation.offsetBytes() / GpuDrawData.BYTES;
        draws.append(
                binding.pass(),
                binding.pipelineId(),
                materialId.value(),
                geometryPage,
                drawDataPage,
                mesh.glMode(),
                mesh.glIndexType(),
                mesh.indexCount(),
                1,
                mesh.firstIndex(),
                mesh.baseVertex(),
                baseInstance,
                0.0F,
                nextSequence++);
    }
    public void captureDirect(
            ShaderInstance shader,
            ResourceLocation texture,
            MeshData meshData
    ) {
        ensureActive();
        Objects.requireNonNull(meshData, "meshData");
        try (meshData) {
            MaterialBinding binding =
                    renderTypes.resolveDirect(shader, texture, meshData.drawState().format());
            int transformIndex = transforms.resolve(
                    RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
            captureResolved(binding, meshData, 0.0F, transformIndex, 0);
        }
    }

    private void captureResolved(
            MaterialBinding binding,
            MeshData meshData,
            float viewDepth,
            int transformIndex,
            int objectFlags
    ) {
            MaterialId materialId = materials.resolve(binding.material());
            MeshData.DrawState state = meshData.drawState();

            int vertexBytes = Math.multiplyExact(
                    state.vertexCount(), CanonicalVertexLayout.STRIDE_BYTES);
            FrameArenaAllocation vertexAllocation = arena.allocate(
                    FrameArenaRegion.VERTEX, vertexBytes, CanonicalVertexLayout.STRIDE_BYTES);
            vertices.encode(
                    state.format(),
                    meshData.vertexBuffer(),
                    state.vertexCount(),
                    vertexAllocation.mappedSlice());

            int indexBytesPerElement = state.indexType().bytes;
            int indexBytes = Math.multiplyExact(state.indexCount(), indexBytesPerElement);
            FrameArenaAllocation indexAllocation;
            ByteBuffer explicitIndices = meshData.indexBuffer();
            if (explicitIndices == null) {
                indexAllocation = arena.allocate(
                        FrameArenaRegion.INDEX, indexBytes, indexBytesPerElement);
                MinecraftSequentialIndexEncoder.encode(
                        state.mode(),
                        state.indexCount(),
                        state.indexType(),
                        indexAllocation.mappedSlice());
            } else {
                indexAllocation = arena.upload(
                        FrameArenaRegion.INDEX,
                        exactSlice(explicitIndices, indexBytes, "index"),
                        indexBytesPerElement);
            }

            FrameArenaAllocation drawDataAllocation = arena.allocate(
                    FrameArenaRegion.DRAW_DATA, GpuDrawData.BYTES, GpuDrawData.BYTES);
            new GpuDrawData(transformIndex, materialId.value(), objectFlags, 0.0F, 0.0F, 0.0F)
                    .writeTo(drawDataAllocation.mappedSlice());
            int drawDataPage = drawDataPages.resolve(
                    drawDataAllocation.buffer(),
                    drawDataAllocation.bufferCapacityBytes());

            int geometryPage = geometryPages.resolve(
                    vertexAllocation.buffer(), indexAllocation.buffer());
            int baseVertex = Math.toIntExact(
                    vertexAllocation.offsetBytes() / CanonicalVertexLayout.STRIDE_BYTES);
            int firstIndex = indexAllocation.offsetBytes() / indexBytesPerElement;
            int baseInstance = drawDataAllocation.offsetBytes() / GpuDrawData.BYTES;
            draws.append(
                    binding.pass(),
                    binding.pipelineId(),
                    materialId.value(),
                    geometryPage,
                    drawDataPage,
                    state.mode().asGLMode,
                    state.indexType().asGLType,
                    state.indexCount(),
                    1,
                    firstIndex,
                    baseVertex,
                    baseInstance,
                    viewDepth,
                    nextSequence++);
    }

    public void finishFrame() {
        ensureActive();
        RenderSystem.assertOnRenderThread();
        int batchCount = 0;
        try {
            draws.sortAndBuildBatches();
            batchCount = draws.batchCount();
            if (transforms.size() > 0) {
                int transformBytes = Math.multiplyExact(
                        transforms.size(), FrameTransformTable.BYTES_PER_TRANSFORM);
                transformTableAllocation = arena.allocate(
                        FrameArenaRegion.INSTANCE, transformBytes, storageBufferOffsetAlignment);
                transforms.writeTo(transformTableAllocation.mappedSlice());
            }
            if (draws.size() > 0) {
                int indirectBytes = Math.multiplyExact(
                        draws.size(), DrawElementsIndirectCommand.BYTES);
                indirectAllocation = arena.allocate(
                        FrameArenaRegion.INDIRECT_COMMAND, indirectBytes, storageBufferOffsetAlignment);
                draws.writeIndirectCommands(indirectAllocation.mappedSlice(), 0, draws.size());
                if (gpuDriven) {
                    compactIndirectAllocation = arena.allocate(
                            FrameArenaRegion.INDIRECT_COMMAND, indirectBytes, storageBufferOffsetAlignment);
                    int countBytes = Math.multiplyExact(batchCount, Integer.BYTES);
                    indirectCountAllocation = arena.allocate(
                            FrameArenaRegion.INDIRECT_COMMAND, countBytes, storageBufferOffsetAlignment);
                    ByteBuffer counts = indirectCountAllocation.mappedSlice();
                    while (counts.hasRemaining()) counts.putInt(0);
                    int metadataBytes = Math.multiplyExact(draws.size(), GpuCullingMetadata.BYTES);
                    cullingMetadataAllocation = arena.allocate(
                            FrameArenaRegion.INSTANCE, metadataBytes, storageBufferOffsetAlignment);
                    draws.writeGpuCullingMetadata(cullingMetadataAllocation.mappedSlice());
                }
            }
            executor.execute(draws, geometryPages, drawDataPages, transformTableAllocation,
                    indirectAllocation, compactIndirectAllocation, indirectCountAllocation,
                    cullingMetadataAllocation, materials, renderTypes);
        } finally {
            arena.endFrame();
            frameActive = false;
            performance.record(
                    System.nanoTime() - frameStartedNanos,
                    draws.size(), batchCount, arena.lastStats());
            logPerformanceIfNeeded();
        }
    }

    public void abortFrame() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (!frameActive) return;
        arena.endFrame();
        frameActive = false;
    }

    public void onResourceReload() {
        RenderSystem.assertOnRenderThread();
        renderTypes.clearDynamic();
        materials.clear();
        vertices.clear();
        executor.reloadResources();
    }


    public void resize(int width, int height) {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        executor.resize(width, height);
    }
    public TakeoverDrawBuffer draws() {
        return draws;
    }

    public GeometryPageTable geometryPages() {
        return geometryPages;
    }

    public PersistentFrameArena arena() {
        return arena;
    }

    public FrameArenaAllocation indirectAllocation() {
        return indirectAllocation;
    }

    public int unknownRenderTypeCount() {
        return renderTypes.unknownCount();
    }

    public FramePerformanceSnapshot performanceSnapshot() {
        return performance.snapshot();
    }

    public FrameProfile frameProfile() {
        return executor.frameProfile();
    }

    public RenderGraph.Description graphDescription() {
        return executor.graphDescription();
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        executor.close();
        arena.close();
        frameActive = false;
        closed = true;
    }

    private void logPerformanceIfNeeded() {
        if (performanceLogInterval == 0
                || performance.sampleCount() % performanceLogInterval != 0L) return;
        FramePerformanceSnapshot snapshot = performance.snapshot();
        HaikalatHost.LOGGER.info(
                "Host frame CPU avg={}ms p95={}ms p99={}ms, draws={}, MDI batches={}, "
                        + "arena={}B + overflow={}B/{} pages, busy-slot-spill={}",
                String.format("%.3f", snapshot.averageCpuMillis()),
                String.format("%.3f", snapshot.p95CpuMillis()),
                String.format("%.3f", snapshot.p99CpuMillis()),
                snapshot.lastDrawCount(),
                snapshot.lastBatchCount(),
                snapshot.lastArenaBaseBytes(),
                snapshot.lastArenaOverflowBytes(),
                snapshot.lastArenaOverflowPages(),
                snapshot.lastBusySlotSpill());
        FrameProfile graphProfile = executor.frameProfile();
        long available = graphProfile.passes().stream()
                .filter(pass -> pass.gpuStatus() == PassProfile.GpuTimingStatus.AVAILABLE)
                .count();
        PassProfile slowest = graphProfile.passes().stream()
                .filter(pass -> pass.gpuStatus() == PassProfile.GpuTimingStatus.AVAILABLE)
                .max(java.util.Comparator.comparingLong(PassProfile::gpuNanos))
                .orElse(null);
        HaikalatHost.LOGGER.info(
                "Host H5 graph GPU sampled={}ms ({}/{} pass samples), slowest={}, commands={}",
                String.format("%.3f", graphProfile.totalGpuMillis()),
                available,
                graphProfile.passes().size(),
                slowest == null
                        ? "pending"
                        : slowest.passName() + "/"
                                + String.format("%.3fms", slowest.gpuMillis()),
                executor.recordedGraphCommandCount());
        if (gpuDriven) {
            HaikalatHost.LOGGER.info("Host H6 MDI batches by pass={}", batchSummary());
        }
    }

    private String batchSummary() {
        StringBuilder summary = new StringBuilder("{");
        boolean first = true;
        for (PassKey pass : PassKey.values()) {
            int count = draws.batchCount(pass);
            if (count == 0) continue;
            if (!first) summary.append(", ");
            summary.append(pass).append('=').append(count);
            first = false;
        }
        return summary.append('}').toString();
    }

    private void ensureActive() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (!frameActive) throw new IllegalStateException("submission frame is not active");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("takeover frame submission is closed");
    }

    private static ByteBuffer exactSlice(ByteBuffer source, int bytes, String label) {
        ByteBuffer result = source.duplicate().order(ByteOrder.nativeOrder());
        if (bytes < 0 || result.remaining() < bytes) {
            throw new IllegalArgumentException(
                    label + " payload requires " + bytes + " bytes, found " + result.remaining());
        }
        result.limit(result.position() + bytes);
        return result.slice().order(ByteOrder.nativeOrder());
    }
}
