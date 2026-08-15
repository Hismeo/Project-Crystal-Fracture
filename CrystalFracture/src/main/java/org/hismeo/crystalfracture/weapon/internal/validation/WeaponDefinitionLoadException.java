package org.hismeo.crystalfracture.weapon.internal.validation;

import java.util.List;

public final class WeaponDefinitionLoadException extends RuntimeException {
    private final List<WeaponDefinitionProblem> problems;

    public WeaponDefinitionLoadException(List<WeaponDefinitionProblem> problems) {
        super(format(problems));
        this.problems = problems.stream().sorted(WeaponDefinitionProblem.STABLE_ORDER).toList();
    }

    public List<WeaponDefinitionProblem> problems() {
        return problems;
    }

    private static String format(List<WeaponDefinitionProblem> problems) {
        return "invalid weapon definition: " + problems.stream()
                .map(problem -> problem.resourceId() + " " + problem.jsonPath() + " ["
                        + problem.errorCode() + "] " + problem.message())
                .sorted()
                .toList();
    }
}
