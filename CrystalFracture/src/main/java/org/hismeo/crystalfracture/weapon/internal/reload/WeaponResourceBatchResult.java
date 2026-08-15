package org.hismeo.crystalfracture.weapon.internal.reload;

import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistrySnapshot;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.List;
import java.util.Optional;

public record WeaponResourceBatchResult(
        Optional<WeaponRegistrySnapshot> snapshot,
        List<WeaponDefinitionProblem> problems
) {
    public WeaponResourceBatchResult {
        snapshot = snapshot == null ? Optional.empty() : snapshot;
        problems = problems == null ? List.of() : problems.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();
        boolean errors = problems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        if (errors == snapshot.isPresent()) {
            throw new IllegalArgumentException(
                    "snapshot must be present exactly when the resource batch has no errors");
        }
    }

    public boolean valid() {
        return snapshot.isPresent();
    }
}
