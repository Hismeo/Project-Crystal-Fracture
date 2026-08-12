package org.hismeo.actionguide.internal.runtime;

import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.internal.definition.ActionRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionRuntimeStore {
    private final ActionRegistry registry;
    private final Map<UUID, ActionRuntime> runtimes = new ConcurrentHashMap<>();

    public ActionRuntimeStore(ActionRegistry registry) {
        this.registry = registry;
    }

    public ActionRuntime getOrCreate(ActionOwner owner) {
        return runtimes.computeIfAbsent(owner.id(), ignored -> new ActionRuntime(owner, registry));
    }

    public Optional<ActionRuntime> find(ActionOwner owner) {
        return Optional.ofNullable(runtimes.get(owner.id()));
    }

    public void remove(ActionOwner owner, ActionStopReason reason) {
        ActionRuntime runtime = runtimes.remove(owner.id());
        if (runtime != null) {
            runtime.interrupt(reason);
        }
    }

    public void clear(ActionStopReason reason) {
        runtimes.values().forEach(runtime -> runtime.interrupt(reason));
        runtimes.clear();
    }

    public Collection<ActionRuntime> snapshot() {
        return List.copyOf(runtimes.values());
    }
}
