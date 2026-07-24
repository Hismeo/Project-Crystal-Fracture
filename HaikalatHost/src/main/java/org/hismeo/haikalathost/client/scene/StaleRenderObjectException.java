package org.hismeo.haikalathost.client.scene;

public final class StaleRenderObjectException extends IllegalStateException {
    public StaleRenderObjectException(RenderObjectId id) {
        super("Stale or unknown render object handle: " + id);
    }
}
