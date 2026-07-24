package org.hismeo.haikalathost.client.staticmesh;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.hismeo.haikalathost.client.geometry.CanonicalVertexLayout;

/** Immutable shared-arena slice used by fixed world and cached model instances. */
public final class LongLivedStaticMesh {
    private final VertexFormat sourceFormat;
    private final StaticMeshArena.Allocation allocation;
    private final int glMode;
    private final int indexCount;
    private final int glIndexType;
    private final int indexElementBytes;

    LongLivedStaticMesh(StaticMeshPayload payload, StaticMeshArena.Allocation allocation) {
        this.sourceFormat = payload.format();
        this.allocation = allocation;
        this.glMode = payload.glMode();
        this.indexCount = payload.indexCount();
        this.glIndexType = payload.glIndexType();
        this.indexElementBytes = payload.indexElementBytes();
    }

    public VertexFormat sourceFormat() {
        return sourceFormat;
    }

    public GlBuffer vertexBuffer() {
        return allocation.page().vertexBuffer();
    }

    public GlBuffer indexBuffer() {
        return allocation.page().indexBuffer();
    }

    public int baseVertex() {
        return allocation.vertex().offsetBytes() / CanonicalVertexLayout.STRIDE_BYTES;
    }

    public int firstIndex() {
        return allocation.index().offsetBytes() / indexElementBytes;
    }

    public int glMode() {
        return glMode;
    }

    public int indexCount() {
        return indexCount;
    }

    public int glIndexType() {
        return glIndexType;
    }

    StaticMeshArena.Allocation allocation() {
        return allocation;
    }
}
