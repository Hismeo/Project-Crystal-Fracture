package org.hismeo.actionguide.internal.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.math.BigDecimal;

final class JsonFields {
    private JsonFields() {
    }

    static JsonObject object(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new JsonParseException(path + "." + name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static JsonObject optionalObject(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        if (value == null || value.isJsonNull()) {
            return new JsonObject();
        }
        if (!value.isJsonObject()) {
            throw new JsonParseException("$." + name + " must be an object");
        }
        return value.getAsJsonObject();
    }

    static String string(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException(path + "." + name + " must be a string");
        }
        String result = value.getAsString().trim();
        if (result.isEmpty()) {
            throw new JsonParseException(path + "." + name + " must not be blank");
        }
        return result;
    }

    static String optionalString(JsonObject parent, String name, String fallback) {
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    static int integer(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + "." + name + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new JsonParseException(path + "." + name + " must be an integer", exception);
        }
    }

    static int optionalInteger(JsonObject parent, String name, int fallback) {
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    static BigDecimal decimal(JsonObject parent, String name, String path) {
        JsonElement value = parent.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + "." + name + " must be a finite number");
        }
        try {
            return value.getAsBigDecimal();
        } catch (NumberFormatException exception) {
            throw new JsonParseException(path + "." + name + " must be a finite number", exception);
        }
    }

    static boolean optionalBoolean(JsonObject parent, String name, boolean fallback) {
        JsonElement value = parent.get(name);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }
}
