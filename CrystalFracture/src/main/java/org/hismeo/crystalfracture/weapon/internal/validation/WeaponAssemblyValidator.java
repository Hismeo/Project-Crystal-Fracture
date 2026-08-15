package org.hismeo.crystalfracture.weapon.internal.validation;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistrySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WeaponAssemblyValidator {
    public List<WeaponDefinitionProblem> validate(
            WeaponAssembly assembly,
            WeaponRegistrySnapshot registry
    ) {
        List<WeaponDefinitionProblem> problems = new ArrayList<>();
        ResourceLocation resource = assembly.schema().value();
        WeaponSchemaDefinition schema = registry.schema(assembly.schema()).orElse(null);
        if (schema == null) {
            error(problems, resource, "unknown_schema", "$.schema",
                    "unknown weapon schema " + assembly.schema());
            return stable(problems);
        }

        for (WeaponSlotId slot : schema.slots().keySet()) {
            if (!assembly.parts().containsKey(slot)) {
                error(problems, resource, "missing_assembly_slot", "$.parts." + slot,
                        "assembly does not provide required slot " + slot);
            }
        }
        for (WeaponSlotId slot : assembly.parts().keySet()) {
            if (!schema.slots().containsKey(slot)) {
                error(problems, resource, "unexpected_assembly_slot", "$.parts." + slot,
                        "assembly provides unknown slot " + slot);
            }
        }

        Map<WeaponSlotId, WeaponPartDefinition> resolved = new HashMap<>();
        schema.slots().forEach((slot, slotDefinition) -> {
            var partId = assembly.parts().get(slot);
            if (partId == null) {
                return;
            }
            WeaponPartDefinition part = registry.part(partId).orElse(null);
            if (part == null) {
                error(problems, resource, "unknown_part", "$.parts." + slot,
                        "unknown weapon part " + partId);
                return;
            }
            resolved.put(slot, part);
            if (!part.type().equals(slotDefinition.partType())) {
                error(problems, resource, "part_type_mismatch", "$.parts." + slot,
                        "part " + part.id() + " has type " + part.type()
                                + ", expected " + slotDefinition.partType());
            }
        });

        for (int index = 0; index < schema.connections().size(); index++) {
            var connection = schema.connections().get(index);
            validateConnect(resolved.get(connection.parent().slot()), connection.parent().connect(),
                    resource, "$.connections[" + index + "].parent.connect", problems);
            validateConnect(resolved.get(connection.child().slot()), connection.child().connect(),
                    resource, "$.connections[" + index + "].child.connect", problems);
        }
        schema.markers().forEach((exportName, export) -> {
            WeaponPartDefinition part = resolved.get(export.slot());
            if (part != null && !part.markers().containsKey(export.marker())) {
                error(problems, resource, "unknown_marker", "$.markers." + exportName + ".marker",
                        "part " + part.id() + " does not provide marker " + export.marker());
            }
        });
        return stable(problems);
    }

    private static void validateConnect(
            WeaponPartDefinition part,
            org.hismeo.crystalfracture.weapon.api.ConnectName connect,
            ResourceLocation resource,
            String path,
            List<WeaponDefinitionProblem> problems
    ) {
        if (part != null && !part.connects().containsKey(connect)) {
            error(problems, resource, "unknown_connect", path,
                    "part " + part.id() + " does not provide connect " + connect);
        }
    }

    private static void error(
            List<WeaponDefinitionProblem> problems,
            ResourceLocation resource,
            String code,
            String path,
            String message
    ) {
        problems.add(WeaponDefinitionProblem.error(code, resource, path, message));
    }

    private static List<WeaponDefinitionProblem> stable(List<WeaponDefinitionProblem> problems) {
        return problems.stream().sorted(WeaponDefinitionProblem.STABLE_ORDER).toList();
    }
}
