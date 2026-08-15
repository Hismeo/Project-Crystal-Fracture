package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.api.ConnectName;
import org.hismeo.crystalfracture.weapon.api.MarkerName;
import org.hismeo.crystalfracture.weapon.api.WeaponPartTypeId;
import org.hismeo.crystalfracture.weapon.definition.WeaponPartTypeDefinition;

import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public final class WeaponPartTypeLoader {
    private static final Set<String> FIELDS = Set.of("required_connects", "required_markers");

    public LoadedWeaponDefinition<WeaponPartTypeDefinition> load(WeaponPartTypeId id, Reader json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    public LoadedWeaponDefinition<WeaponPartTypeDefinition> load(WeaponPartTypeId id, String json) {
        return WeaponLoaderSupport.load(id.value(), json, element -> parse(id, element));
    }

    private static WeaponPartTypeDefinition parse(WeaponPartTypeId id, JsonElement element) {
        var root = WeaponJson.rootObject(element);
        WeaponJson.onlyFields(root, "$", FIELDS);
        return new WeaponPartTypeDefinition(
                id,
                connectNames(WeaponJson.array(root, "required_connects", "$"), "$.required_connects"),
                markerNames(WeaponJson.array(root, "required_markers", "$"), "$.required_markers")
        );
    }

    private static Set<ConnectName> connectNames(JsonArray array, String path) {
        Set<ConnectName> result = new TreeSet<>();
        Set<String> values = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String value = WeaponJson.arrayString(array, index, path);
            if (!values.add(value)) {
                throw WeaponJson.problem(path + "[" + index + "]", "duplicate_name", "duplicate connect name '" + value + "'");
            }
            try {
                result.add(new ConnectName(value));
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem(path + "[" + index + "]", "invalid_name", exception.getMessage());
            }
        }
        return result;
    }

    private static Set<MarkerName> markerNames(JsonArray array, String path) {
        Set<MarkerName> result = new TreeSet<>();
        Set<String> values = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String value = WeaponJson.arrayString(array, index, path);
            if (!values.add(value)) {
                throw WeaponJson.problem(path + "[" + index + "]", "duplicate_name", "duplicate marker name '" + value + "'");
            }
            try {
                result.add(new MarkerName(value));
            } catch (IllegalArgumentException exception) {
                throw WeaponJson.problem(path + "[" + index + "]", "invalid_name", exception.getMessage());
            }
        }
        return result;
    }
}
