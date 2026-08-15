package org.hismeo.crystalfracture.weapon.internal.registry;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionValidator;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WeaponDefinitionRegistry {
    private final WeaponDefinitionValidator validator;

    public WeaponDefinitionRegistry() {
        this(new WeaponDefinitionValidator());
    }

    public WeaponDefinitionRegistry(WeaponDefinitionValidator validator) {
        this.validator = validator;
    }

    public WeaponRegistryBuildResult build(
            Collection<WeaponPartTypeDefinition> partTypes,
            Collection<WeaponPartDefinition> parts,
            Collection<WeaponSchemaDefinition> schemas
    ) {
        List<WeaponDefinitionProblem> problems = new ArrayList<>();
        Map<WeaponPartTypeId, WeaponPartTypeDefinition> typeMap = collect(
                partTypes, WeaponPartTypeDefinition::id, definition -> definition.id().value(), "weapon part type", problems);
        Map<WeaponPartId, WeaponPartDefinition> partMap = collect(
                parts, WeaponPartDefinition::id, definition -> definition.id().value(), "weapon part", problems);
        Map<WeaponSchemaId, WeaponSchemaDefinition> schemaMap = collect(
                schemas, WeaponSchemaDefinition::id, definition -> definition.id().value(), "weapon schema", problems);

        for (WeaponPartTypeDefinition type : typeMap.values()) {
            problems.addAll(validator.validate(type));
        }
        for (WeaponPartDefinition part : partMap.values()) {
            problems.addAll(validator.validate(part, typeMap));
        }
        for (WeaponSchemaDefinition schema : schemaMap.values()) {
            problems.addAll(validator.validate(schema, typeMap));
        }
        List<WeaponDefinitionProblem> sortedProblems = problems.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();

        boolean hasErrors = sortedProblems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        Optional<WeaponRegistrySnapshot> snapshot = hasErrors
                ? Optional.empty()
                : Optional.of(new WeaponRegistrySnapshot(typeMap, partMap, schemaMap));
        return new WeaponRegistryBuildResult(snapshot, sortedProblems);
    }

    private static <K extends Comparable<K>, V> Map<K, V> collect(
            Collection<V> values,
            java.util.function.Function<V, K> key,
            java.util.function.Function<V, ResourceLocation> resource,
            String label,
            List<WeaponDefinitionProblem> problems
    ) {
        Map<K, V> result = new TreeMap<>();
        for (V value : values) {
            K id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                problems.add(WeaponDefinitionProblem.error(
                        "duplicate_definition", resource.apply(value), "$", "duplicate " + label + " id " + id));
            }
        }
        return result;
    }
}
