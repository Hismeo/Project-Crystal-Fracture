package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartId;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartDefinition;

import java.io.Reader;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class WeaponPartLoader {
    private static final Set<String> FIELDS = Set.of("id", "type", "visual", "connects", "markers");
    private static final Set<String> VISUAL_FIELDS = Set.of("model");

    public LoadedWeaponDefinition<WeaponPartDefinition> load(WeaponPartId id, Reader json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    public LoadedWeaponDefinition<WeaponPartDefinition> load(WeaponPartId id, String json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    private static WeaponPartDefinition parse(WeaponPartId resourceId, JsonElement element) {
        JsonObject root = WeaponJson.rootObject(element);
        WeaponJson.onlyFields(root, "$", FIELDS);

        String declaredIdText = WeaponJson.string(root, "id", "$" );
        var declaredId = WeaponJson.resourceLocation(declaredIdText, "$.id", "weapon part id");
        if (!resourceId.value().equals(declaredId)) {
            throw WeaponJson.problem("$.id", "id_mismatch",
                    "declared id " + declaredId + " does not match resource id " + resourceId);
        }

        String typeText = WeaponJson.string(root, "type", "$" );
        WeaponPartTypeId type = new WeaponPartTypeId(
                WeaponJson.resourceLocation(typeText, "$.type", "weapon part type id"));

        JsonObject visual = WeaponJson.object(root, "visual", "$" );
        WeaponJson.onlyFields(visual, "$.visual", VISUAL_FIELDS);
        String modelText = WeaponJson.string(visual, "model", "$.visual");

        return new WeaponPartDefinition(
                resourceId,
                type,
                WeaponJson.resourceLocation(modelText, "$.visual.model", "visual model"),
                connects(WeaponJson.object(root, "connects", "$")),
                markers(WeaponJson.object(root, "markers", "$"))
        );
    }

    private static Map<ConnectName, String> connects(JsonObject object) {
        Map<ConnectName, String> result = new TreeMap<>();
        for (String key : object.keySet().stream().sorted().toList()) {
            ConnectName name;
            try {
                name = new ConnectName(key);
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem("$.connects." + key, "invalid_name", exception.getMessage());
            }
            String locator = WeaponJson.string(object, key, "$.connects");
            requireLocator(locator, "$.connects." + key);
            result.put(name, locator);
        }
        return result;
    }

    private static Map<MarkerName, String> markers(JsonObject object) {
        Map<MarkerName, String> result = new TreeMap<>();
        for (String key : object.keySet().stream().sorted().toList()) {
            MarkerName name;
            try {
                name = new MarkerName(key);
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem("$.markers." + key, "invalid_name", exception.getMessage());
            }
            String locator = WeaponJson.string(object, key, "$.markers");
            requireLocator(locator, "$.markers." + key);
            result.put(name, locator);
        }
        return result;
    }

    private static void requireLocator(String value, String path) {
        if (value.isBlank()) {
            throw WeaponJson.problem(path, "invalid_locator_name", "locator/node name must not be blank");
        }
    }
}
