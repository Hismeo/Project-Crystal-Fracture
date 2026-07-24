package org.hismeo.haikalathost.client.submission;

import org.hismeo.haikalathost.client.gpu.GpuCullingMetadata;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Allocation-free-after-warmup frame submission storage.
 *
 * <p>Draws are stored as primitive structure-of-arrays data. A stable LSD radix sort preserves
 * original ordering where required while allowing opaque/cutout material batching.</p>
 */
public final class TakeoverDrawBuffer {
    private static final PassKey[] PASSES = PassKey.values();
    private static final int MIN_CAPACITY = 16;
    private static final int KEY_DEPTH = 0;
    private static final int KEY_GEOMETRY_PAGE = 1;
    private static final int KEY_DRAW_DATA_PAGE = 2;
    private static final int KEY_MATERIAL = 3;
    private static final int KEY_PIPELINE = 4;
    private static final int KEY_PASS = 5;

    private int[] passOrdinals;
    private int[] pipelineIds;
    private int[] materialIds;
    private int[] geometryPages;
    private int[] drawDataPages;
    private int[] glModes;
    private int[] glIndexTypes;
    private int[] indexCounts;
    private int[] instanceCounts;
    private int[] firstIndices;
    private int[] baseVertices;
    private int[] baseInstances;
    private float[] viewDepths;
    private long[] originalSequences;
    private int[] cullingTransformIndices;
    private float[] cullingCenterX;
    private float[] cullingCenterY;
    private float[] cullingCenterZ;
    private float[] cullingRadii;
    private boolean[] cullingEnabled;

    private int[] order;
    private int[] scratch;
    private final int[] radixCounts = new int[256];

    private int[] batchStarts;
    private int[] batchLengths;
    private int size;
    private int batchCount;
    private boolean sorted = true;
    private boolean sequenceMonotonic = true;
    private boolean orderIndependentTransparency;

    public TakeoverDrawBuffer(int initialCapacity) {
        int capacity = Math.max(MIN_CAPACITY, initialCapacity);
        passOrdinals = new int[capacity];
        pipelineIds = new int[capacity];
        materialIds = new int[capacity];
        geometryPages = new int[capacity];
        drawDataPages = new int[capacity];
        glModes = new int[capacity];
        glIndexTypes = new int[capacity];
        indexCounts = new int[capacity];
        instanceCounts = new int[capacity];
        firstIndices = new int[capacity];
        baseVertices = new int[capacity];
        baseInstances = new int[capacity];
        viewDepths = new float[capacity];
        originalSequences = new long[capacity];
        cullingTransformIndices = new int[capacity];
        cullingCenterX = new float[capacity];
        cullingCenterY = new float[capacity];
        cullingCenterZ = new float[capacity];
        cullingRadii = new float[capacity];
        cullingEnabled = new boolean[capacity];
        order = new int[capacity];
        scratch = new int[capacity];
        batchStarts = new int[capacity];
        batchLengths = new int[capacity];
    }

    public int append(
            PassKey pass,
            int pipelineId,
            int materialId,
            int geometryPage,
            int drawDataPage,
            int glMode,
            int glIndexType,
            int indexCount,
            int instanceCount,
            int firstIndex,
            int baseVertex,
            int baseInstance,
            float viewDepth,
            long originalSequence
    ) {
        Objects.requireNonNull(pass, "pass");
        requireNonNegative(pipelineId, "pipelineId");
        requireNonNegative(materialId, "materialId");
        requireNonNegative(geometryPage, "geometryPage");
        requireNonNegative(drawDataPage, "drawDataPage");
        requireNonNegative(indexCount, "indexCount");
        requireNonNegative(instanceCount, "instanceCount");
        requireNonNegative(firstIndex, "firstIndex");
        requireNonNegative(baseInstance, "baseInstance");
        if (!Float.isFinite(viewDepth)) {
            throw new IllegalArgumentException("viewDepth must be finite");
        }
        if (originalSequence < 0L) {
            throw new IllegalArgumentException("originalSequence must not be negative");
        }

        if (size > 0 && originalSequence < originalSequences[size - 1]) {
            sequenceMonotonic = false;
        }
        ensureCapacity(size + 1);
        int draw = size++;
        passOrdinals[draw] = pass.ordinal();
        pipelineIds[draw] = pipelineId;
        materialIds[draw] = materialId;
        geometryPages[draw] = geometryPage;
        drawDataPages[draw] = drawDataPage;
        glModes[draw] = glMode;
        glIndexTypes[draw] = glIndexType;
        indexCounts[draw] = indexCount;
        instanceCounts[draw] = instanceCount;
        firstIndices[draw] = firstIndex;
        baseVertices[draw] = baseVertex;
        baseInstances[draw] = baseInstance;
        viewDepths[draw] = viewDepth;
        originalSequences[draw] = originalSequence;
        cullingEnabled[draw] = false;
        sorted = false;
        return draw;
    }

