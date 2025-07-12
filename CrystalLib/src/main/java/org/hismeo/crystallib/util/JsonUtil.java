package org.hismeo.crystallib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonUtil {
    public static Boolean tryGetBoolean(JsonObject jsonObject, String name) {
        return tryGetBoolean(jsonObject, name, null);
    }

    public static Boolean tryGetBoolean(JsonObject jsonObject, String name, Boolean defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsBoolean();
    }

    public static Integer tryGetInt(JsonObject jsonObject, String name) {
        return tryGetInt(jsonObject, name, null);
    }

    public static Integer tryGetInt(JsonObject jsonObject, String name, Integer defaultValue) {
        return jsonObject.get(name) == null ? defaultValue : jsonObject.get(name).getAsInt();
    }

    public static Float tryGetFloat(JsonObject jsonObject, String name) {
        return tryGetFloat(jsonObject, name, null);
    }

    public static Float tryGetFloat(JsonObject jsonObject, String name, Float defaultValue) {
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
