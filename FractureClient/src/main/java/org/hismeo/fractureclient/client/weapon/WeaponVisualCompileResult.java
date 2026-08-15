package org.hismeo.fractureclient.client.weapon;

import java.util.List;
import java.util.Optional;

public record WeaponVisualCompileResult(
        Optional<WeaponVisualAssembly> assembly,
        List<WeaponVisualProblem> problems
) {
    public WeaponVisualCompileResult {
        assembly = assembly == null ? Optional.empty() : assembly;
        problems = problems == null ? List.of() : problems.stream()
                .sorted(WeaponVisualProblem.STABLE_ORDER)
                .toList();
        if (assembly.isPresent() == !problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "visual assembly must be present exactly when there are no problems");
        }
    }
}
