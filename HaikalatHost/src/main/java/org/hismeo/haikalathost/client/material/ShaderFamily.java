package org.hismeo.haikalathost.client.material;

/** Stable Host shader families. Minecraft shader instances never cross this boundary. */
public enum ShaderFamily {
    WORLD_OPAQUE("world_opaque"),
    WORLD_CUTOUT("world_cutout"),
    WORLD_TRANSLUCENT("world_translucent"),
    ENTITY_OPAQUE("entity_opaque"),
    ENTITY_CUTOUT("entity_cutout"),
    ENTITY_TRANSLUCENT("entity_translucent"),
    PARTICLE("particle"),
    LINE("line"),
    TEXT("text"),
    UI("ui"),
    SKY("sky"),
    CLOUD("cloud"),
    WEATHER("weather"),
    OUTLINE("outline"),
    POSTPROCESS("postprocess");

    private final String shaderName;

    ShaderFamily(String shaderName) {
        this.shaderName = shaderName;
    }

    public String shaderName() {
        return shaderName;
    }
}
