package org.hismeo.crystallib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonUtil {
    public static boolean tryGetBoolean(JsonObject jsonObject, String name, Boolean defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsBoolean();
    }

    public static int tryGetInt(JsonObject jsonObject, String name) {
        return tryGetInt(jsonObject, name, 0);
    }

    public static int tryGetInt(JsonObject jsonObject, String name, int defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsInt();
    }

    public static float tryGetFloat(JsonObject jsonObject, String name) {
        return tryGetFloat(jsonObject, name, 0);
    }

    public static float tryGetFloat(JsonObject jsonObject, String name, float defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsFloat();
    }

    public static String tryGetString(JsonObject jsonObject, String name) {
        return tryGetString(jsonObject, name, null);
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
