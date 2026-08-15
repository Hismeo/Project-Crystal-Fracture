package org.hismeo.fractureclient.client.weapon;

import java.util.List;

public final class WeaponVisualCompilationException extends RuntimeException {
    private final List<WeaponVisualProblem> problems;

    public WeaponVisualCompilationException(List<WeaponVisualProblem> problems) {
        super("invalid weapon visual assembly: " + problems.stream()
                .sorted(WeaponVisualProblem.STABLE_ORDER)
                .map(problem -> problem.slot() + "/" + problem.part() + " node '"
                        + problem.nodeName() + "' [" + problem.errorCode() + "] "
                        + problem.message())
                .toList());
        this.problems = problems.stream().sorted(WeaponVisualProblem.STABLE_ORDER).toList();
    }

    public List<WeaponVisualProblem> problems() {
        return problems;
    }
}
