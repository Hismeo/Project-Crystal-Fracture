package org.hismeo.crystalfracture.weapon.internal.loader;

import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;

import java.util.List;
import java.util.Objects;

public record LoadedWeaponDefinition<T>(T value, List<WeaponDefinitionProblem> warnings) {
    public LoadedWeaponDefinition {
        Objects.requireNonNull(value, "value");
        warnings = warnings == null ? List.of() : warnings.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();
    }

    public static <T> LoadedWeaponDefinition<T> success(T value) {
        return new LoadedWeaponDefinition<>(value, List.of());
    }
}
