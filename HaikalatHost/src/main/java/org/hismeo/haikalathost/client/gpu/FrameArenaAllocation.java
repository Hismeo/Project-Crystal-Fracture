package org.hismeo.haikalathost.client.gpu;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;

import java.nio.ByteBuffer;
import java.util.Objects;

public record FrameArenaAllocation(
        FrameArenaRegion region,
        GlBuffer buffer,
        int bufferCapacityBytes,
        int offsetBytes,
        int lengthBytes,
        int frameSlot,
        boolean overflow,
        ByteBuffer mappedSlice
) {
    public FrameArenaAllocation {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(mappedSlice, "mappedSlice");
        if (offsetBytes < 0 || lengthBytes < 0) {
            throw new IllegalArgumentException("arena offsets and lengths must not be negative");
        }
        if (bufferCapacityBytes < 0
                || Math.addExact(offsetBytes, lengthBytes) > bufferCapacityBytes) {
            throw new IllegalArgumentException("allocation exceeds its backing buffer");
        }
        if (mappedSlice.remaining() != lengthBytes) {
            throw new IllegalArgumentException("mapped slice length does not match allocation length");
        }
    }
}
