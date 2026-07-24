package org.hismeo.haikalathost.client.geometry;

/** Byte offsets of the fixed Minecraft vertex element representations, or -1 when absent. */
public record CanonicalSourceLayout(
        int strideBytes,
        int positionOffset,
        int colorOffset,
        int uvOffset,
        int overlayOffset,
        int lightOffset,
        int normalOffset
) {
    public CanonicalSourceLayout {
        if (strideBytes <= 0) throw new IllegalArgumentException("strideBytes must be positive");
        requireRange(positionOffset, 12, strideBytes, "position");
        requireOptionalRange(colorOffset, 4, strideBytes, "color");
        requireOptionalRange(uvOffset, 8, strideBytes, "uv");
        requireOptionalRange(overlayOffset, 4, strideBytes, "overlay");
        requireOptionalRange(lightOffset, 4, strideBytes, "light");
        requireOptionalRange(normalOffset, 3, strideBytes, "normal");
    }

    private static void requireRange(int offset, int bytes, int stride, String label) {
        if (offset < 0 || (long) offset + bytes > stride) {
            throw new IllegalArgumentException(label + " element is outside source stride");
        }
    }

    private static void requireOptionalRange(int offset, int bytes, int stride, String label) {
        if (offset == -1) return;
        requireRange(offset, bytes, stride, label);
    }
}
