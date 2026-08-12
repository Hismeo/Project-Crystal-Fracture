package org.hismeo.actionguide.internal;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.ActionGuideApi;
import org.hismeo.actionguide.api.action.ActionAdmission;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.action.ActionResolver;
import org.hismeo.actionguide.api.event.ActionLifecycleListener;
import org.hismeo.actionguide.api.event.CueEventHandler;
import org.hismeo.actionguide.api.event.CueStateHandler;
import org.hismeo.actionguide.api.runtime.ActionCommand;
import org.hismeo.actionguide.api.runtime.ActionRequestResult;
import org.hismeo.actionguide.api.runtime.ActionRuntimeView;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.api.network.ActionSyncListener;
import org.hismeo.actionguide.api.network.ActionSyncMessage;
import org.hismeo.actionguide.internal.definition.ActionRegistry;
import org.hismeo.actionguide.internal.runtime.ActionRuntime;
import org.hismeo.actionguide.internal.runtime.ActionRuntimeStore;
import org.hismeo.actionguide.internal.runtime.ActionRuntimeObserver;
import org.hismeo.actionguide.internal.network.ActionNetworkPayloads;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionGuideService implements ActionGuideApi {
    private final ActionRegistry registry = new ActionRegistry();
    private final ActionRuntimeStore runtimes = new ActionRuntimeStore(registry);
    private final List<ActionResolver> resolvers = new ArrayList<>();
    private final List<ActionAdmission> admissions = new ArrayList<>();
    private final Map<ResourceLocation, List<CueEventHandler>> eventHandlers = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, List<CueStateHandler>> stateHandlers = new ConcurrentHashMap<>();
    private final List<ActionLifecycleListener> lifecycleListeners = new ArrayList<>();
    private final List<ActionSyncListener> syncListeners = new ArrayList<>();
    private final List<ActionRuntimeObserver> runtimeObservers = new ArrayList<>();
    private final Map<java.util.UUID, RequestRate> requestRates = new ConcurrentHashMap<>();

    public ActionRegistry registry() {
        return registry;
    }

    public ActionRuntimeStore runtimeStore() {
        return runtimes;
    }

    @Override
    public synchronized void registerResolver(ActionResolver resolver) {
        resolvers.add(resolver);
        runtimes.snapshot().forEach(runtime -> runtime.registerResolver(resolver));
    }

    @Override
    public synchronized void registerAdmission(ActionAdmission admission) {
        admissions.add(admission);
        runtimes.snapshot().forEach(runtime -> runtime.registerAdmission(admission));
    }

    @Override
    public synchronized void registerEventHandler(ResourceLocation type, CueEventHandler handler) {
        eventHandlers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
        runtimes.snapshot().forEach(runtime -> runtime.registerEventHandler(type, handler));
    }

    @Override
    public synchronized void registerStateHandler(ResourceLocation type, CueStateHandler handler) {
        stateHandlers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
        runtimes.snapshot().forEach(runtime -> runtime.registerStateHandler(type, handler));
    }

    @Override
    public synchronized void registerLifecycleListener(ActionLifecycleListener listener) {
        lifecycleListeners.add(listener);
        runtimes.snapshot().forEach(runtime -> runtime.registerLifecycleListener(listener));
    }

    @Override
    public synchronized void registerSyncListener(ActionSyncListener listener) {
        syncListeners.add(listener);
    }

    public synchronized void registerRuntimeObserver(ActionRuntimeObserver observer) {
        runtimeObservers.add(observer);
        runtimes.snapshot().forEach(runtime -> runtime.registerObserver(observer));
    }

    @Override
    public void sendIntentToServer(ActionIntentRequest request) {
        ActionNetworkPayloads.sendIntent(request);
    }

    @Override
    public ActionRequestResult submitIntent(ActionOwner owner, ActionIntentRequest request) {
        return configured(owner).submit(request);
    }

    public ActionRequestResult submitNetworkIntent(ActionOwner owner, ActionIntentRequest request, long serverTick) {
        RequestRate rate = requestRates.compute(owner.id(), (ignored, previous) ->
                previous == null || previous.tick != serverTick ? new RequestRate(serverTick, 1)
                        : new RequestRate(serverTick, previous.count + 1));
        if (rate.count > 8) {
            return new ActionRequestResult(false,
                    org.hismeo.actionguide.api.ResourceIds.parse("action_guide:rate_limited", "rejection reason"),
                    runtime(owner).flatMap(ActionRuntimeView::currentAction));
        }
        return submitIntent(owner, request);
    }

    @Override
    public void submitCommand(ActionOwner owner, ActionCommand command) {
        configured(owner).submit(command);
    }

    @Override
    public Optional<ActionRuntimeView> runtime(ActionOwner owner) {
        return runtimes.find(owner).map(value -> value);
    }

    public void tick() {
        runtimes.snapshot().forEach(ActionRuntime::tick);
    }

    public void remove(ActionOwner owner, ActionStopReason reason) {
        runtimes.remove(owner, reason);
        requestRates.remove(owner.id());
    }

    public void clear(ActionStopReason reason) {
        runtimes.clear(reason);
        requestRates.clear();
    }

    public synchronized void receiveSync(ActionSyncMessage message) {
        List.copyOf(syncListeners).forEach(listener -> listener.onMessage(message));
    }

    private synchronized ActionRuntime configured(ActionOwner owner) {
        Optional<ActionRuntime> existing = runtimes.find(owner);
        if (existing.isPresent()) {
            return existing.get();
        }
        ActionRuntime runtime = runtimes.getOrCreate(owner);
        resolvers.forEach(runtime::registerResolver);
        admissions.forEach(runtime::registerAdmission);
        eventHandlers.forEach((type, handlers) -> handlers.forEach(handler -> runtime.registerEventHandler(type, handler)));
        stateHandlers.forEach((type, handlers) -> handlers.forEach(handler -> runtime.registerStateHandler(type, handler)));
        lifecycleListeners.forEach(runtime::registerLifecycleListener);
        runtimeObservers.forEach(runtime::registerObserver);
        return runtime;
    }

    private record RequestRate(long tick, int count) {
    }
}
