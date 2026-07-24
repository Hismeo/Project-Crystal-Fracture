package org.hismeo.haikalathost.client.submission;

import net.minecraft.client.renderer.RenderType;

import java.util.Objects;

public record DrawPacket(
        RenderType renderType,
        MinecraftPass pass,
        long stateEpoch,
        OrderDomain orderDomain,
        long originalSequence,
        long pipelineIdentity
) {
    public DrawPacket {
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(orderDomain, "orderDomain");
        if (stateEpoch < 0L || originalSequence < 0L || pipelineIdentity < 0L) {
            throw new IllegalArgumentException("Draw packet identifiers must not be negative");
        }
    }
}
