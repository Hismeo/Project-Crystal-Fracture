package org.hismeo.haikalathost.client.chunk;

import com.kaleblangley.haikalat.backend.sync.GpuFence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

final class DeferredChunkFreeQueue implements AutoCloseable {
    private final Deque<PendingFree> pending = new ArrayDeque<>();

    void defer(ChunkMeshHandle handle) {
        if (handle == null) return;
        pending.addLast(new PendingFree(handle, GpuFence.insert()));
    }

    void drainSignaled() {
        Iterator<PendingFree> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingFree free = iterator.next();
            if (!free.fence.isSignaled()) continue;
            free.fence.close();
            free.handle.close();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        PendingFree free;
        while ((free = pending.pollFirst()) != null) {
            free.fence.close();
            free.handle.close();
        }
    }

    private record PendingFree(ChunkMeshHandle handle, GpuFence fence) {
    }
}
