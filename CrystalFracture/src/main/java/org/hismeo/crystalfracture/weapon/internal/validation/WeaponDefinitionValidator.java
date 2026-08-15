package org.hismeo.crystalfracture.weapon.internal.validation;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WeaponDefinitionValidator {
    public List<WeaponDefinitionProblem> validate(WeaponPartTypeDefinition type) {
        List<WeaponDefinitionProblem> problems = new ArrayList<>();
        Set<String> connectNames = type.requiredConnects().stream()
                .map(ConnectName::value)
                .collect(java.util.stream.Collectors.toSet());
        for (MarkerName marker : type.requiredMarkers()) {
            if (connectNames.contains(marker.value())) {
                error(problems, type.id().value(), "name_role_conflict", "$.required_markers",
                        "semantic name '" + marker + "' cannot be both a connect and marker");
            }
        }
        return stable(problems);
    }

    public List<WeaponDefinitionProblem> validate(
            WeaponPartDefinition part,
            Map<WeaponPartTypeId, WeaponPartTypeDefinition> types
    ) {
        List<WeaponDefinitionProblem> problems = new ArrayList<>();
        WeaponPartTypeDefinition type = types.get(part.type());
        if (type == null) {
            error(problems, part.id().value(), "unknown_part_type", "$.type",
                    "unknown weapon part type " + part.type());
        } else {
            for (ConnectName required : type.requiredConnects()) {
                if (!part.connects().containsKey(required)) {
                    error(problems, part.id().value(), "missing_required_connect", "$.connects." + required,
                            "part does not provide required connect " + required);
                }
            }
            for (MarkerName required : type.requiredMarkers()) {
                if (!part.markers().containsKey(required)) {
                    error(problems, part.id().value(), "missing_required_marker", "$.markers." + required,
                            "part does not provide required marker " + required);
                }
            }
        }

        Map<String, String> locatorOwners = new HashMap<>();
        part.connects().forEach((name, locator) -> registerLocator(
                locatorOwners, locator, "$.connects." + name, part.id().value(), problems));
        part.markers().forEach((name, locator) -> {
            if (part.connects().keySet().stream().anyMatch(connect -> connect.value().equals(name.value()))) {
                error(problems, part.id().value(), "name_role_conflict", "$.markers." + name,
                        "semantic name '" + name + "' cannot be both a connect and marker");
            }
            registerLocator(locatorOwners, locator, "$.markers." + name, part.id().value(), problems);
        });
        return stable(problems);
    }

    public List<WeaponDefinitionProblem> validate(
            WeaponSchemaDefinition schema,
            Map<WeaponPartTypeId, WeaponPartTypeDefinition> types
    ) {
        List<WeaponDefinitionProblem> problems = new ArrayList<>();
        ResourceLocation resource = schema.id().value();
        if (schema.schemaVersion() != WeaponSchemaDefinition.CURRENT_SCHEMA_VERSION) {
            error(problems, resource, "unsupported_schema_version", "$.schema_version",
                    "unsupported schema version " + schema.schemaVersion());
        }
        if (schema.slots().isEmpty()) {
            error(problems, resource, "missing_slot", "$.slots", "schema must define at least one slot");
        }
        if (!schema.slots().containsKey(schema.root())) {
            error(problems, resource, "unknown_slot", "$.root", "unknown root slot " + schema.root());
        }
        schema.slots().forEach((slot, definition) -> {
            if (!types.containsKey(definition.partType())) {
                error(problems, resource, "unknown_part_type", "$.slots." + slot + ".part_type",
                        "unknown weapon part type " + definition.partType());
            }
        });

        Map<WeaponSlotId, Integer> indegree = new HashMap<>();
        Map<WeaponSlotId, Set<WeaponSlotId>> children = new HashMap<>();
        Set<String> usedEndpoints = new HashSet<>();
        Set<WeaponSlotId> disconnectedReported = new HashSet<>();
        for (WeaponSlotId slot : schema.slots().keySet()) {
            indegree.put(slot, 0);
            children.put(slot, new HashSet<>());
        }

        for (int index = 0; index < schema.connections().size(); index++) {
            WeaponConnectionDefinition connection = schema.connections().get(index);
            String path = "$.connections[" + index + "]";
            boolean parentExists = validateEndpoint(
                    connection.parent(), path + ".parent", schema, types, resource, usedEndpoints, problems);
            boolean childExists = validateEndpoint(
                    connection.child(), path + ".child", schema, types, resource, usedEndpoints, problems);

            if (connection.parent().slot().equals(connection.child().slot())) {
                error(problems, resource, "self_connection", path + ".child.slot",
                        "connection cannot join slot " + connection.parent().slot() + " to itself");
            }
            if (childExists) {
                int incoming = indegree.merge(connection.child().slot(), 1, Integer::sum);
                if (connection.child().slot().equals(schema.root())) {
                    error(problems, resource, "root_has_parent", path + ".child.slot",
                            "root slot " + schema.root() + " cannot have a parent");
                } else if (incoming > 1) {
                    error(problems, resource, "slot_has_multiple_parents", path + ".child.slot",
                            "slot " + connection.child().slot() + " has more than one parent");
                }
            }
            if (parentExists && childExists && !connection.parent().slot().equals(connection.child().slot())) {
                children.get(connection.parent().slot()).add(connection.child().slot());
            }
        }

        for (WeaponSlotId slot : schema.slots().keySet()) {
            if (!slot.equals(schema.root()) && indegree.getOrDefault(slot, 0) == 0) {
                disconnectedReported.add(slot);
                error(problems, resource, "disconnected_slot", "$.slots." + slot,
                        "non-root slot " + slot + " has no parent");
            }
        }

        if (hasCycle(schema.slots().keySet(), children)) {
            error(problems, resource, "cyclic_topology", "$.connections",
                    "schema connections contain a directed cycle");
        }
        if (schema.slots().containsKey(schema.root())) {
            Set<WeaponSlotId> reachable = reachable(schema.root(), children);
            for (WeaponSlotId slot : schema.slots().keySet()) {
                if (!reachable.contains(slot) && disconnectedReported.add(slot)) {
                    error(problems, resource, "disconnected_slot", "$.slots." + slot,
                            "slot " + slot + " is not reachable from root " + schema.root());
                }
            }
        }

        schema.markers().forEach((exportName, export) -> {
            WeaponSlotDefinition slot = schema.slots().get(export.slot());
            String path = "$.markers." + exportName;
            if (slot == null) {
                error(problems, resource, "unknown_slot", path + ".slot",
                        "unknown marker slot " + export.slot());
                return;
            }
            WeaponPartTypeDefinition type = types.get(slot.partType());
            if (type != null && !type.requiredMarkers().contains(export.marker())) {
                error(problems, resource, "unknown_marker", path + ".marker",
                        "part type " + type.id() + " does not guarantee marker " + export.marker());
            }
        });
        return stable(problems);
    }

    private static boolean validateEndpoint(
            WeaponConnectEndpoint endpoint,
            String path,
            WeaponSchemaDefinition schema,
            Map<WeaponPartTypeId, WeaponPartTypeDefinition> types,
            ResourceLocation resource,
            Set<String> usedEndpoints,
            List<WeaponDefinitionProblem> problems
    ) {
        WeaponSlotDefinition slot = schema.slots().get(endpoint.slot());
        if (slot == null) {
            error(problems, resource, "unknown_slot", path + ".slot", "unknown slot " + endpoint.slot());
            return false;
        }
        String key = endpoint.slot().value() + "\u0000" + endpoint.connect().value();
        if (!usedEndpoints.add(key)) {
            error(problems, resource, "duplicate_endpoint", path + ".connect",
                    "connect endpoint " + endpoint.slot() + "." + endpoint.connect() + " is already used");
        }
        WeaponPartTypeDefinition type = types.get(slot.partType());
        if (type != null && !type.requiredConnects().contains(endpoint.connect())) {
            error(problems, resource, "unknown_connect", path + ".connect",
                    "part type " + type.id() + " does not guarantee connect " + endpoint.connect());
        }
        return true;
    }

    private static void registerLocator(
            Map<String, String> owners,
            String locator,
            String path,
            ResourceLocation resource,
            List<WeaponDefinitionProblem> problems
    ) {
        if (locator.isBlank()) {
            error(problems, resource, "invalid_locator_name", path, "locator/node name must not be blank");
            return;
        }
        String previous = owners.putIfAbsent(locator, path);
        if (previous != null) {
            error(problems, resource, "duplicate_locator_binding", path,
                    "locator/node '" + locator + "' is already bound at " + previous);
        }
    }

    private static boolean hasCycle(
            Set<WeaponSlotId> slots,
            Map<WeaponSlotId, Set<WeaponSlotId>> children
    ) {
        Map<WeaponSlotId, Integer> colors = new HashMap<>();
        for (WeaponSlotId slot : slots) {
            if (cycleVisit(slot, children, colors)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cycleVisit(
            WeaponSlotId slot,
            Map<WeaponSlotId, Set<WeaponSlotId>> children,
            Map<WeaponSlotId, Integer> colors
    ) {
        int color = colors.getOrDefault(slot, 0);
        if (color == 1) {
            return true;
        }
        if (color == 2) {
            return false;
        }
        colors.put(slot, 1);
        for (WeaponSlotId child : children.getOrDefault(slot, Set.of())) {
            if (cycleVisit(child, children, colors)) {
                return true;
            }
        }
        colors.put(slot, 2);
        return false;
    }

    private static Set<WeaponSlotId> reachable(
            WeaponSlotId root,
            Map<WeaponSlotId, Set<WeaponSlotId>> children
    ) {
        Set<WeaponSlotId> result = new HashSet<>();
        ArrayDeque<WeaponSlotId> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            WeaponSlotId slot = pending.removeFirst();
            if (result.add(slot)) {
                children.getOrDefault(slot, Set.of()).stream().sorted().forEach(pending::addLast);
            }
        }
        return result;
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
