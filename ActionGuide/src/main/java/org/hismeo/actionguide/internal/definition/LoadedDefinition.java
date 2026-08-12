package org.hismeo.actionguide.internal.definition;

import java.util.List;
import java.util.Objects;

public record LoadedDefinition<T>(T value, List<DefinitionProblem> warnings) {
    public LoadedDefinition {
        Objects.requireNonNull(value, "value");
        warnings = List.copyOf(warnings);
    }
}
