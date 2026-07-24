package org.hismeo.haikalathost.client.submission;

/** Ordered frame-pass identity. Enum order is execution order. */
public enum PassKey {
    DEPTH_PREPASS(OrderingPolicy.BATCH),
    WORLD_OPAQUE(OrderingPolicy.BATCH),
    WORLD_CUTOUT(OrderingPolicy.BATCH),
    ENTITY_OPAQUE(OrderingPolicy.BATCH),
    ENTITY_CUTOUT(OrderingPolicy.BATCH),
    SKY(OrderingPolicy.STABLE),
    CLOUD(OrderingPolicy.STABLE),
    WEATHER(OrderingPolicy.STABLE),
    WORLD_TRANSLUCENT(OrderingPolicy.BACK_TO_FRONT),
    ENTITY_TRANSLUCENT(OrderingPolicy.BACK_TO_FRONT),
    PARTICLE(OrderingPolicy.MATERIAL_THEN_BACK_TO_FRONT),
    LINE(OrderingPolicy.STABLE),
    OUTLINE(OrderingPolicy.STABLE),
    FIRST_PERSON(OrderingPolicy.STABLE),
    POSTPROCESS(OrderingPolicy.STABLE),
    TEXT(OrderingPolicy.STABLE),
    UI(OrderingPolicy.STABLE);

    private final OrderingPolicy orderingPolicy;

    PassKey(OrderingPolicy orderingPolicy) {
        this.orderingPolicy = orderingPolicy;
    }

    public OrderingPolicy orderingPolicy() {
        return orderingPolicy;
    }

    public boolean cullsBackFaces() {
        return this == WORLD_OPAQUE
                || this == WORLD_CUTOUT
                || this == WORLD_TRANSLUCENT;
    }
}
