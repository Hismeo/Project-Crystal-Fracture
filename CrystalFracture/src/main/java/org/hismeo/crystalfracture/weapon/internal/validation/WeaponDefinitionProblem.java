package org.hismeo.crystalfracture.weapon.internal.validation;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public record WeaponDefinitionProblem(
        WeaponProblemSeverity severity,
        String errorCode,
        ResourceLocation resourceId,
        String jsonPath,
        String message,
        Optional<ResourceLocation> relatedResourceId
) {
    public static final Comparator<WeaponDefinitionProblem> STABLE_ORDER = Comparator
            .comparing((WeaponDefinitionProblem problem) -> problem.resourceId().toString())
            .thenComparing(WeaponDefinitionProblem::jsonPath)
            .thenComparing(WeaponDefinitionProblem::errorCode)
            .thenComparing(WeaponDefinitionProblem::message);

    public WeaponDefinitionProblem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(jsonPath, "jsonPath");
        Objects.requireNonNull(message, "message");
        relatedResourceId = relatedResourceId == null ? Optional.empty() : relatedResourceId;
    }

    public static WeaponDefinitionProblem error(
            String code,
            ResourceLocation resource,
            String path,
            String message
    ) {
        return new WeaponDefinitionProblem(
                WeaponProblemSeverity.ERROR, code, resource, path, message, Optional.empty());
    }
}
