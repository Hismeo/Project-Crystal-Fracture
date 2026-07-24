package org.hismeo.haikalathost.client.material;

public enum MaterialFeature {
    TEXTURED(1 << 0),
    VERTEX_COLOR(1 << 1),
    LIGHTMAP(1 << 2),
    OVERLAY(1 << 3),
    ALPHA_CUTOUT(1 << 4),
    EMISSIVE(1 << 5),
    FOG(1 << 6),
    SKINNED(1 << 7),
    INSTANCED(1 << 8),
    DOUBLE_SIDED(1 << 9);

    public static final int ALL_MASK = (1 << values().length) - 1;

    private final int bit;

    MaterialFeature(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int mask(MaterialFeature... features) {
        int result = 0;
        for (MaterialFeature feature : features) result |= feature.bit;
        return result;
    }

    public static boolean contains(int mask, MaterialFeature feature) {
        return (mask & feature.bit) != 0;
    }
}
