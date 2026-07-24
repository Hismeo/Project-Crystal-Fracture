package org.hismeo.haikalathost.client.submission;

public enum OrderDomain {
    OPAQUE(true),
    CUTOUT(true),
    TRANSLUCENT(false),
    GLINT(false),
    FIRST_PERSON(false),
    UI(false),
    UNKNOWN_STABLE(false);

    private final boolean reorderable;

    OrderDomain(boolean reorderable) {
        this.reorderable = reorderable;
    }

    public boolean reorderable() {
        return reorderable;
    }
}
