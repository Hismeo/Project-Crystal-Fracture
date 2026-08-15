package org.hismeo.crystalfracture.weapon.internal.compile;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponAssembly;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.List;
import java.util.Optional;

public record WeaponAssemblyCompileResult(
        Optional<CompiledWeaponAssembly> assembly,
        List<WeaponDefinitionProblem> problems
) {
    public WeaponAssemblyCompileResult {
        assembly = assembly == null ? Optional.empty() : assembly;
        problems = problems == null ? List.of() : problems.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();
        boolean hasErrors = problems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        if (hasErrors == assembly.isPresent()) {
            throw new IllegalArgumentException(
                    "compiled assembly must be present exactly when validation has no errors");
        }
    }

    public boolean valid() {
        return assembly.isPresent();
    }
}
