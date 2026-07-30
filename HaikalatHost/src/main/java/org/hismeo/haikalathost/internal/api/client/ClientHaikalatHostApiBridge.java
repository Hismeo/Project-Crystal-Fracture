package org.hismeo.haikalathost.internal.api.client;

import org.hismeo.haikalathost.api.HaikalatHostApi;
import org.hismeo.haikalathost.api.content.HaikalatAssets;
import org.hismeo.haikalathost.api.runtime.HostStatus;
import org.hismeo.haikalathost.internal.api.MinecraftHaikalatAssets;
import org.hismeo.haikalathost.internal.runtime.MinecraftHaikalatRuntime;

/**
 * Client-only linkage boundary used by the distribution-safe service provider.
 */
public final class ClientHaikalatHostApiBridge implements HaikalatHostApi {
    private static final HaikalatAssets ASSETS = new MinecraftHaikalatAssets();

    @Override
    public HostStatus status() {
        return MinecraftHaikalatRuntime.instance().status();
    }

    @Override
    public HaikalatAssets assets() {
        return ASSETS;
    }
}
