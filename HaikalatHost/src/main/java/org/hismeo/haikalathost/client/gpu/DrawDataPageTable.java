package org.hismeo.haikalathost.client.gpu;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;

import java.util.Arrays;
import java.util.Objects;

/** Frame-local table for draw-data SSBO pages, including arena overflow pages. */
public final class DrawDataPageTable {
    private GlBuffer[] buffers;
    private int[] capacityBytes;
    private int size;

    public DrawDataPageTable(int initialCapacity) {
        int capacity = Math.max(4, initialCapacity);
        buffers = new GlBuffer[capacity];
        capacityBytes = new int[capacity];
    }

    public int resolve(GlBuffer buffer, int bufferCapacityBytes) {
        Objects.requireNonNull(buffer, "buffer");
        if (bufferCapacityBytes <= 0) {
            throw new IllegalArgumentException("bufferCapacityBytes must be positive");
        }
        for (int page = 0; page < size; page++) {
            if (buffers[page] == buffer) {
                if (capacityBytes[page] != bufferCapacityBytes) {
                    throw new IllegalArgumentException("buffer page capacity changed within a frame");
                }
                return page;
            }
        }
        ensureCapacity(size + 1);
        int page = size++;
        buffers[page] = buffer;
        capacityBytes[page] = bufferCapacityBytes;
        return page;
    }

    public GlBuffer buffer(int page) {
        checkPage(page);
        return buffers[page];
    }

    public int capacityBytes(int page) {
        checkPage(page);
        return capacityBytes[page];
    }

    public void clear() {
        Arrays.fill(buffers, 0, size, null);
        Arrays.fill(capacityBytes, 0, size, 0);
        size = 0;
    }

    private void ensureCapacity(int requested) {
        if (requested <= buffers.length) return;
        int grown = Math.max(requested, Math.multiplyExact(buffers.length, 2));
        buffers = Arrays.copyOf(buffers, grown);
        capacityBytes = Arrays.copyOf(capacityBytes, grown);
    }

    private void checkPage(int page) {
        if (page < 0 || page >= size) throw new IndexOutOfBoundsException(page);
    }
}
