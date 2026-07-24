package org.hismeo.haikalathost.client.geometry;

import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class UnsupportedVertexFormatException extends RuntimeException {
    private final VertexFormatElement element;

    public UnsupportedVertexFormatException(VertexFormatElement element, String message) {
        super(message);
        this.element = element;
    }

    public VertexFormatElement element() {
        return element;
    }
}
