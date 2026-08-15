package org.hismeo.crystalfracture.weapon.internal.reload;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponPartTypeLoader;
import org.hismeo.crystalfracture.weapon.internal.loader.WeaponSchemaLoader;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponDefinitionRegistry;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionLoadException;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Strictly parses and validates a complete resource generation in dependency order. */
public final class WeaponResourceBatchLoader {
    public static final String ROOT = "crystal_fracture";
    public static final String TYPE_DIRECTORY = ROOT + "/weapon_part_types/";
    public static final String PART_DIRECTORY = ROOT + "/weapon_parts/";
    public static final String SCHEMA_DIRECTORY = ROOT + "/weapon_schemas/";

    private final WeaponPartTypeLoader typeLoader = new WeaponPartTypeLoader();
    private final WeaponPartLoader partLoader = new WeaponPartLoader();
    private final WeaponSchemaLoader schemaLoader = new WeaponSchemaLoader();
    private final WeaponDefinitionRegistry registry = new WeaponDefinitionRegistry();

    public WeaponResourceBatchResult load(Map<ResourceLocation, String> resources) {
        List<WeaponPartTypeDefinition> types = new ArrayList<>();
        List<WeaponPartDefinition> parts = new ArrayList<>();
        List<WeaponSchemaDefinition> schemas = new ArrayList<>();
        List<WeaponDefinitionProblem> problems = new ArrayList<>();

        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> loadOne(
                        entry.getKey(), entry.getValue(), types, parts, schemas, problems));

        var built = registry.build(types, parts, schemas);
        problems.addAll(built.problems());
        List<WeaponDefinitionProblem> stableProblems = problems.stream()
                .sorted(WeaponDefinitionProblem.STABLE_ORDER)
                .toList();
        boolean errors = stableProblems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        return new WeaponResourceBatchResult(
                errors ? Optional.empty() : built.snapshot(), stableProblems);
    }

    public static boolean isWeaponDefinition(ResourceLocation resource) {
        String path = resource.getPath();
        return path.endsWith(".json") && (path.startsWith(TYPE_DIRECTORY)
                || path.startsWith(PART_DIRECTORY)
                || path.startsWith(SCHEMA_DIRECTORY));
    }

    private void loadOne(
            ResourceLocation resource,
            String json,
            List<WeaponPartTypeDefinition> types,
            List<WeaponPartDefinition> parts,
            List<WeaponSchemaDefinition> schemas,
            List<WeaponDefinitionProblem> problems
    ) {
        try {
            String path = resource.getPath();
            if (path.startsWith(TYPE_DIRECTORY)) {
                WeaponPartTypeId id = new WeaponPartTypeId(definitionId(resource, TYPE_DIRECTORY));
                var loaded = typeLoader.load(id, json);
                types.add(loaded.value());
                problems.addAll(loaded.warnings());
            } else if (path.startsWith(PART_DIRECTORY)) {
                WeaponPartId id = new WeaponPartId(definitionId(resource, PART_DIRECTORY));
                var loaded = partLoader.load(id, json);
                parts.add(loaded.value());
                problems.addAll(loaded.warnings());
            } else if (path.startsWith(SCHEMA_DIRECTORY)) {
                WeaponSchemaId id = new WeaponSchemaId(definitionId(resource, SCHEMA_DIRECTORY));
                var loaded = schemaLoader.load(id, json);
                schemas.add(loaded.value());
                problems.addAll(loaded.warnings());
            }
        } catch (WeaponDefinitionLoadException failure) {
            problems.addAll(failure.problems());
        } catch (RuntimeException failure) {
            problems.add(WeaponDefinitionProblem.error(
                    "resource_load_failure",
                    resource,
                    "$",
                    failure.getMessage() == null
                            ? failure.getClass().getSimpleName()
                            : failure.getMessage()));
        }
    }

    private static ResourceLocation definitionId(ResourceLocation resource, String directory) {
        String path = resource.getPath();
        String definitionPath = path.substring(directory.length(), path.length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(resource.getNamespace(), definitionPath);
    }
}
