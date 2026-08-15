package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.api.WeaponSchemaId;
import org.hismeo.crystalfracture.weapon.api.WeaponSlotId;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectEndpoint;
import org.hismeo.crystalfracture.weapon.definition.WeaponConnectionDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponMarkerExport;
import org.hismeo.crystalfracture.weapon.definition.WeaponSchemaDefinition;
import org.hismeo.crystalfracture.weapon.definition.WeaponSlotDefinition;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WeaponSchemaLoader {
    private static final Set<String> FIELDS = Set.of("schema_version", "root", "slots", "connections", "markers");
    private static final Set<String> SLOT_FIELDS = Set.of("part_type");
    private static final Set<String> CONNECTION_FIELDS = Set.of("parent", "child");
    private static final Set<String> ENDPOINT_FIELDS = Set.of("slot", "connect");
    private static final Set<String> MARKER_FIELDS = Set.of("slot", "marker");

    public LoadedWeaponDefinition<WeaponSchemaDefinition> load(WeaponSchemaId id, Reader json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    public LoadedWeaponDefinition<WeaponSchemaDefinition> load(WeaponSchemaId id, String json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    private static WeaponSchemaDefinition parse(WeaponSchemaId id, JsonElement element) {
        JsonObject root = WeaponJson.rootObject(element);
        WeaponJson.onlyFields(root, "$", FIELDS);
        int version = WeaponJson.integer(root, "schema_version", "$" );
        if (version != WeaponSchemaDefinition.CURRENT_SCHEMA_VERSION) {
            throw WeaponJson.problem("$.schema_version", "unsupported_schema_version",
                    "unsupported schema version " + version + "; expected "
                            + WeaponSchemaDefinition.CURRENT_SCHEMA_VERSION);
        }

        return new WeaponSchemaDefinition(
                id,
                version,
                slotName(WeaponJson.string(root, "root", "$"), "$.root"),
                slots(WeaponJson.object(root, "slots", "$")),
                connections(WeaponJson.array(root, "connections", "$")),
                markers(WeaponJson.object(root, "markers", "$"))
        );
    }

    private static Map<WeaponSlotId, WeaponSlotDefinition> slots(JsonObject object) {
        Map<WeaponSlotId, WeaponSlotDefinition> result = new TreeMap<>();
        for (String key : object.keySet().stream().sorted().toList()) {
            String path = "$.slots." + key;
            WeaponSlotId slot = slotName(key, path);
            JsonObject value = WeaponJson.object(object, key, "$.slots");
            WeaponJson.onlyFields(value, path, SLOT_FIELDS);
            String typeText = WeaponJson.string(value, "part_type", path);
            result.put(slot, new WeaponSlotDefinition(new WeaponPartTypeId(
                    WeaponJson.resourceLocation(typeText, path + ".part_type", "weapon part type id"))));
        }
        return result;
    }

    private static List<WeaponConnectionDefinition> connections(JsonArray array) {
        List<WeaponConnectionDefinition> result = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String path = "$.connections[" + index + "]";
            JsonObject value = WeaponJson.arrayObject(array, index, "$.connections");
            WeaponJson.onlyFields(value, path, CONNECTION_FIELDS);
            result.add(new WeaponConnectionDefinition(
                    endpoint(WeaponJson.object(value, "parent", path), path + ".parent"),
                    endpoint(WeaponJson.object(value, "child", path), path + ".child")
            ));
        }
        return result;
    }

    private static WeaponConnectEndpoint endpoint(JsonObject object, String path) {
        WeaponJson.onlyFields(object, path, ENDPOINT_FIELDS);
        WeaponSlotId slot = slotName(WeaponJson.string(object, "slot", path), path + ".slot");
        String connectText = WeaponJson.string(object, "connect", path);
        try {
            return new WeaponConnectEndpoint(slot, new ConnectName(connectText));
        } catch (IllegalArgumentException exception) {
            throw WeaponJson.problem(path + ".connect", "invalid_name", exception.getMessage());
        }
    }

    private static Map<MarkerName, WeaponMarkerExport> markers(JsonObject object) {
        Map<MarkerName, WeaponMarkerExport> result = new TreeMap<>();
        for (String key : object.keySet().stream().sorted().toList()) {
            String path = "$.markers." + key;
            MarkerName exportName;
            try {
                exportName = new MarkerName(key);
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem(path, "invalid_name", exception.getMessage());
            }
            JsonObject value = WeaponJson.object(object, key, "$.markers");
            WeaponJson.onlyFields(value, path, MARKER_FIELDS);
            WeaponSlotId slot = slotName(WeaponJson.string(value, "slot", path), path + ".slot");
            String markerText = WeaponJson.string(value, "marker", path);
            MarkerName marker;
            try {
                marker = new MarkerName(markerText);
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem(path + ".marker", "invalid_name", exception.getMessage());
            }
            result.put(exportName, new WeaponMarkerExport(slot, marker));
        }
        return result;
    }

    private static WeaponSlotId slotName(String value, String path) {
        try {
            return new WeaponSlotId(value);
        } catch (IllegalArgumentException exception) {
            throw WeaponJson.problem(path, "invalid_name", exception.getMessage());
        }
    }
}
