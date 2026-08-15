package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.Set;

final class WeaponJson {
    private WeaponJson() {
    }

    static JsonObject rootObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw problem("$", "invalid_json_type", "root value must be an object");
        }
        return element.getAsJsonObject();
    }

    static void onlyFields(JsonObject object, String path, Set<String> allowed) {
        object.keySet().stream().sorted().forEach(name -> {
            if (!allowed.contains(name)) {
                throw problem(member(path, name), "unknown_field", "unknown field '" + name + "'");
            }
        });
    }

    static JsonObject object(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        String fieldPath = member(path, name);
        if (value == null || !value.isJsonObject()) {
            throw problem(fieldPath, "invalid_json_type", "field must be an object");
        }
        return value.getAsJsonObject();
    }

    static JsonArray array(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        String fieldPath = member(path, name);
        if (value == null || !value.isJsonArray()) {
            throw problem(fieldPath, "invalid_json_type", "field must be an array");
        }
        return value.getAsJsonArray();
    }

    static String string(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        String fieldPath = member(path, name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw problem(fieldPath, "invalid_json_type", "field must be a string");
        }
        return value.getAsString();
    }

    static String arrayString(JsonArray array, int index, String path) {
        JsonElement value = array.get(index);
        String itemPath = path + "[" + index + "]";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw problem(itemPath, "invalid_json_type", "value must be a string");
        }
        return value.getAsString();
    }

    static JsonObject arrayObject(JsonArray array, int index, String path) {
        JsonElement value = array.get(index);
        String itemPath = path + "[" + index + "]";
        if (!value.isJsonObject()) {
            throw problem(itemPath, "invalid_json_type", "value must be an object");
        }
        return value.getAsJsonObject();
    }

    static int integer(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        String fieldPath = member(path, name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw problem(fieldPath, "invalid_json_type", "field must be an integer");
        }
        try {
            return new BigDecimal(value.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw problem(fieldPath, "invalid_json_type", "field must be an integer");
        }
    }

    static ResourceLocation resourceLocation(String value, String path, String label) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw problem(path, "invalid_resource_location", "invalid " + label + ": " + value);
        }
        return parsed;
    }

    static String member(String parent, String name) {
        return "$".equals(parent) ? "$." + name : parent + "." + name;
    }

    static WeaponJsonException problem(String path, String code, String message) {
        return new WeaponJsonException(path, code, message);
    }
}
