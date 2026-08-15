package org.hismeo.actionguide.internal.runtime;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.ActionGuide;
import org.hismeo.actionguide.api.ResourceIds;
import org.hismeo.actionguide.api.action.ActionAdmission;
import org.hismeo.actionguide.api.action.ActionContext;
import org.hismeo.actionguide.api.action.ActionDecision;
import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.action.ActionId;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.action.ActionResolver;
import org.hismeo.actionguide.api.action.ActionTransition;
import org.hismeo.actionguide.api.action.BufferedIntent;
import org.hismeo.actionguide.api.action.TransitionMode;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;
import org.hismeo.actionguide.api.cue.CueItemId;
import org.hismeo.actionguide.api.cue.CueSection;
import org.hismeo.actionguide.api.cue.CueState;
import org.hismeo.actionguide.api.cue.CueTime;
import org.hismeo.actionguide.api.cue.RootMotionContract;
import org.hismeo.actionguide.api.cue.SectionId;
import org.hismeo.actionguide.api.event.ActionLifecycleListener;
import org.hismeo.actionguide.api.event.CueEventHandler;
import org.hismeo.actionguide.api.event.CueStateHandler;
import org.hismeo.actionguide.api.runtime.ActionInstanceId;
import org.hismeo.actionguide.api.runtime.ActionInstanceStatus;
import org.hismeo.actionguide.api.runtime.ActionInstanceView;
import org.hismeo.actionguide.api.runtime.ActionCommand;
import org.hismeo.actionguide.api.runtime.ActionRequestResult;
import org.hismeo.actionguide.api.runtime.ActionRuntimeView;
import org.hismeo.actionguide.api.runtime.ActionStopReason;
import org.hismeo.actionguide.internal.definition.ActionRegistry;
import org.hismeo.actionguide.internal.definition.CueTypes;
import org.hismeo.actionguide.internal.definition.RegistrySnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class ActionRuntime implements ActionRuntimeView {
    public static final ResourceLocation ACCEPTED = ResourceIds.parse("action_guide:accepted", "result");
    public static final ResourceLocation BUSY = ResourceIds.parse("action_guide:busy", "result");
    public static final ResourceLocation UNRESOLVED = ResourceIds.parse("action_guide:unresolved_intent", "result");
    public static final ResourceLocation UNKNOWN_ACTION = ResourceIds.parse("action_guide:unknown_action", "result");
    public static final ResourceLocation UNKNOWN_CUE = ResourceIds.parse("action_guide:unknown_cue", "result");
    public static final ResourceLocation DUPLICATE_SEQUENCE = ResourceIds.parse("action_guide:duplicate_or_stale_sequence", "result");
    public static final ResourceLocation BUFFER_FULL = ResourceIds.parse("action_guide:input_buffer_full", "result");

    private static final AtomicLong INSTANCE_IDS = new AtomicLong();

    private final ActionOwner owner;
    private final ActionRegistry registry;
    private final TimelineStepper stepper = new TimelineStepper();
    private final InputBuffer inputBuffer;
    private final List<ActionResolver> resolvers = new ArrayList<>();
    private final List<ActionAdmission> admissions = new ArrayList<>();
    private final Map<ResourceLocation, List<CueEventHandler>> eventHandlers = new HashMap<>();
    private final Map<ResourceLocation, List<CueStateHandler>> stateHandlers = new HashMap<>();
    private final List<ActionLifecycleListener> lifecycleListeners = new ArrayList<>();
    private final List<ActionRuntimeObserver> observers = new ArrayList<>();
    private final Deque<Runnable> deferredCommands = new ArrayDeque<>();
    private ActionInstance current;
    private boolean dispatching;
    private long serverTick;

    public ActionRuntime(ActionOwner owner, ActionRegistry registry) {
        this(owner, registry, 8, 6);
    }

    public ActionRuntime(ActionOwner owner, ActionRegistry registry, int inputCapacity, long inputLifetimeTicks) {
        this.owner = owner;
        this.registry = registry;
        this.inputBuffer = new InputBuffer(inputCapacity, inputLifetimeTicks);
    }

    public void registerResolver(ActionResolver resolver) {
        resolvers.add(resolver);
    }

    public void registerAdmission(ActionAdmission admission) {
        admissions.add(admission);
    }

    public void registerEventHandler(ResourceLocation type, CueEventHandler handler) {
        eventHandlers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
    }

    public void registerStateHandler(ResourceLocation type, CueStateHandler handler) {
        stateHandlers.computeIfAbsent(type, ignored -> new ArrayList<>()).add(handler);
    }

    public void registerLifecycleListener(ActionLifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    public void registerObserver(ActionRuntimeObserver observer) {
        observers.add(observer);
    }

    public ActionRequestResult submit(ActionIntentRequest request) {
        InputOfferResult offered = inputBuffer.offer(request, serverTick);
        if (offered == InputOfferResult.DUPLICATE_OR_STALE) {
            return rejected(DUPLICATE_SEQUENCE);
        }
        if (offered == InputOfferResult.CAPACITY_REACHED) {
            return rejected(BUFFER_FULL);
        }
        if (current != null) {
            consumeBufferedInput();
            drainCommands();
            return new ActionRequestResult(true, ACCEPTED, currentAction());
        }
        Optional<ActionId> resolved = resolvers.stream().map(resolver -> resolver.resolve(context(), request))
                .flatMap(Optional::stream).findFirst();
        if (resolved.isEmpty()) {
            inputBuffer.consumeFirst(intent -> intent.sequence() == request.sequence(), serverTick);
            return rejected(UNRESOLVED);
        }
        inputBuffer.consumeFirst(intent -> intent.sequence() == request.sequence(), serverTick);
        return start(resolved.get());
    }

    public ActionRequestResult start(ActionId actionId) {
        if (dispatching) {
            deferredCommands.add(() -> start(actionId));
            return new ActionRequestResult(true, ACCEPTED, currentAction());
        }
        if (current != null) {
            return rejected(BUSY);
        }
        return startNow(actionId);
    }

    public void tick() {
        tick(CueTime.SERVER_TICK);
    }

    public void tick(CueTime delta) {
        serverTick++;
        inputBuffer.expire(serverTick);
        if (current == null) {
            return;
        }
        ActionInstance advancing = current;
        TimelineAdvance advance = stepper.advance(advancing.cue, advancing.cursor, advancing.loopIteration, delta);
        boolean reachedAdvanceEnd = dispatch(advance.operations());
        if (current == advancing && reachedAdvanceEnd) {
            current.cursor = advance.cursor();
            current.loopIteration = advance.loopIteration();
        }
        if (current != null) {
            consumeBufferedInput();
        }
        drainCommands();
    }

    public void interrupt(ActionStopReason reason) {
        if (dispatching) {
            deferredCommands.add(() -> interrupt(reason));
        } else {
            stopNow(reason, ActionInstanceStatus.INTERRUPTED);
        }
    }

    public void stop(ActionStopReason reason) {
        if (dispatching) {
            deferredCommands.add(() -> stop(reason));
        } else {
            stopNow(reason, ActionInstanceStatus.COMPLETED);
        }
    }

    public void submit(ActionCommand command) {
        Runnable operation = switch (command) {
            case ActionCommand.RequestStart request -> () -> start(request.action());
            case ActionCommand.RequestInterrupt request -> () -> interrupt(request.reason());
            case ActionCommand.RequestTransition request -> () -> directEnter(request.section());
            case ActionCommand.RequestStop request -> () -> stop(request.reason());
        };
        if (dispatching) {
            deferredCommands.add(operation);
        } else {
            operation.run();
            drainCommands();
        }
    }

    @Override
    public Optional<ActionInstanceView> currentAction() {
        return Optional.ofNullable(current).map(ActionInstance::view);
    }

    @Override
    public Optional<RootMotionContract> currentRootMotion() {
        return Optional.ofNullable(current).map(instance -> instance.cue.rootMotion());
    }

    @Override
    public boolean isStateActive(ResourceLocation stateType) {
        if (current == null) {
            return false;
        }
        return current.cue.states().stream().anyMatch(state -> state.type().equals(stateType) && current.activeStateIds.contains(state.id()));
    }

    @Override
    public boolean isInSection(SectionId section) {
        return current != null && section.equals(current.currentSection);
    }

    @Override
    public CueTime cursor() {
        return current == null ? CueTime.ZERO : current.cursor;
    }

    public List<BufferedIntent> bufferedIntents() {
        return inputBuffer.snapshot();
    }

    public long serverTick() {
        return serverTick;
    }

    private ActionRequestResult startNow(ActionId actionId) {
        RegistrySnapshot snapshot = registry.snapshot();
        ActionDefinition definition = snapshot.action(actionId).orElse(null);
        if (definition == null) {
            return rejected(UNKNOWN_ACTION);
        }
        CombatCueDefinition cue = snapshot.cue(definition.cue()).orElse(null);
        if (cue == null) {
            return rejected(UNKNOWN_CUE);
        }
        ActionContext context = context();
        for (ActionAdmission admission : admissions) {
            ActionDecision decision = admission.evaluate(context, definition);
            if (!decision.accepted()) {
                return rejected(decision.reason());
            }
        }
        ActionInstance instance = new ActionInstance(new ActionInstanceId(INSTANCE_IDS.incrementAndGet()),
                definition, cue, owner, snapshot.generation());
        for (ActionAdmission admission : admissions) {
            admission.commit(context, definition, instance.instanceId);
        }
        current = instance;
        dispatch(stepper.enterAt(cue, instance.cursor, 0));
        drainCommands();
        if (current == instance) {
            instance.status = ActionInstanceStatus.RUNNING;
            ActionContext startedContext = context();
            for (ActionLifecycleListener listener : List.copyOf(lifecycleListeners)) {
                try {
                    listener.onStarted(startedContext, instance.view());
                } catch (RuntimeException exception) {
                    stopNow(ActionStopReason.HANDLER_FAILURE, ActionInstanceStatus.FAILED);
                    break;
                }
            }
        }
        return new ActionRequestResult(current == instance, current == instance ? ACCEPTED : ActionStopReason.HANDLER_FAILURE.value(),
                Optional.of(instance.view()));
    }

    private boolean dispatch(List<TimelineOperation> operations) {
        boolean reachedEnd = true;
        dispatching = true;
        try {
            for (int index = 0; index < operations.size(); index++) {
                TimelineOperation operation = operations.get(index);
                if (current == null) {
                    break;
                }
                current.cursor = operation.time();
                current.loopIteration = operation.loopIteration();
                apply(operation);
                boolean endOfTimePoint = index + 1 == operations.size()
                        || !sameTimePoint(operation, operations.get(index + 1));
                if (endOfTimePoint && !deferredCommands.isEmpty()) {
                    dispatching = false;
                    drainCommands();
                    reachedEnd = false;
                    break;
                }
            }
        } catch (RuntimeException exception) {
            deferredCommands.addFirst(() -> stopNow(ActionStopReason.HANDLER_FAILURE, ActionInstanceStatus.FAILED));
        } finally {
            dispatching = false;
        }
        return reachedEnd;
    }

    private static boolean sameTimePoint(TimelineOperation left, TimelineOperation right) {
        return left.time().equals(right.time()) && left.loopIteration() == right.loopIteration();
    }

    private void apply(TimelineOperation operation) {
        switch (operation) {
            case TimelineOperation.StateExit value -> {
                current.activeStateIds.remove(value.state().id());
                for (CueStateHandler handler : handlers(stateHandlers, value.state().type())) {
                    handler.onExit(context(), current.view(), value.state());
                }
            }
            case TimelineOperation.SectionExit value -> {
                if (value.section().id().equals(current.currentSection)) {
                    current.currentSection = null;
                }
            }
            case TimelineOperation.SectionEnter value -> current.currentSection = value.section().id();
            case TimelineOperation.StateEnter value -> {
                current.activeStateIds.add(value.state().id());
                for (CueStateHandler handler : handlers(stateHandlers, value.state().type())) {
                    handler.onEnter(context(), current.view(), value.state());
                }
            }
            case TimelineOperation.Event value -> {
                for (CueEventHandler handler : handlers(eventHandlers, value.event().type())) {
                    handler.handle(context(), current.view(), value.event());
                }
                for (ActionRuntimeObserver observer : List.copyOf(observers)) {
                    try {
                        observer.onEvent(context(), current.view(), value.event(), value.loopIteration());
                    } catch (RuntimeException exception) {
                        ActionGuide.LOGGER.warn("Action timeline observer failed for {}", current.definition.id(), exception);
                    }
                }
            }
            case TimelineOperation.Complete ignored -> {
                if (current.definition.endPolicy() == org.hismeo.actionguide.api.action.ActionEndPolicy.COMPLETE) {
                    deferredCommands.add(() -> stopNow(ActionStopReason.COMPLETED, ActionInstanceStatus.COMPLETED));
                }
            }
        }
    }

    private void consumeBufferedInput() {
        if (current == null || current.currentSection == null) {
            return;
        }
        List<CueState> inputStates = current.cue.states().stream()
                .filter(state -> state.type().equals(CueTypes.INPUT))
                .filter(state -> current.activeStateIds.contains(state.id()))
                .filter(state -> !current.consumedInputStateIds.contains(state.id()))
                .sorted(Comparator.comparing(CueState::start).thenComparing(CueState::id))
                .toList();
        for (CueState state : inputStates) {
            Optional<BufferedIntent> intent = inputBuffer.consumeFirst(candidate -> hasTransition(state.id(), candidate), serverTick);
            if (intent.isEmpty()) {
                continue;
            }
            current.consumedInputStateIds.add(state.id());
            ActionTransition transition = findTransition(state.id(), intent.get()).orElseThrow();
            executeTransition(transition);
            return;
        }
    }

    private boolean hasTransition(CueItemId stateId, BufferedIntent intent) {
        return findTransition(stateId, intent).isPresent();
    }

    private Optional<ActionTransition> findTransition(CueItemId stateId, BufferedIntent intent) {
        if (current == null) {
            return Optional.empty();
        }
        return current.definition.transitions().stream()
                .filter(transition -> transition.fromSection().equals(current.currentSection))
                .filter(transition -> transition.inputState().equals(stateId))
                .filter(transition -> transition.intent().equals(intent.intent()))
                .findFirst();
    }

    private void executeTransition(ActionTransition transition) {
        if (transition.targetAction().isPresent()) {
            ActionId target = transition.targetAction().orElseThrow();
            deferredCommands.add(() -> {
                stopNow(ActionStopReason.TRANSITIONED, ActionInstanceStatus.INTERRUPTED);
                startNow(target);
            });
            return;
        }
        SectionId target = transition.targetSection().orElseThrow();
        if (transition.mode() == TransitionMode.DIRECT) {
            deferredCommands.add(() -> directEnter(target));
        }
    }

    private void directEnter(SectionId targetId) {
        if (current == null) {
            return;
        }
        CueSection target = current.cue.requireSection(targetId);
        List<TimelineOperation> operations = new ArrayList<>();
        for (CueState state : current.cue.states()) {
            if (current.activeStateIds.contains(state.id()) && !state.activeAt(target.start())) {
                operations.add(new TimelineOperation.StateExit(target.start(), current.loopIteration, state));
            }
        }
        if (current.currentSection != null) {
            current.cue.sections().stream().filter(section -> section.id().equals(current.currentSection)).findFirst()
                    .ifPresent(section -> operations.add(new TimelineOperation.SectionExit(target.start(), current.loopIteration, section)));
        }
        operations.addAll(stepper.enterAt(current.cue, target.start(), current.loopIteration));
        operations.removeIf(operation -> operation instanceof TimelineOperation.StateEnter enter
                && current.activeStateIds.contains(enter.state().id()));
        dispatch(operations);
        if (current != null) {
            current.cursor = target.start();
            for (ActionLifecycleListener listener : List.copyOf(lifecycleListeners)) {
                try {
                    listener.onTransitioned(context(), current.view());
                } catch (RuntimeException exception) {
                    stopNow(ActionStopReason.HANDLER_FAILURE, ActionInstanceStatus.FAILED);
                    break;
                }
            }
        }
    }

    private void stopNow(ActionStopReason reason, ActionInstanceStatus status) {
        ActionInstance instance = current;
        if (instance == null) {
            return;
        }
        dispatching = true;
        try {
            for (CueState state : instance.cue.states()) {
                if (instance.activeStateIds.remove(state.id())) {
                    for (CueStateHandler handler : handlers(stateHandlers, state.type())) {
                        try {
                            handler.onExit(context(), instance.view(), state);
                        } catch (RuntimeException ignored) {
                            // Cleanup is best effort per handler, but every active state must still be exited.
                        }
                    }
                }
            }
        } finally {
            dispatching = false;
        }
        instance.status = status;
        instance.stopReason = reason;
        ActionContext stoppedContext = context();
        for (ActionLifecycleListener listener : List.copyOf(lifecycleListeners)) {
            try {
                listener.onStopped(stoppedContext, instance.view(), reason);
            } catch (RuntimeException ignored) {
                // A listener cannot keep a stopped instance in the runtime store.
            }
        }
        current = null;
    }

    private void drainCommands() {
        if (dispatching) {
            return;
        }
        while (!deferredCommands.isEmpty()) {
            deferredCommands.removeFirst().run();
        }
    }

    private ActionRequestResult rejected(ResourceLocation reason) {
        return new ActionRequestResult(false, reason, currentAction());
    }

    private ActionContext context() {
        return new ActionContext(owner, this, serverTick);
    }

    private static <T> List<T> handlers(Map<ResourceLocation, List<T>> handlers, ResourceLocation type) {
        return List.copyOf(handlers.getOrDefault(type, List.of()));
    }
}
