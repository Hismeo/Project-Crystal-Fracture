package org.hismeo.haikalathost.client.submission;

import java.nio.ByteBuffer;

public record DrawElementsIndirectCommand(
        int count,
        int instanceCount,
        int firstIndex,
        int baseVertex,
        int baseInstance
) {
    public static final int BYTES = Integer.BYTES * 5;

    public DrawElementsIndirectCommand {
        if (count < 0 || instanceCount < 0 || firstIndex < 0 || baseInstance < 0) {
            throw new IllegalArgumentException("Indirect draw counts and unsigned fields must not be negative");
        }
    }

    void writeTo(ByteBuffer target) {
        target.putInt(count);
        target.putInt(instanceCount);
        target.putInt(firstIndex);
        target.putInt(baseVertex);
        target.putInt(baseInstance);
    }
}
