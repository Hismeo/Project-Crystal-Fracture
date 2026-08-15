package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

final class WeaponStrictJsonParser {
    private WeaponStrictJsonParser() {
    }

    static JsonElement parse(Reader source) {
        try {
            JsonReader reader = new JsonReader(source);
            reader.setLenient(false);
            JsonElement result = read(reader, "$" );
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw WeaponJson.problem("$", "invalid_json", "JSON must contain exactly one root value");
            }
            return result;
        } catch (IOException | NumberFormatException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            throw WeaponJson.problem("$", "invalid_json", message);
        }
    }

    static JsonElement parse(String source) {
        return parse(new StringReader(source));
    }

    private static JsonElement read(JsonReader reader, String path) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, path);
            case BEGIN_ARRAY -> readArray(reader, path);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw WeaponJson.problem(path, "invalid_json", "unexpected token " + reader.peek());
        };
    }

    private static JsonObject readObject(JsonReader reader, String path) throws IOException {
        JsonObject result = new JsonObject();
        Set<String> names = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            String memberPath = WeaponJson.member(path, name);
            if (!names.add(name)) {
                throw WeaponJson.problem(memberPath, "duplicate_field", "duplicate field '" + name + "'");
            }
            result.add(name, read(reader, memberPath));
        }
        reader.endObject();
        return result;
    }

    private static JsonArray readArray(JsonReader reader, String path) throws IOException {
        JsonArray result = new JsonArray();
        reader.beginArray();
        int index = 0;
        while (reader.hasNext()) {
            result.add(read(reader, path + "[" + index + "]"));
            index++;
        }
        reader.endArray();
        return result;
    }
}