    /** Marks one captured draw as eligible for conservative GPU frustum culling. */
    public void setCullingSphere(
            int draw,
            int transformIndex,
            float centerX,
            float centerY,
            float centerZ,
            float radius
    ) {
        if (draw < 0 || draw >= size) throw new IndexOutOfBoundsException(draw);
        requireNonNegative(transformIndex, "transformIndex");
        if (!Float.isFinite(centerX)
                || !Float.isFinite(centerY)
                || !Float.isFinite(centerZ)
                || !Float.isFinite(radius)
                || radius < 0.0F) {
            throw new IllegalArgumentException("culling sphere must be finite and non-negative");
        }
        cullingTransformIndices[draw] = transformIndex;
        cullingCenterX[draw] = centerX;
        cullingCenterY[draw] = centerY;
        cullingCenterZ[draw] = centerZ;
        cullingRadii[draw] = radius;
        cullingEnabled[draw] = true;
    }

    public void sortAndBuildBatches() {
        sortAndBuildBatches(false);
    }

    public void sortAndBuildBatches(boolean orderIndependentTransparency) {
        if (sorted) return;
        this.orderIndependentTransparency = orderIndependentTransparency;
        for (int draw = 0; draw < size; draw++) order[draw] = draw;

        if (!sequenceMonotonic) radixSortSequence();
        radixSortInt(KEY_DEPTH);
        radixSortInt(KEY_DRAW_DATA_PAGE);
        radixSortInt(KEY_GEOMETRY_PAGE);
        radixSortInt(KEY_MATERIAL);
        radixSortInt(KEY_PIPELINE);
        radixSortInt(KEY_PASS);
        buildBatches();
        sorted = true;
    }

    public void clear() {
        size = 0;
        batchCount = 0;
        sequenceMonotonic = true;
        sorted = true;
        orderIndependentTransparency = false;
    }

    public int size() {
        return size;
    }

    public int batchCount() {
        ensureSorted();
        return batchCount;
    }
    public int batchCount(PassKey pass) {
        ensureSorted();
        Objects.requireNonNull(pass, "pass");
        int count = 0;
        for (int batch = 0; batch < batchCount; batch++) {
            if (PASSES[passOrdinals[firstDrawInBatch(batch)]] == pass) count++;
        }
        return count;
    }


    public int batchStart(int batch) {
        checkBatch(batch);
        return batchStarts[batch];
    }

    public int batchLength(int batch) {
        checkBatch(batch);
        return batchLengths[batch];
    }

    public int orderedDraw(int position) {
        ensureSorted();
        if (position < 0 || position >= size) throw new IndexOutOfBoundsException(position);
        return order[position];
    }

    public PassKey orderedPass(int position) {
        return PASSES[passOrdinals[orderedDraw(position)]];
    }

    public int orderedPipelineId(int position) {
        return pipelineIds[orderedDraw(position)];
    }

    public int orderedMaterialId(int position) {
        return materialIds[orderedDraw(position)];
    }

    public int orderedGeometryPage(int position) {
        return geometryPages[orderedDraw(position)];
    }

    public int orderedDrawDataPage(int position) {
        return drawDataPages[orderedDraw(position)];
    }

    public int orderedIndexCount(int position) {
        return indexCounts[orderedDraw(position)];
    }

    public int orderedInstanceCount(int position) {
        return instanceCounts[orderedDraw(position)];
    }

    public int orderedFirstIndex(int position) {
        return firstIndices[orderedDraw(position)];
    }

    public int orderedBaseVertex(int position) {
        return baseVertices[orderedDraw(position)];
    }

    public int orderedBaseInstance(int position) {
        return baseInstances[orderedDraw(position)];
    }

    public float orderedViewDepth(int position) {
        return viewDepths[orderedDraw(position)];
    }

    public long orderedOriginalSequence(int position) {
        return originalSequences[orderedDraw(position)];
    }

