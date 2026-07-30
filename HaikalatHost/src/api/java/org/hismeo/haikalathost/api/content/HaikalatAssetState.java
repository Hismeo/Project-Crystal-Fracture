package org.hismeo.haikalathost.api.content;

/**
 * Stable, GPU-free view of a registered asset's preparation lifecycle.
 */
public enum HaikalatAssetState {
    /**
     * The declaration is known, but this Host version has not started an asset-specific consumer.
     */
    DECLARED,
    PREPARING,
    CPU_READY,
    FAILED,
    CLOSED
}
