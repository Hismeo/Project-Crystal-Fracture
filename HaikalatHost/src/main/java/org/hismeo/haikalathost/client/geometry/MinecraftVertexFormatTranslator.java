package org.hismeo.haikalathost.client.geometry;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MinecraftVertexFormatTranslator {
    private final Map<VertexFormat, MinecraftVertexLayout> cache = new IdentityHashMap<>();

    public MinecraftVertexLayout translate(VertexFormat format) {
        MinecraftVertexLayout cached = cache.get(format);
        if (cached != null) return cached;

        var attributes = new ArrayList<MinecraftVertexAttribute>(format.getElements().size());
        for (int location = 0; location < format.getElements().size(); location++) {
            VertexFormatElement element = format.getElements().get(location);
            attributes.add(translateElement(format, element, location));
        }

        MinecraftVertexLayout translated = new MinecraftVertexLayout(format.getVertexSize(), attributes);
        cache.put(format, translated);
        return translated;
    }

    public void clear() {
        cache.clear();
    }

    private static MinecraftVertexAttribute translateElement(
            VertexFormat format, VertexFormatElement element, int location) {
        VertexInputClass inputClass;
        boolean normalized;

        if (element == VertexFormatElement.POSITION) {
            require(element, VertexFormatElement.Type.FLOAT, 3);
            inputClass = VertexInputClass.FLOATING;
            normalized = false;
        } else if (element == VertexFormatElement.COLOR) {
            require(element, VertexFormatElement.Type.UBYTE, 4);
            inputClass = VertexInputClass.FLOATING;
            normalized = true;
        } else if (element == VertexFormatElement.UV0) {
            require(element, VertexFormatElement.Type.FLOAT, 2);
            inputClass = VertexInputClass.FLOATING;
            normalized = false;
        } else if (element == VertexFormatElement.UV1 || element == VertexFormatElement.UV2) {
            require(element, VertexFormatElement.Type.SHORT, 2);
            inputClass = VertexInputClass.INTEGER;
            normalized = false;
        } else if (element == VertexFormatElement.NORMAL) {
            require(element, VertexFormatElement.Type.BYTE, 3);
            inputClass = VertexInputClass.FLOATING;
            normalized = true;
        } else {
            throw unsupported(element, "unregistered or unsupported vertex element");
        }

        int offset = format.getOffset(element);
        if (offset < 0 || (long) offset + element.byteSize() > format.getVertexSize()) {
            throw unsupported(element, "vertex element lies outside its stride");
        }
        return new MinecraftVertexAttribute(
                location, element.count(), element.type().glType(), normalized, offset, inputClass);
    }

    private static void require(VertexFormatElement element, VertexFormatElement.Type type, int count) {
        if (element.type() != type || element.count() != count) {
            throw unsupported(element, "unexpected built-in vertex element representation");
        }
    }

    private static UnsupportedVertexFormatException unsupported(VertexFormatElement element, String reason) {
        return new UnsupportedVertexFormatException(element, reason + ": " + element);
    }
}
