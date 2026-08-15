package org.hismeo.crystalfracture.weapon.internal.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.CrystalFracture;
import org.hismeo.crystalfracture.weapon.internal.reload.WeaponResourceBatchLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Loads the mod's own static data definitions on a dedicated client process. */
public final class BundledWeaponDefinitions {
    private static final String ROOT = "/data/crystal_fracture/crystal_fracture/";
    private static final String MANIFEST = ROOT + "weapon_manifest.json";

    private BundledWeaponDefinitions() {
    }

    public static synchronized long publishIfEmpty(WeaponRegistryManager registry) {
        Objects.requireNonNull(registry, "registry");
        if (registry.generation() != 0L) {
            return registry.generation();
        }
        Map<ResourceLocation, String> resources = loadResources();
        var result = new WeaponResourceBatchLoader().load(resources);
        if (result.snapshot().isEmpty()) {
            throw new IllegalStateException("Bundled weapon definitions are invalid: " + result.problems());
        }
        return registry.publish(result.snapshot().orElseThrow());
    }

    static Map<ResourceLocation, String> loadResources() {
        JsonObject manifest = JsonParser.parseString(read(MANIFEST)).getAsJsonObject();
        Map<ResourceLocation, String> resources = new TreeMap<>();
        add(resources, manifest.getAsJsonArray("part_types"), "weapon_part_types");
        add(resources, manifest.getAsJsonArray("parts"), "weapon_parts");
        add(resources, manifest.getAsJsonArray("schemas"), "weapon_schemas");
        return resources;
    }

    private static void add(
            Map<ResourceLocation, String> resources,
            JsonArray names,
            String directory
    ) {
        if (names == null) {
            throw new IllegalStateException("Missing " + directory + " in " + MANIFEST);
        }
        names.forEach(element -> {
            String relative = directory + "/" + element.getAsString() + ".json";
            resources.put(
                    ResourceLocation.fromNamespaceAndPath(
                            CrystalFracture.MODID, "crystal_fracture/" + relative),
                    read(ROOT + relative));
        });
    }

    private static String read(String path) {
        try (InputStream stream = BundledWeaponDefinitions.class.getResourceAsStream(path)) {
            return new String(Objects.requireNonNull(stream, path).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read bundled weapon resource " + path, failure);
        }
    }
}
