package org.hismeo.crystalfracture.weapon.internal.registry;

import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.List;
import java.util.Optional;

public record WeaponRegistryBuildResult(
        Optional<WeaponRegistrySnapshot> snapshot,
        List<WeaponDefinitionProblem> problems
) {
    public WeaponRegistryBuildResult {
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        problems = problems == null ? List.of() : problems.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();
        boolean hasErrors = problems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        if (hasErrors == snapshot.isPresent()) {
            throw new IllegalArgumentException("snapshot must be present exactly when validation has no errors");
        }
    }

    public boolean valid() {
        return snapshot.isPresent();
    }
}
