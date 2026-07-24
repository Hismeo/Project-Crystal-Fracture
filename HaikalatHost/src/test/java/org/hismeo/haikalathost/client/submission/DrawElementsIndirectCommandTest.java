package org.hismeo.haikalathost.client.submission;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawElementsIndirectCommandTest {
    @Test
    void serializesAllCommandFieldsInOrder() {
        DrawElementsIndirectCommand command = new DrawElementsIndirectCommand(12, 2, 4, -3, 7);
        ByteBuffer target = ByteBuffer.allocate(DrawElementsIndirectCommand.BYTES);

        command.writeTo(target);
        target.flip();

        assertEquals(12, target.getInt());
        assertEquals(2, target.getInt());
        assertEquals(4, target.getInt());
        assertEquals(-3, target.getInt());
        assertEquals(7, target.getInt());
        assertEquals(0, target.remaining());
    }

    @Test
    void rejectsNegativeUnsignedFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new DrawElementsIndirectCommand(-1, 1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DrawElementsIndirectCommand(1, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DrawElementsIndirectCommand(1, 1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DrawElementsIndirectCommand(1, 1, 0, 0, -1));
    }

    @Test
    void onlyOpaqueDomainsAreReorderable() {
        assertTrue(OrderDomain.OPAQUE.reorderable());
        assertTrue(OrderDomain.CUTOUT.reorderable());
        assertEquals(2, java.util.Arrays.stream(OrderDomain.values())
                .filter(OrderDomain::reorderable)
                .count());
    }
}
