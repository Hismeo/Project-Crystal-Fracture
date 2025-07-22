package org.hismeo.crystallib.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.Function;
import java.util.function.IntFunction;

public class JsonUtil {
    public static <T> T[] readConfigArray(JsonObject configObject, String key, Function<JsonElement, T> parser, IntFunction<T[]> arraySupplier) {
        JsonArray array = configObject.getAsJsonArray(key);
        if (array == null) return arraySupplier.apply(0);

        T[] result = arraySupplier.apply(array.size());
        for (int i = 0; i < result.length; i++) {
            result[i] = parser.apply(array.get(i));
        }
        return result;
    }

    public static Boolean tryGetBoolean(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? null : jsonObject.get(name).getAsBoolean();
    }

    public static Boolean tryGetBoolean(JsonObject jsonObject, String name, Boolean defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsBoolean();
    }

    public static Integer tryGetInt(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? null : jsonObject.get(name).getAsInt();
    }

    public static Integer tryGetInt(JsonObject jsonObject, String name, Integer defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsInt();
    }

    public static Float tryGetFloat(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? null : jsonObject.get(name).getAsFloat();
    }

    public static Float tryGetFloat(JsonObject jsonObject, String name, Float defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsFloat();
    }

    public static String tryGetString(JsonObject jsonObject, String name) {
        return jsonObject.get(name) == null ? null : jsonObject.get(name).getAsString();
    }

    public static String tryGetString(JsonObject jsonObject, String name, String defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsString();
    }

    public static JsonElement tryGet(JsonObject jsonObject, String name) {
        if (jsonObject.has(name)) {
            return jsonObject.get(name);
        }
        return null;
    }
}
