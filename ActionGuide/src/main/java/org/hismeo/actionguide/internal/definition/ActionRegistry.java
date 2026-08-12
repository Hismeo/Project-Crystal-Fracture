package org.hismeo.actionguide.internal.definition;

import org.hismeo.actionguide.api.action.ActionDefinition;
import org.hismeo.actionguide.api.cue.CombatCueDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ActionRegistry {
    private final DefinitionValidator validator;
    private final AtomicLong generations = new AtomicLong();
    private final AtomicReference<RegistrySnapshot> current = new AtomicReference<>(new RegistrySnapshot(0, Map.of(), Map.of()));

    public ActionRegistry() {
        this(new DefinitionValidator());
    }

    public ActionRegistry(DefinitionValidator validator) {
        this.validator = validator;
    }

    public RegistrySnapshot snapshot() {
        return current.get();
    }

    public RegistrySnapshot publish(Collection<CombatCueDefinition> cues, Collection<ActionDefinition> actions) {
        Map<org.hismeo.actionguide.api.cue.CombatCueId, CombatCueDefinition> cueMap = unique(
                cues, CombatCueDefinition::id, "combat cue");
        Map<org.hismeo.actionguide.api.action.ActionId, ActionDefinition> actionMap = unique(
                actions, ActionDefinition::id, "action");
        List<DefinitionProblem> problems = new ArrayList<>();
        cueMap.values().forEach(cue -> problems.addAll(validator.validate(cue)));
        actionMap.values().forEach(action -> problems.addAll(validator.validate(action, cueMap)));
        actionMap.values().forEach(action -> action.transitions().forEach(transition ->
                transition.targetAction().filter(target -> !actionMap.containsKey(target)).ifPresent(target ->
                        problems.add(new DefinitionProblem(ProblemSeverity.ERROR, "$.transitions.target_action",
                                "unknown target action " + target)))));
        if (problems.stream().anyMatch(problem -> problem.severity() == ProblemSeverity.ERROR)) {
            throw new DefinitionLoadException("registry", problems);
        }
        RegistrySnapshot next = new RegistrySnapshot(generations.incrementAndGet(), cueMap, actionMap);
        current.set(next);
        return next;
    }

    private static <K, V> Map<K, V> unique(Collection<V> values, Function<V, K> key, String label) {
        try {
            return values.stream().collect(Collectors.toUnmodifiableMap(key, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new DefinitionLoadException("registry", List.of(
                    new DefinitionProblem(ProblemSeverity.ERROR, "$", "duplicate " + label + " id")));
        }
    }
}
