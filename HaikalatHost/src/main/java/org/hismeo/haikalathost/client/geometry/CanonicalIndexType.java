package org.hismeo.haikalathost.client.geometry;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;

public enum CanonicalIndexType {
    UNSIGNED_SHORT(Short.BYTES, GL_UNSIGNED_SHORT),
    UNSIGNED_INT(Integer.BYTES, GL_UNSIGNED_INT);

    private final int bytes;
    private final int glType;

    CanonicalIndexType(int bytes, int glType) {
        this.bytes = bytes;
        this.glType = glType;
    }

    public int bytes() {
        return bytes;
    }

    public int glType() {
        return glType;
    }
}
