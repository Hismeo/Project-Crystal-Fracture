package org.hismeo.haikalathost.client.scene;

public record MeshId(int value) {
    public MeshId {
        if (value < 0) throw new IllegalArgumentException("mesh id must not be negative");
    }
}
