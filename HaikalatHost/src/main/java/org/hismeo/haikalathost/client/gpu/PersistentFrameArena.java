package org.hismeo.haikalathost.client.gpu;

import com.kaleblangley.haikalat.backend.buffer.GlBuffer;
import com.kaleblangley.haikalat.backend.sync.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import org.hismeo.haikalathost.client.geometry.GpuRingAllocator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;

/**
 * Five-region, persistently mapped frame arena owned exclusively by the Haikalat render thread.
 */
public final class PersistentFrameArena implements AutoCloseable {
    private static final int STORAGE_FLAGS =
            GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_DYNAMIC_STORAGE_BIT;
    private static final int MAP_FLAGS =
            GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;

    private final PersistentFrameArenaConfiguration configuration;
    private final Map<FrameArenaRegion, RegionStorage> regions =
            new EnumMap<>(FrameArenaRegion.class);
    private final GpuFence[] slotFences;
    private final List<OverflowPage> frameOverflow = new ArrayList<>();
    private final ArrayDeque<RetiredOverflow> retiredOverflow = new ArrayDeque<>();

    private long nextFrameNumber;
    private long currentBaseBytes;
    private long currentOverflowBytes;
    private int currentOverflowPages;
    private boolean currentBusySlotSpill;
    private boolean baseSlotAvailable;
    private boolean baseSlotUsed;
    private boolean frameActive;
    private boolean closed;
    private FrameArenaStats lastStats = FrameArenaStats.empty();

    public PersistentFrameArena(PersistentFrameArenaConfiguration configuration) {
        RenderSystem.assertOnRenderThread();
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.slotFences = new GpuFence[configuration.frameSlots()];
        try {
            for (FrameArenaRegion region : FrameArenaRegion.values()) {
                regions.put(region, new RegionStorage(
                        region, configuration.bytesPerSlot(region), configuration.frameSlots()));
            }
        } catch (Throwable failure) {
            regions.values().forEach(RegionStorage::close);
            regions.clear();
            throw failure;
        }
    }

    public void beginFrame() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (frameActive) throw new IllegalStateException("frame arena already has an active frame");
        collectRetiredOverflow();

        int slot = currentSlot();
        GpuFence fence = slotFences[slot];
        if (fence == null) {
            baseSlotAvailable = true;
        } else if (fence.isSignaled()) {
            fence.close();
            slotFences[slot] = null;
            baseSlotAvailable = true;
        } else {
            baseSlotAvailable = false;
        }

