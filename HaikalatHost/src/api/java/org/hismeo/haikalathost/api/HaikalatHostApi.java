package org.hismeo.haikalathost.api;

import org.hismeo.haikalathost.api.content.HaikalatAssets;
import org.hismeo.haikalathost.api.runtime.HostStatus;

/**
 * Stable, GL-free entry point for optional HaikalatHost integrations.
 *
 * <p>The API artifact intentionally contains no runtime provider. When HaikalatHost is absent,
 * misconfigured, or installed on an unsupported distribution, {@link #get()} returns a stable
 * unavailable implementation instead of throwing a service-loading error.</p>
 */
public interface HaikalatHostApi {
    static HaikalatHostApi get() {
        return HaikalatHostApiLocator.instance();
    }

    HostStatus status();

    /**
     * Returns immutable snapshots of declaratively registered assets.
     *
     * <p>The empty service is returned by default so providers compiled against the first API
     * revision remain binary-compatible.</p>
     */
    default HaikalatAssets assets() {
        return HaikalatAssets.empty();
    }

    default boolean available() {
        return status().available();
    }
}
