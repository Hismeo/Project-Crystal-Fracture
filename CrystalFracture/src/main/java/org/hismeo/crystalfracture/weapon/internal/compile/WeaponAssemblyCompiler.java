package org.hismeo.crystalfracture.weapon.internal.compile;

import org.hismeo.crystalfracture.weapon.api.CompiledWeaponConnection;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.ResolvedMarker;
import org.hismeo.crystalfracture.weapon.api.WeaponAssembly;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.internal.registry.WeaponRegistrySnapshot;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponAssemblyValidator;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponProblemSeverity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class WeaponAssemblyCompiler {
    private static final Comparator<WeaponConnectionDefinition> CHILD_ORDER = Comparator
            .comparing((WeaponConnectionDefinition connection) -> connection.child().slot())
            .thenComparing(connection -> connection.child().connect())
            .thenComparing(connection -> connection.parent().connect());

    private final WeaponAssemblyValidator validator;

    public WeaponAssemblyCompiler() {
        this(new WeaponAssemblyValidator());
    }

    public WeaponAssemblyCompiler(WeaponAssemblyValidator validator) {
        this.validator = validator;
    }

    public WeaponAssemblyCompileResult compile(
            WeaponAssembly assembly,
            WeaponRegistrySnapshot registry
    ) {
        var problems = validator.validate(assembly, registry);
        boolean hasErrors = problems.stream()
                .anyMatch(problem -> problem.severity() == WeaponProblemSeverity.ERROR);
        if (hasErrors) {
            return new WeaponAssemblyCompileResult(Optional.empty(), problems);
        }

        WeaponSchemaDefinition schema = registry.schema(assembly.schema()).orElseThrow();
        Map<WeaponSlotId, WeaponPartDefinition> resolvedParts = new TreeMap<>();
        schema.slots().keySet().forEach(slot -> resolvedParts.put(
                slot, registry.part(assembly.parts().get(slot)).orElseThrow()));

        Map<WeaponSlotId, List<WeaponConnectionDefinition>> children = new HashMap<>();
        for (WeaponConnectionDefinition connection : schema.connections()) {
            children.computeIfAbsent(connection.parent().slot(), ignored -> new ArrayList<>())
                    .add(connection);
        }
        children.values().forEach(connections -> connections.sort(CHILD_ORDER));

        List<WeaponSlotId> slotOrder = new ArrayList<>();
        appendSlotOrder(schema.root(), children, slotOrder);
        Map<WeaponSlotId, WeaponPartDefinition> parts = new LinkedHashMap<>();
        slotOrder.forEach(slot -> parts.put(slot, resolvedParts.get(slot)));

        List<CompiledWeaponConnection> parentFirst = new ArrayList<>();
        appendParentFirst(schema.root(), children, parts, parentFirst);

        Map<MarkerName, ResolvedMarker> markers = new TreeMap<>();
        schema.markers().forEach((exportName, export) -> {
            WeaponPartDefinition part = parts.get(export.slot());
            markers.put(exportName, new ResolvedMarker(
                    exportName,
                    export.slot(),
                    part.id(),
                    export.marker(),
                    part.markers().get(export.marker())
            ));
        });

        String contentHash = WeaponAssemblyContentHasher.hash(
                schema.id(), schema.root(), parts, parentFirst, markers);
        return new WeaponAssemblyCompileResult(Optional.of(new CompiledWeaponAssemblyImpl(
                schema.id(), schema.root(), parts, parentFirst, markers, contentHash)), problems);
    }

    private static void appendParentFirst(
            WeaponSlotId parent,
            Map<WeaponSlotId, List<WeaponConnectionDefinition>> children,
            Map<WeaponSlotId, WeaponPartDefinition> parts,
            List<CompiledWeaponConnection> result
    ) {
        for (WeaponConnectionDefinition connection : children.getOrDefault(parent, List.of())) {
            WeaponPartDefinition parentPart = parts.get(connection.parent().slot());
            WeaponPartDefinition childPart = parts.get(connection.child().slot());
            result.add(new CompiledWeaponConnection(
                    connection.parent(),
                    parentPart.connects().get(connection.parent().connect()),
                    connection.child(),
                    childPart.connects().get(connection.child().connect())
            ));
            appendParentFirst(connection.child().slot(), children, parts, result);
        }
    }

    private static void appendSlotOrder(
            WeaponSlotId slot,
            Map<WeaponSlotId, List<WeaponConnectionDefinition>> children,
            List<WeaponSlotId> result
    ) {
        result.add(slot);
        for (WeaponConnectionDefinition connection : children.getOrDefault(slot, List.of())) {
            appendSlotOrder(connection.child().slot(), children, result);
        }
    }
}
