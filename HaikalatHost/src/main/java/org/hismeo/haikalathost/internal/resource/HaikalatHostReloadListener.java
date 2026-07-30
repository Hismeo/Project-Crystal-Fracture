package org.hismeo.haikalathost.internal.resource;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.hismeo.haikalathost.internal.runtime.MinecraftHaikalatRuntime;

import java.util.Objects;

/**
 * Establishes the reload-generation seam without performing GL work on reload executors.
 */
public final class HaikalatHostReloadListener
        extends SimplePreparableReloadListener<Long> {
    private final MinecraftHaikalatRuntime runtime;

    public HaikalatHostReloadListener(MinecraftHaikalatRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    protected Long prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return runtime.beginResourceReload();
    }

    @Override
    protected void apply(
            Long generation,
            ResourceManager resourceManager,
            ProfilerFiller profiler
    ) {
        runtime.enqueuePreparedReload(generation);
    }
}
