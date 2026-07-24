package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.backend.sync.GpuFence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Returns replaced arena slices only after all earlier GPU commands have completed. */
final class ChunkArenaRetirementQueue implements AutoCloseable {
    private final ChunkMeshArena arena;
    private final List<ChunkMeshArena.Allocation> staged = new ArrayList<>();
    private final Deque<Batch> pending = new ArrayDeque<>();

    ChunkArenaRetirementQueue(ChunkMeshArena arena) {
        this.arena = arena;
    }

    void retire(LongLivedChunkMesh mesh) {
        if (mesh != null) staged.add(mesh.allocation());
    }

    void endFrame() {
        if (!staged.isEmpty()) {
            pending.addLast(new Batch(GpuFence.insert(), List.copyOf(staged)));
            staged.clear();
        }
        while (!pending.isEmpty() && pending.peekFirst().fence.isSignaled()) {
            Batch batch = pending.removeFirst();
            batch.fence.close();
            batch.allocations.forEach(arena::release);
        }
    }

    @Override
    public void close() {
        staged.clear();
        while (!pending.isEmpty()) pending.removeFirst().fence.close();
    }

    private record Batch(GpuFence fence, List<ChunkMeshArena.Allocation> allocations) {
    }
}
