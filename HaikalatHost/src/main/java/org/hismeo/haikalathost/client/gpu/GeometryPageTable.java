package org.hismeo.haikalathost.client.gpu;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;

import java.util.Arrays;
import java.util.Objects;

/** Frame-local primitive table for vertex/index buffer binding pairs. */
public final class GeometryPageTable {
    private GlBuffer[] vertexBuffers;
    private GlBuffer[] indexBuffers;
    private int size;

    public GeometryPageTable(int initialCapacity) {
        int capacity = Math.max(4, initialCapacity);
        vertexBuffers = new GlBuffer[capacity];
        indexBuffers = new GlBuffer[capacity];
    }

    public int resolve(GlBuffer vertexBuffer, GlBuffer indexBuffer) {
        Objects.requireNonNull(vertexBuffer, "vertexBuffer");
        Objects.requireNonNull(indexBuffer, "indexBuffer");
        for (int page = 0; page < size; page++) {
            if (vertexBuffers[page] == vertexBuffer && indexBuffers[page] == indexBuffer) return page;
        }
        ensureCapacity(size + 1);
        int page = size++;
        vertexBuffers[page] = vertexBuffer;
        indexBuffers[page] = indexBuffer;
        return page;
    }

    public GlBuffer vertexBuffer(int page) {
        checkPage(page);
        return vertexBuffers[page];
    }

    public GlBuffer indexBuffer(int page) {
        checkPage(page);
        return indexBuffers[page];
    }

    public int size() {
        return size;
    }

    public void clear() {
        Arrays.fill(vertexBuffers, 0, size, null);
        Arrays.fill(indexBuffers, 0, size, null);
        size = 0;
    }

    private void ensureCapacity(int requested) {
        if (requested <= vertexBuffers.length) return;
        int grown = Math.max(requested, Math.multiplyExact(vertexBuffers.length, 2));
        vertexBuffers = Arrays.copyOf(vertexBuffers, grown);
        indexBuffers = Arrays.copyOf(indexBuffers, grown);
    }

    private void checkPage(int page) {
        if (page < 0 || page >= size) throw new IndexOutOfBoundsException(page);
    }
}
