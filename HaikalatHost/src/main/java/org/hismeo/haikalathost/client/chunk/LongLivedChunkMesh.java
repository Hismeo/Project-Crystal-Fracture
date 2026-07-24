package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import net.minecraft.client.renderer.RenderType;

import java.util.Objects;

/** Immutable GPU slice published after a chunk generation has finished uploading. */
public final class LongLivedChunkMesh {
    private final Object section;
    private final RenderType renderType;
    private final long generation;
    private final ChunkMeshArena.Allocation allocation;
    private final int glMode;
    private final int indexCount;
    private final int glIndexType;
    private final int indexElementBytes;
    private final int originX;
    private final int originY;
    private final int originZ;

    LongLivedChunkMesh(Object section, RenderType renderType, long generation,
                       ChunkMeshArena.Allocation allocation, int glMode, int indexCount,
                       int glIndexType, int indexElementBytes,
                       int originX, int originY, int originZ) {
        this.section = Objects.requireNonNull(section, "section");
        this.renderType = Objects.requireNonNull(renderType, "renderType");
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
        if (indexCount <= 0) throw new IllegalArgumentException("indexCount must be positive");
        if (indexElementBytes != Short.BYTES && indexElementBytes != Integer.BYTES) {
            throw new IllegalArgumentException("unsupported index element size " + indexElementBytes);
        }
        this.generation = generation;
        this.glMode = glMode;
        this.indexCount = indexCount;
        this.glIndexType = glIndexType;
        this.indexElementBytes = indexElementBytes;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public Object section() { return section; }
    public RenderType renderType() { return renderType; }
    public long generation() { return generation; }
    public GlBuffer vertexBuffer() { return allocation.page().vertexBuffer(); }
    public GlBuffer indexBuffer() { return allocation.page().indexBuffer(); }
    public int baseVertex() {
        return allocation.vertex().offsetBytes()
                / org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout.STRIDE_BYTES;
    }
    public int firstIndex() { return allocation.index().offsetBytes() / indexElementBytes; }
    public int glMode() { return glMode; }
    public int indexCount() { return indexCount; }
    public int glIndexType() { return glIndexType; }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public float squaredDistanceTo(double cameraX, double cameraY, double cameraZ) {
        double x = originX + 8.0 - cameraX;
        double y = originY + 8.0 - cameraY;
        double z = originZ + 8.0 - cameraZ;
        double squared = x * x + y * y + z * z;
        return squared >= Float.MAX_VALUE ? Float.MAX_VALUE : (float) squared;
    }
    ChunkMeshArena.Allocation allocation() { return allocation; }
}
