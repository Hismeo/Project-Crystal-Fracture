package org.hismeo.haikalathost.client.scene;

public record MaterialId(int value) {
    public MaterialId {
        if (value < 0) throw new IllegalArgumentException("material id must not be negative");
    }
}
