package org.hismeo.haikalathost.client.geometry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftVertexLayoutTest {
    @Test
    void createsAnImmutableAttributeSnapshot() {
        MinecraftVertexAttribute position = new MinecraftVertexAttribute(
                0, 3, 0x1406, false, 0, VertexInputClass.FLOATING);
        List<MinecraftVertexAttribute> source = new ArrayList<>(List.of(position));

        MinecraftVertexLayout layout = new MinecraftVertexLayout(12, source);
        source.clear();

        assertEquals(12, layout.strideBytes());
        assertEquals(List.of(position), layout.attributes());
        assertThrows(UnsupportedOperationException.class, () -> layout.attributes().clear());
    }

    @Test
    void validatesLayoutAndAttributeFields() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftVertexLayout(0, List.of(
                new MinecraftVertexAttribute(0, 3, 0x1406, false, 0, VertexInputClass.FLOATING))));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftVertexLayout(12, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new MinecraftVertexAttribute(-1, 3, 0x1406, false, 0, VertexInputClass.FLOATING));
        assertThrows(IllegalArgumentException.class, () ->
                new MinecraftVertexAttribute(0, 5, 0x1406, false, 0, VertexInputClass.FLOATING));
        assertThrows(IllegalArgumentException.class, () ->
                new MinecraftVertexAttribute(0, 4, 0x1404, true, 0, VertexInputClass.INTEGER));
    }
}
