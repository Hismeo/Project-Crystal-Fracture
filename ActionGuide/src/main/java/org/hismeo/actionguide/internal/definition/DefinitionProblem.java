package org.hismeo.actionguide.internal.definition;

import java.util.Objects;

public record DefinitionProblem(ProblemSeverity severity, String path, String message) {
    public DefinitionProblem {
        Objects.requireNonNull(severity, "severity");
        path = Objects.requireNonNull(path, "path");
        message = Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return severity + " " + path + ": " + message;
    }
}
