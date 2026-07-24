package org.hismeo.haikalathost.client.scene;

public record TransformId(int value) {
    public TransformId {
        if (value < 0) throw new IllegalArgumentException("transform id must not be negative");
    }
}
