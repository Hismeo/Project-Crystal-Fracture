package org.hismeo.haikalathost.client.submission;

import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

public final class MinecraftDrawScheduler {
    private final IdentityHashMap<RenderType, Long> pipelineIdentities = new IdentityHashMap<>();
    private long nextEpoch;
    private long nextSequence;
    private long nextPipelineIdentity;
    private long lastImmediateSequence = -1L;

    public long beginEpoch() {
        return nextEpoch++;
    }

    public DrawPacket capture(RenderType renderType, long stateEpoch) {
        Objects.requireNonNull(renderType, "renderType");
        MinecraftDrawClassifier.Classification classification =
                MinecraftDrawClassifier.classify(renderType);
        long pipelineIdentity = pipelineIdentities.computeIfAbsent(
                renderType, ignored -> nextPipelineIdentity++);
        return new DrawPacket(
                renderType,
                classification.pass(),
                stateEpoch,
                classification.domain(),
                nextSequence++,
                pipelineIdentity);
    }

    /**
     * Immediate compatibility scopes are separate epochs because Minecraft owns their shader/uniform state.
     */
    public DrawPacket captureImmediate(RenderType renderType) {
        return capture(renderType, beginEpoch());
    }

    public void submitImmediate(DrawPacket packet, Runnable draw) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(draw, "draw");
        if (packet.originalSequence() <= lastImmediateSequence) {
            throw new IllegalStateException("Immediate draw packets must retain original sequence");
        }
        draw.run();
        lastImmediateSequence = packet.originalSequence();
    }

    /**
     * Reorders only contiguous reorderable regions within one pass and one state epoch.
     * Stable packets remain barriers at their original positions.
     */
    public List<DrawPacket> orderCompatibleBatch(List<DrawPacket> packets) {
        Objects.requireNonNull(packets, "packets");
        if (packets.size() < 2) return List.copyOf(packets);

        MinecraftPass pass = packets.getFirst().pass();
        long epoch = packets.getFirst().stateEpoch();
        for (DrawPacket packet : packets) {
            if (packet.pass() != pass || packet.stateEpoch() != epoch) {
                throw new IllegalArgumentException("A reorder batch may not cross pass or state-epoch boundaries");
            }
        }

        List<DrawPacket> ordered = new ArrayList<>(packets);
        int start = 0;
        while (start < ordered.size()) {
            if (!ordered.get(start).orderDomain().reorderable()) {
                start++;
                continue;
            }
            int end = start + 1;
            while (end < ordered.size() && ordered.get(end).orderDomain().reorderable()) {
                end++;
            }
            ordered.subList(start, end).sort(
                    Comparator.comparingLong(DrawPacket::pipelineIdentity)
                            .thenComparingLong(DrawPacket::originalSequence));
            start = end;
        }
        return List.copyOf(ordered);
    }

    public void clearPipelineIdentities() {
        pipelineIdentities.clear();
    }
}
