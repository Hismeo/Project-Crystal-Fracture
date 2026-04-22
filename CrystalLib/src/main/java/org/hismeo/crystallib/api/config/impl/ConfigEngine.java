package org.hismeo.crystallib.api.config.impl;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.hismeo.crystallib.CrystalLib;
import org.hismeo.crystallib.api.config.ConfigFormat;
import org.hismeo.crystallib.api.config.ConfigHandle;
import org.hismeo.crystallib.api.config.ConfigTypeHandler;
import org.hismeo.crystallib.api.config.ConfigValue;
import org.hismeo.crystallib.api.config.CrystalConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigEngine {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigEngine() {}

    public static <T> ConfigHandle<T> createHandle(
            Class<T> configClass,
            CrystalConfig config,
            Map<Class<?>, ConfigTypeHandler<?>> handlers
    ) {
        List<ConfigField> fields = collectFields(configClass);
        boolean needsInstance = fields.stream().anyMatch(f -> !Modifier.isStatic(f.field().getModifiers()));
        T instance = createInstance(configClass, needsInstance);
        Path path = resolvePath(config);
        ConfigStorage storage = createStorage(config.format(), path);
        ConfigSchema<T> schema = new ConfigSchema<>(instance, fields, storage, path, copyHandlers(handlers));
        return new ConfigHandle<>(configClass, instance, path, schema::load, schema::save);
    }

    private static Map<Class<?>, ConfigTypeHandler<?>> copyHandlers(Map<Class<?>, ConfigTypeHandler<?>> handlers) {
        return new ConcurrentHashMap<>(handlers);
    }

    private static List<ConfigField> collectFields(Class<?> configClass) {
        List<ConfigField> fields = new ArrayList<>();
        for (Field field : configClass.getDeclaredFields()) {
            ConfigValue configValue = field.getAnnotation(ConfigValue.class);
            if (configValue == null) continue;
            if (Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true);
            String key = configValue.key().isBlank() ? field.getName() : configValue.key();
            fields.add(new ConfigField(
                    field,
                    key,
                    Arrays.asList(configValue.aliases()),
                    configValue.comment(),
                    configValue.allowNull(),
                    configValue.notBlank(),
                    configValue.regex(),
                    configValue.min(),
                    configValue.max(),
                    configValue.minSize(),
                    configValue.maxSize()
            ));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("No @ConfigValue field found: " + configClass.getName());
        }
        return fields;
    }

    private static <T> T createInstance(Class<T> configClass, boolean required) {
        try {
            Constructor<T> constructor = configClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable ignored) {
            if (required) {
                throw new IllegalArgumentException("Config class needs no-arg constructor: " + configClass.getName());
            }
            return null;
        }
    }

    private static Path resolvePath(CrystalConfig config) {
        String fileName = config.fileName().isBlank()
                ? config.modId() + "-" + config.scope().suffix()
                : config.fileName();
        String ext = "." + config.format().extension();
        if (!fileName.endsWith(ext)) fileName += ext;
        return FMLPaths.CONFIGDIR.get().resolve(fileName);
    }

    private static ConfigStorage createStorage(ConfigFormat format, Path path) {
        return switch (format) {
            case TOML -> new TomlStorage(path);
            case JSON -> new JsonStorage(path);
            case PROPERTIES -> new PropertiesStorage(path);
        };
    }

    private static final class ConfigSchema<T> {
        private final T instance;
        private final List<ConfigField> fields;
        private final ConfigStorage storage;
        private final Path path;
        private final Map<Class<?>, ConfigTypeHandler<?>> handlers;

        private ConfigSchema(
                T instance,
                List<ConfigField> fields,
                ConfigStorage storage,
                Path path,
                Map<Class<?>, ConfigTypeHandler<?>> handlers
        ) {
            this.instance = instance;
            this.fields = fields;
            this.storage = storage;
            this.path = path;
            this.handlers = handlers;
        }

        private void load() {
            try {
                storage.load();
                boolean touched = false;
                for (ConfigField configField : fields) {
                    Field field = configField.field();
                    Object defaultValue = readField(field, instance);
                    RawValue rawValue = readWithAliases(storage, configField);
                    Object raw = rawValue.value();
                    Object converted = deserialize(raw, field.getGenericType(), defaultValue, configField, handlers);
                    converted = applyConstraints(converted, defaultValue, configField);
                    writeField(field, instance, converted);
                    if (!storage.contains(configField.key()) || !rawValue.primaryKey()) {
                        storage.set(configField.key(), serialize(converted, field.getGenericType(), handlers));
                        touched = true;
                    }
                    if (!configField.comment().isBlank()) storage.setComment(configField.key(), configField.comment());
                }
                if (touched) storage.save();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load config: " + path, e);
            } finally {
                storage.close();
            }
        }

        private void save() {
            try {
                storage.load();
                for (ConfigField configField : fields) {
                    Field field = configField.field();
                    Object value = readField(field, instance);
                    storage.set(configField.key(), serialize(value, field.getGenericType(), handlers));
                    if (!configField.comment().isBlank()) storage.setComment(configField.key(), configField.comment());
                }
                storage.save();
            } catch (IOException e) {
                throw new RuntimeException("Failed to save config: " + path, e);
            } finally {
                storage.close();
            }
        }
    }

    private record ConfigField(
            Field field,
            String key,
            List<String> aliases,
            String comment,
            boolean allowNull,
            boolean notBlank,
            String regex,
            double min,
            double max,
            int minSize,
            int maxSize
    ) {}

    private record RawValue(Object value, boolean primaryKey) {}

    private interface ConfigStorage {
        void load() throws IOException;

        boolean contains(String key);

        Object get(String key);

        void set(String key, Object value);

        void setComment(String key, String comment);

        void save() throws IOException;

        void close();
    }

    private static final class TomlStorage implements ConfigStorage {
        private final Path path;
        private CommentedFileConfig config;

        private TomlStorage(Path path) {
            this.path = path;
        }

        @Override
        public void load() throws IOException {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.createFile(path);
            config = CommentedFileConfig.builder(path).sync().build();
            config.load();
        }

        @Override
        public boolean contains(String key) {
            return config.contains(key);
        }

        @Override
        public Object get(String key) {
            return config.get(key);
        }

        @Override
        public void set(String key, Object value) {
            config.set(key, value);
        }

        @Override
        public void setComment(String key, String comment) {
            config.setComment(key, comment);
        }

        @Override
        public void save() {
            config.save();
        }

        @Override
        public void close() {
            if (config != null) config.close();
            config = null;
        }
    }

    private static final class JsonStorage implements ConfigStorage {
        private final Path path;
        private JsonObject root = new JsonObject();

        private JsonStorage(Path path) {
            this.path = path;
        }

        @Override
        public void load() throws IOException {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                root = new JsonObject();
                return;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement element = JsonParser.parseReader(reader);
                root = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
            }
        }

        @Override
        public boolean contains(String key) {
            return getPath(root, key) != null;
        }

        @Override
        public Object get(String key) {
            JsonElement element = getPath(root, key);
            return fromJsonElement(element);
        }

        @Override
        public void set(String key, Object value) {
            setPath(root, key, toJsonElement(value));
        }

        @Override
        public void setComment(String key, String comment) {}

        @Override
        public void save() throws IOException {
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        }

        @Override
        public void close() {}
    }

    private static final class PropertiesStorage implements ConfigStorage {
        private final Path path;
        private final Properties properties = new Properties();

        private PropertiesStorage(Path path) {
            this.path = path;
        }

        @Override
        public void load() throws IOException {
            properties.clear();
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) return;
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
        }

        @Override
        public boolean contains(String key) {
            return properties.containsKey(key);
        }

        @Override
        public Object get(String key) {
            return properties.getProperty(key);
        }

        @Override
        public void set(String key, Object value) {
            if (isSimple(value)) {
                properties.setProperty(key, String.valueOf(value));
            } else {
                properties.setProperty(key, GSON.toJson(value));
            }
        }

        @Override
        public void setComment(String key, String comment) {}

        @Override
        public void save() throws IOException {
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "Crystal Config");
            }
        }

        @Override
        public void close() {}
    }

    private static Object readField(Field field, Object instance) {
        try {
            return field.get(Modifier.isStatic(field.getModifiers()) ? null : instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeField(Field field, Object instance, Object value) {
        try {
            field.set(Modifier.isStatic(field.getModifiers()) ? null : instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object deserialize(
            Object raw,
            Type targetType,
            Object defaultValue,
            ConfigField fieldInfo,
            Map<Class<?>, ConfigTypeHandler<?>> handlers
    ) {
        if (raw == null) return defaultValue;
        raw = normalizeRaw(raw);

        try {
            if (targetType instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass) {
                    if (Collection.class.isAssignableFrom(rawClass)) {
                        Type elementType = parameterizedType.getActualTypeArguments()[0];
                        List<Object> source = asList(raw);
                        List<Object> result = new ArrayList<>(source.size());
                        for (Object element : source) {
                            result.add(deserialize(element, elementType, null, fieldInfo, handlers));
                        }
                        return result;
                    }
                    if (Map.class.isAssignableFrom(rawClass)) {
                        Type keyType = parameterizedType.getActualTypeArguments()[0];
                        Type valueType = parameterizedType.getActualTypeArguments()[1];
                        Map<?, ?> source = asMap(raw);
                        Map<Object, Object> result = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : source.entrySet()) {
                            Object key = convertMapKey(entry.getKey(), keyType);
                            Object value = deserialize(entry.getValue(), valueType, null, fieldInfo, handlers);
                            result.put(key, value);
                        }
                        return result;
                    }
                }
                return deserialize(raw, parameterizedType.getRawType(), defaultValue, fieldInfo, handlers);
            }

            if (!(targetType instanceof Class<?> type)) return defaultValue;

            ConfigTypeHandler handler = resolveHandler(type, handlers);
            if (handler != null) return handler.decode(raw, targetType);

            if (type == String.class) return String.valueOf(raw);
            if (type == boolean.class || type == Boolean.class) return toBoolean(raw, defaultValue);

            if (isNumberType(type)) return clampNumber(toDouble(raw), fieldInfo, type);

            if (type.isEnum()) {
                String name = String.valueOf(raw).toUpperCase(Locale.ROOT);
                return Enum.valueOf((Class<? extends Enum>) type, name);
            }

            if (type.isArray()) {
                Type componentType = type.getComponentType();
                List<Object> source = asList(raw);
                Object array = Array.newInstance((Class<?>) componentType, source.size());
                for (int i = 0; i < source.size(); i++) {
                    Array.set(array, i, deserialize(source.get(i), componentType, null, fieldInfo, handlers));
                }
                return array;
            }

            if (Collection.class.isAssignableFrom(type)) {
                return new ArrayList<>(asList(raw));
            }

            if (Map.class.isAssignableFrom(type)) {
                return new LinkedHashMap<>(asMap(raw));
            }

            Object obj = createPojo(type);
            Map<?, ?> source = asMap(raw);
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                Object childRaw = source.get(field.getName());
                Object childDefault = readField(field, obj);
                Object childValue = deserialize(childRaw, field.getGenericType(), childDefault, fieldInfo, handlers);
                writeField(field, obj, childValue);
            }
            return obj;
        } catch (Throwable e) {
            CrystalLib.LOGGER.warn("Config cast failed for key {}: {}", fieldInfo.key(), e.getMessage());
            return defaultValue;
        }
    }

    private static Object applyConstraints(Object value, Object defaultValue, ConfigField fieldInfo) {
        if (value == null) {
            return fieldInfo.allowNull() ? null : defaultValue;
        }

        if (value instanceof String str) {
            if (fieldInfo.notBlank() && str.trim().isEmpty()) return defaultValue;
            if (!fieldInfo.regex().isBlank() && !str.matches(fieldInfo.regex())) return defaultValue;
            int len = str.length();
            if (len < fieldInfo.minSize() || len > fieldInfo.maxSize()) return defaultValue;
            return str;
        }

        int size = sizeOf(value);
        if (size >= 0 && (size < fieldInfo.minSize() || size > fieldInfo.maxSize())) {
            return defaultValue;
        }
        return value;
    }

    private static int sizeOf(Object value) {
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        if (value != null && value.getClass().isArray()) return Array.getLength(value);
        return -1;
    }

    private static RawValue readWithAliases(ConfigStorage storage, ConfigField fieldInfo) {
        if (storage.contains(fieldInfo.key())) {
            return new RawValue(storage.get(fieldInfo.key()), true);
        }
        for (String alias : fieldInfo.aliases()) {
            if (alias == null || alias.isBlank()) continue;
            if (storage.contains(alias)) {
                return new RawValue(storage.get(alias), false);
            }
        }
        return new RawValue(null, false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object serialize(Object value, Type declaredType, Map<Class<?>, ConfigTypeHandler<?>> handlers) {
        if (value == null) return null;

        ConfigTypeHandler handler = resolveHandler(value.getClass(), handlers);
        if (handler != null) return normalizeSimple(handler.encode(value));

        if (isSimple(value)) return value;
        if (value instanceof Enum<?> enumValue) return enumValue.name();

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), serialize(entry.getValue(), Object.class, handlers));
            }
            return result;
        }

        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) {
                result.add(serialize(item, Object.class, handlers));
            }
            return result;
        }

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> result = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                result.add(serialize(Array.get(value, i), Object.class, handlers));
            }
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object child = readField(field, value);
            result.put(field.getName(), serialize(child, field.getGenericType(), handlers));
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static ConfigTypeHandler resolveHandler(Class<?> type, Map<Class<?>, ConfigTypeHandler<?>> handlers) {
        ConfigTypeHandler<?> exact = handlers.get(type);
        if (exact != null) return exact;
        for (Map.Entry<Class<?>, ConfigTypeHandler<?>> entry : handlers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Object createPojo(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable e) {
            throw new IllegalArgumentException("Custom config type needs no-arg constructor: " + type.getName(), e);
        }
    }

    private static Object convertMapKey(Object raw, Type keyType) {
        if (!(keyType instanceof Class<?> keyClass)) return String.valueOf(raw);
        if (keyClass == String.class) return String.valueOf(raw);
        if (keyClass.isEnum()) {
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object enumValue = Enum.valueOf((Class<? extends Enum>) keyClass, String.valueOf(raw).toUpperCase(Locale.ROOT));
            return enumValue;
        }
        if (isNumberType(keyClass)) return castNumber(toDouble(raw), keyClass);
        if (keyClass == Boolean.class || keyClass == boolean.class) return toBoolean(raw, false);
        return String.valueOf(raw);
    }

    private static Object normalizeRaw(Object raw) {
        if (raw instanceof UnmodifiableConfig config) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (UnmodifiableConfig.Entry entry : config.entrySet()) {
                result.put(entry.getKey(), normalizeRaw(entry.getValue()));
            }
            return result;
        }
        if (raw instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object element : collection) result.add(normalizeRaw(element));
            return result;
        }
        if (raw instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    return fromJsonElement(JsonParser.parseString(trimmed));
                } catch (Throwable ignored) {}
            }
        }
        return raw;
    }

    private static List<Object> asList(Object raw) {
        raw = normalizeRaw(raw);
        if (raw instanceof List<?> list) return new ArrayList<>(list);
        if (raw instanceof Collection<?> collection) return new ArrayList<>(collection);
        if (raw != null && raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) list.add(Array.get(raw, i));
            return list;
        }
        return List.of();
    }

    private static Map<?, ?> asMap(Object raw) {
        raw = normalizeRaw(raw);
        if (raw instanceof Map<?, ?> map) return map;
        return Map.of();
    }

    private static boolean isSimple(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }

    private static Object normalizeSimple(Object value) {
        if (value instanceof Character c) return String.valueOf(c);
        return value;
    }

    private static boolean isNumberType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class;
    }

    private static Object clampNumber(double value, ConfigField configField, Class<?> type) {
        double clamped = Math.min(configField.max(), Math.max(configField.min(), value));
        return castNumber(clamped, type);
    }

    private static Object castNumber(double value, Class<?> type) {
        if (type == int.class || type == Integer.class) return (int) Math.round(value);
        if (type == long.class || type == Long.class) return (long) Math.round(value);
        if (type == float.class || type == Float.class) return (float) value;
        if (type == short.class || type == Short.class) return (short) Math.round(value);
        if (type == byte.class || type == Byte.class) return (byte) Math.round(value);
        return value;
    }

    private static boolean toBoolean(Object raw, Object defaultValue) {
        if (raw instanceof Boolean b) return b;
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (Objects.equals(text, "true")) return true;
        if (Objects.equals(text, "false")) return false;
        if (defaultValue instanceof Boolean b) return b;
        return false;
    }

    private static double toDouble(Object raw) {
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(raw));
    }

    private static Object fromJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) return element.getAsBoolean();
            if (element.getAsJsonPrimitive().isNumber()) return element.getAsNumber();
            if (element.getAsJsonPrimitive().isString()) return element.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) list.add(fromJsonElement(item));
            return list;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                map.put(e.getKey(), fromJsonElement(e.getValue()));
            }
            return map;
        }
        return null;
    }

    private static JsonElement toJsonElement(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        return GSON.toJsonTree(value);
    }

    private static JsonElement getPath(JsonObject root, String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        JsonElement current = root;
        for (String part : parts) {
            if (!(current instanceof JsonObject object) || !object.has(part)) return null;
            current = object.get(part);
        }
        return current;
    }

    private static void setPath(JsonObject root, String dottedKey, JsonElement value) {
        String[] parts = dottedKey.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String key = parts[i];
            JsonElement child = current.get(key);
            JsonObject childObject;
            if (child instanceof JsonObject object) {
                childObject = object;
            } else {
                childObject = new JsonObject();
                current.add(key, childObject);
            }
            current = childObject;
        }
        current.add(parts[parts.length - 1], value == null ? JsonNull.INSTANCE : value);
    }
}
