package org.hismeo.haikalathost.client.geometry;

public record MinecraftVertexAttribute(
        int location,
        int componentCount,
        int glType,
        boolean normalized,
        int offsetBytes,
        VertexInputClass inputClass
) {
    public MinecraftVertexAttribute {
        if (location < 0) throw new IllegalArgumentException("location must be non-negative");
        if (componentCount < 1 || componentCount > 4) {
            throw new IllegalArgumentException("componentCount must be in [1, 4]");
        }
        if (offsetBytes < 0) throw new IllegalArgumentException("offsetBytes must be non-negative");
        if (inputClass != VertexInputClass.FLOATING && normalized) {
            throw new IllegalArgumentException("Only floating inputs may be normalized");
        }
    }
}
