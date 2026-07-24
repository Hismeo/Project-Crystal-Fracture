package org.hismeo.haikalathost.client.gpu;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameTransformTableTest {
    @Test
    void deduplicatesExactMatrixPairsAndWritesStd430Payload() {
        FrameTransformTable table = new FrameTransformTable(1);
        Matrix4f modelView = new Matrix4f().translate(1.0F, 2.0F, 3.0F);
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);

        assertEquals(0, table.resolve(modelView, projection));
        assertEquals(0, table.resolve(new Matrix4f(modelView), new Matrix4f(projection)));
        assertEquals(1, table.resolve(new Matrix4f().scale(2.0F), projection));
        assertEquals(2, table.size());

        ByteBuffer bytes = ByteBuffer.allocate(table.size() * FrameTransformTable.BYTES_PER_TRANSFORM)
                .order(ByteOrder.nativeOrder());
        table.writeTo(bytes);
        assertEquals(0, bytes.remaining());

        Matrix4f restored = table.modelView(0, new Matrix4f());
        assertEquals(modelView, restored);
        assertEquals(projection, table.projection(0, new Matrix4f()));
    }
}
