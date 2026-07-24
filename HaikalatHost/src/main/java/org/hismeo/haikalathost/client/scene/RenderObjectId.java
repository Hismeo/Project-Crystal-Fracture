package org.hismeo.haikalathost.client.scene;

public record RenderObjectId(int index, int generation) {
    public RenderObjectId {
        if (index < 0) throw new IllegalArgumentException("render object index must not be negative");
        if (generation <= 0) throw new IllegalArgumentException("generation must be positive");
    }
}
