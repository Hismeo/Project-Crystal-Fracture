package org.hismeo.actionguide.internal.definition;

import java.util.List;

public final class DefinitionLoadException extends IllegalArgumentException {
    private final List<DefinitionProblem> problems;

    public DefinitionLoadException(String resource, List<DefinitionProblem> problems) {
        super(format(resource, problems));
        this.problems = List.copyOf(problems);
    }

    public List<DefinitionProblem> problems() {
        return problems;
    }

    private static String format(String resource, List<DefinitionProblem> problems) {
        return "invalid definition " + resource + ":\n" + String.join("\n", problems.stream().map(DefinitionProblem::toString).toList());
    }
}