        currentBaseBytes = 0L;
        currentOverflowBytes = 0L;
        currentOverflowPages = 0;
        currentBusySlotSpill = !baseSlotAvailable;
        baseSlotUsed = false;
        frameOverflow.clear();
        frameActive = true;
    }

    public FrameArenaAllocation allocate(FrameArenaRegion region, int lengthBytes) {
        return allocate(region, lengthBytes, region.defaultAlignment());
    }

    public FrameArenaAllocation allocate(
            FrameArenaRegion region, int lengthBytes, int alignmentBytes) {
        ensureActive();
        Objects.requireNonNull(region, "region");
        if (lengthBytes < 0) throw new IllegalArgumentException("lengthBytes must not be negative");
        if (alignmentBytes <= 0) throw new IllegalArgumentException("alignmentBytes must be positive");

        RegionStorage storage = regions.get(region);
        if (baseSlotAvailable && storage.allocator.canAllocate(lengthBytes, alignmentBytes)) {
            GpuRingAllocator.Allocation allocation =
                    storage.allocator.allocate(lengthBytes, alignmentBytes);
            baseSlotUsed = true;
            currentBaseBytes = Math.addExact(currentBaseBytes, lengthBytes);
            return new FrameArenaAllocation(
                    region,
                    storage.buffer,
                    storage.allocator.totalBytes(),
                    allocation.offsetBytes(),
                    lengthBytes,
                    allocation.slot(),
                    false,
                    slice(storage.mapping, allocation.offsetBytes(), lengthBytes));
        }
        return allocateOverflow(region, lengthBytes, alignmentBytes);
    }

    public FrameArenaAllocation upload(
            FrameArenaRegion region, ByteBuffer source, int alignmentBytes) {
        Objects.requireNonNull(source, "source");
        ByteBuffer input = source.duplicate();
        FrameArenaAllocation allocation = allocate(region, input.remaining(), alignmentBytes);
        allocation.mappedSlice().put(input).flip();
        return allocation;
    }

    public void endFrame() {
        ensureActive();
        RenderSystem.assertOnRenderThread();
        int slot = currentSlot();
        if (baseSlotUsed) {
            if (slotFences[slot] != null) {
                throw new IllegalStateException("attempted to reuse a frame slot before its fence completed");
            }
            slotFences[slot] = GpuFence.insert();
        }
        if (!frameOverflow.isEmpty()) {
            retiredOverflow.addLast(new RetiredOverflow(
                    GpuFence.insert(), List.copyOf(frameOverflow)));
        }

        lastStats = new FrameArenaStats(
                nextFrameNumber++, currentBaseBytes, currentOverflowBytes,
                currentOverflowPages, currentBusySlotSpill);
        for (RegionStorage storage : regions.values()) storage.allocator.advanceFrame();
        frameOverflow.clear();
        frameActive = false;
        baseSlotAvailable = false;
        baseSlotUsed = false;
    }

    public long capacityBytes(FrameArenaRegion region) {
        ensureOpen();
        return Math.multiplyExact((long) configuration.bytesPerSlot(region),
                configuration.frameSlots());
    }

    public FrameArenaStats lastStats() {
        return lastStats;
    }

    public int currentSlot() {
        int slot = -1;
        for (RegionStorage storage : regions.values()) {
            int candidate = storage.allocator.currentSlot();
            if (slot == -1) slot = candidate;
            else if (slot != candidate) {
                throw new IllegalStateException("frame arena region slots lost synchronization");
            }
        }
        return slot;
    }

    public GlBuffer buffer(FrameArenaRegion region) {
        ensureOpen();
        return regions.get(Objects.requireNonNull(region, "region")).buffer;
    }

    private FrameArenaAllocation allocateOverflow(
            FrameArenaRegion region, int lengthBytes, int alignmentBytes) {
        OverflowPage page = findOverflowPage(region, lengthBytes, alignmentBytes);
        int offset = page.allocate(lengthBytes, alignmentBytes);
        currentOverflowBytes = Math.addExact(currentOverflowBytes, lengthBytes);
        return new FrameArenaAllocation(
                region,
                page.buffer,
                page.capacity,
                offset,
                lengthBytes,
                -1,
                true,
                slice(page.mapping, offset, lengthBytes));
    }

    private OverflowPage findOverflowPage(
            FrameArenaRegion region, int lengthBytes, int alignmentBytes) {
        for (int index = frameOverflow.size() - 1; index >= 0; index--) {
            OverflowPage candidate = frameOverflow.get(index);
            if (candidate.region == region && candidate.canAllocate(lengthBytes, alignmentBytes)) {
                return candidate;
            }
        }
        int required = Math.addExact(lengthBytes, alignmentBytes - 1);
        int capacity = growCapacity(configuration.overflowPageBytes(), required);
        OverflowPage created = new OverflowPage(region, capacity);
        frameOverflow.add(created);
        currentOverflowPages++;
        return created;
    }

    private void collectRetiredOverflow() {
        while (!retiredOverflow.isEmpty()) {
            RetiredOverflow retired = retiredOverflow.peekFirst();
            if (!retired.fence.isSignaled()) return;
            retiredOverflow.removeFirst();
            retired.close();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        RenderSystem.assertOnRenderThread();
        frameOverflow.forEach(OverflowPage::close);
        frameOverflow.clear();
        retiredOverflow.forEach(RetiredOverflow::close);
        retiredOverflow.clear();
        for (int slot = 0; slot < slotFences.length; slot++) {
            GpuFence fence = slotFences[slot];
            if (fence != null) {
                fence.close();
                slotFences[slot] = null;
            }
        }
        regions.values().forEach(RegionStorage::close);
        regions.clear();
        frameActive = false;
        closed = true;
    }

    private void ensureActive() {
        ensureOpen();
        RenderSystem.assertOnRenderThread();
        if (!frameActive) throw new IllegalStateException("frame arena has no active frame");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("persistent frame arena is closed");
    }

    private static GlBuffer createBuffer(FrameArenaRegion region) {
        return switch (region) {
            case VERTEX -> GlBuffer.arrayBuffer(GL_DYNAMIC_DRAW);
            case INDEX -> GlBuffer.elementArrayBuffer(GL_DYNAMIC_DRAW);
            case INSTANCE, DRAW_DATA -> GlBuffer.shaderStorageBuffer(GL_DYNAMIC_DRAW);
            case INDIRECT_COMMAND -> new GlBuffer(GL_DRAW_INDIRECT_BUFFER, GL_DYNAMIC_DRAW);
        };
    }

    private static ByteBuffer slice(ByteBuffer mapping, int offset, int length) {
        ByteBuffer result = mapping.duplicate().order(ByteOrder.nativeOrder());
        result.position(offset);
        result.limit(Math.addExact(offset, length));
        return result.slice().order(ByteOrder.nativeOrder());
    }

    private static int growCapacity(int minimum, int required) {
        int capacity = minimum;
        while (capacity < required) capacity = Math.multiplyExact(capacity, 2);
        return capacity;
    }

    private static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
    }

    private static final class RegionStorage implements AutoCloseable {
        private final GlBuffer buffer;
        private final ByteBuffer mapping;
        private final GpuRingAllocator allocator;

        private RegionStorage(FrameArenaRegion region, int slotBytes, int frameSlots) {
            allocator = new GpuRingAllocator(slotBytes, frameSlots);
            GlBuffer created = createBuffer(region);
            try {
                created.allocateStorage(allocator.totalBytes(), STORAGE_FLAGS);
                ByteBuffer mapped = created.mapRange(0L, allocator.totalBytes(), MAP_FLAGS);
                if (mapped == null) throw new IllegalStateException("OpenGL returned a null persistent mapping");
                buffer = created;
                mapping = mapped.order(ByteOrder.nativeOrder());
            } catch (Throwable failure) {
                created.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            buffer.unmap();
            buffer.close();
        }
    }

    private static final class OverflowPage implements AutoCloseable {
        private final FrameArenaRegion region;
        private final int capacity;
        private final GlBuffer buffer;
        private final ByteBuffer mapping;
        private int cursor;

        private OverflowPage(FrameArenaRegion region, int capacity) {
            this.region = region;
            this.capacity = capacity;
            GlBuffer created = createBuffer(region);
            try {
                created.allocateStorage(capacity, STORAGE_FLAGS);
                ByteBuffer mapped = created.mapRange(0L, capacity, MAP_FLAGS);
                if (mapped == null) throw new IllegalStateException("OpenGL returned a null overflow mapping");
                buffer = created;
                mapping = mapped.order(ByteOrder.nativeOrder());
            } catch (Throwable failure) {
                created.close();
                throw failure;
            }
        }

        private boolean canAllocate(int length, int alignment) {
            return (long) align(cursor, alignment) + length <= capacity;
        }

        private int allocate(int length, int alignment) {
            int offset = align(cursor, alignment);
            if ((long) offset + length > capacity) {
                throw new IllegalStateException("overflow page capacity calculation failed");
            }
            cursor = Math.addExact(offset, length);
            return offset;
        }

        @Override
        public void close() {
            buffer.unmap();
            buffer.close();
        }
    }

    private record RetiredOverflow(GpuFence fence, List<OverflowPage> pages)
            implements AutoCloseable {
        @Override
        public void close() {
            pages.forEach(OverflowPage::close);
            fence.close();
        }
    }
}