    public void writeIndirectCommands(ByteBuffer target, int orderedStart, int count) {
        ensureSorted();
        Objects.requireNonNull(target, "target");
        if (orderedStart < 0 || count < 0 || orderedStart + count > size) {
            throw new IndexOutOfBoundsException(
                    "indirect range start=" + orderedStart + ", count=" + count + ", size=" + size);
        }
        int required = Math.multiplyExact(count, DrawElementsIndirectCommand.BYTES);
        if (target.remaining() < required) {
            throw new IllegalArgumentException(
                    "indirect target requires " + required + " bytes, found " + target.remaining());
        }
        for (int position = orderedStart; position < orderedStart + count; position++) {
            int draw = order[position];
            target.putInt(indexCounts[draw]);
            target.putInt(instanceCounts[draw]);
            target.putInt(firstIndices[draw]);
            target.putInt(baseVertices[draw]);
            target.putInt(baseInstances[draw]);
        }
    }

    /** Writes one std430 culling record for every draw in sorted indirect-command order. */
    public void writeGpuCullingMetadata(ByteBuffer target) {
        ensureSorted();
        Objects.requireNonNull(target, "target");
        int required = Math.multiplyExact(size, GpuCullingMetadata.BYTES);
        if (target.remaining() < required) {
            throw new IllegalArgumentException(
                    "culling metadata target requires " + required
                            + " bytes, found " + target.remaining());
        }
        for (int batch = 0; batch < batchCount; batch++) {
            int start = batchStarts[batch];
            int end = start + batchLengths[batch];
            boolean compact = orderingPolicy(PASSES[passOrdinals[order[start]]])
                    == OrderingPolicy.BATCH;
            for (int position = start; position < end; position++) {
                int draw = order[position];
                int flags = compact ? GpuCullingMetadata.FLAG_COMPACT : 0;
                if (cullingEnabled[draw]) flags |= GpuCullingMetadata.FLAG_FRUSTUM_SPHERE;
                GpuCullingMetadata.writeTo(
                        target,
                        cullingCenterX[draw],
                        cullingCenterY[draw],
                        cullingCenterZ[draw],
                        cullingRadii[draw],
                        cullingTransformIndices[draw],
                        batch,
                        start,
                        flags);
            }
        }
    }

    public boolean batchGpuCompactable(int batch) {
        return orderingPolicy(PASSES[passOrdinals[firstDrawInBatch(batch)]])
                == OrderingPolicy.BATCH;
    }

    public int batchGlMode(int batch) {
        return glModes[firstDrawInBatch(batch)];
    }

    public int batchGlIndexType(int batch) {
        return glIndexTypes[firstDrawInBatch(batch)];
    }

