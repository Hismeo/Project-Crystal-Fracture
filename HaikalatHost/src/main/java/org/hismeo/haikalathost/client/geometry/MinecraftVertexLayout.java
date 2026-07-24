package org.hismeo.haikalathost.client.geometry;

import java.util.List;

public record MinecraftVertexLayout(int strideBytes, List<MinecraftVertexAttribute> attributes) {
    public MinecraftVertexLayout {
        if (strideBytes <= 0) throw new IllegalArgumentException("strideBytes must be positive");
        attributes = List.copyOf(attributes);
        if (attributes.isEmpty()) throw new IllegalArgumentException("attributes must not be empty");
    }
}
