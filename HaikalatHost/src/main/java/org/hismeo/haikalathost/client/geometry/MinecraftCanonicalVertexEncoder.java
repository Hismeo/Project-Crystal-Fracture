package org.hismeo.haikalathost.client.geometry;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MinecraftCanonicalVertexEncoder {
    private final Map<VertexFormat, CanonicalSourceLayout> layouts = new IdentityHashMap<>();

    public void encode(
            VertexFormat format,
            ByteBuffer source,
            int vertexCount,
            ByteBuffer destination
    ) {
        CanonicalVertexEncoder.encode(
                source, layouts.computeIfAbsent(format, this::translate), vertexCount, destination);
    }

    public void clear() {
        layouts.clear();
    }

    private CanonicalSourceLayout translate(VertexFormat format) {
        for (VertexFormatElement element : format.getElements()) {
            if (element == VertexFormatElement.POSITION) require(element, VertexFormatElement.Type.FLOAT, 3);
            else if (element == VertexFormatElement.COLOR) require(element, VertexFormatElement.Type.UBYTE, 4);
            else if (element == VertexFormatElement.UV0) require(element, VertexFormatElement.Type.FLOAT, 2);
            else if (element == VertexFormatElement.UV1 || element == VertexFormatElement.UV2) {
                require(element, VertexFormatElement.Type.SHORT, 2);
            } else if (element == VertexFormatElement.NORMAL) {
                require(element, VertexFormatElement.Type.BYTE, 3);
            } else {
                throw new UnsupportedVertexFormatException(
                        element, "element has no canonical H1 representation");
            }
        }
        return new CanonicalSourceLayout(
                format.getVertexSize(),
                format.getOffset(VertexFormatElement.POSITION),
                format.getOffset(VertexFormatElement.COLOR),
                format.getOffset(VertexFormatElement.UV0),
                format.getOffset(VertexFormatElement.UV1),
                format.getOffset(VertexFormatElement.UV2),
                format.getOffset(VertexFormatElement.NORMAL));
    }

    private static void require(
            VertexFormatElement element, VertexFormatElement.Type type, int count) {
        if (element.type() != type || element.count() != count) {
            throw new UnsupportedVertexFormatException(
                    element, "unexpected built-in element representation");
        }
    }
}