    private void radixSortSequence() {
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            Arrays.fill(radixCounts, 0);
            for (int position = 0; position < size; position++) {
                int key = (int) ((originalSequences[order[position]] >>> shift) & 0xFFL);
                radixCounts[key]++;
            }
            prefixCounts();
            for (int position = 0; position < size; position++) {
                int draw = order[position];
                int key = (int) ((originalSequences[draw] >>> shift) & 0xFFL);
                scratch[radixCounts[key]++] = draw;
            }
            swapOrderBuffers();
        }
    }

    private void radixSortInt(int keyKind) {
        for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
            Arrays.fill(radixCounts, 0);
            for (int position = 0; position < size; position++) {
                int key = (sortKey(order[position], keyKind) >>> shift) & 0xFF;
                radixCounts[key]++;
            }
            prefixCounts();
            for (int position = 0; position < size; position++) {
                int draw = order[position];
                int key = (sortKey(draw, keyKind) >>> shift) & 0xFF;
                scratch[radixCounts[key]++] = draw;
            }
            swapOrderBuffers();
        }
    }

    private int sortKey(int draw, int keyKind) {
        PassKey pass = PASSES[passOrdinals[draw]];
        OrderingPolicy policy = orderingPolicy(pass);
        return switch (keyKind) {
            case KEY_DEPTH -> switch (policy) {
                case BACK_TO_FRONT, MATERIAL_THEN_BACK_TO_FRONT ->
                        descendingFloatKey(viewDepths[draw]);
                case BATCH, STABLE -> 0;
            };
            case KEY_GEOMETRY_PAGE ->
                    policy == OrderingPolicy.BATCH || policy == OrderingPolicy.MATERIAL_THEN_BACK_TO_FRONT
                            ? geometryPages[draw] : 0;
            case KEY_DRAW_DATA_PAGE ->
                    policy == OrderingPolicy.BATCH || policy == OrderingPolicy.MATERIAL_THEN_BACK_TO_FRONT
                            ? drawDataPages[draw] : 0;
            case KEY_MATERIAL ->
                    policy == OrderingPolicy.BATCH || policy == OrderingPolicy.MATERIAL_THEN_BACK_TO_FRONT
                            ? materialIds[draw] : 0;
            case KEY_PIPELINE ->
                    policy == OrderingPolicy.BATCH || policy == OrderingPolicy.MATERIAL_THEN_BACK_TO_FRONT
                            ? pipelineIds[draw] : 0;
            case KEY_PASS -> passOrdinals[draw];
            default -> throw new IllegalArgumentException("unknown radix key kind " + keyKind);
        };
    }

    private void prefixCounts() {
        int offset = 0;


        for (int bucket = 0; bucket < radixCounts.length; bucket++) {
            int count = radixCounts[bucket];
            radixCounts[bucket] = offset;
            offset += count;
        }
    }

    private OrderingPolicy orderingPolicy(PassKey pass) {
        if (orderIndependentTransparency && pass == PassKey.ENTITY_TRANSLUCENT) {
            return OrderingPolicy.BATCH;
        }
        return pass.orderingPolicy();
    }

    private void swapOrderBuffers() {
        int[] previousOrder = order;
        order = scratch;
        scratch = previousOrder;
    }

    private void buildBatches() {
        batchCount = 0;
        int start = 0;
        while (start < size) {
            int first = order[start];
            int end = start + 1;
            while (end < size && batchCompatible(first, order[end])) end++;
            batchStarts[batchCount] = start;
            batchLengths[batchCount] = end - start;
            batchCount++;
            start = end;
        }
    }

    private boolean batchCompatible(int left, int right) {
        return passOrdinals[left] == passOrdinals[right]
                && pipelineIds[left] == pipelineIds[right]
                && materialIds[left] == materialIds[right]
                && geometryPages[left] == geometryPages[right]
                && drawDataPages[left] == drawDataPages[right]
                && glModes[left] == glModes[right]
                && glIndexTypes[left] == glIndexTypes[right];
    }

    private void ensureCapacity(int requested) {
        if (requested <= passOrdinals.length) return;
        int grown = Math.max(requested, Math.multiplyExact(passOrdinals.length, 2));
        passOrdinals = Arrays.copyOf(passOrdinals, grown);
        pipelineIds = Arrays.copyOf(pipelineIds, grown);
        materialIds = Arrays.copyOf(materialIds, grown);
        geometryPages = Arrays.copyOf(geometryPages, grown);
        drawDataPages = Arrays.copyOf(drawDataPages, grown);
        glModes = Arrays.copyOf(glModes, grown);
        glIndexTypes = Arrays.copyOf(glIndexTypes, grown);
        indexCounts = Arrays.copyOf(indexCounts, grown);
        instanceCounts = Arrays.copyOf(instanceCounts, grown);
        firstIndices = Arrays.copyOf(firstIndices, grown);
        baseVertices = Arrays.copyOf(baseVertices, grown);
        baseInstances = Arrays.copyOf(baseInstances, grown);
        viewDepths = Arrays.copyOf(viewDepths, grown);
        originalSequences = Arrays.copyOf(originalSequences, grown);
        cullingTransformIndices = Arrays.copyOf(cullingTransformIndices, grown);
        cullingCenterX = Arrays.copyOf(cullingCenterX, grown);
        cullingCenterY = Arrays.copyOf(cullingCenterY, grown);
        cullingCenterZ = Arrays.copyOf(cullingCenterZ, grown);
        cullingRadii = Arrays.copyOf(cullingRadii, grown);
        cullingEnabled = Arrays.copyOf(cullingEnabled, grown);
        order = Arrays.copyOf(order, grown);
        scratch = Arrays.copyOf(scratch, grown);
        batchStarts = Arrays.copyOf(batchStarts, grown);
        batchLengths = Arrays.copyOf(batchLengths, grown);
    }

    private int firstDrawInBatch(int batch) {
        checkBatch(batch);
        return order[batchStarts[batch]];
    }

    private void checkBatch(int batch) {
        ensureSorted();
        if (batch < 0 || batch >= batchCount) throw new IndexOutOfBoundsException(batch);
    }

    private void ensureSorted() {
        if (!sorted) throw new IllegalStateException("draw buffer must be sorted before ordered access");
    }

    private static int descendingFloatKey(float value) {
        int bits = Float.floatToRawIntBits(value);
        int ascending = bits ^ ((bits >> 31) | 0x80000000);
        return ~ascending;
    }

    private static void requireNonNegative(int value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }
}
